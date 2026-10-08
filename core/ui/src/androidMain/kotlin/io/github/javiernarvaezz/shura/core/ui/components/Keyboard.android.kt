package io.github.javiernarvaezz.shura.core.ui.components

import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHideKeyboard(): () -> Unit {
    val view = LocalView.current
    val controller = LocalSoftwareKeyboardController.current
    return remember(view, controller) {
        {
            controller?.hide()
            // Compose hides through the window insets API, which some Android 11–12 keyboards ignore (seen with
            // Samsung's keyboard on Android 12): ask the input method directly as well.
            view.context
                .getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
