package com.bolapeler.assistive.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.bolapeler.assistive.data.PrefsManager

class BolaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val prefs = PrefsManager(this)
        if (prefs.isServiceEnabled && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingAssistiveService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Sengaja dikosongkan: aplikasi tidak membaca atau memantau isi layar pengguna.
    }

    override fun onInterrupt() {
        // Tidak ada proses latar belakang yang perlu diinterupsi.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun triggerGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }

    companion object {
        @Volatile
        var instance: BolaAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun lockScreenSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        fun screenshotSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
