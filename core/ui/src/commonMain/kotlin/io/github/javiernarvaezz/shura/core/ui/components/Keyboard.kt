package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.runtime.Composable

/** Hides the software keyboard, reliably across platforms and Android versions (see the Android actual). */
@Composable
expect fun rememberHideKeyboard(): () -> Unit
