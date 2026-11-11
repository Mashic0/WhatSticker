package com.whatsticker.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.math.min

class FloatingDiscardZoneView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val minSizePx = (MIN_SIZE_DP * density).toInt()

    var isHighlighted: Boolean = false
        private set

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    init {
        contentDescription = "Soltar para desactivar botón flotante"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = false
        isClickable = false
        isLongClickable = false
        setWillNotDraw(false)
        applyStateColors()
    }

    fun setHighlighted(highlighted: Boolean) {
        if (highlighted == isHighlighted) return
        isHighlighted = highlighted
        applyStateColors()
        invalidate()
        if (highlighted) announceForAccessibility("Suelta para desactivar el botón flotante")
    }

    private fun applyStateColors() {
        backgroundPaint.color = if (isHighlighted) 0xFF25D366.toInt() else Color.argb(200, 66, 66, 66)
        crossPaint.strokeWidth = (if (isHighlighted) 4 else 3) * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(max(minSizePx, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(max(minSizePx, suggestedMinimumHeight), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val scale = if (isHighlighted) 1f else NORMAL_SCALE
        val radius = min(width, height) / 2f * scale - borderPaint.strokeWidth
        canvas.drawCircle(cx, cy, radius, backgroundPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val arm = radius * 0.4f
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, crossPaint)
        canvas.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, crossPaint)
    }

    private companion object {
        const val MIN_SIZE_DP = 48
        const val NORMAL_SCALE = 0.85f
    }
}
