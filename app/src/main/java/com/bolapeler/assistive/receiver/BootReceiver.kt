package com.bolapeler.assistive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.bolapeler.assistive.data.PrefsManager
import com.bolapeler.assistive.service.FloatingAssistiveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PrefsManager(context)
        if (prefs.autoStartOnBoot && prefs.isServiceEnabled && Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, FloatingAssistiveService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
