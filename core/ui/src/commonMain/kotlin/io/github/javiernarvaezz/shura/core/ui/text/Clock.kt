package io.github.javiernarvaezz.shura.core.ui.text

import kotlin.time.Duration

/** A track time as `m:ss`, or `h:mm:ss` from an hour on; never negative. */
fun Duration.toClock(): String =
    coerceAtLeast(Duration.ZERO).toComponents { hours, minutes, seconds, _ ->
        val ss = seconds.toString().padStart(2, '0')
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }
