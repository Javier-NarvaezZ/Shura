package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap

// Desktop bitmaps are always readable.
internal actual fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder = this

internal actual fun Image.toComposeBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
