package com.whatsticker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimatedWebpSizePolicyTest {

    @Test
    fun pickFirstFittingQuality_returnsFirstThatFits() {
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = listOf(90, 80, 70),
            maxSizeBytes = 500 * 1024
        ) { quality ->
            when (quality) {
                90 -> 600 * 1024
                80 -> 500 * 1024
                else -> 300 * 1024
            }
        }

        assertEquals(80, picked?.quality)
        assertEquals(500 * 1024, picked?.sizeBytes)
    }

    @Test
    fun pickFirstFittingQuality_skipsQualitiesWithoutBytes() {
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = listOf(90, 80, 70),
            maxSizeBytes = 500 * 1024
        ) { quality ->
            when (quality) {
                90 -> null
                80 -> 700 * 1024
                else -> 450 * 1024
            }
        }

        assertEquals(70, picked?.quality)
        assertEquals(450 * 1024, picked?.sizeBytes)
    }

    @Test
    fun pickFirstFittingQuality_returnsNullWhenNoneFits() {
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = listOf(90, 80, 70),
            maxSizeBytes = 500 * 1024
        ) { 800 * 1024 }

        assertNull(picked)
    }

    @Test
    fun pickFirstFittingQuality_acceptsExactLimit() {
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = listOf(90),
            maxSizeBytes = 500 * 1024
        ) { 500 * 1024 }

        assertEquals(90, picked?.quality)
        assertEquals(500 * 1024, picked?.sizeBytes)
    }
}
