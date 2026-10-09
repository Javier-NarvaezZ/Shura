package io.github.javiernarvaezz.shura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.javiernarvaezz.shura.appshell.ShellDependencies
import io.github.javiernarvaezz.shura.appshell.ShuraAppShell
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val graph get() = (application as ShuraApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyDebugLaunchOptions(intent) // Debug builds only; before the graph is first used.
        setContent {
            ShuraTheme {
                ShuraAppShell(
                    remember {
                        ShellDependencies(
                            player = graph.player,
                            history = graph.store,
                            searchSongs = graph.catalog::searchSongs,
                            onPlaybackIntent = graph.playbackWarmup::onPlaybackIntent,
                            currentHour = { LocalTime.now().hour },
                            networkRegained = graph.networkRegained,
                        )
                    },
                )
            }
        }
        // Compose paints the whole background itself; the window's own fill under it is wasted work every frame.
        window.setBackgroundDrawable(null)
    }
}
