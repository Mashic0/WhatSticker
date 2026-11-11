package com.whatsticker.app.service

import kotlin.math.abs

data class Point(val x: Int, val y: Int)

enum class FloatingButtonState {
    Available,
    AwaitingPermission,
    SelectingArea,
    StaticCapture,
    GifRecording,
    GifFinishing
}

/** True only when movement exceeds [thresholdPx] (strictly) on at least one axis. */
fun isEffectiveDrag(deltaX: Int, deltaY: Int, thresholdPx: Int): Boolean =
    abs(deltaX) > thresholdPx || abs(deltaY) > thresholdPx

/** Same strict semantics as the Int overload, without truncating sub-pixel movement. */
fun isEffectiveDrag(deltaX: Float, deltaY: Float, thresholdPx: Int): Boolean =
    abs(deltaX) > thresholdPx || abs(deltaY) > thresholdPx

/**
 * Returns the nearest top-left position that keeps the whole button inside [usableBounds].
 * If the area is smaller than the button on an axis, the button is pinned to the
 * left/top edge of the bounds so its top-left corner stays visible.
 */
fun clampButtonPosition(position: Point, buttonSize: Int, usableBounds: IntRect): Point {
    val maxX = maxOf(usableBounds.left, usableBounds.right - buttonSize)
    val maxY = maxOf(usableBounds.top, usableBounds.bottom - buttonSize)
    return Point(
        x = position.x.coerceIn(usableBounds.left, maxX),
        y = position.y.coerceIn(usableBounds.top, maxY)
    )
}

/** Geometric center of the button; uses integer division, so odd sizes round down. */
fun buttonCenter(position: Point, buttonSize: Int): Point =
    Point(position.x + buttonSize / 2, position.y + buttonSize / 2)

/** Left and top edges are inside the zone; right and bottom edges are outside. */
fun isPointInDiscardZone(point: Point, discardBounds: IntRect): Boolean =
    point.x >= discardBounds.left && point.x < discardBounds.right &&
        point.y >= discardBounds.top && point.y < discardBounds.bottom

fun canShowDiscardZone(state: FloatingButtonState): Boolean =
    state == FloatingButtonState.Available
