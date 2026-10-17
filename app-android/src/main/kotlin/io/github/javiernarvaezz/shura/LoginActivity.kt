package io.github.javiernarvaezz.shura

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.github.javiernarvaezz.shura.core.auth.CookieJarText
import io.github.javiernarvaezz.shura.core.auth.GoogleSignInHosts
import io.github.javiernarvaezz.shura.core.auth.KeystoreSessionStore
import io.github.javiernarvaezz.shura.core.auth.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream

/**
 * Google sign-in for YouTube Music in a hardened WebView (ADR 0002), in its own process (`:signin`) with its own
 * WebView data directory. The user types into Google's own page; the app never reads it, and the window is
 * FLAG_SECURE. On reaching YouTube Music the session (YouTube's and Google's cookies, the DATASYNC_ID) is saved to
 * the encrypted store from this process, so no secret crosses processes. However the screen closes (signed in,
 * back, a camera check, an error), the WebView is destroyed and its cookies and storage wiped; the main process
 * then ends this process and deletes its whole data directory ([WebViewData.endSignIn]), and also at every start.
 */
class LoginActivity : ComponentActivity() {
    private var web: WebView? = null
    private var done = false
    private lateinit var cameraNotice: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before any WebView in this process: its data stays apart from the app's other WebViews (Android 9+; on
        // 8.x it shares the default directory, which the main process deletes after sign-in and at every start).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { WebView.setDataDirectorySuffix(WebViewData.SIGN_IN_SUFFIX) }
        }
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
        setContentView(layout(view))
        view.loadUrl(SIGN_IN_URL)
    }

    override fun onDestroy() {
        web?.let(WebViewData::destroyAndWipe)
        web = null
        super.onDestroy()
    }

    /**
     * Back, the title, help, and a notice when Google asks for a camera check (a video selfie), which the app does
     * not support: the user completes it in the browser and signs in here again. Android views and resources:
     * Compose resources are not available in this process.
     */
    private fun layout(page: WebView): View {
        val text = getColor(R.color.sunloop_text)
        val accent = getColor(R.color.sunloop_accent)
        val padding = (resources.displayMetrics.density * PADDING_DP).toInt()
        val help = notice(R.string.sign_in_help_body, padding).apply { visibility = View.GONE }
        cameraNotice = notice(R.string.sign_in_camera_check, padding).apply { visibility = View.GONE }
        val top =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    Button(this@LoginActivity, null, android.R.attr.borderlessButtonStyle).apply {
                        setText(R.string.sign_in_back)
                        setTextColor(text)
                        setOnClickListener { finish() }
                    },
                )
                addView(
                    TextView(this@LoginActivity).apply {
                        setText(R.string.sign_in_title)
                        setTextColor(text)
                        textSize = TITLE_SP
                    },
                )
            }
        val helpLink =
            TextView(this).apply {
                setText(R.string.sign_in_help)
                setTextColor(accent)
                setPadding(padding, padding / 2, padding, padding / 2)
                setOnClickListener {
                    help.visibility =
                        if (help.visibility ==
                            View.VISIBLE
                        ) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
                }
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.sunloop_background))
            fitsSystemWindows = true
            addView(top)
            addView(helpLink)
            addView(help)
            addView(cameraNotice)
            addView(page, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun notice(
        message: Int,
        padding: Int,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.sunloop_surface))
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(this@LoginActivity).apply {
                    setText(message)
                    setTextColor(getColor(R.color.sunloop_text))
                },
            )
            addView(
                Button(this@LoginActivity).apply {
                    setText(R.string.sign_in_open_browser)
                    setTextColor(getColor(R.color.sunloop_on_accent))
                    setBackgroundColor(getColor(R.color.sunloop_accent))
                    setOnClickListener { openBrowser() }
                },
            )
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
            val session = Session(youtube, dataSyncIdOf(value), null, System.currentTimeMillis(), google)
            // Saved from this process: the session never travels to the main process.
            Thread {
                KeystoreSessionStore.create(applicationContext).save(session)
                Log.i(TAG, "signed in: cookies=${youtube.size}+${google.size} dataSyncId=${session.dataSyncId != null}")
                runOnUiThread {
                    setResult(RESULT_OK)
                    finish()
                }
            }.start()
        }
    }

    /** The script's JSON string result, or null when empty or not a string. */
    private fun dataSyncIdOf(value: String): String? =
        runCatching { (Json.parseToJsonElement(value) as? JsonPrimitive)?.content }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

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
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) {
                runOnUiThread { cameraNotice.visibility = View.VISIBLE }
            }
        }
    }

    private companion object {
        const val TAG = "SunloopSignIn"
        const val YOUTUBE_MUSIC = "https://music.youtube.com"
        const val GOOGLE_ACCOUNTS = "https://accounts.google.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
        const val PADDING_DP = 16
        const val TITLE_SP = 20f
        const val DATASYNC_ID_SCRIPT = "(function(){try{return ytcfg.get('DATASYNC_ID')||''}catch(e){return ''}})()"
    }
}
