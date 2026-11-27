package com.bolapeler.assistive.service

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bolapeler.assistive.R
import com.bolapeler.assistive.data.PrefsManager
import com.bolapeler.assistive.model.PanelAction
import com.bolapeler.assistive.ui.MainActivity
import com.bolapeler.assistive.ui.PanelActionAdapter
import com.bolapeler.assistive.util.DeviceActionHelper
import kotlin.math.abs

class FloatingAssistiveService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ballContainer: View? = null
    private var ballView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null

    private var panelView: View? = null
    private var panelAdapter: PanelActionAdapter? = null
    private var currentMode = PanelMode.MAIN

    private var screenWidth = 1080
    private var screenHeight = 1920

    private enum class PanelMode { MAIN, DEVICE, FAVORITES }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_CONFIG) {
                applyBallAppearance()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenMetrics()
        startForegroundNotification()

        if (Settings.canDrawOverlays(this)) {
            createFloatingBall()
        } else {
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this,
            configReceiver,
            IntentFilter(ACTION_REFRESH_CONFIG),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            prefs.isServiceEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(configReceiver)
        } catch (_: Exception) {
        }
        removePanel()
        removeBall()
    }

    private fun updateScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun startForegroundNotification() {
        val channelId = "bola_peler_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingAssistiveService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bola_peler_launcher)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        val inflater = LayoutInflater.from(this)
        val container = inflater.inflate(R.layout.overlay_floating_ball, null)
        val orb = container.findViewById<View>(R.id.floatingBallView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.ballX
            y = prefs.ballY
        }

        ballContainer = container
        ballView = orb
        ballParams = params

        applyBallAppearance()

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    showPanel(PanelMode.MAIN)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    executeGestureAction(prefs.doubleTapAction)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    executeGestureAction(prefs.longPressAction)
                }
            }
        )

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        orb.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = (initialY + dy).coerceIn(0, screenHeight - orb.height)
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapBallToEdge(params, container, orb.width)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
    }

    private fun applyBallAppearance() {
        val orb = ballView ?: return
        val sizePx = (prefs.ballSizeDp * resources.displayMetrics.density).toInt()
        orb.layoutParams = orb.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        orb.alpha = prefs.ballAlphaPercent / 100f
        ballContainer?.let { container ->
            ballParams?.let { params ->
                try {
                    windowManager.updateViewLayout(container, params)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun snapBallToEdge(
        params: WindowManager.LayoutParams,
        container: View,
        ballWidth: Int
    ) {
        updateScreenMetrics()
        val centerX = params.x + ballWidth / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - ballWidth
        val startX = params.x

        ValueAnimator.ofInt(startX, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                try {
                    windowManager.updateViewLayout(container, params)
                } catch (_: Exception) {
                }
            }
            start()
        }
        prefs.ballX = targetX
        prefs.ballY = params.y
    }

    private fun showPanel(mode: PanelMode) {
        currentMode = mode
        ballContainer?.visibility = View.GONE

        if (panelView == null) {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_floating_panel, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            view.findViewById<View>(R.id.panelRoot).setOnClickListener {
                removePanel()
            }
            view.findViewById<View>(R.id.panelCard).setOnClickListener {
                // Mencegah klik di dalam kartu menutup panel
            }
            view.findViewById<View>(R.id.tvPanelBack).setOnClickListener {
                showPanel(PanelMode.MAIN)
            }

            val grid = view.findViewById<GridView>(R.id.gridPanelActions)
            panelAdapter = PanelActionAdapter(this, emptyList())
            grid.adapter = panelAdapter
            grid.setOnItemClickListener { _, _, position, _ ->
                panelAdapter?.getItem(position)?.let { handlePanelAction(it) }
            }

            panelView = view
            windowManager.addView(view, params)
        }

        val root = panelView ?: return
        val title = root.findViewById<TextView>(R.id.tvPanelTitle)
        val backBtn = root.findViewById<View>(R.id.tvPanelBack)

        when (mode) {
            PanelMode.MAIN -> {
                title.text = getString(R.string.app_name)
                backBtn.visibility = View.GONE
                panelAdapter?.updateItems(buildMainActions())
            }
            PanelMode.DEVICE -> {
                title.text = getString(R.string.action_device)
                backBtn.visibility = View.VISIBLE
                panelAdapter?.updateItems(buildDeviceActions())
            }
            PanelMode.FAVORITES -> {
                title.text = getString(R.string.action_favorites)
                backBtn.visibility = View.VISIBLE
                panelAdapter?.updateItems(buildFavoriteActions())
            }
        }
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            panelView = null
            panelAdapter = null
        }
        ballContainer?.visibility = View.VISIBLE
    }

    private fun removeBall() {
        ballContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            ballContainer = null
            ballView = null
            ballParams = null
        }
    }

    private fun buildMainActions(): List<PanelAction> = listOf(
        PanelAction("home", getString(R.string.action_home), R.drawable.ic_home),
        PanelAction("back", getString(R.string.action_back), R.drawable.ic_back),
        PanelAction("recents", getString(R.string.action_recents), R.drawable.ic_recents),
        PanelAction("notifications", getString(R.string.action_notifications), R.drawable.ic_notifications),
        PanelAction("lock", getString(R.string.action_lock), R.drawable.ic_lock),
        PanelAction("screenshot", getString(R.string.action_screenshot), R.drawable.ic_screenshot),
        PanelAction("power", getString(R.string.action_power), R.drawable.ic_power),
        PanelAction("device", getString(R.string.action_device), R.drawable.ic_device),
        PanelAction("favorites", getString(R.string.action_favorites), R.drawable.ic_star)
    )

    private fun buildDeviceActions(): List<PanelAction> = listOf(
        PanelAction("wifi", getString(R.string.dev_wifi), R.drawable.ic_wifi),
        PanelAction("bluetooth", getString(R.string.dev_bluetooth), R.drawable.ic_bluetooth),
        PanelAction("flashlight", getString(R.string.dev_flashlight), R.drawable.ic_flashlight),
        PanelAction("sound_mode", getString(R.string.dev_sound), R.drawable.ic_sound_mode),
        PanelAction("vol_up", getString(R.string.dev_vol_up), R.drawable.ic_volume_up),
        PanelAction("vol_down", getString(R.string.dev_vol_down), R.drawable.ic_volume_down),
        PanelAction("rotate", getString(R.string.dev_rotate), R.drawable.ic_rotate),
        PanelAction("settings", getString(R.string.dev_settings), R.drawable.ic_device),
        PanelAction("back_main", getString(R.string.dev_back_menu), R.drawable.ic_back)
    )

    private fun buildFavoriteActions(): List<PanelAction> {
        val pm = packageManager
        val list = mutableListOf<PanelAction>()
        for (pkg in prefs.favoritePackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                list.add(PanelAction("launch_app", label, appDrawable = icon, packageName = pkg))
            } catch (_: Exception) {
            }
        }
        if (list.isEmpty()) {
            list.add(PanelAction("open_main_app", "Pilih Favorit", R.drawable.ic_star))
        }
        return list
    }

    private fun handlePanelAction(action: PanelAction) {
        when (action.id) {
            "device" -> showPanel(PanelMode.DEVICE)
            "favorites" -> showPanel(PanelMode.FAVORITES)
            "back_main" -> showPanel(PanelMode.MAIN)
            "home" -> performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "power" -> performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            "lock" -> {
                if (BolaAccessibilityService.lockScreenSupported()) {
                    performAccessibilityAndClose(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    Toast.makeText(this, "Kunci layar butuh Android 9+", Toast.LENGTH_SHORT).show()
                    removePanel()
                }
            }
            "screenshot" -> {
                if (BolaAccessibilityService.screenshotSupported()) {
                    removePanel()
                    mainHandler.postDelayed({
                        performAccessibility(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }, 180)
                } else {
                    Toast.makeText(this, "Screenshot butuh Android 9+", Toast.LENGTH_SHORT).show()
                    removePanel()
                }
            }
            "wifi" -> {
                removePanel()
                DeviceActionHelper.openWifiSettings(this)
            }
            "bluetooth" -> {
                removePanel()
                DeviceActionHelper.openBluetoothSettings(this)
            }
            "flashlight" -> DeviceActionHelper.toggleFlashlight(this)
            "sound_mode" -> DeviceActionHelper.cycleSoundMode(this)
            "vol_up" -> DeviceActionHelper.adjustVolume(this, AudioManager.ADJUST_RAISE)
            "vol_down" -> DeviceActionHelper.adjustVolume(this, AudioManager.ADJUST_LOWER)
            "rotate" -> DeviceActionHelper.toggleAutoRotate(this)
            "settings" -> {
                removePanel()
                DeviceActionHelper.openSystemSettings(this)
            }
            "launch_app" -> {
                removePanel()
                action.packageName?.let { pkg ->
                    packageManager.getLaunchIntentForPackage(pkg)?.let { launchIntent ->
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
            }
            "open_main_app" -> {
                removePanel()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    private fun executeGestureAction(actionKey: String) {
        when (actionKey) {
            PrefsManager.ACTION_HOME -> performAccessibility(AccessibilityService.GLOBAL_ACTION_HOME)
            PrefsManager.ACTION_BACK -> performAccessibility(AccessibilityService.GLOBAL_ACTION_BACK)
            PrefsManager.ACTION_RECENTS -> performAccessibility(AccessibilityService.GLOBAL_ACTION_RECENTS)
            PrefsManager.ACTION_NOTIFICATIONS -> performAccessibility(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            PrefsManager.ACTION_LOCK -> {
                if (BolaAccessibilityService.lockScreenSupported()) {
                    performAccessibility(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            PrefsManager.ACTION_SCREENSHOT -> {
                if (BolaAccessibilityService.screenshotSupported()) {
                    performAccessibility(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
            }
        }
    }

    private fun performAccessibilityAndClose(globalAction: Int) {
        removePanel()
        performAccessibility(globalAction)
    }

    private fun performAccessibility(globalAction: Int) {
        val service = BolaAccessibilityService.instance
        if (service == null) {
            Toast.makeText(
                this,
                "Aktifkan Layanan Aksesibilitas Bola Peler di Pengaturan",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        service.triggerGlobalAction(globalAction)
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.bolapeler.assistive.ACTION_STOP_SERVICE"
        const val ACTION_REFRESH_CONFIG = "com.bolapeler.assistive.ACTION_REFRESH_CONFIG"
        private const val NOTIFICATION_ID = 4041
    }
}
