package com.whatsticker.app.ui.screens.captures

import androidx.compose.runtime.Composable
import com.whatsticker.app.ui.components.AnimatedStickerPreview

/** Thin adapter keeping the "Captura animada" semantics of the Captures screen. */
@Composable
fun AnimatedCapturePreview(
    filePath: String,
    onStopReady: (stop: (() -> Unit)?) -> Unit = {}
) {
    AnimatedStickerPreview(filePath, "Captura animada", onStopReady)
}
