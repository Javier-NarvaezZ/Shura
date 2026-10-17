package io.github.javiernarvaezz.shura

import android.content.Context
import android.content.Intent

/** Debug builds only: the account fixture dump does not exist in release. */
@Suppress("UnusedParameter")
internal fun debugDumpAccountFixtures(
    intent: Intent,
    context: Context,
    graph: AppGraph,
) = Unit
