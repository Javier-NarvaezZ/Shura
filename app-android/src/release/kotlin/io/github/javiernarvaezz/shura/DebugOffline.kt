package io.github.javiernarvaezz.shura

import android.content.Intent

/** Debug builds only: the offline trigger does not exist in release. */
@Suppress("UnusedParameter")
internal fun debugOffline(
    intent: Intent,
    graph: AppGraph,
) = Unit
