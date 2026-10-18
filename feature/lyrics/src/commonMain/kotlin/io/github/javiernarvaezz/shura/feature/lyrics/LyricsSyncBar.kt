package io.github.javiernarvaezz.shura.feature.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.lyrics.resources.Res
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_earlier
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_earlier_description
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_later
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_later_description
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_reset_description
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_sync_value
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Duration

/**
 * The timing fix for synced lyrics: "Atrasar" when the lines come before they are sung, "Adelantar" when after,
 * half a second per tap; the value between them goes back to none.
 */
@Composable
internal fun LyricsSyncBar(
    model: LyricsModel,
    modifier: Modifier = Modifier,
) {
    val offset by model.timing.offset.collectAsStateWithLifecycle()
    val reset = stringResource(Res.string.lyrics_sync_reset_description)
    Row(
        modifier.clip(Shura.shapes.pill).background(Shura.colors.text.copy(alpha = VEIL_ALPHA)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SyncButton(Res.string.lyrics_sync_later, Res.string.lyrics_sync_later_description) {
            model.timing.shift(-LyricsOffsets.STEP)
        }
        TextButton(
            onClick = model.timing::reset,
            enabled = offset != Duration.ZERO,
            modifier = Modifier.semantics { contentDescription = reset },
        ) {
            Text(
                stringResource(Res.string.lyrics_sync_value, formatOffset(offset)),
                style = MaterialTheme.typography.labelLarge,
                color = if (offset == Duration.ZERO) Shura.colors.textMuted else Shura.colors.text,
            )
        }
        SyncButton(Res.string.lyrics_sync_earlier, Res.string.lyrics_sync_earlier_description) {
            model.timing.shift(LyricsOffsets.STEP)
        }
    }
}

@Composable
private fun SyncButton(
    label: StringResource,
    description: StringResource,
    onClick: () -> Unit,
) {
    val spoken = stringResource(description)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = spoken }) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = Shura.colors.text,
            modifier = Modifier.padding(horizontal = Shura.spacing.xs),
        )
    }
}

/** "+1,5", "−0,5" or "0": tenths of a second, with a decimal comma. */
internal fun formatOffset(offset: Duration): String {
    val tenths = offset.inWholeMilliseconds / MILLIS_PER_TENTH
    if (tenths == 0L) return "0"
    val sign = if (tenths > 0) "+" else "−"
    return "$sign${abs(tenths) / TENTHS_PER_SECOND},${abs(tenths) % TENTHS_PER_SECOND}"
}

private const val VEIL_ALPHA = 0.16f
private const val MILLIS_PER_TENTH = 100
private const val TENTHS_PER_SECOND = 10
