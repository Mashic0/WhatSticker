package com.whatsticker.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRulesTest {

    @Test
    fun normalizeSelection_clampsToScreen_andReturnsValidRect() {
        val result = normalizeSelection(
            rectangle = IntRect(left = -20, top = 30, right = 200, bottom = 90),
            screenSize = IntSize(width = 100, height = 100)
        )

        assertTrue(result is SelectionResult.Valid)
        val rect = (result as SelectionResult.Valid).rect
        assertEquals(IntRect(left = 0, top = 30, right = 100, bottom = 90), rect)
    }

    @Test
    fun normalizeSelection_rejectsTooSmallArea() {
        val result = normalizeSelection(
            rectangle = IntRect(left = 10, top = 10, right = 16, bottom = 16),
            screenSize = IntSize(width = 100, height = 100)
        )

        assertEquals(SelectionResult.Invalid, result)
    }

    @Test
    fun normalizeSelection_rejectsCompletelyOutOfBoundsArea() {
        val result = normalizeSelection(
            rectangle = IntRect(left = -100, top = -100, right = -10, bottom = -10),
            screenSize = IntSize(width = 100, height = 100)
        )

        assertEquals(SelectionResult.Invalid, result)
    }

    private val screen = IntSize(1000, 2000)

    private fun capture(
        visible: IntRect,
        origin: IntPoint = IntPoint(0, 0),
        screenSize: IntSize = screen,
        imageSize: IntSize = screen,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) = toCaptureRect(visible, origin, screenSize, imageSize, scaleX, scaleY)

    @Test
    fun toCaptureRect_table_validCases() {
        data class Case(val name: String, val actual: SelectionResult, val expected: IntRect)

        val cases = listOf(
            Case("identity", capture(IntRect(100, 200, 300, 500)), IntRect(100, 200, 300, 500)),
            Case(
                "positive offset",
                capture(IntRect(100, 200, 300, 500), origin = IntPoint(24, 80)),
                IntRect(124, 280, 324, 580)
            ),
            Case(
                "negative offset",
                capture(IntRect(100, 200, 300, 500), origin = IntPoint(-50, -100)),
                IntRect(50, 100, 250, 400)
            ),
            Case(
                "image half of screen",
                capture(IntRect(100, 200, 300, 500), imageSize = IntSize(500, 1000)),
                IntRect(50, 100, 150, 250)
            ),
            Case(
                "image double of screen with offset",
                capture(IntRect(10, 20, 110, 220), origin = IntPoint(5, 10), imageSize = IntSize(2000, 4000)),
                IntRect(30, 60, 230, 460)
            ),
            Case(
                "surface scale 2",
                capture(IntRect(10, 20, 60, 70), origin = IntPoint(100, 200), scaleX = 2f, scaleY = 2f),
                IntRect(120, 240, 220, 340)
            ),
            Case(
                "tie 0.5 rounds up",
                capture(IntRect(5, 5, 25, 25), imageSize = IntSize(500, 1000)),
                IntRect(3, 3, 13, 13)
            ),
            Case(
                "below tie rounds down",
                capture(IntRect(4, 4, 24, 24), imageSize = IntSize(500, 1000)),
                IntRect(2, 2, 12, 12)
            ),
            Case(
                "above tie rounds up",
                capture(IntRect(0, 0, 21, 21), imageSize = IntSize(500, 1000)),
                IntRect(0, 0, 11, 11)
            ),
            Case(
                "full image",
                capture(IntRect(0, 0, 1000, 2000)),
                IntRect(0, 0, 1000, 2000)
            ),
            Case(
                "top limit partially above",
                capture(IntRect(100, 0, 300, 100), origin = IntPoint(0, -30)),
                IntRect(100, 0, 300, 70)
            ),
            Case(
                "bottom limit partially below",
                capture(IntRect(100, 1900, 300, 2000), origin = IntPoint(0, 50)),
                IntRect(100, 1950, 300, 2000)
            ),
            Case(
                "left limit partially outside",
                capture(IntRect(0, 100, 100, 300), origin = IntPoint(-40, 0)),
                IntRect(0, 100, 60, 300)
            ),
            Case(
                "right limit partially outside",
                capture(IntRect(900, 100, 1000, 300), origin = IntPoint(30, 0)),
                IntRect(930, 100, 1000, 300)
            ),
            Case(
                "partial intersection keeps exact intersection",
                capture(IntRect(-20, -20, 40, 40)),
                IntRect(0, 0, 40, 40)
            ),
            Case(
                "exactly 8x8 is valid",
                capture(IntRect(10, 10, 18, 18)),
                IntRect(10, 10, 18, 18)
            )
        )

        cases.forEach { case ->
            assertEquals(case.name, SelectionResult.Valid(case.expected), case.actual)
        }
    }

    @Test
    fun toCaptureRect_table_invalidCases() {
        val cases = listOf(
            "7x7 area" to capture(IntRect(10, 10, 17, 17)),
            "7 px wide only" to capture(IntRect(10, 10, 17, 100)),
            "7 px high only" to capture(IntRect(10, 10, 100, 17)),
            "intersection under 8 px" to capture(IntRect(0, 0, 100, 100), origin = IntPoint(0, -95)),
            "completely outside" to capture(IntRect(0, 0, 100, 100), origin = IntPoint(2000, 0))
        )

        cases.forEach { (name, result) -> assertEquals(name, SelectionResult.Invalid, result) }
    }

    @Test
    fun toCaptureRect_table_invalidGeometryCases() {
        val cases = listOf(
            "inverted rect" to capture(IntRect(100, 100, 10, 10)),
            "inverted horizontally" to capture(IntRect(100, 10, 10, 100)),
            "inverted vertically" to capture(IntRect(10, 100, 100, 10)),
            "zero screen width" to capture(IntRect(0, 0, 100, 100), screenSize = IntSize(0, 2000)),
            "negative screen height" to capture(IntRect(0, 0, 100, 100), screenSize = IntSize(1000, -1)),
            "zero image width" to capture(IntRect(0, 0, 100, 100), imageSize = IntSize(0, 2000)),
            "zero image height" to capture(IntRect(0, 0, 100, 100), imageSize = IntSize(1000, 0)),
            "zero scale" to capture(IntRect(0, 0, 100, 100), scaleX = 0f),
            "negative scale" to capture(IntRect(0, 0, 100, 100), scaleY = -1f),
            "NaN scale" to capture(IntRect(0, 0, 100, 100), scaleX = Float.NaN),
            "infinite scale" to capture(IntRect(0, 0, 100, 100), scaleY = Float.POSITIVE_INFINITY)
        )

        cases.forEach { (name, result) -> assertEquals(name, SelectionResult.InvalidGeometry, result) }
    }

    @Test
    fun isValidSurfaceGeometry_table() {
        val screen = IntSize(1080, 2400)
        data class Case(val name: String, val expected: Boolean, val actual: Boolean)
        val cases = listOf(
            Case("exact", true, isValidSurfaceGeometry(IntPoint(0, 0), screen, screen)),
            Case(
                "cutout offset",
                true,
                isValidSurfaceGeometry(IntPoint(0, 126), IntSize(1080, 2274), screen)
            ),
            Case("smaller than screen", true, isValidSurfaceGeometry(IntPoint(10, 20), IntSize(500, 500), screen)),
            Case("negative origin", false, isValidSurfaceGeometry(IntPoint(-1, 0), screen, screen)),
            Case("overflows bottom", false, isValidSurfaceGeometry(IntPoint(0, 127), IntSize(1080, 2274), screen)),
            Case("overflows right", false, isValidSurfaceGeometry(IntPoint(1, 0), screen, screen)),
            Case("zero width", false, isValidSurfaceGeometry(IntPoint(0, 0), IntSize(0, 100), screen)),
            Case("zero height", false, isValidSurfaceGeometry(IntPoint(0, 0), IntSize(100, 0), screen)),
            Case("larger than screen", false, isValidSurfaceGeometry(IntPoint(0, 0), IntSize(1081, 2400), screen)),
            Case("invalid screen", false, isValidSurfaceGeometry(IntPoint(0, 0), IntSize(10, 10), IntSize(0, 0)))
        )
        cases.forEach { assertEquals(it.name, it.expected, it.actual) }
    }

    @Test
    fun frameSchedule_returns0Frames_whenNoDuration() {
        val schedule = frameSchedule(startMs = 1_000L, endMs = 1_000L)

        assertEquals(0, schedule.timestampsMs.size)
        assertEquals(0L, schedule.effectiveDurationMs)
        assertEquals(0, schedule.maxFrames)
    }

    @Test
    fun frameSchedule_returns130Frames_forTenSecondsAt13Fps() {
        val schedule = frameSchedule(startMs = 0L, endMs = 10_000L)

        assertEquals(130, schedule.timestampsMs.size)
        assertEquals(10_000L, schedule.effectiveDurationMs)
        assertEquals(130, schedule.maxFrames)
    }

    @Test
    fun frameSchedule_capsAt130Frames_whenDurationExceedsTenSeconds() {
        val schedule = frameSchedule(startMs = 0L, endMs = 11_000L)

        assertEquals(130, schedule.timestampsMs.size)
        assertEquals(10_000L, schedule.effectiveDurationMs)
        assertTrue(schedule.timestampsMs.last() < 10_000L)
    }

    @Test
    fun allFramesIdentical_trueOnlyWhenAllSignaturesMatch() {
        assertTrue(allFramesIdentical(listOf(5L, 5L, 5L)))
        assertFalse(allFramesIdentical(listOf(5L, 5L, 6L)))
        assertFalse(allFramesIdentical(emptyList()))
    }

    @Test
    fun shouldStopRecording_returnsManualStopOnTap_andNotOnDrag() {
        val tapStop = shouldStopRecording(elapsedMs = 200L, buttonGesture = ButtonGesture.Tap)
        val dragStop = shouldStopRecording(elapsedMs = 200L, buttonGesture = ButtonGesture.Drag)

        assertEquals(StopReason.ManualTap, tapStop)
        assertNull(dragStop)
    }

    @Test
    fun shouldStopRecording_stopsAtTenSeconds() {
        val stopAtLimit = shouldStopRecording(elapsedMs = 10_000L, buttonGesture = null)
        assertEquals(StopReason.MaxDurationReached, stopAtLimit)
    }

    @Test
    fun nextCaptureState_rejectsInvalidTransition() {
        val invalid = nextCaptureState(CaptureState.Idle, CaptureEvent.PermissionGranted)
        assertNull(invalid)
    }

    @Test
    fun nextCaptureState_supportsHappyPathToFinishing() {
        val s1 = nextCaptureState(CaptureState.Idle, CaptureEvent.Start)
        val s2 = nextCaptureState(s1!!, CaptureEvent.TypeChosen)
        val s3 = nextCaptureState(s2!!, CaptureEvent.AreaConfirmed)
        val s4 = nextCaptureState(s3!!, CaptureEvent.PermissionGranted)
        val s5 = nextCaptureState(s4!!, CaptureEvent.RecordingStarted)
        val s6 = nextCaptureState(s5!!, CaptureEvent.RecordingStopped)
        val s7 = nextCaptureState(s6!!, CaptureEvent.Reset)

        assertEquals(CaptureState.ChoosingType, s1)
        assertEquals(CaptureState.SelectingArea, s2)
        assertEquals(CaptureState.AwaitingPermission, s3)
        assertEquals(CaptureState.Capturing, s4)
        assertEquals(CaptureState.Recording, s5)
        assertEquals(CaptureState.Finishing, s6)
        assertEquals(CaptureState.Idle, s7)
    }
}
