package com.apkforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

/**
 * APK Forge — Application entry point (Kotlin)
 */
class ApkForgeApp : Application() {

    companion object {
        const val TAG = "ApkForgeApp"
        const val CHANNEL_BUILD   = "apkforge_build"
        const val CHANNEL_DOWNLOAD = "apkforge_download"
        lateinit var instance: ApkForgeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "APK Forge starting up")
        createNotificationChannels()
        AppPreferences.init(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_BUILD, "Build Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "APK build progress and completion notifications"
            })

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_DOWNLOAD, "APK Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "APK file download progress"
            })

            Log.d(TAG, "Notification channels created")
        }
    }
}
