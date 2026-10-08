package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraDarkColors
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion

/**
 * A cover's colors for tinted chrome (the mini-player and the player), animated when the cover changes. Read the
 * values only in draw lambdas: the fade then redraws without recomposing.
 */
@Stable
class ArtworkTint internal constructor(
    private val deepState: State<Color>,
    private val topState: State<Color>,
    private val glowState: State<Color>,
) {
    /** Dark enough for white text: the bottom of the cover. */
    val deep: Color get() = deepState.value

    /** Dark enough for white text: the top of the cover. */
    val top: Color get() = topState.value

    /** The cover's main color, for a soft glow; transparent for greyscale covers. */
    val glow: Color get() = glowState.value
}

@Composable
fun rememberArtworkTint(url: String?): ArtworkTint {
    val colors = rememberArtworkColors(url)
    val spec = ShuraMotion.color<Color>()
    val deep = animateColorAsState(colors?.deep ?: FALLBACK, spec)
    val top = animateColorAsState(colors?.deepTop ?: FALLBACK, spec)
    val glow = animateColorAsState(colors?.accent ?: Color.Transparent, spec)
    return remember(deep, top, glow) { ArtworkTint(deep, top, glow) }
}

// Until a cover's colors are known (or without a cover): the neutral dark surface.
private val FALLBACK = ShuraDarkColors.surface2
