package io.github.javiernarvaezz.shura

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import io.github.javiernarvaezz.shura.appshell.ShellDependencies
import io.github.javiernarvaezz.shura.appshell.ShuraAppShell
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme
import java.io.FileDescriptor
import java.io.PrintWriter
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val graph get() = (application as ShuraApp).graph

    /** However the sign-in screen ended: end its process, delete its WebView data, then read the session. */
    private val signIn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val context = applicationContext
            Thread {
                WebViewData.endSignIn(context)
                graph.account.reload()
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark only: light status and navigation bar icons whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyDebugLaunchOptions(intent) // Debug builds only; before the graph is first used.
        debugDumpAccountFixtures(intent, applicationContext, graph) // Debug builds only.
        setContent {
            ShuraTheme {
                ShuraAppShell(
                    remember {
                        ShellDependencies(
                            player = graph.player,
                            history = graph.store,
                            catalog = graph.catalog,
                            onPlaybackIntent = graph.playbackWarmup::onPlaybackIntent,
                            currentHour = { LocalTime.now().hour },
                            networkRegained = graph.networkRegained,
                            settings = graph.settings,
                            lyrics = graph.lyrics,
                            lyricsOffsets = graph.lyricsOffsets,
                            library = graph.library,
                            account = graph.account,
                            accountLibrary = graph.accountLibrary,
                            onSignIn = { signIn.launch(Intent(this, LoginActivity::class.java)) },
                        )
                    },
                )
            }
        }
        // Compose paints the whole background itself; the window's own fill under it is wasted work every frame.
        window.setBackgroundDrawable(null)
    }

    /** `dumpsys activity <package>/.MainActivity`: how long sessions lasted ("<date> alive" or "<date> expired"). */
    override fun dump(
        prefix: String,
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<out String>?,
    ) {
        super.dump(prefix, fd, writer, args)
        writer.println("${prefix}Session checks (one line per day):")
        graph.sessionHealth.lines().forEach { writer.println("$prefix  $it") }
    }
}
