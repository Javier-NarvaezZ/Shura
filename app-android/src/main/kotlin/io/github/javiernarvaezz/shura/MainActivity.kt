package io.github.javiernarvaezz.shura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme
import io.github.javiernarvaezz.shura.feature.search.SearchController
import io.github.javiernarvaezz.shura.feature.search.SearchScreen

class MainActivity : ComponentActivity() {
    private val graph get() = (application as ShuraApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShuraTheme {
                val scope = rememberCoroutineScope()
                val controller = remember { SearchController(graph.catalog::searchSongs, graph.player, scope) }
                SearchScreen(controller)
            }
        }
    }

    // Spike plays in the foreground only; background playback with MediaSession comes in Phase 2.
    override fun onStop() {
        super.onStop()
        graph.player.pause()
    }
}
