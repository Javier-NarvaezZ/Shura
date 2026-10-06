package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
actual fun rememberHideKeyboard(): () -> Unit {
    val controller = LocalSoftwareKeyboardController.current
    return { controller?.hide() }
}
