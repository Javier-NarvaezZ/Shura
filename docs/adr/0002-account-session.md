# ADR 0002: YouTube Music account and session

- **Status:** Accepted on 2026-10-17 (Phase 3, E4).
- **Date:** 2026-10-17
- **Scope:** `:core:auth`, `:core:innertube` (account client), `:core:network` (`HostPolicy`), `:feature:settings`, `app-android` (sign-in screen)

## Context

Phase 3 adds the user's YouTube Music library. That needs a signed-in session: Google's cookies, signed per request. There is no public API for it.

A throwaway spike (E3, 2026-10-15, test account only) showed that it works:
- Google's sign-in in an Android `WebView` succeeds on the test phone (Galaxy A31, Android 12) with no "browser not secure" block.
- The page then reaches YouTube Music and yields the session cookies and `DATASYNC_ID`.
- An InnerTube `browse` request signed with them answers `logged_in=1`.

The spike also found three things to plan for:
- the sign-in redirects through Google's country domain (`accounts.google.com.co`);
- Google may ask for a camera check (a video selfie), which a `WebView` without camera access cannot do;
- a destroyed `WebView` writes its data directory back while it shuts down.

The session is the most sensitive data the app holds. The owner's rules:
- never in logs, the repository or backups;
- encrypted with a device key;
- deleted on sign-out;
- tests only with a secondary account, with the owner typing the credentials;
- no camera permission.

## Decision

### Sign-in
- **Where it runs:** Google's own page in a `WebView` (`LoginActivity`), in its **own process** (`:signin`) with its own `WebView` data directory (`WebView.setDataDirectorySuffix`, Android 9+).
- **Hardening:**
  - `FLAG_SECURE` on the window;
  - no JavaScript interface and no file or content access;
  - nothing the user types is read.
- **Closed host list** (`GoogleSignInHosts`), never a wildcard:
  - pages may go to `accounts.google.<domain>` for each of Google's official domains (https://www.google.com/supported_domains, 187 domains, fetched 2026-10-15), to `*.google.com`, to `accounts.youtube.com` and to `music.youtube.com`;
  - resources may also come from `gstatic.com`, `googleusercontent.com`, `ytimg.com` and `ggpht.com`;
  - ads and analytics stay blocked; sign-in works without them;
  - only the host of a blocked request is logged.
- **On reaching `music.youtube.com`**, the sign-in process:
  1. reads YouTube's cookies (which sign requests) and Google's (which sign out);
  2. reads `ytcfg` `DATASYNC_ID`;
  3. saves them to the encrypted store itself, so no secret crosses processes.
- **Camera checks:** the camera is always refused. When Google asks for it, a notice says to complete the check in the browser and sign in again, with an "Abrir en el navegador" button. A help link always explains the same. The app has no camera permission.
- **Android views, not Compose:** the screen is built with Android views and resources, because Compose Multiplatform resources are only initialized in the main process and reading them crashed the sign-in process.

### Nothing of the sign-in survives it
- **However the screen ends** (signed in, back, a camera check, an error), the `WebView` is destroyed and its cookies, web storage, cache and history are cleared.
- **Then the main process:**
  - ends the sign-in process;
  - deletes its whole data directory (`app_webview_signin`);
  - deletes the `WebView`'s record of visited origins (`AwOriginVisitLoggerPrefs.xml`).
- **At every start, before any `WebView` exists,** the main process deletes `app_webview*`. This covers a sign-in interrupted by the process dying.
- **On Android 8.x** there is no per-process data directory. Sign-in uses the default one, which the main process deletes after sign-in and at every start.
- **Verified on the A31:**
  - a cancelled sign-in and one killed mid-way leave no `app_webview*` and no cookie files;
  - after a real sign-in, the same holds.

