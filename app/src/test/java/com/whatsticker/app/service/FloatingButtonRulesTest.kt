package com.whatsticker.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingButtonRulesTest {

    @Test
    fun floatDragExactlyThresholdIsNotDrag() {
        assertFalse(isEffectiveDrag(10f, 10f, 10))
    }

    @Test
    fun floatDragSubPixelAboveThresholdIsDrag() {
        assertTrue(isEffectiveDrag(10.5f, 0f, 10))
    }

    @Test
    fun floatDragElevenOnXIsDrag() {
        assertTrue(isEffectiveDrag(11f, 0f, 10))
    }

    @Test
    fun floatDragElevenOnYIsDrag() {
        assertTrue(isEffectiveDrag(0f, 11f, 10))
    }

    @Test
    fun floatDragZeroIsNotDrag() {
        assertFalse(isEffectiveDrag(0f, 0f, 10))
    }

    @Test
    fun floatDragNegativeValues() {
        assertTrue(isEffectiveDrag(-10.5f, 0f, 10))
        assertFalse(isEffectiveDrag(-10f, -10f, 10))
    }

    @Test
    fun floatDragCombinedAxes() {
        assertTrue(isEffectiveDrag(3f, -10.1f, 10))
        assertFalse(isEffectiveDrag(9.9f, -9.9f, 10))
    }

    // Drag threshold
    @Test
    fun dragExactlyAtThresholdIsNotEffective() {
        assertFalse(isEffectiveDrag(10, 10, 10))
        assertFalse(isEffectiveDrag(-10, 0, 10))
    }

    @Test
    fun dragOverThresholdOnXIsEffective() {
        assertTrue(isEffectiveDrag(11, 0, 10))
    }

    @Test
    fun dragOverThresholdOnYIsEffective() {
        assertTrue(isEffectiveDrag(0, 11, 10))
    }

    @Test
    fun zeroDragIsNotEffective() {
        assertFalse(isEffectiveDrag(0, 0, 10))
    }

    @Test
    fun negativeDragOverThresholdIsEffective() {
        assertTrue(isEffectiveDrag(-11, 0, 10))
        assertTrue(isEffectiveDrag(0, -11, 10))
    }

    @Test
    fun negativeDragWithinThresholdIsNotEffective() {
        assertFalse(isEffectiveDrag(-9, -9, 10))
    }

    // Center
    @Test
    fun centerWithEvenSize() {
        assertEquals(Point(150, 270), buttonCenter(Point(100, 220), 100))
    }

    @Test
    fun centerWithOddSizeRoundsDown() {
        assertEquals(Point(135, 225), buttonCenter(Point(100, 190), 71))
    }

    // Discard zone
    private val zone = IntRect(100, 200, 300, 400)

    @Test
    fun pointInsideZone() {
        assertTrue(isPointInDiscardZone(Point(200, 300), zone))
    }

    @Test
    fun pointOutsideZoneOnEachSide() {
        assertFalse(isPointInDiscardZone(Point(99, 300), zone))
        assertFalse(isPointInDiscardZone(Point(300, 300), zone))
        assertFalse(isPointInDiscardZone(Point(200, 199), zone))
        assertFalse(isPointInDiscardZone(Point(200, 400), zone))
    }

    @Test
    fun pointOnZoneEdges() {
        assertTrue(isPointInDiscardZone(Point(100, 300), zone))
        assertTrue(isPointInDiscardZone(Point(200, 200), zone))
        assertTrue(isPointInDiscardZone(Point(299, 300), zone))
        assertTrue(isPointInDiscardZone(Point(200, 399), zone))
        assertFalse(isPointInDiscardZone(Point(300, 400), zone))
    }

    // Clamping
    private val bounds = IntRect(0, 50, 1000, 2000)
    private val size = 100

    @Test
    fun positionInsideBoundsIsUnchanged() {
        assertEquals(Point(400, 500), clampButtonPosition(Point(400, 500), size, bounds))
    }

    @Test
    fun positionBeyondEachSideIsClamped() {
        assertEquals(Point(0, 500), clampButtonPosition(Point(-30, 500), size, bounds))
        assertEquals(Point(900, 500), clampButtonPosition(Point(950, 500), size, bounds))
        assertEquals(Point(400, 50), clampButtonPosition(Point(400, 10), size, bounds))
        assertEquals(Point(400, 1900), clampButtonPosition(Point(400, 1950), size, bounds))
    }

    @Test
    fun positionFullyOutsideCornerIsClampedToCorner() {
        assertEquals(Point(0, 50), clampButtonPosition(Point(-500, -500), size, bounds))
        assertEquals(Point(900, 1900), clampButtonPosition(Point(5000, 5000), size, bounds))
    }

    @Test
    fun buttonExactlyAsLargeAsBounds() {
        val exact = IntRect(20, 30, 120, 130)
        assertEquals(Point(20, 30), clampButtonPosition(Point(500, 500), size, exact))
        assertEquals(Point(20, 30), clampButtonPosition(Point(-500, -500), size, exact))
    }

    @Test
    fun boundsSmallerThanButtonPinTopLeft() {
        val tiny = IntRect(20, 30, 70, 60)
        assertEquals(Point(20, 30), clampButtonPosition(Point(500, 500), size, tiny))
        assertEquals(Point(20, 30), clampButtonPosition(Point(0, 0), size, tiny))
    }

    // Availability
    @Test
    fun discardZoneOnlyAvailableWhenButtonIsAvailable() {
        assertTrue(canShowDiscardZone(FloatingButtonState.Available))
    }

    @Test
    fun discardZoneBlockedInEveryOtherState() {
        FloatingButtonState.values()
            .filter { it != FloatingButtonState.Available }
            .forEach { assertFalse(it.name, canShowDiscardZone(it)) }
        assertFalse(canShowDiscardZone(FloatingButtonState.GifRecording))
        assertFalse(canShowDiscardZone(FloatingButtonState.GifFinishing))
    }
}
