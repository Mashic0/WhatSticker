package com.whatsticker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.whatsticker.app.data.local.WhatStickerDatabase
import com.whatsticker.app.service.FloatingCaptureService

class WhatStickerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Inicializar Room DB
        WhatStickerDatabase.getInstance(this)
        // Crear canal de notificación para el servicio flotante
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FloatingCaptureService.CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