### Storage
- **`EncryptedSessionFile`:** one file, `noBackupFilesDir/session.bin`, AES-256-GCM, with a fresh IV per save chosen by the cipher.
- **The key** (`KeystoreSessionStore`) lives in the Android Keystore and never leaves it.
- **Backups:** the file is outside them twice over. The backup rules include only `database/shura.db` (ADR 0001), and the file is in `no_backup`. Nothing of the session goes to `shura.db`.
- **A file that cannot be read back** (key changed or invalidated, damaged file) is deleted with its key. Settings then asks to sign in again, and the app never fails over it.
  - Verified with a damaged file on the A31; the changed key is covered by a JVM test.
  - A real `KeyPermanentlyInvalidatedException` cannot be provoked without root, and takes the same path.
- **`Session.toString()`** and **`AccountCredentials.toString()`** hide every value.

### Requests as the user
- **A separate client:** `InnerTubeAccountClient`. `InnerTubeClient` stays anonymous, with a test that it sends no `Cookie`, `Authorization` or `X-Goog-AuthUser`; search, catalog and streams never carry the session.
- **Signing:** `Cookie`, `Authorization: SAPISIDHASH <time>_<sha1(time SAPISID origin)>` (using `SAPISID`, or `__Secure-3PAPISID`), `X-Goog-AuthUser: 0`, `X-Origin`, and `onBehalfOfUser` only for a brand channel.
- **Checking the session:** `account/account_menu` tells whether the session is alive and gives the account's name.
  - It runs at every start: a session Google no longer accepts is deleted (Settings: "Tu sesión caducó"); an unreachable Google changes nothing.
  - Rotated cookies (`Set-Cookie`) are merged into the stored session.
- **Fixtures** for account responses are synthetic: a real one would carry the account. The hygiene test rejects e-mail addresses. The real shape was confirmed on the device, where the account name appeared.

### Signing out
- **First at Google:** `https://accounts.google.com/Logout` with Google's cookies, through the single client. That needs the exact host `accounts.google.com` in `HostPolicy` (approved, with a test).
- **Then here:** the session and its key are deleted even if Google cannot be reached.
- **Result at Google, checked by the owner with the test account in "Tus dispositivos" on 2026-10-17:** the session the app opened ("SM-A315G · Chrome Webview") shows as signed out, so **the sign-out ends the session on Google's side**. The spike's sessions of 2026-10-15, signed out by hand, were also closed.
- **A separate "Galaxy A31 · Android device" session** appeared at the same time, and the test account is also among the phone's Android accounts.
  - Sunloop did not create either: it uses no `AccountManager` and holds no account permission. Its merged manifest asks only for network, foreground-service, wake-lock and its internal receiver permission.
  - They come from the system. The likely source is the test account being added to the phone when the camera check was completed in Chrome (2026-10-15); this is not verified.
  - Signing out of Sunloop does not, and should not, touch the phone's Android accounts.

## Consequences
- Sign-in depends on Google keeping the `WebView` sign-in usable; Google can block embedded browsers at any time. A camera check needs the browser.
- The country-domain list must be refreshed if Google adds domains (`GoogleSignInHosts`).
- **How long a session lasts is not measured yet.** It will be measured in real use with the encrypted store, with only "alive or expired" and the date recorded.
- **Desktop (Phase 5)** needs its own sign-in (an embedded browser or another approach) and Windows' protected storage (DPAPI).

## Verification
- **JVM tests:** `SapisidHash`, `CookieJarText`, `GoogleSignInHosts`, `EncryptedSessionFile` (round trip, IV, changed key, damage, clear), `InnerTubeAccountClient` (headers only on account requests, brand channel, rotated cookies, errors without secrets, anonymous client clean), `AccountModel`, `HostPolicy`.
- **On the A31,** with the test account, typed by the owner:
  - sign-in;
  - name shown in Settings;
  - no `WebView` data or cookie files left;
  - `session.bin` has no readable text;
  - the session survives an update and a restart;
  - sign-out deletes the session;
  - a damaged session file asks to sign in again.
