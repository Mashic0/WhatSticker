package com.whatsticker.app.service

private const val MIN_SELECTION_SIZE_PX = 8

data class IntSize(val width: Int, val height: Int)

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

sealed class SelectionResult {
    data class Valid(val rect: IntRect) : SelectionResult()
    /** Final selection is smaller than the 8x8 px minimum. */
    data object Invalid : SelectionResult()
    /** Input geometry (screen, image, scales or rectangle) is unusable. */
    data object InvalidGeometry : SelectionResult()
}

fun normalizeSelection(rectangle: IntRect, screenSize: IntSize): SelectionResult {
    if (screenSize.width <= 0 || screenSize.height <= 0) return SelectionResult.Invalid

    val clampedLeft = rectangle.left.coerceIn(0, screenSize.width)
    val clampedTop = rectangle.top.coerceIn(0, screenSize.height)
    val clampedRight = rectangle.right.coerceIn(0, screenSize.width)
    val clampedBottom = rectangle.bottom.coerceIn(0, screenSize.height)

    val normalizedLeft = minOf(clampedLeft, clampedRight)
    val normalizedTop = minOf(clampedTop, clampedBottom)
    val normalizedRight = maxOf(clampedLeft, clampedRight)
    val normalizedBottom = maxOf(clampedTop, clampedBottom)

    val normalized = IntRect(
        left = normalizedLeft,
        top = normalizedTop,
        right = normalizedRight,
        bottom = normalizedBottom
    )

    if (normalized.width < MIN_SELECTION_SIZE_PX || normalized.height < MIN_SELECTION_SIZE_PX) {
        return SelectionResult.Invalid
    }

    return SelectionResult.Valid(normalized)
}

data class IntPoint(val x: Int, val y: Int)

/**
 * Converts a rectangle in surface-local coordinates into captured-image coordinates.
 *
 * Each edge: physical = surfaceOriginOnScreen + local * surfaceScale, then
 * image = physical * imageSize / screenSize (per axis). Edges are rounded to the nearest
 * integer, with exact .5 ties rounded away from zero (see [roundHalfAwayFromZero]).
 *
 * The output is in captured-image coordinates. Partial intersection with
 * [0, imageWidth] x [0, imageHeight] is intentional: the available part is kept and the
 * result is Invalid only when it ends up smaller than 8x8 px.
 */
fun toCaptureRect(
    visible: IntRect,
    surfaceOriginOnScreen: IntPoint,
    screenSize: IntSize,
    imageSize: IntSize,
    surfaceScaleX: Float = 1f,
    surfaceScaleY: Float = 1f
): SelectionResult {
    if (screenSize.width <= 0 || screenSize.height <= 0) return SelectionResult.InvalidGeometry
    if (imageSize.width <= 0 || imageSize.height <= 0) return SelectionResult.InvalidGeometry
    if (!surfaceScaleX.isFinite() || surfaceScaleX <= 0f) return SelectionResult.InvalidGeometry
    if (!surfaceScaleY.isFinite() || surfaceScaleY <= 0f) return SelectionResult.InvalidGeometry
    if (visible.right < visible.left || visible.bottom < visible.top) {
        return SelectionResult.InvalidGeometry
    }

    val ratioX = imageSize.width.toDouble() / screenSize.width
    val ratioY = imageSize.height.toDouble() / screenSize.height

    fun edge(origin: Int, local: Int, surfaceScale: Float, ratio: Double, max: Int): Int {
        val physical = origin + local * surfaceScale.toDouble()
        return roundHalfAwayFromZero(physical * ratio).coerceIn(0L, max.toLong()).toInt()
    }

    val rect = IntRect(
        left = edge(surfaceOriginOnScreen.x, visible.left, surfaceScaleX, ratioX, imageSize.width),
        top = edge(surfaceOriginOnScreen.y, visible.top, surfaceScaleY, ratioY, imageSize.height),
        right = edge(surfaceOriginOnScreen.x, visible.right, surfaceScaleX, ratioX, imageSize.width),
        bottom = edge(surfaceOriginOnScreen.y, visible.bottom, surfaceScaleY, ratioY, imageSize.height)
    )

    if (rect.width < MIN_SELECTION_SIZE_PX || rect.height < MIN_SELECTION_SIZE_PX) {
        return SelectionResult.Invalid
    }
    return SelectionResult.Valid(rect)
}

/**
 * Validates the surface contract: the overlay view uses MATCH_PARENT with no layout scaling,
 * so its local coordinates are already physical pixels and the surface scale is 1 (the scale
 * parameters of [toCaptureRect] remain for the pure rule and its tests). The surface may be
 * offset (e.g. by a display cutout) but must lie fully inside the screen.
 */
fun isValidSurfaceGeometry(
    surfaceOriginOnScreen: IntPoint,
    surfaceSize: IntSize,
    screenSize: IntSize
): Boolean {
    if (screenSize.width <= 0 || screenSize.height <= 0) return false
    if (surfaceSize.width <= 0 || surfaceSize.height <= 0) return false
    if (surfaceSize.width > screenSize.width || surfaceSize.height > screenSize.height) return false
    return surfaceOriginOnScreen.x in 0..(screenSize.width - surfaceSize.width) &&
        surfaceOriginOnScreen.y in 0..(screenSize.height - surfaceSize.height)
}

