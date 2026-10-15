package io.github.javiernarvaezz.shura

import android.content.Context
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

    /**
     * At process start, before any WebView exists: deletes what a previous run left (a sign-in interrupted by the
     * process dying, or files a destroyed WebView wrote back while shutting down).
     */
    fun wipeAtStart(context: Context) {
        File(context.dataDir, "app_webview").deleteRecursively()
        File(context.cacheDir, "WebView").deleteRecursively()
    }
}
