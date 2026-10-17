package io.github.javiernarvaezz.shura

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File

/**
 * The WebView's own data (cookies, web storage, cache) must not outlive a sign-in: the session lives only in the
 * encrypted store. The app's WebViews (sign-in, PoToken minter) keep nothing between runs, so it is all wiped.
 */
object WebViewData {
    /** After a sign-in screen closes, however it closed: destroys [view], then wipes cookies and storage. */
    fun destroyAndWipe(view: WebView) {
        view.stopLoading()
        view.clearHistory()
        view.clearCache(true)
        view.destroy()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** The sign-in process's WebView data directory suffix: `app_webview_signin`. */
    const val SIGN_IN_SUFFIX = "signin"

    /**
     * In the main process at start, before any WebView exists: deletes what a previous run left (a sign-in
     * interrupted by the process dying, or files a destroyed WebView wrote back while shutting down).
     */
    fun wipeAtStart(context: Context) {
        File(context.dataDir, "app_webview").deleteRecursively()
        File(context.cacheDir, "WebView").deleteRecursively()
        wipeSignIn(context)
    }

    /**
     * In the main process, after the sign-in screen closed: ends the sign-in process (same app, so allowed), waits
     * for it to go, then deletes its WebView data. Blocking: call off the main thread.
     */
    fun endSignIn(context: Context) {
        val name = "${context.packageName}:$SIGN_IN_SUFFIX"
        val manager = context.getSystemService(ActivityManager::class.java)

        fun alive() = manager?.runningAppProcesses.orEmpty().filter { it.processName == name }
        alive().forEach { Process.killProcess(it.pid) }
        repeat(WAIT_STEPS) { if (alive().isNotEmpty()) Thread.sleep(WAIT_STEP_MS) }
        wipeSignIn(context)
    }

    /** Deletes the sign-in WebView's whole data directory; the sign-in process must not be running. */
    fun wipeSignIn(context: Context) {
        File(context.dataDir, "app_webview_$SIGN_IN_SUFFIX").deleteRecursively()
        File(context.cacheDir, "WebView_$SIGN_IN_SUFFIX").deleteRecursively()
        // The WebView's own record of visited origins (metrics): it would show a sign-in happened.
        File(context.dataDir, "shared_prefs/$VISITED_ORIGINS").delete()
        // Android 8.x has no per-process directory: sign-in used the default one.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            File(context.dataDir, "app_webview").deleteRecursively()
            File(context.cacheDir, "WebView").deleteRecursively()
        }
    }

    private const val VISITED_ORIGINS = "AwOriginVisitLoggerPrefs.xml"
    private const val WAIT_STEPS = 30
    private const val WAIT_STEP_MS = 100L
}
