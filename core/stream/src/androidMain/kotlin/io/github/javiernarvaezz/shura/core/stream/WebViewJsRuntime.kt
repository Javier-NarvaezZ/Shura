package io.github.javiernarvaezz.shura.core.stream

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Hardened, network-less `WebView` that runs the BotGuard bootstrap (ADR 0001 R2). It only computes: every
 * request it attempts is blocked and logged by host only, because all HTTP happens in Kotlin through the app's
 * single client (R7). It has its own network stack, so the R7 source guard does not cover it; this class does.
 *
 * Create it with [create] (it switches to the main thread). Not shared: one instance per attestation.
 */
class WebViewJsRuntime private constructor(
    private val webView: WebView,
    private val bridge: Bridge,
    override val userAgent: String,
) : JsRuntime {
    private val requestIds = AtomicLong()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The single JavaScript interface: it only receives `(requestId, ok, payload)` and exposes nothing else. */
    private class Bridge {
        val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

        @JavascriptInterface
        fun onResult(
            requestId: String,
            ok: Boolean,
            payload: String,
        ) {
            val deferred = pending.remove(requestId) ?: return
            if (ok) {
                deferred.complete(
                    payload,
                )
            } else {
                deferred.completeExceptionally(JsRuntimeException(payload.take(CODE_LIMIT)))
            }
        }

        fun failAll(code: String) {
            pending.keys.toList().forEach { id -> pending.remove(id)?.completeExceptionally(JsRuntimeException(code)) }
        }
    }

    override suspend fun load(script: String) {
        val done = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) { webView.evaluateJavascript(script) { done.complete(Unit) } }
        done.await()
    }

    override suspend fun call(
        function: String,
        argumentsJs: String,
    ): String {
        val id = "r${requestIds.incrementAndGet()}"
        val result = CompletableDeferred<String>()
        bridge.pending[id] = result
        try {
            withContext(Dispatchers.Main) { webView.evaluateJavascript("$function('$id', $argumentsJs);", null) }
            return result.await()
        } finally {
            bridge.pending.remove(id)
        }
    }

    override fun close() {
        bridge.failAll("closed")
        mainHandler.post {
            // The WebView may already be destroyed after a renderer crash; storage is wiped either way.
            runCatching {
                webView.removeJavascriptInterface(BotGuardScripts.BRIDGE)
                webView.stopLoading()
                webView.clearCache(true)
                webView.destroy()
            }
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    companion object {
        private const val TAG = "ShuraPoToken"
        private const val BASE_URL = "https://www.youtube.com"
        private const val BLANK_PAGE = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>"
        private const val CODE_LIMIT = 32
        private const val HTTP_FORBIDDEN = 403
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

        @SuppressLint("SetJavaScriptEnabled") // Required to run BotGuard; the page has no network and no file access.
        suspend fun create(context: Context): WebViewJsRuntime =
            withContext(Dispatchers.Main) {
                // This process has no other WebView: cookies are refused and wiped (R2).
                CookieManager.getInstance().setAcceptCookie(false)
                val bridge = Bridge()
                val pageReady = CompletableDeferred<Unit>()
                val webView = WebView(context.applicationContext)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true // Cleared on close (R2); whether BotGuard needs it is not verified.
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    setGeolocationEnabled(false)
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mediaPlaybackRequiresUserGesture = true
                    safeBrowsingEnabled = true
                    blockNetworkLoads = true
                    // The Android WebView user agent gets no integrity token for web page attestation (measured on
                    // 2026-10-05); a desktop Chrome one, as InnerTubeX's harness uses, does. HTTP for the attestation
                    // uses this same value (JsRuntime.userAgent).
                    userAgentString = DESKTOP_USER_AGENT
                }
                webView.webViewClient = BlockingClient(bridge, pageReady)
                webView.webChromeClient = SilentChromeClient()
                webView.addJavascriptInterface(bridge, BotGuardScripts.BRIDGE)
                webView.loadDataWithBaseURL(BASE_URL, BLANK_PAGE, "text/html", "utf-8", null)
                pageReady.await()
                WebViewJsRuntime(webView, bridge, webView.settings.userAgentString)
            }
    }

    /** Blocks every request and navigation; recovers from a renderer crash without taking the app down. */
    private class BlockingClient(
        private val bridge: Bridge,
        private val pageReady: CompletableDeferred<Unit>,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            // BotGuard loads an inline data: resource; without it GenerateIT returns no integrity token (measured on
            // 2026-10-05). data: is in-memory content, not network; every other scheme stays blocked.
            if (request.url.scheme == "data") return null
            Log.w(TAG, "WebView request blocked scheme=${request.url.scheme} host=${request.url.host}")
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                HTTP_FORBIDDEN,
                "Blocked",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            Log.w(TAG, "WebView navigation blocked host=${request.url.host}")
            return true
        }

        override fun onPageFinished(
            view: WebView,
            url: String?,
        ) {
            pageReady.complete(Unit)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            Log.w(TAG, "WebView renderer gone (crashed=${detail.didCrash()})")
            bridge.failAll("renderer-gone")
            pageReady.completeExceptionally(JsRuntimeException("renderer-gone"))
            view.destroy()
            return true
        }
    }

    /** Swallows console output: the default client would copy it to logcat. */
    private class SilentChromeClient : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
    }
}
