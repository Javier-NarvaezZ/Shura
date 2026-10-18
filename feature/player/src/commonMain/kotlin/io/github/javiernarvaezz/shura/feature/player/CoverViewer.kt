package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.cover_full
import org.jetbrains.compose.resources.stringResource

/**
 * The cover on its own, as wide as the screen on a dark backdrop; a tap anywhere or Back closes it. It asks for the
 * player's cover size, so it is already loaded.
 */
@Composable
internal fun CoverViewer(
    url: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Shura.colors.background.copy(alpha = BACKDROP_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url,
                sizePx = playerCoverPx(),
                contentDescription = stringResource(Res.string.cover_full),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RectangleShape,
            )
        }
    }
}

private const val BACKDROP_ALPHA = 0.96f
