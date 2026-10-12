package io.github.javiernarvaezz.shura.core.ui.components

/** A linked line under a page title (an album's artist), or plain text without [onClick]. */
class HeroLink(
    val text: String,
    val onClick: (() -> Unit)? = null,
)
