package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

/** A page's save or follow action: shown as [activeIcon] in the accent color while [active]. */
class DetailToggle(
    val active: Boolean,
    val label: String,
    val activeLabel: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
    val onToggle: () -> Unit,
)
