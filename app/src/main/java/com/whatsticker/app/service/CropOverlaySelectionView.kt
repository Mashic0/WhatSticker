package com.whatsticker.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class CropOverlaySelectionView(
    context: Context,
    private val screenSize: IntSize,
    private val onSelectionResult: (CropSelectionResult) -> Unit
) : FrameLayout(context) {

    private var cropRect: RectF? = null
    private var touchStartX = 0f
    private var touchStartY = 0f

    private val dimPaint = Paint().apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = 0xFF25D366.toInt() // Verde WhatsApp
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val guideTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 2f, Color.BLACK)
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val controlsLayout: LinearLayout

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Por defecto, sugerir un recuadro central cuadrado
        val defaultSize = min(screenSize.width, screenSize.height) * 0.5f
        val centerX = screenSize.width / 2f
        val centerY = screenSize.height / 2f
        cropRect = RectF(
            centerX - defaultSize / 2,
            centerY - defaultSize / 2,
            centerX + defaultSize / 2,
            centerY + defaultSize / 2
        )

        // Botones de acción inferiores
        controlsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.argb(220, 20, 20, 20))
            elevation = 16f
        }

        val btnCancel = Button(context).apply {
            text = "Cancelar"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 80, 80, 80))
            setOnClickListener { onSelectionResult(CropSelectionResult.Canceled) }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40, 1)
        }

        val btnSave = Button(context).apply {
            text = "Confirmar área"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF25D366.toInt()) // Verde WhatsApp
            setOnClickListener {
                confirmSelection()
            }
        }

        controlsLayout.addView(btnCancel)
        controlsLayout.addView(spacer)
        controlsLayout.addView(btnSave)

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM
        )
        addView(controlsLayout, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                cropRect = RectF(touchStartX, touchStartY, touchStartX, touchStartY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val left = min(touchStartX, event.x)
                val top = min(touchStartY, event.y)
                val right = max(touchStartX, event.x)
                val bottom = max(touchStartY, event.y)
                cropRect = RectF(left, top, right, bottom)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                cropRect?.let { rect ->
                    // Si el área es muy pequeña (un simple toque), hacer un recuadro de 350x350 alrededor del toque
                    if (rect.width() < 40 || rect.height() < 40) {
                        val touchRadius = 200f
                        cropRect = RectF(
                            (touchStartX - touchRadius).coerceAtLeast(0f),
                            (touchStartY - touchRadius).coerceAtLeast(0f),
                            (touchStartX + touchRadius).coerceAtMost(width.toFloat()),
                            (touchStartY + touchRadius).coerceAtMost(height.toFloat())
                        )
                    }
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar capa oscura sobre toda la pantalla
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Recortar agujero transparente donde el usuario seleccionó
        cropRect?.let { rect ->
            canvas.drawRect(rect, clearPaint)
            canvas.drawRect(rect, borderPaint)

            // Esquinas visuales
            val cornerLen = 30f
            val cornerPaint = Paint(borderPaint).apply { strokeWidth = 10f }
            // Top-left
            canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)
            // Top-right
            canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)
            // Bottom-left
            canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, cornerPaint)
            // Bottom-right
            canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, cornerPaint)
        }

        // Texto instructivo en la parte superior
        canvas.drawText(
            "Selecciona el área que deseas capturar",
            width / 2f,
            120f,
            guideTextPaint
        )
    }

    /**
     * Measures the surface geometry at confirmation time only. The minimum size is
     * evaluated later on the final capture rectangle (toCaptureRect), not here.
     */
    private fun confirmSelection() {
        val rect = cropRect
        if (rect == null || !isAttachedToWindow || width <= 0 || height <= 0) {
            onSelectionResult(CropSelectionResult.GeometryUnavailable)
            return
        }

        // Gestures already produce ordered rectangles; an inverted one is treated as invalid.
        val localRect = IntRect(
            left = rect.left.toInt(),
            top = rect.top.toInt(),
            right = rect.right.toInt(),
            bottom = rect.bottom.toInt()
        )
        if (localRect.right <= localRect.left || localRect.bottom <= localRect.top) {
            onSelectionResult(CropSelectionResult.GeometryUnavailable)
            return
        }

        val location = IntArray(2)
        getLocationOnScreen(location)

        onSelectionResult(
            CropSelectionResult.Confirmed(
                localRect = localRect,
                surfaceOriginOnScreen = IntPoint(location[0], location[1]),
                surfaceSize = IntSize(width, height)
            )
        )
    }
}

sealed class CropSelectionResult {
    data class Confirmed(
        val localRect: IntRect,
        val surfaceOriginOnScreen: IntPoint,
        val surfaceSize: IntSize
    ) : CropSelectionResult()

    data object Canceled : CropSelectionResult()
    data object GeometryUnavailable : CropSelectionResult()
}
