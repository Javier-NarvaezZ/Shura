# Referencia: Metrolist

Análisis de arquitectura de [Metrolist](https://github.com/MetrolistGroup/Metrolist) (GPL-3.0) como referencia para Shura. Aquí solo se describen nombres de módulos, archivos, clases, flujos y decisiones de diseño. No se copió código.

- **Revisado:** 2026-10-04, commit `f758c86` de `main` (2026-10-03).
- **Método:** lectura directa del código en `~/ref/Metrolist`, análisis de dependencias entre clases y lectura del repositorio público de InnerTubeX.
- **Convención:** lo que no se pudo confirmar en el código aparece marcado como **No confirmado**.

---

## 0. Resumen

- Metrolist tiene solo dos módulos Gradle: `:app` (Android) e `:innertube` (biblioteca Android). Casi todo lo difícil ya no vive en el repo, sino en una dependencia externa: **InnerTubeX** (`com.github.MetrolistGroup.innertubex`, vía JitPack, GPL-3.0). Esa biblioteca se encarga de construir las peticiones, la sesión, los reintentos, el descifrado (cipher), la elección de cliente y la extracción del stream.
- **InnerTubeX ya es Kotlin Multiplatform**, con targets `android`, `jvm("desktop")` e `iosArm64`. Es la pieza más relevante para Shura.
- El `:innertube` de Metrolist ahora es una **fachada de compatibilidad**: conserva los modelos de respuesta y los parsers (`pages/`) propios y delega el transporte en InnerTubeX.
- **No usa NewPipeExtractor.** El descifrado usa configuraciones remotas de **zemer-cipher** ejecutadas en **QuickJS** (`quickjs-kt`). Si fallan, recurre al solver **EJS de yt-dlp** y después a un parser por regex. El PoToken se obtiene con **BotGuard dentro de un `WebView` de Android**.
- La reproducción está concentrada en un solo `MusicService` (`MediaLibraryService` de Media3) de unas **5000 líneas**. Es la clase que más áreas funcionales une: toca unas 30 (EQ, Discord, widgets, alarmas, Cast, scrobbling, recuperación de errores…).
- Lo frágil está en la resolución del stream: versiones de cliente fijadas en código, un catálogo de clientes que cambia entre versiones menores, configs del cipher que se publican casi a diario y PoToken atado al `WebView`.

---

## 1. Módulo InnerTube

### 1.1 Organización

| Lugar | Qué contiene |
|---|---|
| `innertube/.../InnerTube.kt` (~450 líneas) | Fachada sobre `com.metrolist.innertubex.InnerTube`. Expone `search`, `player`, `browse`, `next`, `getQueue`, `getTranscript`, `accountMenu`, `accountsList`, likes, suscripciones, edición de playlists, subida de canciones, etc. Crea el `HttpClient` de Ktor. |
| `innertube/.../YouTube.kt` (~3400 líneas) | Objeto `YouTube`, la API de alto nivel que usa la app (`home()`, `library()`, `playlist()`, `album()`, `artist()`, `lyrics()`, `transcript()`, `visitorData()`, `accountInfo()`…). Devuelve `Result<Page>`. |
| `innertube/.../models/` | Unos 40 modelos `@Serializable` que reflejan los *renderers* de YouTube (`MusicResponsiveListItemRenderer`, `MusicTwoRowItemRenderer`, `MusicShelfRenderer`, `SectionListRenderer`, `Tabs`, `Runs`, `Thumbnails`, `NavigationEndpoint`…) más `models/response/` (`BrowseResponse`, `NextResponse`, `PlayerResponse`, `SearchResponse`, …). |
| `innertube/.../pages/` | Unos 22 parsers que convierten respuestas crudas en páginas de dominio: `HomePage`, `AlbumPage`, `ArtistPage`, `PlaylistPage`, `SearchPage`, `NextPage`, `LibraryPage`, `PodcastPage`, `ChartsPage`, `MoodAndGenres`… El código compartido está en `PageHelper.kt`. |
| `innertube/.../models/YTItem.kt` | Modelo de dominio: `sealed class YTItem` con `SongItem`, `AlbumItem`, `PlaylistItem`, `ArtistItem`, `PodcastItem` y `EpisodeItem`. |
| `models/YouTubeClient.kt` | Solo un `typealias` a `innertubex.models.YouTubeClient`. |

**Decisión de diseño:** tres capas. La primera es el transporte y la sesión (InnerTubeX). La segunda son los DTO que reflejan JSON (`models/`). La tercera son los parsers a páginas de dominio (`pages/`) y a `YTItem`. En `YouTube.kt`, 26 de las 39 deserializaciones son `BrowseResponse`: casi todo el catálogo sale del endpoint `browse`.

### 1.2 Construcción de peticiones (InnerTubeX)

- **Base URL:** `https://music.youtube.com/youtubei/v1/`, con `Accept: application/json`.
- **Cliente HTTP** (lo crea la fachada de Metrolist): Ktor con motor OkHttp, `ContentNegotiation` con kotlinx-json (`ignoreUnknownKeys`, `explicitNulls = false`, `encodeDefaults`), gzip/deflate, HTTP/2, caché HTTP de 50 MB y soporte de proxy con autenticación. Cuando cambia el proxy, se recrea el transporte y se copia la sesión (`sessionSnapshot` → `replaceSession`) con un contador `transportGeneration`.
- **Contexto (`models/Context.kt` en InnerTubeX):** `client` (`clientName`, `clientVersion`, `userAgent`, `osName`, `osVersion`, `deviceMake`, `deviceModel`, `androidSdkVersion`, `platform`, `gl`, `hl`, `visitorData`), `thirdParty.embedUrl` (solo en clientes "embedded"), `request` y `user.onBehalfOfUser` (que es el `dataSyncId` cuando el cliente admite login).
- **Clientes (`YouTubeClient`):** cada uno es una constante con nombre, versión, UA y banderas `loginSupported`, `useSignatureTimestamp` e `isEmbedded`. Para *browse/search* la app usa casi siempre `WEB_REMIX` (12 usos) y `WEB` en algunos casos. Para *player* hay un catálogo aparte (ver §2).
- **Headers por petición:** `X-Goog-Api-Format-Version: 1`, `X-YouTube-Client-Name`, `X-YouTube-Client-Version`, `Origin`/`X-Origin`/`Referer` (los de music.youtube.com, o un referer de terceros en clientes embedded), `X-Goog-Visitor-Id`, y con login además `Cookie`, `X-Goog-AuthUser` y `Authorization: SAPISIDHASH <ts>_<sha1>` calculado a partir de la cookie `SAPISID`.
- **Reintentos:** InnerTubeX reintenta los estados transitorios con *backoff* y respeta `Retry-After`. Un 429 solo se reintenta si trae `Retry-After`. En *player* no reintenta el mismo cliente: pasa al siguiente.

### 1.3 Login, cookies, visitor data

- **Login (`ui/screens/LoginScreen.kt`):** un `WebView` de Android abre `accounts.google.com/ServiceLogin?continue=…music.youtube.com`. Después:
  - lee la cookie con `CookieManager.getCookie("https://music.youtube.com")`;
  - obtiene el `dataSyncId` inyectando JS en la página (`LoginJsInterface.onRetrieveDataSyncId`), y se queda con la parte anterior a `"||"`;
  - llama a `accountsList()` para elegir la cuenta o canal (marca o *brand account*).
  - Hay estados explícitos (`LoginStage`: `CheckingAccounts`, `SelectingAccount`, `SwitchingAccount`, `Authenticating`, `Completing`).
- **Persistencia:** DataStore guarda `InnerTubeCookieKey`, `VisitorDataKey`, `DataSyncIdKey` y datos de cuenta. `App.kt` los observa como `Flow` y los asigna a `YouTube.cookie`, `YouTube.visitorData` y `YouTube.dataSyncId`. Al cerrar sesión se borran las tres claves y se ponen a `null`.
- **Visitor data:** si no hay una guardada, se pide `YouTube.visitorData()`. InnerTubeX la obtiene de `https://www.youtube.com/sw.js_data` buscando `VISITOR_DATA` con regex, con un tope de 4 MB y deduplicación de peticiones simultáneas. La sanea antes de usarla (la v0.7.4 corrigió un bug con visitor data partida en varias líneas). `App.kt` espera hasta 12 s a tener visitor data al arrancar.
- **`useLoginForBrowse`** es configurable: permite navegar sin enviar credenciales aunque haya sesión.
- Ojo: InnerTubeX advierte que `cookie` y `SAPISID` son secretos que nunca deben registrarse en logs. Sus `toString()` los ocultan.

---

## 2. Resolución del stream

### 2.1 Quién hace qué

| Pieza | Dónde | Rol |
|---|---|---|
| `InnerTubeXPlayer` (objeto) | `app/.../utils/InnerTubeXPlayer.kt` | Único punto de entrada de extracción en la app. Monta un `ExtractionBundle` (`YouTubeCipherService` + `InnerTubeExtractor` + `YtConfigParserImpl`) por cada `transportGeneration`. |
| `InnerTubeExtractor`, `PlayerClientDirector` | InnerTubeX `extraction/` | Eligen el cliente, piden `/player`, aplican el cipher y seleccionan el formato. |
| `YouTubeCipherService` | InnerTubeX `cipher/` | Descifran la firma (`sig`) y el parámetro `n`. |
| `TokenProvider` (interfaz) | InnerTubeX; la implementa la app | Proporciona el PoToken. |
| `PoTokenGenerator`, `PoTokenWebView` | `app/.../utils/potoken/` | Generan el PoToken con BotGuard en un `WebView`. |
| `StreamUrlCache` | `app/.../playback/StreamUrlCache.kt` | Guarda en memoria las URL firmadas y su caducidad. |

### 2.2 Cipher: zemer-cipher → EJS → regex (no NewPipeExtractor)

`YouTubeCipherService` prueba tres capas en orden:
1. **Configs remotas tipo "zemer/Faraday".** `RemotePlayerConfigStore` descarga `player_configs.json` desde `raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json`. Ese archivo trae, para cada hash de `player.js`, las expresiones JS de `sig` y `n`. `ZemerCipherSolver` carga el `player.js` real en **QuickJS**, le inyecta dos funciones exportadas y comprueba la de `n` con una entrada de prueba antes de aceptarla. La app guarda la config en `SharedPreferences` (`AndroidPlayerConfigRepository`: JSON, ETag y fecha).
2. **EJS de yt-dlp** (`EjsChallengeSolver`, `yt_ejs/*.min.js` como recurso de la biblioteca), también ejecutado en QuickJS.
3. **`PlayerScriptParser`**, que extrae las funciones de `player.js` con regex.

Además, cachea el código de `player.js` por URL y los *solvers* ya compilados. Si aparece un hash de player desconocido, fuerza un refresco de la config remota (`refreshForUnknownPlayer`). Si YouTube rechaza un stream, puede refrescar la config de nuevo, con límite de frecuencia (`refreshAfterStreamRejection`).

**No confirmado:** `app/src/main/assets/solver/` (`yt.solver.core.js`, `meriyah.js`, `astring.js`, generados por yt-dlp/ejs) no se referencia desde ningún archivo Kotlin de la app, y InnerTubeX trae su propia copia en `yt_ejs/`. Parecen restos de una versión anterior, pero no lo verifiqué en tiempo de ejecución.

### 2.3 PoToken

- La app declara un `TokenProvider` con capacidad `WEB_BOTGUARD` y `usesWebView = true`.
- Flujo de `PoTokenWebView`:
  1. carga `assets/po_token.html` con base `https://www.youtube.com`;
  2. llama a `jnn/v1/Create`, ejecuta BotGuard y luego `jnn/v1/GenerateIT`;
  3. crea un *minter* en JS;
  4. genera un token por `videoId`.
- `PoTokenGenerator` genera **una vez por sesión** (ligado a `visitorData`) el token de *streaming* y después uno de *player* por vídeo. Recrea el `WebView` si caduca, si se cae el proceso de render o si cambia la sesión. Tiene un **timeout de 8 s**: si se pasa, sigue sin PoToken para que InnerTubeX caiga a clientes que no lo necesitan. Si el `WebView` del sistema está roto (`BadWebViewException`), lo desactiva para el resto de la ejecución.
- **No confirmado:** la estructura (`PoTokenWebView`, `getNewPoTokenGenerator`, `JavaScriptUtil.descramble`) se parece mucho a la implementación de la app NewPipe, pero el archivo no lo atribuye.

### 2.4 Elección de cliente y de formato

- **Catálogo de clientes de reproducción** (`PlaybackClientCatalog`): cada perfil declara `selectionMode` (`AUTOMATIC` o `PROBE_ONLY`), `priority`, transportes (`DIRECT`, `HLS`, `SABR`), reglas de PoToken (`REQUIRED`/`OPTIONAL`, ligado a `VIDEO_ID` o a `VISITOR_DATA`) y política de autenticación.
- **Puntuación** (`ContentAwareFallbackStrategy`): parte de la prioridad del perfil y la ajusta según el soporte del contenido, si el contenido es restringido (explícito, infantil, con restricción de edad o subido por el usuario) y hay o no login, la preferencia de transporte y si se pide vídeo. `ClientHealthTracker` registra qué clientes van fallando.
- **Metrolist pide solo `DIRECT`:** `allowHls = false` y `allowSabr = false`. Si InnerTubeX devuelve un stream SABR, lo rechaza con un `check`.
- Con la **v0.7.0 que usa Metrolist**, los perfiles automáticos con transporte directo, por prioridad base, son: `VISIONOS_0_1` (100), `TVHTML5` (75), `WEB_REMIX` (70), `WEB_EMBEDDED_PLAYER` (65), `WEB_CREATOR` y `WEB_KIDS` (55) y `TVHTML5_SIMPLY` (42). La v0.7.2 pasó `VISIONOS_0_1` a solo prueba, porque YouTube empezó a rechazar ese cliente. El orden real en ejecución depende de la puntuación y del historial de fallos. **No lo verifiqué en un dispositivo.**
- **Formato de audio** (`selectBestAudioFormat` en `FormatSelectors.kt`):
  - `HIGH`: mayor bitrate; desempata por canales, frecuencia de muestreo y contenedor (webm antes que mp4).
  - `AUTO`: prefiere `audio/webm` (Opus) según una puntuación por códec, estéreo, bitrate y frecuencia de muestreo.
  - `LOW`: el `audio/mp4` de menor bitrate.
  - La app convierte su `AUTO` en `LOW` si la red es medida (`isActiveNetworkMetered`).
- **Resultado** (`ExtractedStream` → `PlaybackData`): URL, cabeceras requeridas, caducidad (por defecto 5 min si no se conoce), cliente usado, `loudnessDb`, `perceptualLoudnessDb` y banderas de rango (`requireBoundedRange`, `rangeChunkSizeBytes`, `useRangeChunks`). El formato se guarda en Room (`FormatEntity`) para la normalización de volumen y para saber el `contentLength` de lo que está en caché.

### 2.5 Cuando la URL caduca o falla (`MusicService`)

- La URL se resuelve tarde, en `ResolvingDataSource`. El `mediaId` es la clave de caché. Antes de pedir una URL nueva comprueba si el rango ya está en `downloadCache` o `playerCache`.
- `StreamUrlCache`: LRU en memoria con `expiresAtMillis` y una **generación** por `mediaId`, para descartar resoluciones viejas.
- En `onPlayerError`, cada error tiene su manejador:
  - **403 / 410** (`handleExpiredUrlError`): invalida la URL cacheada, marca el cliente como fallido para ese vídeo durante **5 min** (`markStreamClientFailed`) y pide en segundo plano un refresco de la config del cipher. Si la config cambió, borra la lista de clientes fallidos (`clearStreamClientFailures`). Tras un retardo reintenta en la misma posición, salvo que el usuario haya cambiado de canción o de posición entretanto.
  - **416:** recuperación estricta desde la posición 0.
  - **Archivo no encontrado:** purga la caché y vuelve a resolver.
  - También hay manejadores para error del renderer de audio, error genérico de IO y error del cliente de stream.
- **Límites:** `MAX_RETRY_PER_SONG = 3`; si se supera, la canción se marca como fallida y salta. A nivel global, `MAX_RETRY_COUNT = 10` con *backoff* `3 s · 2^n` (máximo 30 s); al llegar al límite se pausa hasta que vuelve la red.

---

## 3. Reproducción (Media3)

- **`MusicService : MediaLibraryService(), Player.Listener, PlaybackStatsListener.Callback`**, inyectado con Hilt. En el manifest: `foregroundServiceType="mediaPlayback"` y las acciones `MediaSessionService`, `MediaLibraryService` y `MediaBrowserService` (por compatibilidad con Android Auto).
- **ExoPlayer:**
  - `setHandleAudioBecomingNoisy(true)`, `WAKE_MODE_NETWORK` y `AudioAttributes` de música;
  - *audio offload* opcional;
  - procesadores de audio propios: `VolumeNormalizationAudioProcessor` (usa `loudnessDb`), `CustomEqualizerAudioProcessor` (EQ paramétrico con `BiquadFilter`) y `SilenceDetectorAudioProcessor` (salto de silencios);
  - **crossfade** con un segundo `ExoPlayer` (`secondaryPlayer`/`fadingPlayer`).
- **Sesión:** `MediaLibrarySessionCallback` implementa `onGetLibraryRoot`, `onGetChildren`, `onSearch`, `onSetMediaItems`, `onPlaybackResumption` y `onCustomCommand`, es decir, el árbol de navegación para Android Auto y la reanudación.
- **Notificación:** `DefaultMediaNotificationProvider` con un *custom layout* de `CommandButton`: me gusta, shuffle, repeat (off/all/one), iniciar radio y "añadir a playlist destino". Los comandos están en `MediaSessionConstants`.
- **Primer plano:** llama a `startForeground` lo antes posible en `onCreate` para cumplir el límite de 5 s de `startForegroundService`, envuelto en `startForegroundSafely`, y hace `stopSelf` si no se permite. `onTaskRemoved` detiene todo solo si `StopMusicOnTaskClearKey` está activado; en ese caso `stopOnTaskClear()` es idempotente y también para el crossfade y Cast.
- **`PlayerConnection`** es el puente entre la UI y el servicio: expone `StateFlow` del estado del player y los comandos.
- **Caché** (`di/AppModule.kt`): dos `SimpleCache`:
  - `playerCache`: `LeastRecentlyUsedCacheEvictor` con tamaño configurable (`MaxSongCacheSizeKey`, 1024 MB por defecto; `-1` = sin límite, con `NoOpCacheEvictor`);
  - `downloadCache`: `NoOpCacheEvictor`.
  - El `CacheDataSource` está encadenado: primero `downloadCache`, luego `playerCache`, luego OkHttp.
  - Al terminar una canción completa se marca `isCached` en Room (`markCachedIfFullyDownloaded`).
- **Descargas:** `DownloadUtil` usa el `DownloadManager` de Media3 (`maxParallelDownloads = 3`) sobre `downloadCache`, con su propio `ResolvingDataSource`. `ExoDownloadService` es el servicio de primer plano (`foregroundServiceType="dataSync"`). El estado se refleja en `SongEntity.isDownloaded`/`dateDownload`.
- **Media3 está fijado en 1.10.1** a propósito: según un comentario del build, la 1.11.1 ocultaba los controles multimedia en Android 17 (issue #4404).

---

## 4. Cola

- **Interfaz `Queue`** (`playback/queues/Queue.kt`): `preloadItem`, `getInitialStatus(): Status` (título, items, índice y posición), `hasNextPage()` y `nextPage()`. Implementaciones: `ListQueue`, `YouTubeQueue` (sigue `next` con *continuation*), `YouTubePlaylistQueue`, `YouTubeAlbumRadio`, `LocalAlbumRadio` y `EmptyQueue`. También trae filtros de explícitos y de "video songs".
- **Cargar más:** en `onMediaItemTransition`, si quedan **≤ 5** items y `currentQueue.hasNextPage()`, pide `nextPage()` y lo añade. Se desactiva si `AutoLoadMoreKey` está apagado o si repeat-all está activo y `DisableLoadMoreWhenRepeatAllKey` está encendido.
- **Shuffle:** usa el shuffle de ExoPlayer con un orden propio (`applyShuffleOrder` → `DefaultShuffleOrder`). La canción actual siempre queda primera. Con `ShufflePlaylistFirstKey`, baraja por separado la cola original y lo añadido después, y pone lo original primero. Se vuelve a aplicar cada vez que se añaden items.
- **Repeat:** usa el de ExoPlayer, pero en `STATE_ENDED` lo resuelve a mano: `REPEAT_MODE_ALL` vuelve a 0 y `REPEAT_MODE_ONE` vuelve al inicio. Con `AutoplayKey`, avanza al siguiente. El temporizador de apagado puede impedir el avance.
- **Radio y autoplay:**
  - `startRadioSeamlessly()` reemplaza lo que viene después por una radio (`YouTube.next` con `WatchEndpoint`) sin cortar la canción actual.
  - **Automix:** `getAutomix(playlistId)` encadena dos llamadas `next` (y si fallan, una radio basada en la canción actual) para llenar `automixItems: StateFlow`, una lista de sugerencias.
  - **Detalle importante:** el añadido automático de la primera sugerencia cuando no hay siguiente canción **ocurre en la UI de Compose** (`ui/player/Player.kt`: si no se puede saltar a la siguiente y hay sugerencias de automix, añade la primera), no en el servicio. Por eso depende de que la pantalla del reproductor esté compuesta. **No confirmado** si hay otra ruta que lo haga con la UI cerrada.
- **Persistencia entre reinicios:**
  - tres archivos en `filesDir`: `persistent_queue.data`, `persistent_automix.data` y `persistent_player_state.data`;
  - se escriben con **Java `Serializable` + `ObjectOutputStream`**. Se guardan cuando cambia el estado de reproducción y cuando cambia de canción, si `PersistentQueueKey` está activo (por defecto sí);
  - `PersistQueue` incluye `QueueType` (`LIST`, `YOUTUBE`, `YOUTUBE_ALBUM_RADIO`, `LOCAL_ALBUM_RADIO`) y `QueueData` con el endpoint y la *continuation*, de modo que al restaurar se puede seguir paginando;
  - si la restauración falla, se borra el archivo;
  - restaurar repeat y shuffle desde `persistent_player_state` está **comentado** en el código; esos dos valores salen de DataStore (`RepeatModeKey`, `ShuffleModeKey`).

---

## 5. Datos

### 5.1 Room

- **`MusicDatabase` (`InternalDatabase`), versión 38**, `exportSchema = true`, con *auto-migrations* encadenadas desde la 2 (muchas con `spec` para renombrar o borrar) y migraciones manuales (`addColumnIfMissing`, `BackupBeforeMigrationFactory`, `applyPragmaSettings`). Hay tests de migración (`MusicDatabaseMigrationTest`).
- **Entidades:**
  - **Catálogo:** `SongEntity`, `ArtistEntity`, `AlbumEntity`, `PlaylistEntity` y `PodcastEntity`.
  - **Relaciones N:M:** `SongArtistMap`, `SongAlbumMap`, `AlbumArtistMap` y `PlaylistSongMap`, más las vistas `SortedSongArtistMap`, `SortedSongAlbumMap` y `PlaylistSongMapPreview`.
  - **Reproducción:** `FormatEntity` (itag, mime, códecs, bitrate, contentLength, loudness), `LyricsEntity` (letra, proveedor y traducción) y `RelatedSongMap`.
  - **Historial y estadísticas:** `Event` (songId, timestamp, playTime), `PlayCountEntity`, `SearchHistory` y `RecognitionHistory`.
  - **Sincronización:** `SetVideoIdEntity` (el `setVideoId` que YouTube usa para editar playlists).
  - **UI:** `SpeedDialItem`.
- **`SongEntity`** concentra el estado de biblioteca:
  - me gusta: `liked`, `likedDate`;
  - biblioteca: `inLibrary`, `libraryAddToken`, `libraryRemoveToken`;
  - archivos: `isDownloaded`, `dateDownload`, `isCached`, `isUploaded`, `uploadEntityId`;
  - tipo: `isLocal`, `isVideo`, `isEpisode`;
  - otros: `totalPlayTime`, `lyricsOffset`, `romanizeLyrics`, `playbackPosition`.
- **DAO:** un `DatabaseDao` enorme (la clase de la que más depende el resto del código) más `SpeedDialDao`. Cada tipo de entidad tiene su enum de orden (`SongSortType`, `AlbumSortType`, `ArtistSortType`, `PlaylistSortType`…).

### 5.2 Sincronización con la cuenta (`utils/SyncUtils.kt`)

- **Cola serializada:** un `Channel<SyncOperation>` lo consume una sola corrutina bajo `syncExecutionMutex`.
  - `SyncOperation` es una *sealed class*: `FullSync`, `LikedSongs`, `LibrarySongs`, `UploadedSongs`, `LikedAlbums`, `UploadedAlbums`, `ArtistsSubscriptions`, `PodcastSubscriptions`, `EpisodesForLater`, `SavedPlaylists`, `AutoSyncPlaylists`, `SinglePlaylist`, `LikeSong`, `SubscribeChannel`, `SavePodcast`, `SaveEpisode` y `ClearPodcastData`.
  - **Agrupación (*coalescing*):** cada operación tiene una clave y no se encolan duplicados. Las operaciones parciales se saltan si ya hay un `FullSync` en cola.
- **Auto-sync:** `tryAutoSync()` se llama desde `HomeViewModel` y `LibraryViewModels`. Solo se ejecuta con login, sync activado y red, y con un **cooldown de 30 min** (`SYNC_COOLDOWN`, guardado en `LastFullSyncKey`).
- **Bajada (remoto → local):**
  - los me gusta salen de `YouTube.playlist("LM")`;
  - la biblioteca, subidas, álbumes y artistas, de `YouTube.library(...)` con *continuations*;
  - todo se escribe dentro de una transacción.
  - Para playlists hay lógica cuidadosa, con tests en `PlaylistSyncTest`: `localSongIndexesAbsentFromRemote`, `preservedLocalSongs`, `isGenuineEmptyPlaylist` (no borra una playlist local si la respuesta remota viene vacía por error) y *backfill* de `setVideoId`. Las playlists que se están editando se marcan (`markPlaylistModifying`) para que el sync no las pise.
  - **No confirmado:** en `executeSyncLikedSongs` solo vi altas y marcados. No comprobé si otro paso desmarca los "me gusta" que ya no están en remoto.
- **Subida (local → remoto):** primero se escribe en Room (optimista: en `MusicService` hace `update(song)` y luego `syncUtils.likeSong(song)`) y después se encola la operación remota con `withRetry`. Si falla, solo se registra en el log; **no hay cola persistente de operaciones pendientes** (si se cierra la app, se pierden).
- Limpieza al cerrar sesión: `clearAllLibraryData()` y `AccountSettingsViewModel.logoutAndClearLibraryData()` / `logoutKeepData()`.

---

## 6. Letras

- **Interfaz** `LyricsProvider`: `name`, `isEnabled(context)`, `getLyrics(context, id, title, artist, duration, album): Result<String>` y `getAllLyrics(..., callback)`.
- **Registro** (`LyricsProviderRegistry`): un mapa de nombre a proveedor más el orden por defecto. El usuario puede reordenarlos (`LyricsProviderOrderKey`, serializado como CSV; los nuevos proveedores se añaden al final) y desactivar cada uno.
- **Orden por defecto:**
  1. **BetterLyrics** (`lyrics-api.boidu.dev`): letra sincronizada palabra por palabra (TTML, `TTMLParser`).
  2. **LrcLib** (`lrclib.net`): LRC, con coincidencia por título, artista y duración (`bestMatchingFor`).
  3. **KuGou** (`lyrics.kugou.com`, `mobileservice.kugou.com`): API no oficial.
  4. **Paxsenix** (`lyrics.paxsenix.org`): pasa por búsquedas en Apple Music.
  5. **LyricsPlus**: varios *mirrors* comunitarios (vercel, workers, binimum…) con intento escalonado.
  6. **Zemer** (`search.zemer.io`): música judía; busca por `videoId` exacto.
  7. **YouTubeSubtitle** (`YouTube.transcript`).
  8. **YouTube** (pestaña de letras vía `next` + `browse`).
- **Flujo:**
  1. `MusicService` mira primero Room (`database.lyrics(id)`). Si no hay letra, llama a `LyricsHelper.getLyrics` y guarda el resultado con `upsert`, **también cuando es "no encontrado"**.
  2. `LyricsHelper` prueba los proveedores **en secuencia**, cada uno con un timeout de 8 s y un total de 25 s, y devuelve el primero que funcione. Usa una caché LRU en memoria de 3 entradas y limpia el título antes de buscar (`LyricsUtils.cleanTitleForSearch`).
  3. `getAllLyrics` consulta todos en paralelo para que el usuario elija.
- **Extras:**
  - traducción automática con servicios externos (`OpenRouterService`, `DeepLService`, `LyricsTranslationHelper`);
  - romanización con Kuromoji para japonés y TinyPinyin para chino, más reglas propias para cirílico (hay tests de macedonio y bielorruso).

---

## 7. Dependencias clave y fragilidad

Estado consultado en GitHub el 2026-10-04.

| Dependencia | Versión en Metrolist | Licencia | Estado | Fragilidad |
|---|---|---|---|---|
| **InnerTubeX** (`MetrolistGroup/innertubex`) | v0.7.0 (actual: v0.7.4, 2026-09-30) | GPL-3.0 | Muy activa: 4 versiones en una semana. KMP (android, jvm desktop, iosArm64). 20 estrellas; un solo equipo. Se publica en JitPack. | **Alta.** Versionado `0.x` y su README pide fijar la versión exacta. El catálogo de clientes cambia entre versiones menores. Depende del mismo equipo que puso Metrolist en mantenimiento. |
| **zemer-cipher** (`ZemerTeam/zemer-cipher`) | se consume en ejecución (JSON en `raw.githubusercontent`) | GPL-3.0 | Activo: añade configs de player casi a diario (5 commits entre el 1 y el 3 de octubre). | **Alta.** Es un servicio externo: si dejan de publicar configs, la primera capa del cipher falla y quedan EJS y regex. |
| **quickjs-kt** (`io.github.dokar3`) | 1.0.14 (dentro de InnerTubeX) | Apache-2.0 | Activo (2026-09-24). KMP; tiene toolchain para Windows x64, Linux y macOS. | Baja-media. Usa binarios nativos. |
| **yt-dlp/ejs** | recurso dentro de InnerTubeX | Unlicense | Activo (2026-06-20). Lo mantiene yt-dlp. | Media. Hay que actualizar cuando YouTube cambia el player. |
| **PoToken por BotGuard en `WebView`** | código propio de la app | (GPL-3.0, el de Metrolist) | — | **Alta.** Depende de endpoints internos (`jnn/v1/Create`, `GenerateIT`) y del `WebView` del sistema. Solo funciona en Android. |
| **Media3** (exoplayer, session, okhttp, cast) | 1.10.1 (fijada a propósito) | Apache-2.0 | Activa (Google). | Media. Ya hubo una regresión con los controles de Android 17. |
| **Room** | 2.8.5 (Android, KSP) | Apache-2.0 | Activa. Room KMP admite JVM de escritorio. | Baja. |
| **Ktor** (core, okhttp, content-negotiation, encoding) | 3.6.0 (InnerTubeX: 3.5.2) | Apache-2.0 | Activa. | Baja. |
| **kotlinx.serialization / coroutines** | — | Apache-2.0 | Activas. | Baja. |
| **Hilt** | 2.60.1 | Apache-2.0 | Activa. | Solo Android. Shura usa Koin. |
| **DataStore Preferences** | 1.2.1 | Apache-2.0 | Activa; tiene soporte KMP. | Baja. |
| **Coil 3** | 3.6.3 | Apache-2.0 | Activa; KMP. | Baja. |
| **Kuromoji** (ipadic) | 0.9.0 | Apache-2.0 | Sin cambios desde 2023-01. | Media: sin mantenimiento, pero estable. |
| **TinyPinyin** | 2.0.3 | Apache-2.0 | Último cambio 2024-11. | Media. |
| **NewPipeExtractor** | **no se usa** | GPL-3.0 | Activo (2026-10-01). | — (aparece aquí solo porque PLAN.md §3 pide evaluarlo). |
| **Proveedores de letras** | servicios HTTP, no bibliotecas | LrcLib: servidor MIT y API pública. El resto son APIs no oficiales o *mirrors* comunitarios. | Variable. | **Alta** para KuGou, Paxsenix y LyricsPlus (no oficiales y con varios *mirrors* que cambian). LrcLib es el más estable. |

**Sobre el "modo mantenimiento":** el README de Metrolist dice que solo aceptan correcciones de errores, pero el repo recibió commits el 2026-10-03 y el trabajo activo se movió a InnerTubeX. La app en sí no va a cambiar mucho. Lo que se rompe con frecuencia (clientes, cipher, PoToken) se sigue arreglando fuera del repo: en InnerTubeX y en zemer-cipher.

**Puntos más frágiles, de más a menos:**
1. el PoToken;
2. las versiones de cliente fijadas en código (por ejemplo `WEB_REMIX` `1.20260707.12.00`);
3. las configs de zemer-cipher;
4. el catálogo y la puntuación de clientes;
5. los proveedores de letras no oficiales.

---

## 8. Qué aplicar en Shura (KMP: Android + Windows)

### 8.1 Ideas que encajan bien

1. **Valorar InnerTubeX como dependencia directa de `:core:innertube` / `:core:stream`.** Ya es KMP con targets `android` + `jvm("desktop")` (JVM 17, igual que Shura) y es GPL-3.0, compatible con la licencia de Shura. Es una alternativa a evaluar junto a NewPipeExtractor (PLAN.md §3), que es una biblioteca Java/JVM y no KMP. No revisé el soporte de YouTube Music de NewPipeExtractor. Precauciones: fijar la versión exacta, ponerlo detrás de `StreamResolver` y de una fachada propia (como hace Metrolist), y apuntarlo en `docs/dependencies.md`. Riesgo de *bus factor*: lo mantiene un equipo pequeño.
2. **Tres capas en InnerTube:** transporte y sesión → DTO de renderers → parsers a páginas de dominio (`YTItem`). Los parsers y modelos son Kotlin puro y van en `commonMain`.
3. **Sesión como valor inmutable con generación.** InnerTubeX hace `sessionSnapshot()`/`replaceSession()` y `transportGeneration`, y así descarta resultados de una sesión anterior al cambiar de cuenta o de proxy.
4. **Cipher por capas** (config remota → solver genérico → regex) ejecutado en QuickJS. quickjs-kt funciona en Android y en JVM con Windows x64.
5. **Elegir cliente por puntuación y salud**, en vez de una lista fija: marcar un cliente como fallido por vídeo durante un tiempo y limpiar esa marca cuando cambia la config del cipher.
6. **Resolver la URL lo más tarde posible**, con caché de URL con caducidad y generación por `mediaId`, y con un manejador por tipo de error (403/410 → invalidar + cambiar de cliente + refrescar cipher + reintentar en la misma posición; 416 → desde 0), con límite de reintentos por canción y global.
7. **Selección de formato:** preferir Opus/webm en `AUTO`, `LOW` en redes medidas, y guardar `loudnessDb` para normalizar el volumen.
8. **La interfaz `Queue`** (`getInitialStatus` / `hasNextPage` / `nextPage`) más "cargar más cuando quedan ≤ 5" y persistir el **tipo de cola y su continuation** para seguir paginando tras reiniciar.
9. **Sync en una cola serializada con agrupación de claves y cooldown,** escritura local optimista y protección contra "respuesta vacía = borrar todo" (`isGenuineEmptyPlaylist`).
10. **Letras con interfaz y registro ordenable,** timeout por proveedor y total, caché en BD y en memoria, y `getAllLyrics` en paralelo para que el usuario elija. Conviene empezar con LrcLib (el más estable) y añadir otros detrás de banderas.

### 8.2 Ideas que no encajan o que hay que rehacer

1. **PoToken con `WebView` de Android:** no existe en Windows. El `TokenProvider` de escritorio del *harness* de InnerTubeX solo sirve en Linux (Chromium + `unshare` + Node) y es una herramienta de diagnóstico, no parte de la biblioteca. Para Windows hay que decidir: o un *minter* propio (por ejemplo con un navegador embebido; **no evaluado**) o depender de los clientes que no exigen PoToken. **Es el mayor riesgo abierto para la versión de escritorio.**
2. **Login con `WebView` + `CookieManager` + JS para el `dataSyncId`:** hay que rehacerlo en escritorio, con un navegador embebido o importando la cookie. La cookie, el `SAPISID` y el `dataSyncId` deben guardarse cifrados y no salir nunca en logs.
3. **`MediaLibraryService`, la notificación, `foregroundServiceType`, Android Auto y `onTaskRemoved`:** van solo en el `actual` de Android de `:core:player`. Metrolist no tiene nada equivalente para escritorio (en Windows serían los controles multimedia del sistema; **no evaluado**).
4. **Caché y descargas de Media3** (`SimpleCache`, `CacheDataSource`, `DownloadManager`, `ExoDownloadService`): son solo de Android. Con libVLC o mpv en escritorio habrá que montar caché y descargas propias detrás de una interfaz común.
5. **Procesadores de audio de ExoPlayer** (normalización, EQ, salto de silencios, crossfade con dos players): ligados a Media3. En escritorio dependerán de lo que ofrezca libVLC o mpv.
6. **Hilt y `SharedPreferences`:** Shura usa Koin; DataStore KMP en lugar de `SharedPreferences`.
7. **Cast, widgets, mosaico de Ajustes rápidos, reconocimiento Shazam, Discord RPC, Listen Together, alarmas:** fuera del alcance (y casi todo depende de Android o de servicios externos).

### 8.3 Anti-patrones que conviene evitar

- **El `MusicService` hace de todo** (unas 5000 líneas, toca unas 30 áreas funcionales): mejor separar en `PlaybackController`, `QueueManager`, `StreamResolver`, `ErrorRecovery` y `LyricsLoader`, y que el servicio Android sea solo un adaptador fino.
- **Lógica de dominio en la UI:** el añadido automático de automix vive en `Player.kt` (Compose). En Shura debe estar en la capa de cola, para que funcione con la UI cerrada y en escritorio.
- **Persistir con Java `Serializable` / `ObjectOutputStream`:** frágil entre versiones y no portable. Usar JSON con kotlinx.serialization y un número de versión del formato.
- **Un DAO gigante** (`DatabaseDao`, del que depende casi todo): mejor un DAO por agregado.
- **Operaciones remotas sin cola persistente:** si un like falla o se cierra la app, se pierde. Conviene una tabla de operaciones pendientes con reintento.
- **Guardar "no encontrado" como letra** sin fecha de caducidad: esa canción no vuelve a buscarse aunque aparezca la letra después.
- **Mezclar en el mismo `DataStore` preferencias y secretos de sesión.**

---

## 9. Pendientes de verificar

- [ ] Si `app/src/main/assets/solver/*.js` se usa en ejecución (no hay referencias en Kotlin).
- [ ] El orden real de clientes en una reproducción (con logs de `ContentAwareFallbackStrategy` en un dispositivo).
- [ ] Si el sync quita "me gusta" locales que ya no están en remoto.
- [ ] Si existe otra ruta de autoplay o radio al final de la cola con la UI cerrada.
- [ ] Probar InnerTubeX `desktop` en Windows: QuickJS nativo y extracción **sin** PoToken (qué clientes responden).
- [ ] El origen del código de PoToken (parecido a NewPipe, sin atribución en el archivo).
