package com.whatsticker.app.util

object AnimatedWebpSizePolicy {
    data class Picked(val quality: Int, val sizeBytes: Int)

    fun pickFirstFittingQuality(
        qualities: List<Int>,
        maxSizeBytes: Int,
        sizeAtQuality: (Int) -> Int?
    ): Picked? {
        for (quality in qualities) {
            val size = sizeAtQuality(quality) ?: continue
            if (size <= maxSizeBytes) {
                return Picked(quality = quality, sizeBytes = size)
            }
        }
        return null
    }
}
