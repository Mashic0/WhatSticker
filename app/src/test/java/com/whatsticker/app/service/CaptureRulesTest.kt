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
