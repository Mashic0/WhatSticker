package com.whatsticker.app.service

import android.graphics.Rect

enum class CaptureType {
    STATIC,
    GIF
}

data class CaptureSession(
    val type: CaptureType,
    val area: Rect
)
