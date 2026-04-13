package com.apkforge.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BuildReceiver", "Received: ${intent.action}")
    }
}
