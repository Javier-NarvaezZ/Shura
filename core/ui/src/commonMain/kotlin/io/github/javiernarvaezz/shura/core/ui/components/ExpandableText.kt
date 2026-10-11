package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/** A description that shows [collapsedLines] lines and opens fully when tapped (and closes again). */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Shura.colors.textMuted,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clickable { expanded = !expanded }
                .animateContentSize()
                .padding(horizontal = Shura.spacing.gutter),
    )
}
