package com.whatsticker.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.graphics.drawable.AnimatedImageDrawable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.whatsticker.app.util.inspectAnimatedWebp
import com.whatsticker.app.MainActivity
import com.whatsticker.app.R
import com.whatsticker.app.data.local.WhatStickerDatabase
import com.whatsticker.app.data.model.CapturedSticker
import com.whatsticker.app.util.AnimatedWebpEncoder
import com.whatsticker.app.util.AureusAppsAnimatedWebpWriter
import com.whatsticker.app.util.ImageUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingCaptureService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_AUTHORIZATION_RESULT = "ACTION_AUTHORIZATION_RESULT"
        const val ACTION_PREPARE_PROJECTION = "ACTION_PREPARE_PROJECTION"
        const val ACTION_CANCEL_PROJECTION = "ACTION_CANCEL_PROJECTION"
        const val ACTION_CLOSE_REQUEST_ACTIVITY = "com.whatsticker.app.ACTION_CLOSE_REQUEST_ACTIVITY"
        const val EXTRA_RESULT_RECEIVER = "EXTRA_RESULT_RECEIVER"
        const val RESULT_PREPARE_OK = 1
        const val RESULT_PREPARE_ERROR = 0
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val CHANNEL_ID = "whatsticker_floating_channel"
        const val NOTIFICATION_ID = 1001
        private const val GIF_FPS = 13
        private const val GIF_FRAME_DURATION_MS = 1000L / GIF_FPS
        private const val MAX_GIF_DURATION_MS = 10_000L
        private const val MAX_GIF_FRAMES = 130
        private const val DRAG_THRESHOLD_PX = 10
        private const val DISCARD_ZONE_SIZE_DP = 72
        private const val DISCARD_ZONE_MARGIN_DP = 16

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private lateinit var windowManager: WindowManager
    private var floatingButtonView: View? = null
    private var cropOverlayView: View? = null
    private var discardZoneView: FloatingDiscardZoneView? = null
    private var discardZoneBounds: IntRect? = null
    private var typeSelectionDialog: AlertDialog? = null
    private var pendingSession: CaptureSession? = null
    private var pendingCaptureType: CaptureType? = null
    private var isSessionUiActive = false
    private var isAuthorizationPending = false
    private var isCaptureInProgress = false
    @Volatile private var isRecording = false
    /** Set when the projection is revoked mid-recording: the incomplete GIF must not be saved. */
    @Volatile private var discardGifOnFinish = false
    /** True from the end of recording until cleanupAfterGif completes (encode, save, message). */
    @Volatile private var isFinalizingGif = false
    private var isStopping = false
    private var recordingJob: Job? = null
    private val recordingFrames = mutableListOf<ByteArray>()
    private var recordingStartMs = 0L

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCaptureMode()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundServiceWithNotification()
                createFloatingButton()
                _isRunning.value = true
            }
            ACTION_PREPARE_PROJECTION -> {
                handlePrepareProjection(intent)
            }
            ACTION_CANCEL_PROJECTION -> {
                resetSessionState()
            }
            ACTION_AUTHORIZATION_RESULT -> {
                handleAuthorizationResult(intent)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun startForegroundWithType(foregroundType: Int) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatSticker Capturador Activo")
            .setContentText("Pulsa la burbuja flotante para recortar stickers en pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun extractReceiver(intent: Intent): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }

    /** Only checks that the service can attend the request; the foreground type is not changed. */
    private fun handlePrepareProjection(intent: Intent) {
        val receiver = extractReceiver(intent)
        if (pendingCaptureType != null && isAuthorizationPending) {
            receiver?.send(RESULT_PREPARE_OK, null)
        } else {
            android.util.Log.e("FloatingCaptureService", "Prepare requested without a pending capture")
            resetSessionState()
            receiver?.send(RESULT_PREPARE_ERROR, null)
        }
    }

    /** True while the service holds the MEDIA_PROJECTION foreground type. */
    private var isProjectionForeground = false

    /** Idempotent: only closes the transparent request activity if alive; never cancels a session. */
    private fun closeRequestActivity() {
        sendBroadcast(Intent(ACTION_CLOSE_REQUEST_ACTIVITY).setPackage(packageName))
    }

    /** Reverts to SPECIAL_USE once; must be called after releasing the projection. */
    private fun revertToSpecialUse() {
        // Every session end path (reset, cancel, static/GIF finish, projection stop) goes through here.
        closeRequestActivity()
        if (!isProjectionForeground) return
        isProjectionForeground = false
        try {
            startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            android.util.Log.e("FloatingCaptureService", "Cannot revert foreground type", e)
        }
    }

    /** Releases the projection, reverts to SPECIAL_USE, clears the session state and shows the button. */
    private fun resetSessionState() {
        removeDiscardZone()
        releaseProjectionResources()
        revertToSpecialUse()
        pendingCaptureType = null
        pendingSession = null
        isAuthorizationPending = false
        isSessionUiActive = false
        isCaptureInProgress = false
        floatingButtonView?.visibility = View.VISIBLE
    }

    private fun initMediaProjection(resultCode: Int, resultData: Intent) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    val pipelineActive = isCaptureInProgress || isRecording ||
                        isFinalizingGif || recordingJob != null
                    removeDiscardZone()
                    releaseProjectionResources()
                    // Ends the GIF loop; the running job finalizes and restores the button.
                    if (isRecording) discardGifOnFinish = true
                    isRecording = false
                    pendingSession = null
                    removeCropOverlay()
                    isSessionUiActive = false
                    pendingCaptureType = null
                    isAuthorizationPending = false
                    revertToSpecialUse()
                    if (!pipelineActive) {
                        isCaptureInProgress = false
                        floatingButtonView?.visibility = View.VISIBLE
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "WhatStickerCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingButton() {
        if (floatingButtonView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val buttonSize = (56 * resources.displayMetrics.density).toInt()
            val margin = (12 * resources.displayMetrics.density).toInt()
            x = (resources.displayMetrics.widthPixels - buttonSize - margin).coerceAtLeast(0)
            y = ((resources.displayMetrics.heightPixels - buttonSize) / 2).coerceAtLeast(0)
        }

        // Creamos una vista de botón flotante estilizada con icono
        val container = FrameLayout(this)
        val containerSizePx = (56 * resources.displayMetrics.density).toInt()
        container.setPadding(8, 8, 8, 8)

        val imgSizePx = (52 * resources.displayMetrics.density).toInt()
        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.WHITE)

            // Fondo circular verde WhatsApp con elevación
            val shape = android.graphics.drawable.GradientDrawable().apply {
                this.shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF25D366.toInt()) // Verde WhatsApp
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
            background = shape
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            elevation = 12f
        }

        container.addView(imageView, FrameLayout.LayoutParams(imgSizePx, imgSizePx))
        floatingButtonView = container

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        fun buttonSizePx(): Int =
            container.width.takeIf { it > 0 } ?: (56 * resources.displayMetrics.density).toInt()

        fun moveButtonTo(position: Point) {
            val clamped = clampButtonPosition(position, buttonSizePx(), getUsableBounds())
            layoutParams.x = clamped.x
            layoutParams.y = clamped.y
            if (container.isAttachedToWindow) windowManager.updateViewLayout(container, layoutParams)
        }

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (isEffectiveDrag(deltaX, deltaY, DRAG_THRESHOLD_PX)) {
                        isMoving = true
                    }
                    if (isMoving) {
                        moveButtonTo(Point(initialX + deltaX.toInt(), initialY + deltaY.toInt()))
                        if (canShowDiscardZone(currentButtonState())) {
                            showDiscardZone()
                            val center = buttonCenter(Point(layoutParams.x, layoutParams.y), buttonSizePx())
                            val bounds = discardZoneBounds
                            discardZoneView?.setHighlighted(
                                bounds != null && isPointInDiscardZone(center, bounds)
                            )
                        } else {
                            removeDiscardZone()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // DOWN+UP without MOVE can still be a real drag: check the total displacement.
                    if (!isMoving && isEffectiveDrag(
                            event.rawX - initialTouchX,
                            event.rawY - initialTouchY,
                            DRAG_THRESHOLD_PX
                        )
                    ) {
                        isMoving = true
                        moveButtonTo(
                            Point(
                                initialX + (event.rawX - initialTouchX).toInt(),
                                initialY + (event.rawY - initialTouchY).toInt()
                            )
                        )
                    }
                    if (!isMoving) {
                        // Fue un click: Ocultar botón, capturar pantalla y mostrar selector de recorte
                        onFloatingButtonClicked()
                    } else {
                        moveButtonTo(Point(layoutParams.x, layoutParams.y))
                        val bounds = discardZoneBounds
                        val center = buttonCenter(Point(layoutParams.x, layoutParams.y), buttonSizePx())
                        val dropped = discardZoneView != null && bounds != null &&
                            canShowDiscardZone(currentButtonState()) &&
                            isPointInDiscardZone(center, bounds)
                        removeDiscardZone()
                        if (dropped) onButtonDroppedInDiscardZone()
                    }
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeDiscardZone()
                    moveButtonTo(Point(layoutParams.x, layoutParams.y))
                    isMoving = false
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, layoutParams)
    }

    /** Maps the service flags to the single availability state used by the discard zone. */
    private fun currentButtonState(): FloatingButtonState = when {
        isAuthorizationPending -> FloatingButtonState.AwaitingPermission
        isSessionUiActive || cropOverlayView != null -> FloatingButtonState.SelectingArea
        isRecording -> FloatingButtonState.GifRecording
        isFinalizingGif -> FloatingButtonState.GifFinishing
        isCaptureInProgress ->
            if (recordingJob != null) FloatingButtonState.GifFinishing else FloatingButtonState.StaticCapture
        else -> FloatingButtonState.Available
    }

    /** Visible area usable by the button, in screen coordinates, read at gesture time. */
    private fun getUsableBounds(): IntRect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.systemGestures()
            )
            val b = metrics.bounds
            return IntRect(
                b.left + insets.left,
                b.top + insets.top,
                b.right - insets.right,
                b.bottom - insets.bottom
            )
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        @Suppress("DEPRECATION")
        floatingButtonView?.rootWindowInsets?.let {
            left = it.systemWindowInsetLeft
            top = it.systemWindowInsetTop
            right = it.systemWindowInsetRight
            bottom = it.systemWindowInsetBottom
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val g = it.systemGestureInsets
                left = maxOf(left, g.left)
                top = maxOf(top, g.top)
                right = maxOf(right, g.right)
                bottom = maxOf(bottom, g.bottom)
            }
        }
        return IntRect(left, top, metrics.widthPixels - right, metrics.heightPixels - bottom)
    }

    /** Adds the non-touchable discard zone, or repositions it if it already exists. */
    private fun showDiscardZone() {
        val density = resources.displayMetrics.density
        val usable = getUsableBounds()
        val size = (DISCARD_ZONE_SIZE_DP * density).toInt().coerceAtLeast((48 * density).toInt())
        val margin = (DISCARD_ZONE_MARGIN_DP * density).toInt()
        val left = usable.left + ((usable.right - usable.left - size) / 2).coerceAtLeast(0)
        val top = (usable.bottom - margin - size).coerceAtLeast(usable.top)
        val existing = discardZoneView
        if (existing != null) {
            try {
                val newBounds = IntRect(left, top, left + size, top + size)
                if (discardZoneBounds == newBounds) return
                val current = existing.layoutParams as? WindowManager.LayoutParams
                if (current != null) {
                    current.x = left
                    current.y = top
                    current.width = size
                    current.height = size
                    windowManager.updateViewLayout(existing, current)
                }
                discardZoneBounds = newBounds
            } catch (e: Exception) {
                android.util.Log.e("FloatingCaptureService", "Cannot update discard zone", e)
            }
            return
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }
        val view = FloatingDiscardZoneView(this)
        try {
            windowManager.addView(view, params)
            discardZoneView = view
            discardZoneBounds = IntRect(left, top, left + size, top + size)
        } catch (e: Exception) {
            android.util.Log.e("FloatingCaptureService", "Cannot show discard zone", e)
        }
    }

    /** Idempotent: safe to call when the zone does not exist. */
    private fun removeDiscardZone() {
        val view = discardZoneView
        discardZoneView = null
        discardZoneBounds = null
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Already detached.
            }
        }
    }

    /** Dropping the button in the discard zone runs the same full stop as ACTION_STOP. */
    private fun onButtonDroppedInDiscardZone() {
        stopCaptureMode()
    }

    /**
     * Single, idempotent stop path (ACTION_STOP and discard zone). The discard zone is removed
     * first; the remaining cleanup (button, overlay, session, projection, notification) runs in
     * onDestroy, which stopSelf() triggers.
     */
    private fun stopCaptureMode() {
        if (isStopping) return
        isStopping = true
        removeDiscardZone()
        closeRequestActivity()
        stopSelf()
    }

    private fun onFloatingButtonClicked() {
        if (isRecording) {
            stopGifRecording()
            return
        }
        if (isAuthorizationPending || isCaptureInProgress) return
        if (isSessionUiActive) return
        isSessionUiActive = true
        removeDiscardZone()
        floatingButtonView?.visibility = View.GONE
        showCaptureTypeSelectionDialog()
    }

    private fun showCaptureTypeSelectionDialog() {
        if (typeSelectionDialog?.isShowing == true) return

        val dialog = AlertDialog.Builder(this)
            .setTitle("Iniciar captura")
            .setMessage("Selecciona el tipo de captura")
            .setCancelable(true)
            .setNegativeButton("Cancelar") { _, _
                -> cancelPendingSession("Captura cancelada") }
            .setNeutralButton("Imagen estática") { _, _ ->
                requestScreenCaptureAuthorization(CaptureType.STATIC)
            }
            .setPositiveButton("GIF en movimiento") { _, _ ->
                requestScreenCaptureAuthorization(CaptureType.GIF)
            }
            .create()

        dialog.setOnCancelListener {
            cancelPendingSession("Captura cancelada")
        }

        val dialogWindowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        dialog.window?.setType(dialogWindowType)
        dialog.show()
        typeSelectionDialog = dialog
    }

    private fun captureScreenBitmap(onResult: (Bitmap?) -> Unit) {
        serviceScope.launch {
            var image: Image? = null
            try {
                image = imageReader?.acquireLatestImage()
                if (image == null) {
                    // Si aún no está listo el frame, esperamos un momento
                    for (i in 0 until 5) {
                        kotlinx.coroutines.delay(80)
                        image = imageReader?.acquireLatestImage()
                        if (image != null) break
                    }
                }

                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Recortar el padding de fila si es necesario
                    val cleanBitmap = if (bitmap.width > screenWidth) {
                        Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    } else {
                        bitmap
                    }
                    onResult(cleanBitmap)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            } finally {
                image?.close()
            }
        }
    }

    /**
     * Muestra una capa interactiva translúcida para recortar con los dedos
     * la imagen o gif deseado de la pantalla.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showCropSelectionOverlay(type: CaptureType) {
        if (cropOverlayView != null) return
        removeDiscardZone()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val currentScreenSize = IntSize(metrics.widthPixels, metrics.heightPixels)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val cropView = CropOverlaySelectionView(this, currentScreenSize) { selectedArea ->
            removeCropOverlay()
            if (selectedArea != null) {
                pendingCaptureType = null
                val session = CaptureSession(type = type, area = selectedArea)
                pendingSession = session
                isSessionUiActive = false
                if (type == CaptureType.STATIC) {
                    startStaticCapture(session)
                } else {
                    startGifRecording(session)
                }
            } else {
                cancelAreaSelection("Selección de área cancelada")
            }
        }

        cropOverlayView = cropView
        windowManager.addView(cropView, overlayParams)
    }

    private fun removeCropOverlay() {
        cropOverlayView?.let {
            windowManager.removeView(it)
            cropOverlayView = null
        }
    }

    private fun cancelAreaSelection(message: String) {
        removeDiscardZone()
        removeCropOverlay()
        pendingSession = null
        pendingCaptureType = null
        isSessionUiActive = false
        isCaptureInProgress = false
        releaseProjectionResources()
        revertToSpecialUse()
        floatingButtonView?.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun cancelPendingSession(message: String) {
        pendingSession = null
        pendingCaptureType = null
        isAuthorizationPending = false
        removeCropOverlay()
        typeSelectionDialog?.dismiss()
        typeSelectionDialog = null
        isSessionUiActive = false
        floatingButtonView?.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestScreenCaptureAuthorization(type: CaptureType) {
        if (isAuthorizationPending) return

        removeDiscardZone()
        typeSelectionDialog?.dismiss()
        typeSelectionDialog = null
        pendingCaptureType = type
        isAuthorizationPending = true
        val requestIntent = Intent(this, CaptureRequestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(requestIntent)
    }

    private fun handleAuthorizationResult(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val receiver = extractReceiver(intent)
        val type = pendingCaptureType
        if (resultCode == Activity.RESULT_OK && resultData != null && type != null) {
            if (isCaptureInProgress) {
                Toast.makeText(this, "Ya hay una captura en curso", Toast.LENGTH_SHORT).show()
                receiver?.send(RESULT_PREPARE_OK, null)
                return
            }
            try {
                // The activity is still visible, so the app is in the foreground here.
                startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                isProjectionForeground = true
                initMediaProjection(resultCode, resultData)
                isAuthorizationPending = false
                showCropSelectionOverlay(type)
            } catch (e: Exception) {
                android.util.Log.e("FloatingCaptureService", "Cannot start MediaProjection", e)
                resetSessionState()
                Toast.makeText(this, "No se pudo iniciar la captura de pantalla", Toast.LENGTH_SHORT).show()
                receiver?.send(RESULT_PREPARE_ERROR, null)
                return
            }
            receiver?.send(RESULT_PREPARE_OK, null)
        } else {
            resetSessionState()
            Toast.makeText(this, "Permiso de captura cancelado o denegado", Toast.LENGTH_SHORT).show()
            receiver?.send(RESULT_PREPARE_OK, null)
        }
    }

    private fun startStaticCapture(session: CaptureSession) {
        removeDiscardZone()
        isCaptureInProgress = true
        floatingButtonView?.visibility = View.GONE
        serviceScope.launch {
            try {
                delay(150)
                val fullBitmap = captureScreenBitmapSync()
                if (fullBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingCaptureService,
                            "No se pudo generar la captura",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val area = session.area
                    val croppedBitmap = ImageUtils.cropBitmap(
                        fullBitmap,
                        area.left,
                        area.top,
                        area.width(),
                        area.height()
                    )
                    fullBitmap.recycle()

                    try {
                        saveStickerFromCrop(croppedBitmap)
                    } finally {
                        if (!croppedBitmap.isRecycled) {
                            croppedBitmap.recycle()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingCaptureService,
                        "No se pudo generar la captura",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                releaseProjectionResources()
                withContext(Dispatchers.Main) {
                    revertToSpecialUse()
                    pendingSession = null
                    isCaptureInProgress = false
                    floatingButtonView?.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun captureScreenBitmapSync(): Bitmap? = withContext(Dispatchers.IO) {
        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            var attempts = 0
            while (image == null && attempts < 5) {
                delay(80)
                image = imageReader?.acquireLatestImage()
                attempts++
            }

            if (image == null) return@withContext null

            val planes = image!!.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (bitmap.width > screenWidth) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }
        } catch (_: Exception) {
            null
        } finally {
            image?.close()
        }
    }

    private fun startGifRecording(session: CaptureSession) {
        if (isRecording) return
        removeDiscardZone()
        discardGifOnFinish = false
        isRecording = true
        isCaptureInProgress = true
        recordingStartMs = SystemClock.elapsedRealtime()
        recordingFrames.clear()
        floatingButtonView?.visibility = View.VISIBLE
        Toast.makeText(this, "Grabando GIF... pulsa el botón para detener", Toast.LENGTH_SHORT).show()

        recordingJob = serviceScope.launch {
            var frameIndex = 0
            try {
                // Let the overlay window close and the reader deliver a clean frame.
                delay(200)
                while (isActive && isRecording &&
                    SystemClock.elapsedRealtime() - recordingStartMs < MAX_GIF_DURATION_MS &&
                    recordingFrames.size < MAX_GIF_FRAMES
                ) {
                    val full = captureScreenBitmapSync()
                    if (full != null) {
                        val area = session.area
                        val cropped = ImageUtils.cropBitmap(full, area.left, area.top, area.width(), area.height())
                        full.recycle()
                        val bytes = ImageUtils.processStickerFrame(cropped, 60)
                        cropped.recycle()
                        if (bytes.isNotEmpty()) {
                            recordingFrames.add(bytes)
                        }
                    }
                    frameIndex++
                    val nextFrameAt = recordingStartMs + (frameIndex * 1000L) / GIF_FPS
                    val wait = nextFrameAt - SystemClock.elapsedRealtime()
                    if (wait > 0) delay(wait)
                }
            } catch (_: Exception) {
                // La limpieza se ejecuta en el finally
            } finally {
                finishGifRecording()
            }
        }
    }

    private fun stopGifRecording() {
        isRecording = false
    }

    private suspend fun finishGifRecording() {
        // Set before isRecording=false so there is never a gap between recording and finalizing.
        isFinalizingGif = true
        isRecording = false
        val frames = recordingFrames.toList()
        recordingFrames.clear()

        if (discardGifOnFinish) {
            // Projection revoked mid-recording: the GIF is incomplete (spec 001 RF-20), do not save.
            discardGifOnFinish = false
            cleanupAfterGif("No se pudo grabar el GIF")
            return
        }
        if (frames.isEmpty()) {
            cleanupAfterGif("No se pudo grabar el GIF")
            return
        }
        if (allByteFramesIdentical(frames)) {
            cleanupAfterGif("El área no contiene movimiento")
            return
        }

        var savedFile: File? = null
        try {
            val encoder = AnimatedWebpEncoder(
                writer = AureusAppsAnimatedWebpWriter(this@FloatingCaptureService)
            )
            val bytes = encoder.encodeCompressed(frames, GIF_FRAME_DURATION_MS)
            if (bytes == null) {
                cleanupAfterGif("No se pudo generar el GIF (máximo 500 KB)")
                return
            }
            savedFile = ImageUtils.saveStickerToFile(
                this@FloatingCaptureService,
                bytes,
                "animated"
            )
            val inspection = inspectAnimatedWebp(bytes)
            val decodeCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeAnimatedWebp(savedFile)
            } else {
                DecodeCheck.NOT_APPLICABLE
            }
            if (saveDisposition(inspection, decodeCheck) == SaveDisposition.DISCARD) {
                // Only the file just created is removed; a failed delete must not break cleanup.
                try {
                    savedFile.delete()
                } catch (e: Exception) {
                    // Ignored on purpose.
                }
                savedFile = null
                cleanupAfterGif("No se pudo generar un GIF con movimiento")
                return
            }
            val db = WhatStickerDatabase.getInstance(this@FloatingCaptureService)
            db.stickerDao().insertSticker(
                CapturedSticker(
                    filePath = savedFile.absolutePath,
                    originalWidth = 512,
                    originalHeight = 512,
                    isAnimated = true,
                    emojis = "✨",
                    packId = null
                )
            )
            cleanupAfterGif("¡GIF capturado y guardado en WhatSticker!")
        } catch (e: Exception) {
            savedFile?.delete()
            cleanupAfterGif("No se pudo guardar el GIF")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAnimatedWebp(file: File): DecodeCheck =
        try {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
            if (drawable is AnimatedImageDrawable) DecodeCheck.CONFIRMED_ANIMATED else DecodeCheck.FAILED
        } catch (e: Exception) {
            DecodeCheck.FAILED
        }

    private suspend fun cleanupAfterGif(message: String) {
        withContext(Dispatchers.Main) {
            try {
                removeDiscardZone()
                releaseProjectionResources()
                revertToSpecialUse()
                pendingSession = null
                isCaptureInProgress = false
                recordingJob = null
                floatingButtonView?.visibility = View.VISIBLE
                Toast.makeText(this@FloatingCaptureService, message, Toast.LENGTH_SHORT).show()
            } finally {
                // Last assignment: the state only returns to Available once cleanup is complete.
                isFinalizingGif = false
            }
        }
    }
    private fun releaseProjectionResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private suspend fun saveStickerFromCrop(croppedBitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            var savedFile: java.io.File? = null
            try {
                val processedWebpBytes = ImageUtils.processStickerBitmap(croppedBitmap)
                savedFile = ImageUtils.saveStickerToFile(
                    this@FloatingCaptureService,
                    processedWebpBytes,
                    "captured"
                )

                val db = WhatStickerDatabase.getInstance(this@FloatingCaptureService)
                val sticker = CapturedSticker(
                    filePath = savedFile.absolutePath,
                    originalWidth = croppedBitmap.width,
                    originalHeight = croppedBitmap.height,
                    isAnimated = false,
                    emojis = "✨",
                    packId = null
                )
                db.stickerDao().insertSticker(sticker)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingCaptureService,
                        "¡Sticker capturado y guardado en WhatSticker!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                savedFile?.let {
                    if (it.exists()) it.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingCaptureService,
                        "No se pudo generar la captura",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRequestActivity()
        _isRunning.value = false
        removeDiscardZone()
        floatingButtonView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // Already detached.
            }
            floatingButtonView = null
        }
        try {
            removeCropOverlay()
        } catch (_: Exception) {
            cropOverlayView = null
        }
        pendingSession = null
        isAuthorizationPending = false

        recordingJob?.cancel()
        recordingJob = null
        isRecording = false
        discardGifOnFinish = false
        isFinalizingGif = false
        isCaptureInProgress = false
        pendingCaptureType = null
        isSessionUiActive = false
        recordingFrames.clear()
        typeSelectionDialog?.dismiss()
        typeSelectionDialog = null

        releaseProjectionResources()
    }
}

