package io.github.javiernarvaezz.shura.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraTheme
import io.github.javiernarvaezz.shura.feature.home.HomeScreen

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Shura") {
            ShuraTheme {
                HomeScreen()
            }
        }
    }
