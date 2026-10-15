package io.github.javiernarvaezz.shura

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import io.github.javiernarvaezz.shura.core.auth.CookieJarText
import io.github.javiernarvaezz.shura.core.auth.GoogleSignInHosts
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme
import io.github.javiernarvaezz.shura.feature.settings.SignInFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream

/**
 * Google sign-in for YouTube Music in a hardened WebView (ADR 0002). The user types into Google's own page; the
 * app never reads it, and the window is FLAG_SECURE. On reaching YouTube Music the session (YouTube's and Google's
 * cookies, the DATASYNC_ID) goes to the encrypted store. However the screen closes (signed in, back, a camera
 * check, an error), the WebView is destroyed and its cookies and storage are wiped; what a dead process leaves is
 * wiped at the next start ([WebViewData.wipeAtStart]).
 */
class LoginActivity : ComponentActivity() {
    private var web: WebView? = null
    private var done = false
    private val cameraCheckAsked = mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val view =
            WebView(this).apply {
                settings.javaScriptEnabled = true // Google's sign-in needs it; no JavaScript interface is added.
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.setGeolocationEnabled(false)
                webViewClient = SignInClient()
                webChromeClient = CameraRefusingClient()
            }
        web = view
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        setContent {
            ShuraTheme {
                SignInFrame(
                    cameraCheckAsked = cameraCheckAsked.value,
                    onBack = ::finish,
                    onOpenBrowser = ::openBrowser,
                ) { modifier -> AndroidView(factory = { view }, modifier = modifier) }
            }
        }
        view.loadUrl(SIGN_IN_URL)
    }

    override fun onDestroy() {
        web?.let(WebViewData::destroyAndWipe)
        web = null
        super.onDestroy()
    }

    private fun openBrowser() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com")))
    }

    /** Signed in: the page reached YouTube Music. Reads the session once, hands it over and closes. */
    private fun onReachedYouTubeMusic(view: WebView) {
        if (done) return
        done = true
        val cookies = CookieManager.getInstance()
        val youtube = CookieJarText.parse(cookies.getCookie(YOUTUBE_MUSIC).orEmpty())
        val google = CookieJarText.parse(cookies.getCookie(GOOGLE_ACCOUNTS).orEmpty())
        view.evaluateJavascript(DATASYNC_ID_SCRIPT) { value ->
            val dataSyncId =
                runCatching {
                    (
                        Json.parseToJsonElement(
                            value,
                        ) as? JsonPrimitive
                    )?.content
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            val graph = (application as ShuraApp).graph
            graph.account.onSignedIn(Session(youtube, dataSyncId, null, System.currentTimeMillis(), google))
            Log.i(TAG, "signed in: cookies=${youtube.size}+${google.size} dataSyncId=${dataSyncId != null}")
            finish()
        }
    }

    private inner class SignInClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val host = request.url.host.orEmpty()
            val allowed = request.url.scheme == "https" && GoogleSignInHosts.navigationAllowed(host)
            if (!allowed) Log.i(TAG, "blocked navigation host=$host")
            return !allowed
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val host = request.url.host.orEmpty()
            if (request.url.scheme == "data" || GoogleSignInHosts.resourceAllowed(host)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            if (Uri.parse(url).host == "music.youtube.com") onReachedYouTubeMusic(view)
        }
    }

    /** The camera is never granted: a camera check (video selfie) is completed in the browser instead. */
    private inner class CameraRefusingClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) cameraCheckAsked.value = true
        }
    }

    private companion object {
        const val TAG = "SunloopSignIn"
        const val YOUTUBE_MUSIC = "https://music.youtube.com"
        const val GOOGLE_ACCOUNTS = "https://accounts.google.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
        const val DATASYNC_ID_SCRIPT = "(function(){try{return ytcfg.get('DATASYNC_ID')||''}catch(e){return ''}})()"
    }
}
