package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * An action on the cover-tinted player (Retry, Search again): a light veil with light text, so it reads on any
 * cover's colors, where Material's default tonal colors do not.
 */
@Composable
fun TintActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = Shura.shapes.pill,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Shura.colors.text.copy(alpha = VEIL_ALPHA),
                contentColor = Shura.colors.text,
            ),
    ) {
        Text(text)
    }
}

private const val VEIL_ALPHA = 0.16f
