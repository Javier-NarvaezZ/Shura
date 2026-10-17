package io.github.javiernarvaezz.shura.core.auth

import java.io.File
import java.time.LocalDate

/**
 * How long a session lasts, measured in real use: one line per day the app checked the session with Google,
 * "<date> alive" or "<date> expired" (and another line if it changed that day). Nothing else: no cookies, no
 * account, no responses. Blocking: call off the main thread.
 */
class SessionHealthLog(
    private val file: File,
    private val today: () -> LocalDate = LocalDate::now,
) {
    fun record(alive: Boolean) {
        val line = "${today()} ${if (alive) "alive" else "expired"}"
        if (lines().lastOrNull() != line) file.appendText("$line\n")
    }

    fun lines(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
}
