package io.github.javiernarvaezz.shura.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme

// Empty window until desktop playback (Phase 5); the app shell is reused then.
fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Shura") {
            ShuraTheme {
                Box(Modifier.fillMaxSize().background(Shura.colors.background))
            }
        }
    }
