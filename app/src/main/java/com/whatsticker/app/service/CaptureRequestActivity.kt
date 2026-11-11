package com.whatsticker.app.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent activity that requests the screen capture permission. Once the user consents, the
 * system sends the app to the background, and Android only applies the MEDIA_PROJECTION service
 * type if the app has a visible activity. So after consent this activity brings itself back to the
 * front and only then hands the result to the service. It finishes when the service confirms.
 */
class CaptureRequestActivity : ComponentActivity() {

    /** True once the permission result has been stored (granted) or sent (denied). */
    private var resultStored = false

    /** True once the result delivery to the service has started. */
    private var deliveryStarted = false

    /** True once the service has confirmed the authorization result (or the prepare failed). */
    private var confirmationReceived = false

    /** True once this instance has been brought back to the front after the consent. */
    private var frontRequested = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        resultStored = true
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pendingResultCode = result.resultCode
            pendingResultData = result.data
            hasPendingResult = true
            // Bring the task back to the front; delivery happens in onResume (see onNewIntent).
            startActivity(
                Intent(this, CaptureRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } else {
            deliveryStarted = true
            sendToService(Intent(this, FloatingCaptureService::class.java).apply {
                action = FloatingCaptureService.ACTION_AUTHORIZATION_RESULT
                putExtra(FloatingCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            })
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPendingResult) {
            // Re-launched after consent: no handshake, just deliver on resume.
            resultStored = true
            frontRequested = true
            return
        }
        if (savedInstanceState != null) return

        // Handshake only: checks that the service can attend before showing the system dialog.
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (isFinishing || isDestroyed) return
                if (resultCode == FloatingCaptureService.RESULT_PREPARE_OK) {
                    val projectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                } else {
                    // The service already cleaned its own state.
                    resultStored = true
                    deliveryStarted = true
                    confirmationReceived = true
                    showStartError()
                    finish()
                }
            }
        }
        sendToService(Intent(this, FloatingCaptureService::class.java).apply {
            action = FloatingCaptureService.ACTION_PREPARE_PROJECTION
            putExtra(FloatingCaptureService.EXTRA_RESULT_RECEIVER, receiver)
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (hasPendingResult) frontRequested = true
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Ignore a stale close sent before the result was delivered.
            if (deliveryStarted && !isFinishing) finish()
        }
    }
    private var closeReceiverRegistered = false

    private fun registerCloseReceiver() {
        if (closeReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(FloatingCaptureService.ACTION_CLOSE_REQUEST_ACTIVITY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        closeReceiverRegistered = true
    }

    private fun unregisterCloseReceiver() {
        if (!closeReceiverRegistered) return
        closeReceiverRegistered = false
        unregisterReceiver(closeReceiver)
    }

    override fun onStart() {
        super.onStart()
        registerCloseReceiver()
    }

    override fun onResume() {
        super.onResume()
        if (!frontRequested || !hasPendingResult || deliveryStarted) return
        deliveryStarted = true
        // Wait for the first frame so the activity is really visible when the service is promoted.
        window.decorView.post {
            val resultCode = pendingResultCode
            val resultData = pendingResultData
            hasPendingResult = false
            pendingResultData = null
            val confirmationReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(code: Int, data: Bundle?) {
                    confirmationReceived = true
                    if (code == FloatingCaptureService.RESULT_PREPARE_ERROR) {
                        showStartError()
                        if (!isFinishing) finish()
                    }
                    // On success stay alive and transparent: the projection needs a visible activity.
                }
            }
            sendToService(Intent(this, FloatingCaptureService::class.java).apply {
                action = FloatingCaptureService.ACTION_AUTHORIZATION_RESULT
                putExtra(FloatingCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(FloatingCaptureService.EXTRA_RESULT_RECEIVER, confirmationReceiver)
                putExtra(FloatingCaptureService.EXTRA_RESULT_DATA, resultData)
            })
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterCloseReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterCloseReceiver()
        // Left without consent and without delivering anything: let the service clean up.
        if (isFinishing && !resultStored && !deliveryStarted && !hasPendingResult) {
            resultStored = true
            sendToService(Intent(this, FloatingCaptureService::class.java).apply {
                action = FloatingCaptureService.ACTION_CANCEL_PROJECTION
            })
        }
    }

    private fun showStartError() {
        Toast.makeText(this, "No se pudo iniciar la captura de pantalla", Toast.LENGTH_SHORT).show()
    }

    private fun sendToService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private companion object {
        // Same process as the service; holds the consent between the two activity instances.
        @Volatile var hasPendingResult = false
        var pendingResultCode = Activity.RESULT_CANCELED
        var pendingResultData: Intent? = null
    }
}