// Nearest integer; exact .5 ties move away from zero (kotlin.math.round would tie to even).
private fun roundHalfAwayFromZero(value: Double): Long {
    val magnitude = kotlin.math.floor(kotlin.math.abs(value) + 0.5).toLong()
    return if (value < 0) -magnitude else magnitude
}

data class FrameSchedule(
    val timestampsMs: List<Long>,
    val effectiveDurationMs: Long,
    val maxFrames: Int
)

fun frameSchedule(
    startMs: Long,
    endMs: Long,
    fps: Int = 13,
    maxDurationMs: Long = 10_000L
): FrameSchedule {
    if (fps <= 0 || maxDurationMs <= 0L || endMs <= startMs) {
        return FrameSchedule(emptyList(), 0L, 0)
    }

    val framePeriodMs = 1_000.0 / fps.toDouble()
    val maxFrames = ((maxDurationMs * fps) / 1_000L).toInt()
    val effectiveDurationMs = (endMs - startMs).coerceAtMost(maxDurationMs)
    val theoreticalFrames = kotlin.math.floor(effectiveDurationMs / framePeriodMs).toInt()
    val totalFrames = theoreticalFrames.coerceAtMost(maxFrames)

    val timestamps = (0 until totalFrames)
        .map { index -> startMs + kotlin.math.floor(index * framePeriodMs).toLong() }

    return FrameSchedule(
        timestampsMs = timestamps,
        effectiveDurationMs = effectiveDurationMs,
        maxFrames = maxFrames
    )
}

fun allFramesIdentical(frameSignatures: List<Long>): Boolean {
    if (frameSignatures.isEmpty()) return false
    val first = frameSignatures.first()
    return frameSignatures.all { it == first }
}

fun allByteFramesIdentical(frames: List<ByteArray>): Boolean {
    if (frames.isEmpty()) return false
    val first = frames.first()
    return frames.all { it.contentEquals(first) }
}

enum class ButtonGesture {
    Tap,
    Drag
}

enum class StopReason {
    ManualTap,
    MaxDurationReached
}

fun shouldStopRecording(
    elapsedMs: Long,
    buttonGesture: ButtonGesture?,
    maxDurationMs: Long = 10_000L
): StopReason? {
    if (elapsedMs >= maxDurationMs) return StopReason.MaxDurationReached
    if (buttonGesture == ButtonGesture.Tap) return StopReason.ManualTap
    return null
}

enum class CaptureState {
    Idle,
    ChoosingType,
    SelectingArea,
    AwaitingPermission,
    Capturing,
    Recording,
    Finishing
}

enum class CaptureEvent {
    Start,
    TypeChosen,
    AreaConfirmed,
    AreaCanceled,
    PermissionGranted,
    PermissionDenied,
    CaptureDone,
    RecordingStarted,
    RecordingStopped,
    Fail,
    Reset
}

fun nextCaptureState(state: CaptureState, event: CaptureEvent): CaptureState? = when (state) {
    CaptureState.Idle -> when (event) {
        CaptureEvent.Start -> CaptureState.ChoosingType
        CaptureEvent.Reset -> CaptureState.Idle
        else -> null
    }

    CaptureState.ChoosingType -> when (event) {
        CaptureEvent.TypeChosen -> CaptureState.SelectingArea
        CaptureEvent.Fail,
        CaptureEvent.AreaCanceled,
        CaptureEvent.PermissionDenied,
        CaptureEvent.Reset -> CaptureState.Idle
        else -> null
    }

    CaptureState.SelectingArea -> when (event) {
        CaptureEvent.AreaConfirmed -> CaptureState.AwaitingPermission
        CaptureEvent.AreaCanceled,
        CaptureEvent.Fail,
        CaptureEvent.Reset -> CaptureState.Idle
        else -> null
    }

    CaptureState.AwaitingPermission -> when (event) {
        CaptureEvent.PermissionGranted -> CaptureState.Capturing
        CaptureEvent.PermissionDenied,
        CaptureEvent.Fail,
        CaptureEvent.Reset -> CaptureState.Idle
        else -> null
    }

    CaptureState.Capturing -> when (event) {
        CaptureEvent.RecordingStarted -> CaptureState.Recording
        CaptureEvent.CaptureDone,
        CaptureEvent.Fail,
        CaptureEvent.Reset -> CaptureState.Finishing
        else -> null
    }

    CaptureState.Recording -> when (event) {
        CaptureEvent.RecordingStopped,
        CaptureEvent.Fail,
        CaptureEvent.Reset -> CaptureState.Finishing
        else -> null
    }

    CaptureState.Finishing -> when (event) {
        CaptureEvent.Reset,
        CaptureEvent.CaptureDone,
        CaptureEvent.Fail -> CaptureState.Idle
        else -> null
    }
}
