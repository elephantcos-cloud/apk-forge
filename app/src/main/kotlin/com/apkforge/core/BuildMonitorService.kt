package com.apkforge.core

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apkforge.ApkForgeApp

/**
 * Foreground service to monitor build in background
 */
class BuildMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, ApkForgeApp.CHANNEL_BUILD)
            .setContentTitle("APK Forge")
            .setContentText("Monitoring build…")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
        return START_STICKY
    }

    fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(this, ApkForgeApp.CHANNEL_BUILD)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()
        nm.notify(1001, n)
    }

    fun stopMonitoring() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
