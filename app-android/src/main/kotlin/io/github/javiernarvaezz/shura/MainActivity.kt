package io.github.javiernarvaezz.shura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.javiernarvaezz.shura.appshell.ShellDependencies
import io.github.javiernarvaezz.shura.appshell.ShuraAppShell
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme

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
                            searchSongs = graph.catalog::searchSongs,
                            onPlaybackIntent = graph.playbackWarmup::onPlaybackIntent,
                        )
                    },
                )
            }
        }
    }
}
