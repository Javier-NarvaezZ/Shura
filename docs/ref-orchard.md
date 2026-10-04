# Referencia: Orchard

Análisis de arquitectura de [Orchard](https://github.com/SFG5453/Orchard) (AGPL-3.0) como referencia para Shura. Aquí solo se describen nombres de módulos, archivos, clases, flujos y decisiones de diseño. No se copió código.

- **Revisado:** 2026-10-04, commit `e6cf95b` de `main` (2026-09-25).
- **Método:** lectura directa del código en `~/ref/Orchard` y análisis de dependencias entre módulos y clases.
- **Convención:** lo que no se pudo confirmar en el código aparece marcado como **No confirmado**.
- **Licencia:** AGPL-3.0. Las ideas se pueden reutilizar; el código no se copia.

---

## 0. Resumen

- Orchard son **dos clientes independientes que resuelven lo mismo dos veces**:
  - **escritorio:** Electron + Vue. El proceso principal está en `electron/` y la UI en `src/app/`;
  - **Android:** Kotlin + Compose + Media3, en `mobile/android/app/.../dev/sfg/orchard/mobile/`.

  No comparten código salvo constantes JS en `shared/`, como `shared/streamQuality.js`. Las dos versiones del resolver de streams llegaron a las mismas conclusiones, pero cada una con su propia implementación.
- **Escritorio:** usa **youtubei.js v18** (`Innertube.create`, cliente `WEB_REMIX`) para el catálogo y la reproducción, pero normaliza el JSON crudo con sus propias funciones. El PoToken se obtiene con **bgutils-js + jsdom** (BotGuard en Node). La reproducción pasa por un **proxy HTTP local** (`127.0.0.1/stream/<id>`), que guarda el audio en una caché en disco.
- **Android:** el catálogo usa un **cliente InnerTube propio** (`InnerTubeClient`, OkHttp + `org.json`, sin DTO). El stream se resuelve primero con **NewPipeExtractor v0.26.4** y, si falla, recorre un catálogo de clientes InnerTube. El cipher y el PoToken corren en **WebViews ocultos** con bundles JS (`yt_solver.bundle.js`, `yt_potoken.bundle.js`).
- **Media3 1.11.1:** `OrchardPlaybackService : MediaLibraryService`, con dos `ExoPlayer` (para crossfade), `ResolvingDataSource` sobre URIs estables `orchard://stream/<videoId>` y un `SimpleCache` LRU **por encima** de la resolución.
- Hay muchas decisiones **medidas en dispositivo** y documentadas en comentarios. Son de lo más útil del repo:
  - las URL de `ANDROID_VR` solo sirven un prefijo del archivo;
  - googlevideo frena las peticiones sin `Range` acotado;
  - la URL hay que pedirla con la misma identidad (User-Agent, Origin, Referer) del cliente que la generó.
- **Lo que es infraestructura propia y no se traslada:** Cloudflare Workers (`workers/`: bpm, lastfm, concerts, artwork-proxy, support, listening-party, song-links…), Supabase, Qobuz como fuente "MAX", Discord, Cast, Spotify Canvas y la mezcla inteligente con modelos ONNX/TFLite.

---

## 1. Catálogo: peticiones a InnerTube, normalización y sesión

### 1.1 Escritorio (`electron/catalog`, `electron/auth`)

| Lugar | Qué hace |
|---|---|
| `auth/authService.js` (~600 líneas) | Es el dueño de los clientes `Innertube`. Hay **tres**: OAuth (`getInnertube`, flujo *device code* antiguo), **cookies del navegador** (`getBrowserInnertube`) e **invitado** (`getGuestInnertube`). Todos son `WEB_REMIX`, con `retrieve_player: true`, `generate_session_locally: true` y un `UniversalCache` en disco separado por ámbito (`youtubei-cache/oauth`, `/browser`, `/guest`). |
| `auth/youtubeClientSession.js` | `withFreshYouTubeSession()` desactiva **solo** la caché de sesión de youtubei.js. Así cada arranque usa una identidad de visitante nueva, pero conserva el `player.js` y las credenciales OAuth en caché. |
| `auth/browserMusicApi.js` | `createBrowserMusicFetch()` es un `fetch` que intercepta toda petición a `/youtubei/v1/` y la reescribe como si viniera de music.youtube.com. Pone `Authorization` (ver §1.3), `Cookie` (añade `SOCS=CAI` y `PREF=…hl=en` si faltan), `Origin`/`Referer`/`X-Origin`, `X-YouTube-Client-Name: 67`, `X-Goog-AuthUser` y `X-Goog-PageId`. También fuerza `context.client = WEB_REMIX` y `context.user.onBehalfOfUser = dataSyncId`. Además trae `rawBrowserMusicBrowse()`, que llama a `/browse` "a mano". |
| `catalog/musicBrowse.js` | `musicBrowseRequests(kind, payload)` genera **variantes de `browseId`**: para playlists añade o quita el prefijo `VL` y prueba con y sin `params`. `resolveMusicCollectionWithFallback` prueba el cliente con sesión, luego la cookie del navegador en crudo y luego el invitado. Si todo falla, guarda en `error.browseContext` qué se intentó. |
| `catalog/browseNormalizers.js`, `browseItemNormalizers.js` | **No usan los objetos parseados de youtubei.js**: leen el JSON crudo (`yt.actions.execute('/browse', …)`) y lo convierten a objetos planos. |
| `catalog/musicItemTypes.js` | Clasifica los items: `musicPageType`, `isUploadedMusicItem` (marca *privately owned*) y `releaseTypeFromText`. |
| `catalog/innertubeParserErrors.js` | Sustituye el manejador de errores del parser de youtubei.js. El original fallaba en las builds empaquetadas porque leía `package.json.bugs.url`, y eso rompía búsquedas con *renderers* desconocidos. |

**Modelo normalizado (escritorio):** cada fila de lista (`musicResponsiveListItemRenderer`) se convierte en un objeto con estos campos:
- identidad y navegación: `id` (videoId), `browseId`, `browsePayload` y `type` (`track`, o el `pageType` sin el prefijo `MUSIC_PAGE_TYPE_`);
- tipo de vídeo: `musicVideoType` y `isAudioOnly` (= `MUSIC_VIDEO_TYPE_ATV`);
- metadatos: `title`, `subtitle`, `artists[]`, `artistBrowseIds[]`, `album`, `albumId`, `duration`, `explicit`, `thumbnail` (la mejor resolución), `index` e `isUpload`.

El `videoId` se toma de `playlistItemData`, y si no está, del *watch endpoint* del título, del overlay o del propio renderer, en ese orden.

### 1.2 Android (`mobile/catalog`)

| Lugar | Qué hace |
|---|---|
| `catalog/InnerTubeClient.kt` (~270 líneas) | Cliente HTTP **propio** sobre OkHttp + `org.json`. Siempre usa `WEB_REMIX`, `hl=en` y `gl=US` contra `music.youtube.com/youtubei/v1/<endpoint>?key=…&prettyPrint=false`. |
| `catalog/JsonTraversal.kt` | Utilidades para recorrer el JSON: `renderers(root, "<nombre>Renderer")` busca un renderer por nombre de forma **recursiva** en todo el árbol. Además: `text`, `runs`, `largestThumbnail`, `navigation`, `videoId`, `browseId`, `pageType`, `musicVideoType` e `isPrivatelyOwned`. |
| `catalog/CatalogParser.kt` (~800 líneas) | `object` sin estado que convierte las respuestas en modelos: `home`, `search`, `detail`, `upNext`, `trackArtists`, `continuationToken`, `continuationTracks`… |
| `catalog/CatalogRepository.kt` | Fachada `suspend` sobre `Dispatchers.IO`. `browsePages(id): Flow<BrowseDetail>` **emite la primera página en cuanto la parsea** y luego una copia más completa tras cada *continuation*. |
| `catalog/AudioVersionResolver.kt`, `VideoVersionResolver.kt` | Buscan la versión "audio de álbum" de una fila que solo enlaza el videoclip, y el videoclip de una canción de audio. |
| `model/CatalogModels.kt` | Modelo de dominio: `Track`, `Album`, `Artist`, `Playlist`, `sealed interface CatalogItem` (`Song`, `Record`, `Performer`, `Collection`, `Category`), `CatalogSection`, `BrowseDetail` y `SearchResults`. |

**Cómo hace las peticiones:**
- **Identidad del cliente:** `loadIdentity()` descarga la página de `music.youtube.com` y extrae con regex `INNERTUBE_API_KEY`, `INNERTUBE_CLIENT_VERSION` y `VISITOR_DATA`. Si falla, usa una clave y una versión **fijadas en el código**. La identidad se guarda en un `@Volatile`.
- **Headers:** `X-Goog-Api-Format-Version: 1`, `X-Youtube-Client-Name: 67`, `X-Youtube-Client-Version`, `X-Goog-Visitor-Id`, `Origin`/`Referer`/`X-Origin` y un User-Agent de Chrome móvil. Con sesión se añaden además `Cookie`, `Authorization` (SAPISIDHASH) y `X-Goog-AuthUser: 0`, y en el body `context.user.onBehalfOfUser`.
- **Reintento con sesión:** si la sesión tiene `dataSyncId` y la respuesta es **400 "invalid argument"**, repite la petición una vez **sin** `onBehalfOfUser`. Ocurre cuando el id de delegación queda obsoleto tras cambiar de cuenta o de canal.
- **Peticiones anónimas a propósito:** `searchSongs` y `searchVideos` (búsquedas que la app hace por su cuenta para resolver versiones) llevan `anonymous = true` para **no ensuciar el historial de búsqueda de la cuenta**.
- **Radio y autoplay:** `upNext(videoId)` llama a `next` con `playlistId = "RDAMVM<videoId>"` e `isAudioOnly = true`.
- Las letras de YouTube Music se leen de la pestaña "Lyrics" de `next`, que lleva a un `browse` con `musicDescriptionShelfRenderer`.

**Normalización (decisiones de diseño en `CatalogParser`):**
- **No hay DTO tipados:** se busca un renderer por nombre en cualquier punto del árbol. Es más tolerante a cambios de estructura, pero también menos explícito.
- **Qué `videoId` reproducir:** se prefiere `playlistItemData.videoId`, que es el audio del álbum, al *endpoint* del título, que a veces apunta al videoclip (más largo, con intro y desfasado de la letra). El comentario dice que sigue el orden de SimpMusic.
- **`musicVideoType` (ATV/OMV/UGC)** se guarda en `Track` y se usa después para deduplicar y para el autoplay.
- **Duplicados:** en álbumes y otras páginas que no son playlists se eliminan por `id` y, con `preferAlbumAudio()`, se descarta el videoclip cuando existe el audio del álbum con el mismo título y artista. **En las playlists se conservan las filas duplicadas.**
- **Paginación:** `pageBudget(id)` da más páginas a las mezclas `RD…` (que son infinitas) que a las playlists normales. Si una página falla, **se corta sin perder lo ya parseado**, y también para si el token de continuación se repite.
- `Track.isUpload` marca las subidas del propio usuario: no tienen página pública y solo se pueden reproducir con la sesión iniciada (ver §2.2).

### 1.3 Sesión y cookies

**Escritorio:**
- **Login:** un `BrowserWindow` de Electron con partición persistente `persist:orchard-youtube-auth`.
- **Cookies:** `collectYouTubeAuthCookie` junta las de `music.youtube.com`, `www.youtube.com` y `youtube.com`, y llama a `flushStore()` si hay cookie de login. `normalizeYouTubeAuthCookie` añade `SAPISID` copiado de `__Secure-3PAPISID` cuando solo existe el segundo.
- **Datos de la página:** `captureBrowserPageAuth` ejecuta JS en la página y lee de `ytcfg` (o de `yt.config_`, o buscando en los `<script>`) estos valores: `VISITOR_DATA`, `DATASYNC_ID`/`DELEGATED_SESSION_ID`, `SESSION_INDEX` y `PO_TOKEN`.
- **`Authorization`:** se genera `SAPISIDHASH`, más `SAPISID1PHASH` y `SAPISID3PHASH` si existen `__Secure-1PAPISID` y `__Secure-3PAPISID`. Cada hash es el SHA-1 del timestamp, el valor de la cookie y el origin, y los tres hashes se unen con espacios.
- **Identidad del cliente:** `browserIdentity()` es la concatenación cookie + accountIndex + dataSyncId + poToken. Si cambia, se recrea el cliente `Innertube`.
- **Recuperación:** `browserSessionRecovery.js` intenta renovar la sesión cuando faltan cookies.
- **Cerrar sesión:** borra las cookies de la partición.

**Android:**
- **Login:** `ui/screens/NativeLoginScreen.kt` usa un `WebView`. Lee las cookies con `CookieManager.getCookie(origin)` y los valores con JS sobre `ytcfg` (el mismo patrón que escritorio). Al terminar llama a `completeSignIn(cookie, visitorData, dataSyncId)`.
- **`NativeYouTubeAuthRepository`:**
  - implementa `YouTubeSessionProvider`, que es lo que consume `InnerTubeClient`;
  - expone `StateFlow<AuthState>` (`Restoring`, `SignedOut`, `Authorizing`, `SignedIn`, `Error`);
  - usa un **contador de generación** (`AtomicLong`) para descartar resultados asíncronos de un intento de login anterior o de un cierre de sesión que llegó en medio;
  - después de iniciar sesión carga el nombre y el avatar (`account/account_menu`) "en lo posible": si falla, la sesión sigue funcionando.
- **`SecureYouTubeSessionStore`:**
  - guarda un JSON con `cookie`, `visitorData`, `dataSyncId`, `displayName` y `avatarUrl`;
  - lo cifra con **AES/GCM con una clave no exportable de AndroidKeyStore** (`security/AndroidKeystoreCipher.kt`) y lo escribe en `SharedPreferences` con `commit()` síncrono;
  - si encuentra datos de OAuth antiguos, los borra.
- **`YouTubeSessionAuth.normalizeDataSyncId`:** decodifica `%xx` y maneja las dos formas, `"<id>||"` y `"…||<id>"`.
- **Cerrar sesión:** borra el almacén y además las cookies del `WebView`.

---

## 2. Resolución del stream

### 2.1 Escritorio (`electron/playback`)

| Lugar | Rol |
|---|---|
| `playbackService.js` (~720 líneas) | `resolveStream()` (elige el cliente y el formato y valida la URL) y `proxyStream()` (proxy local con caché). |
| `playbackFormats.js` | Filtra y ordena los formatos y elige por calidad. |
| `playbackStreamCache.js` | URLs resueltas en memoria (un `Map`). |
| `songCache.js` (~620 líneas) | Caché en disco de canciones completas y descargas. |
| `youtubePoToken.js` | Genera el PoToken con BotGuard (bgutils-js + jsdom) y lo guarda en caché por vídeo. |
| `authenticatedYouTubePlayback.js` | Ruta con sesión para tracks con restricción de edad: itag 18 directo o HLS de Safari. |
| `musicVideoFallback.js`, `playbackErrors.js`, `playbackRouting.js` | Alternativa con el videoclip y clasificación de errores. |
| `bridge/bridgeServer.js` | Orquesta la cadena completa de alternativas para un track (§2.4). |

**Orden de clientes en `resolveStream`:**
1. Si hay una URL en caché válida durante al menos 60 s más, y no es un itag marcado como fallido, se reutiliza.
2. Pide un **PoToken ligado al `videoId`** (`youtubePoTokenService.get`, que acepta un `rejectedToken` para invalidarlo).
3. **Solo si no hubo PoToken:** prueba `ANDROID_VR` (1.65.10) con un `fetch` propio a `www.youtube.com/youtubei/v1/player`. Antes saca `visitorData` de la página `/watch` del vídeo. Si YouTube pide verificación anti-bot, `ANDROID_VR` queda **en pausa 10 min**. También se evita cuando hay "resoluciones rápidas" (3 en 5 s o menos de 1,5 s entre dos), porque eso dispara el anti-bot.
4. Si no, usa youtubei.js `getBasicInfo(videoId, { client: 'YTMUSIC', po_token })`, con descifrado de firma y de `n` a través de `format.decipher(yt.session.player)`. `playbackInfo()` prueba: cliente principal → cliente de cookies del navegador (en errores de edad, anti-bot, vídeo privado o "sin formatos") → invitado (`canFallbackToGuest`: 401, 403, edad, anti-bot o sin formatos jugables).
5. **Valida la URL antes de darla por buena** (`validateUpstreamStreamUrl`: hace un sondeo real a googlevideo). Si falla, prueba el siguiente formato de audio de la lista.

### 2.2 Android (`mobile/playback/YouTubeStreamResolver.kt`, ~1400 líneas)

`resolve(videoId)` va en este orden:
1. **Descargas offline:** `DownloadPlaybackHelper.resolveOfflineStream`.
2. **Caché en memoria** con clave `"$videoId:$quality"`. Solo vale si caduca dentro de más de 60 s.
3. **NewPipeExtractor** (`NewPipeStreamResolver`: extrae desde la página `/watch` pública). Es la **ruta principal para todas las calidades**. Un comentario lo justifica: los clientes InnerTube nativos devuelven URLs que parecen válidas pero el CDN rechaza con 403, mientras que la URL WEB de NewPipe sí se puede descargar. **Se salta para las subidas del usuario** (`accountOnlyVideos`), que no tienen página pública.
4. **Espera tras un fallo:** si el track falló hace menos de **20 s**, relanza el mismo error sin tocar la red. Así se evita que los reintentos inmediatos de ExoPlayer acaben en un bloqueo por IP.
5. **Un bloqueo por clave** (`locks.computeIfAbsent`) une las peticiones del prefetch y del player. Toda la cadena tiene un **límite de 25 s** (`RESOLVE_BUDGET_MS`).
6. **Catálogo de clientes invitados** (`STREAM_CLIENT_PROFILES`, en este orden): `ANDROID_VR@1.65.10`, `VISIONOS@0.1`, `ANDROID_VR@1.61.48`, `WEB_REMIX` (con `signatureTimestamp` y PoToken), `ANDROID_MUSIC`, `IOS_MUSIC`, `ANDROID_UNPLUGGED`, `ANDROID_TESTSUITE` y `TVHTML5`. `orderedClientProfiles()` reordena la lista:
   - si hay PoToken, **`WEB_REMIX` pasa al principio**. Motivo, medido el 2026-08-18: una URL de `ANDROID_VR` solo sirvió hasta el byte 1 129 590 de 3 497 127 y luego respondió 403, es decir, la canción se cortaba a mitad. `WEB_REMIX` con token sirvió el archivo entero;
   - el último cliente que funcionó va primero, salvo que haya cliente con token;
   - se saltan los clientes vetados para ese track (`rejectedClientsUntil`, **10 min**).
   - La identidad de visitante se refresca **una sola vez** por resolución, para no generar ráfagas de peticiones a `/watch`.
7. **`WEB_REMIX` con la sesión iniciada** se usa como último recurso **fuera del límite de tiempo**, porque es el único cliente que puede reproducir subidas propias.
8. Si todo falla, guarda el `FailedResolve` (la espera de 20 s) y lanza el error.

**Piezas de soporte:**
- **`YouTubeChallengeSolver`:** descifra `sig` y `n` con `yt_solver.bundle.js` en un **WebView oculto**. El bundle trae un parser de JS y la cadena `yt-dlp-wins`, así que probablemente es el solver EJS de yt-dlp; **No confirmado**.
- **`YouTubePoTokenMinter`:** BotGuard en un WebView oculto con `yt_potoken.bundle.js`, que es bgutils-js empaquetado con `scripts/build-potoken-bundle.mjs` (el bundle se sube al repo porque Gradle no tiene paso de Node). Guarda el token en caché por `videoId` y puede invalidar un token rechazado.
- **`YouTubePlayerConfig`:** guarda en caché el `signatureTimestamp`, refrescado con un mínimo de 5 min entre peticiones.
- **`PrefsVisitorIdentityStore`:** persiste la identidad de visitante **de invitado**. La de cuenta **nunca** se escribe en disco.

### 2.3 Elección de formato

- **Calidades compartidas por las dos apps.** Escritorio: `saver` / `normal` / `high` en `shared/streamQuality.js`. Android: `AudioQuality { DATA_SAVER, NORMAL, HIGH, MAX }`.
  - `saver`: el bitrate más bajo;
  - `normal`: **≤ 140 kbps** (un poco por encima de 128, porque el AAC de 128 de YouTube declara algo más);
  - `high`: el mejor disponible;
  - `MAX`: solo en Android. Toma lo mejor de NewPipe o, si el usuario lo configuró, FLAC de Qobuz. Si NewPipe falla, baja a `HIGH` y avisa con un toast **solo si esa alternativa funciona**.
  - En vídeo, los techos son 480p, 720p y sin límite.
- **Android (`chooseAudio`):** solo usa `adaptiveFormats` de audio. Ordena por **opus > mp4a > otros** y luego por bitrate de mayor a menor, y aplica el techo de la calidad. Si el formato trae `signatureCipher`, lo descifra con el solver. Opcionalmente acepta `hlsManifestUrl` y descifra su `n`.
- **Escritorio (`chooseAudioFormatFromFormats`):** prioriza los MIME que el renderer dice soportar (`canPlayType`). Si no recibe esa lista, usa este orden por defecto: AAC-LC → HE-AAC → Opus. Dentro del MIME elegido, aplica el techo de bitrate. Admite `avoidItags` y `avoidMimeTypes` para reintentar con otro formato.
- **El fetch tiene que usar la identidad del cliente** (User-Agent, Origin, Referer). `ResolvedStream` lleva sus `requestHeaders` y `clientKey`, y el `ResolvingDataSource` se los añade a la petición. Según un comentario, si no se hace así la resolución funciona pero la descarga del audio da 403.
- **`Range` siempre acotado.** Android: `bounded()`. Escritorio: `upstreamRangeHeader(requireBounded)`. Medición del comentario: sin `Range`, la respuesta fue 200 pero entregó 3,1 MB en 98 s y se cortó; con `bytes=0-<len-1>` fue 206 con el archivo entero en menos de 1 s.
- **Caducidad:** se lee del parámetro `expire` de la URL; si no está, se asumen 45 min. NewPipe no expone la caducidad, así que también se asumen 45 min.

### 2.4 Cuando la URL caduca o falla

**Android (`OrchardPlaybackService.onPlayerError`).** La escalera de recuperación sigue este orden:
1. **Falla el vídeo** (`orchard://video/…`): se reemplaza el item por su versión de audio **en la misma posición** (con tope en la duración del audio) y se avisa al usuario de que el vídeo no está disponible y sigue solo el audio.
2. Si el error trae un código HTTP de URL rechazada y el stream tenía `clientKey`, se llama a `streamResolver.reject()`:
   - invalida el PoToken si la URL tenía `pot`;
   - **veta ese cliente para ese track durante 10 min**;
   - borra "último cliente que funcionó";
   - limpia la caché y la espera de 20 s.

   Así el siguiente intento usa **otra familia de clientes**. Si no fue un rechazo, solo `resetForRetry()`.
3. **Falla el directo con sesión (itag 18):** se cambia a **HLS con sesión**. Si también falla el HLS, se detiene.
4. **Restricción de edad:** si no fue un rechazo del CDN y el resolver detectó una restricción de edad (`consumeAgeGate`), se cambia a la ruta **directa con sesión (itag 18)**. Un 403 del CDN **no** se interpreta como restricción de edad.
5. **La resolución agotó todos los clientes** (`resolvedStream == null`): se detiene **sin reintento automático**, porque repetir la cadena de 25 s solo alarga el spinner. Como la espera de 20 s ya se limpió, el siguiente toque del usuario en Play hace un intento nuevo.
6. **Error irrecuperable** (`isUnrecoverablePlaybackError`): se detiene.
7. **En otro caso** (`prepare()` + `play()`): **hasta 3 reintentos si se vetó un cliente** (`MAX_CLIENT_ROTATION_RETRIES`) y **1 en otro caso**. Lleva la cuenta por `mediaId`.

`replaceMediaItem(index, …)` + `seekTo(index, position)` permite cambiar de fuente **sin perder la posición**. La variante de fuente va codificada en la URI (`authenticated_direct`, `authenticated_hls`, `video`).

**Escritorio:**
- **Proxy (`proxyStream`):** ante 403, 410, 429, 500, 502, 503 o 504 del upstream, borra la URL de la caché y reintenta **una sola vez** con `refreshStream: true` y el mismo itag. Si la URL tenía `pot`, pasa `rejectedPoToken` para generar uno nuevo; si no, marca ese itag como fallido durante 10 min (`upstreamFailures`), y las peticiones siguientes lo evitan.
- **Renderer (`src/app/playback/playbackRecoveryActions.js`):** si la reproducción se atasca más de **4 s**, prueba en orden: refrescar el stream una vez (`streamRefreshTried`) → pedir otro formato (`avoidCurrentFormat`, o `retryAudioWithAlternateFormat` si era el itag 18 con sesión) → mostrar un error. En todos los casos reanuda en `resumeAt`.

**Tracks con restricción de edad (escritorio, `bridgeServer.js`), en orden:**
1. **Heurística proactiva:** si el track **no** está marcado como explícito pero el título coincide con `isAgeGateRiskTrack` (una regex sobre el título), busca primero el videoclip. Es un parche muy específico; ver §5.3.
2. **Resolución normal.** Si da error de edad **y hay sesión del navegador** (`shouldTryAuthenticatedAgeGate`), pasa a la ruta con sesión: **itag 18** (MP4 muxed de 360p) con `WEB_REMIX` y la cookie, con validación. Si falla, usa el **HLS de `WEB` con UA de Safari**, con el `n` del manifest descifrado y el manifest reescrito para que pase por el proxy.
3. **Videoclip como sustituto** (`musicVideoFallback.js`): busca vídeos con el mismo título y artista normalizados y **una duración que no difiera más de ±5 s** de la canción, y se queda con el más cercano. Lo reproduce como vídeo con audio incluido, pero presentado como la canción original (`musicVideoAudioFallback`).
4. **Otra versión de audio** (`preferredAudioTrack` con `excludedVideoIds`).
5. Por último, el `videoId` original, si el resuelto era distinto.

En Android la restricción de edad se resuelve **solo** con itag 18 → HLS con sesión, y siempre requiere sesión (si no la hay, la app pide iniciarla). En `mobile/playback` **no encontré** la alternativa del videoclip con duración ±5 s.

### 2.5 Caché de canciones

**Android (`playback/StreamCache.kt`):**
- **Implementación:** Media3 `SimpleCache` con `LeastRecentlyUsedCacheEvictor`. El tamaño sale de `settings.cacheSizeBytes`.
- **Clave:** `"<uri estable>|<calidad>"`, por ejemplo `orchard://stream/<id>|HIGH`.
- **Posición en la cadena:** la caché va **antes** del `ResolvingDataSource`. Si hay un acierto de caché, **no se resuelve nada**. Usa `FLAG_IGNORE_CACHE_ON_ERROR` para leer y `FLAG_BLOCK_ON_CACHE` para escribir.
- **Precarga:** `prefetch(uri)` / `prefetchAround(player)` descarga pistas completas alrededor de la actual. Si el stream lo permite (`supportsParallelRanges`), descarga **rangos en paralelo** y solo los que faltan (`missingRanges`). Al terminar dispara `onCached`, que relanza la precarga y la medición del bitrate real.
- **El vídeo no se cachea:** usa una `MediaSource.Factory` aparte para no expulsar el audio con cientos de MB.
- **Otras funciones:** `isFullyCached`, `cachedBitrateKbps` (calculado a partir de los bytes reales y la duración) y `retainOnly`.
- **Descargas:** son un sistema aparte (`download/DownloadManager.kt`, `TrackDownloader.kt`). El resolver las consulta primero.

**Escritorio (`electron/playback/songCache.js`):**
- **Archivos:** en `userData/song-cache/`, como `<videoId>-<itag>-<contentLength>.bin` con un `.json` de metadatos al lado.
- **Tamaño:** de 128 a 4096 MB, en saltos de 128; por defecto 512.
- **Escritura** (`pipeAndStore`): duplica (`tee`) el cuerpo que llega mientras suena. Solo guarda cuando la petición empieza en el byte 0 y pide el archivo completo. Escribe a un `.part` y lo renombra al terminar, y comprueba que la longitud sea la esperada. **Aborta la escritura si va más de 8 MB detrás de la reproducción**, salvo que sea una descarga pedida por el usuario.
- **Lectura** (`serve`): responde con soporte de `Range` y actualiza el `mtime` para el LRU.
- **Expulsión** (`prune`): borra por `mtime` (LRU) y **excluye las descargas** (`downloaded: true`).
- **Alcance:** solo audio; el vídeo nunca se cachea.

---

## 3. Cola

### 3.1 Android (la cola vive en el `ExoPlayer` del servicio)

- **Shuffle real:**
  - `onShuffleModeEnabledChanged(true)` guarda `unshuffledOrder` (los `mediaId` en orden) y **reordena físicamente** los items que vienen después del actual con `FisherYates.shuffle` (`kotlin.random.Random.Default`), usando `removeMediaItems` + `addMediaItems`;
  - **No usa el `ShuffleOrder` de ExoPlayer:** `keepQueueOrderUnshuffled()` fuerza `UnshuffledShuffleOrder` en cada cambio de timeline. Así el orden visible en la UI es el orden real de reproducción;
  - al desactivarlo, `QueueEditor.restoreOrder(upcoming, unshuffledOrder)` devuelve el orden original a lo que queda de cola.
- **Repeat:** usa `REPEAT_MODE_OFF/ALL/ONE` de ExoPlayer, convertidos a `RepeatMode { OFF, ALL, ONE }`.
- **Autoplay:**
  - **está en `OrchardViewModel`, no en el servicio.** `observeAutoplay()` combina `playback`, el ajuste y el destino (solo cuando suena en el teléfono);
  - la **semilla es la última canción de la cola**, no la que suena;
  - si quedan **≤ 3** (`AUTOPLAY_REFILL_THRESHOLD`), pide `catalog.upNext(seed)` (radio `RDAMVM`);
  - `AutoplayRecommendations.select()` descarta variantes de la misma grabación: título normalizado sin "(feat …)" ni "(Remastered)", mismo artista principal o `artistId`, y una duración que no difiera más de cierto margen. Entre variantes prefiere **ATV > sin tipo > subida de vídeo**;
  - añade hasta 20 (`AUTOPLAY_QUEUE_LIMIT`) con `Track.autoplayGenerated = true`. Al apagar el autoplay se quitan **solo** esas, y `duplicateGeneratedIndices()` limpia las duplicadas sin reconstruir la cola;
  - **No confirmado:** si el relleno sigue funcionando con la UI cerrada (el `ViewModel` se destruye con la Activity).
- **Persistencia (`PlaybackStateStore`):**
  - **JSON versionado (`version: 3`)** escrito con `AtomicFile` en `noBackupFilesDir/playback-state.json`;
  - guarda `queue` (los `Track` completos), `currentIndex`, `positionMs`, `shuffle`, `repeatMode`, `contextTitle`, `unshuffledOrder` (así el shuffle sigue siendo reversible tras reiniciar) y `currentVideoId` (si sonaba la versión de vídeo);
  - **cuándo se guarda:** en cada evento relevante del player, **cada 5 s mientras suena** (`POSITION_SAVE_INTERVAL_MS`) y de forma síncrona en `onDestroy`;
  - al restaurar, **`playWhenReady` siempre es `false`**: no vuelve a sonar solo porque Android recreó el proceso;
  - si el archivo no se puede leer, se ignora y se empieza de cero;
  - `onPlaybackResumption` devuelve la cola viva o, si no existe, la guardada (para la reanudación desde el sistema o por Bluetooth).

### 3.2 Escritorio (la cola vive en el renderer Vue)

- **Estado:** `activeTrack`, `queue` (**solo lo que viene después**), `history` y `shuffleSourceQueue` (el orden previo al shuffle).
- **Shuffle real:** `ctx.shuffleItems` es Fisher–Yates con **aleatoriedad criptográfica** (`crypto.randomInt` de Node o `getRandomValues`):
  - al activarlo, guarda la cola en `shuffleSourceQueue` y la baraja; al desactivarlo, restaura esa copia;
  - si se baraja una playlist que todavía no está cargada entera, primero carga páginas (`loadBrowseTracksUntil(101)`) y luego sigue rellenando con `backfillPlaylistQueue`, para que el shuffle cubra **toda** la playlist y no solo las páginas descargadas;
  - el flag `queueAlreadyShuffled` evita barajar dos veces.
- **Repeat** (`off` / `queue` / `one`, guardado en las preferencias del usuario):
  - `one` vuelve a reproducir la pista con `refreshStream: true`;
  - `queue` reconstruye la cola desde `repeatQueueSource()`. Si el shuffle está activo, la vuelve a barajar y **evita que la primera pista repetida sea la misma que acaba de sonar**.
- **Autoplay:** cuando no hay siguiente pista, o quedan pocas, emite `music:up-next` al proceso principal, que llama a youtubei.js `music.getUpNext(videoId, true)` (automix) con la cadena cliente con sesión → navegador → invitado. Limita la cola a 100.
- **Persistencia (`queuePersistence.js`):**
  - clave `orchard:playback-state` en un *session store* que el proceso principal escribe a disco (`electron/main/sessionState.js`);
  - guarda `activeTrack`, `queue`, `history` y `shuffleSourceQueue`, con un tope de **2500 pistas**, deduplicadas por `id`;
  - `sanitizedTrack()` **quita los campos volátiles** (`streamUrl`, `itag`, `mimeType`, banderas de reintento…) para no restaurar URLs caducadas;
  - **la posición dentro de la pista no aparece** en lo que se persiste. No encontré otro lugar donde se guarde (**No confirmado**).

---

## 4. Android: servicio Media3 y conexión con Compose

```
OrchardApp (Compose, NavHost)
  └─ collectAsStateWithLifecycle(viewModel.playback / targets / library / settings)
OrchardViewModel (~1700 líneas)  ── combina StateFlows; autoplay; acciones de UI
  └─ LocalPlaybackController
        MediaController (SessionToken → OrchardPlaybackService)
        Player.Listener → publish() → StateFlow<PlaybackSnapshot>
        cola de acciones pendientes (≤ 20) hasta que el controller conecta
OrchardPlaybackService : MediaLibraryService (~1700 líneas)
  ├─ MediaLibrarySession(OrchardSessionPlayer(ForwardingPlayer(ExoPlayer)), callback)
  ├─ player + spare (dos ExoPlayer para crossfade; adoptPlayer intercambia)
  ├─ MediaSource.Factory propia:
  │     audio → StreamCache(CacheDataSource) → ResolvingDataSource → OkHttp
  │     video → ResolvingDataSource → OkHttp (sin caché)
  │     HLS con sesión → HlsMediaSource con UA de Safari
  ├─ PlaybackStateStore (persistencia), OrchardMediaLibrary (árbol para Android Auto)
  └─ YouTubeStreamResolver, TrackAnalyzer, EqualizerAudioProcessor, TransitionFilter
OrchardGraph (DI manual, uno por proceso) ── lo comparten el servicio y la UI
```

- **`MediaItemMapper`:**
  - convierte `Track` ↔ `MediaItem`;
  - el `Track` completo viaja **serializado en JSON en los extras** (`orchard.track.json`), así que `toTrack(item)` recupera el modelo de dominio desde cualquier `MediaItem`, incluso en el controller;
  - la URI es estable y no contiene la URL real: `orchard://stream/<videoId>`, `orchard://video/<videoId>`, y marcas para las rutas `authenticated_direct` y `authenticated_hls`.
- **`ResolvingDataSource`:** por cada URI `orchard://`:
  - reutiliza una resolución anterior (con un bloqueo por URI, porque la misma pista la abren los dos ExoPlayer y varios procesos de caché);
  - si no, resuelve: Qobuz si la calidad es MAX y está configurado, o `resolveVideo`, `resolveAuthenticatedDirect` o `resolve`;
  - devuelve el `DataSpec` con la URL real, los headers del cliente y el `Range` acotado.
- **`OrchardSessionPlayer : ForwardingPlayer`:** anuncia **siempre** los comandos anterior/siguiente/shuffle/repeat, aunque la cola tenga un solo item. Así los controles del sistema, Bluetooth y KDE Connect no se desactivan. También ajusta `seekToPrevious`/`seekToNext`.
- **Callback de la sesión:** `onGetLibraryRoot`/`onGetChildren`/`onGetItem` (navegación desde Android Auto), `onConnect`, `onMediaButtonEvent`, `onPlaybackResumption`, `onSetMediaItems`/`onAddMediaItems` (vuelven a construir los items desde los extras) y `onCustomCommand`, con un *custom layout* de botones.
- **`LoadControl`:** usa un buffer de pista entera (`WHOLE_TRACK_BUFFER_MS`, `TARGET_BUFFER_BYTES`) para que el crossfade y el análisis tengan la canción completa.
- **`PlaybackSnapshot`:** es el contrato hacia la UI. Lleva `status` (`IDLE`/`LOADING`/`READY`/`BUFFERING`/`PLAYING`/`PAUSED`/`ENDED`/`ERROR`), `currentTrack`, `queue`, `currentIndex`, `positionMs`, `durationMs`, `bufferedPositionMs`, `shuffle`, `repeatMode`, `contextTitle`, `errorMessage` y `playingVideo`.
- **Errores en la UI:** el controller asocia el error al `mediaId` en que ocurrió y lo **limpia solo** al cambiar de pista o cuando esa pista llega a `READY`.
- **Duración:** si la cola restaurada todavía no está preparada, se usa la duración que da el catálogo, para que la barra de progreso no muestre 0.

---

## 5. Qué aplicar en Shura (KMP: Android + Windows)

### 5.1 Ideas que encajan bien

1. **URI estable + resolución en el último momento + caché antes de la resolución.**
   - `MediaItem` con `shura://stream/<id>` y la pista de dominio en los extras.
   - En Android, `ResolvingDataSource`, con `CacheDataSource` por encima, con clave `uri|calidad`.
   - En `commonMain`, la interfaz `StreamResolver` y el modelo `ResolvedStream(url, mime, expiresAt, bitrate, requestHeaders, clientKey, contentLength)`.
2. **Que el `ResolvedStream` lleve la identidad del cliente** (User-Agent, Origin, Referer) y que el *fetch* la use. Aplica igual al reproductor de escritorio.
3. **`Range` acotado siempre** que se conozca la longitud. Las dos apps lo midieron por separado.
4. **No confundir "resuelto" con "reproducible":**
   - validar la URL con un sondeo (como escritorio) o vetar el cliente cuando el CDN la rechaza (como Android);
   - tener en cuenta lo medido sobre `ANDROID_VR`: solo sirve un prefijo del archivo, así que **la URL "funciona" y la canción se corta a mitad**.
5. **Reglas de resiliencia del resolver**, todas configurables:
   - límite total de tiempo (25 s);
   - espera tras fallo por pista (20 s), que se limpia cuando el usuario reintenta a mano;
   - bloqueo por pista para unir la precarga y la reproducción;
   - veto por pista y cliente (10 min) cuando el CDN rechaza;
   - "último cliente que funcionó" que se borra si ese cliente resulta rechazado;
   - refrescar la identidad de visitante una sola vez por resolución.
6. **Escalera de recuperación como máquina de estados explícita** (§2.4): vídeo → audio; rechazo → rotar cliente (hasta 3); directo con sesión → HLS; restricción de edad → itag 18. Siempre con `replaceMediaItem` + `seekTo` para conservar la posición, y sin reintentar solo cuando ya se agotaron todos los clientes.
7. **Restricción de edad:** detectarla en el paso *player* (no a partir de un 403). Con sesión, probar itag 18 y luego HLS. Además, la alternativa del videoclip con duración ±5 s. No fiarse de la marca "explícito" del catálogo, que solo indica la letra.
8. **Detalles de InnerTube que conviene copiar como idea:**
   - búsquedas internas **anónimas** para no tocar el historial de la cuenta;
   - reintentar sin `onBehalfOfUser` ante un 400 "invalid argument";
   - leer `INNERTUBE_API_KEY` y `INNERTUBE_CLIENT_VERSION` de la página, con valores por defecto;
   - preferir `playlistItemData.videoId` (audio del álbum);
   - guardar `musicVideoType`;
   - variantes de `browseId` (`VL`, con y sin `params`).
9. **Paginación progresiva** como `Flow<BrowseDetail>` (emitir la primera página y luego copias más completas), con un límite de páginas mayor para las mezclas `RD…`, sin perder filas si falla una página, y deduplicando salvo en las playlists.
10. **Sesión:**
    - contador de generación para descartar resultados asíncronos obsoletos;
    - secretos cifrados aparte de las preferencias (en Android con AndroidKeyStore + AES-GCM);
    - normalizar `SAPISID` a partir de `__Secure-3PAPISID`;
    - `SAPISIDHASH` con sus variantes 1P y 3P;
    - `normalizeDataSyncId` para las dos formas con `||`.
11. **Cola:**
    - Fisher–Yates real sobre lo que queda de cola, guardando el orden original **y persistiéndolo**;
    - repeat-all que vuelve a barajar sin repetir primero la misma canción;
    - shuffle de una playlist entera aunque no esté cargada del todo;
    - semilla del autoplay = la cola, no la canción que suena;
    - deduplicar variantes en el autoplay y marcar `autoplayGenerated` para poder quitarlas.
12. **Persistencia de la cola:** JSON versionado con kotlinx.serialization, escritura atómica, guardado cada 5 s mientras suena y al destruir, `playWhenReady = false` al restaurar, quitar los campos volátiles del stream y un tope de pistas.
13. **`ForwardingPlayer`** para anunciar siempre los comandos. **Puente UI ↔ servicio:** `MediaController` dentro de un controlador con cola de acciones pendientes y un `StateFlow<PlaybackSnapshot>` como único contrato hacia Compose.

### 5.2 Ideas que no encajan o que hay que rehacer

1. **Todo lo de Electron:** el login con `BrowserWindow` y la partición, el proxy HTTP local para `<audio>`, `executeJavaScript` sobre la página y el session store del renderer. Un apunte: un **proxy local** podría volver a ser útil en Windows si el reproductor elegido no permite headers ni `Range` por petición (**no evaluado**).
2. **youtubei.js** (JS) y **bgutils-js + jsdom** (Node) no sirven en KMP.
3. **Cipher y PoToken en un WebView oculto** (`YouTubeChallengeSolver`, `YouTubePoTokenMinter`): solo funcionan en Android. Es el mismo hueco que con Metrolist (ver `ref-metrolist.md` §8.2): **en Windows no hay equivalente** y sigue siendo el mayor riesgo para la versión de escritorio.
4. **NewPipeExtractor como ruta principal:** es una biblioteca Java (GPL-3.0). Encaja en `androidMain` y en `jvmMain` de escritorio, pero **no en `commonMain`**. La decisión sigue abierta en PLAN.md §3, junto con InnerTubeX.
5. **Cloudflare Workers** (`workers/*`: bpm, lastfm, concerts, artwork-proxy, support, listening-party, song-links, artist-packs), **Supabase** (`supabase/`, `SupabaseSyncService`), **Qobuz** (`packages/qobuz`, `QobuzResolver`), **Discord**, **Cast**, **Spotify Canvas**, **Listening Party** y la **mezcla inteligente con modelos** (`models/beat-this`, `vocal-separation`, `native-audio-rust`): son servidores propios o funciones fuera del alcance de Shura. Los Workers existen sobre todo para **no meter claves de API de terceros en el cliente**. Si Shura llega a integrar Last.fm o similares, ese problema habrá que resolverlo de otra forma.
6. **`MediaLibraryService`, el `SimpleCache`, el `LoadControl`, el crossfade con dos ExoPlayer y los `AudioProcessor`:** son solo del `actual` de Android. En escritorio harán falta una caché, una precarga y un EQ propios detrás de la misma interfaz. Puede servir de modelo el `songCache.js` de escritorio (tee del cuerpo, `.part` + renombrar, abortar si la escritura se retrasa más de 8 MB, LRU por `mtime` que excluye las descargas).

### 5.3 Anti-patrones que conviene evitar

- **Archivos enormes:** `OrchardPlaybackService` (~1700 líneas), `OrchardViewModel` (~1700) y `YouTubeStreamResolver` (~1400). Igual que con Metrolist, conviene separar `PlaybackController`, `QueueManager`, `StreamResolver` (con una estrategia por cliente) y `ErrorRecovery`, y dejar el servicio como un adaptador fino.
- **Autoplay en el `ViewModel`:** el mismo problema que el automix de Metrolist en la UI. En Shura va en la capa de cola, para que funcione con la UI cerrada y en escritorio.
- **`runBlocking(Dispatchers.Main)` dentro del `ResolvingDataSource`** (en la ruta de Qobuz, para leer items del player): bloquea un hilo de carga esperando al hilo principal, con riesgo de *deadlock*. Mejor pasar la pista resuelta en los extras del `MediaItem`.
- **Heurística de restricción de edad con una regex sobre el título** (`isAgeGateRiskTrack`): es frágil y está atada al idioma. Mejor apoyarse en la respuesta del *player*.
- **Dos implementaciones del mismo resolver** (JS y Kotlin) que se van separando. KMP evita esto si la lógica de elección de cliente, reintentos y formato vive en `commonMain`.
- **Versiones de cliente, API key y User-Agent fijados en el código** en las dos apps, a veces con versiones distintas para el mismo cliente. Mejor centralizarlos en un solo catálogo, que se pueda actualizar sin tocar la lógica.
- **Callbacks globales mutables en el grafo de dependencias** (`OrchardGraph.onClearStreamCache`, `analysisLookup` como `var`): acoplan el servicio y la UI de forma implícita.
- **Escritorio: posición de reproducción no persistida** (**No confirmado**) y la cola guardada desde la UI. En Shura la persistencia debe estar en la capa de reproducción.

---

## 6. Pendientes de verificar

- [ ] El origen exacto de `yt_solver.bundle.js` (probablemente el EJS de yt-dlp; no hay script de build en el repo, solo el de `yt_potoken.bundle.js`).
- [ ] Si el autoplay de Android sigue rellenando la cola con la UI cerrada.
- [ ] Si el escritorio guarda la posición dentro de la pista en algún otro sitio.
- [ ] El orden real de clientes en uso (con logs de `YouTubeStreamResolver` en un dispositivo) y si la limitación de prefijo de `ANDROID_VR` sigue vigente.
- [ ] La caducidad real de las URL que da NewPipe (Orchard asume 45 min).
- [ ] Probar NewPipeExtractor en `jvmMain` de escritorio con Windows (Orchard solo lo usa en Android).
