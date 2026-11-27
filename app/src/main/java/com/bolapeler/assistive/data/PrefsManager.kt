package com.bolapeler.assistive.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bola_peler_prefs", Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var ballSizeDp: Int
        get() = prefs.getInt(KEY_BALL_SIZE_DP, 56)
        set(value) = prefs.edit().putInt(KEY_BALL_SIZE_DP, value.coerceIn(40, 80)).apply()

    var ballAlphaPercent: Int
        get() = prefs.getInt(KEY_BALL_ALPHA, 90)
        set(value) = prefs.edit().putInt(KEY_BALL_ALPHA, value.coerceIn(30, 100)).apply()

    var ballX: Int
        get() = prefs.getInt(KEY_BALL_X, 0)
        set(value) = prefs.edit().putInt(KEY_BALL_X, value).apply()

    var ballY: Int
        get() = prefs.getInt(KEY_BALL_Y, 300)
        set(value) = prefs.edit().putInt(KEY_BALL_Y, value).apply()

    var doubleTapAction: String
        get() = prefs.getString(KEY_DOUBLE_TAP, ACTION_SCREENSHOT) ?: ACTION_SCREENSHOT
        set(value) = prefs.edit().putString(KEY_DOUBLE_TAP, value).apply()

    var longPressAction: String
        get() = prefs.getString(KEY_LONG_PRESS, ACTION_HOME) ?: ACTION_HOME
        set(value) = prefs.edit().putString(KEY_LONG_PRESS, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var favoritePackages: List<String>
        get() {
            val raw = prefs.getString(KEY_FAVORITES, "") ?: ""
            return if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString(KEY_FAVORITES, value.take(8).joinToString(",")).apply()
        }

    companion object {
        const val ACTION_NONE = "none"
        const val ACTION_HOME = "home"
        const val ACTION_BACK = "back"
        const val ACTION_RECENTS = "recents"
        const val ACTION_LOCK = "lock"
        const val ACTION_SCREENSHOT = "screenshot"
        const val ACTION_NOTIFICATIONS = "notifications"

        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_BALL_SIZE_DP = "ball_size_dp"
        private const val KEY_BALL_ALPHA = "ball_alpha"
        private const val KEY_BALL_X = "ball_x"
        private const val KEY_BALL_Y = "ball_y"
        private const val KEY_DOUBLE_TAP = "double_tap_action"
        private const val KEY_LONG_PRESS = "long_press_action"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_FAVORITES = "favorite_packages"
    }
}
