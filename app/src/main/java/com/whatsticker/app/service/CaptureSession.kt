package com.whatsticker.app.service

import android.graphics.Rect

enum class CaptureType {
    STATIC,
    GIF
}

data class CaptureSession(
    val type: CaptureType,
    /** Final crop rectangle in captured-image pixel coordinates only (already converted and clamped). */
    val area: Rect
)
