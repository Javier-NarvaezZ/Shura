package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil3.compose.LocalPlatformContext
import io.github.javiernarvaezz.shura.core.ui.artwork.LocalNetworkRegained
import io.github.javiernarvaezz.shura.core.ui.artwork.preloadArtwork

/**
 * Loads the next song's cover (and its colors) while the current one plays, at the size the mini-player and the
 * player show, so the track change does not wait for the network. Draws nothing.
 */
@Composable
fun PreloadUpcomingArtwork(model: PlayerModel) {
    val url by model.upcomingArtworkUrl.collectAsState()
    val context = LocalPlatformContext.current
    val networkRegained = LocalNetworkRegained.current
    LaunchedEffect(url) {
        url?.let { preloadArtwork(context, it, PLAYER_COVER_PX, networkRegained) }
    }
}
