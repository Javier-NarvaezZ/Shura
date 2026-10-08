package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where the player is between the mini-player (0) and full screen (1). One value drives opening, dragging and
 * closing: a drag writes it directly and only the release animates, so the player can be caught mid-flight.
 * Read [fraction] in draw or layer lambdas, not in composition, so each frame does not recompose.
 */
@Stable
class PlayerSheetState(
    initiallyOpen: Boolean,
) {
    var fraction by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
        private set

    /** The mini-player's cover, in root coordinates: where the flying cover starts. */
    var miniCover by mutableStateOf(Rect.Zero)

    /** The player's cover slot, in root coordinates: where the flying cover lands. */
    var playerCover by mutableStateOf(Rect.Zero)

    /** The queue replaces the cover while the player is open; it is reset whenever the player closes. */
    var showsQueue by mutableStateOf(false)

    /** Drag distance, in pixels, for a full open or close. */
    var travelPx by mutableFloatStateOf(1f)

    /** True while any part of the player is on screen (it is composed only then). */
    val isShown: Boolean by derivedStateOf { fraction > 0f }

    val isOpen: Boolean by derivedStateOf { fraction >= 1f }

    private var animation: Job? = null

    fun open(scope: CoroutineScope) = animateTo(1f, scope, ShuraMotion.open())

    fun close(scope: CoroutineScope) = animateTo(0f, scope, ShuraMotion.close())

    /** Follows the finger; [deltaPx] is positive upwards. */
    fun dragBy(deltaPx: Float) {
        animation?.cancel()
        moveTo((fraction + deltaPx / travelPx).coerceIn(0f, 1f))
    }

    private fun moveTo(value: Float) {
        fraction = value
        if (value == 0f) showsQueue = false
    }

    /** Settles after a drag: a fling, or past halfway, commits; otherwise it springs back. */
    fun settle(
        velocityPxPerS: Float,
        scope: CoroutineScope,
    ) {
        val target =
            when {
                velocityPxPerS > FLING_PX_PER_S -> 1f
                velocityPxPerS < -FLING_PX_PER_S -> 0f
                else -> if (fraction >= 0.5f) 1f else 0f
            }
        val committed = (target == 1f) == (fraction >= 0.5f)
        animateTo(target, scope, if (committed) ShuraMotion.quick() else ShuraMotion.settle())
    }

    private fun animateTo(
        target: Float,
        scope: CoroutineScope,
        spec: AnimationSpec<Float>,
    ) {
        animation?.cancel()
        animation = scope.launch { animate(fraction, target, animationSpec = spec) { value, _ -> moveTo(value) } }
    }

    companion object {
        private const val FLING_PX_PER_S = 1_200f

        /** Keeps whether the player was open across rotation. */
        val Saver: Saver<PlayerSheetState, Boolean> =
            Saver(save = { it.fraction >= 0.5f }, restore = { PlayerSheetState(initiallyOpen = it) })
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState =
    rememberSaveable(saver = PlayerSheetState.Saver) { PlayerSheetState(initiallyOpen = false) }
