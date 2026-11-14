package com.whatsticker.app.ui.screens.captures

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whatsticker.app.ui.components.AnimatedStickerThumbnail

/** Thin adapter keeping the "Captura animada" semantics of the Captures screen. */
@Composable
fun AnimatedCaptureThumbnail(filePath: String, modifier: Modifier = Modifier) {
    AnimatedStickerThumbnail(filePath, "Captura animada", modifier)
}
