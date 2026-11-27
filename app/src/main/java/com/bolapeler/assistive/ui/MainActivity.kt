package com.bolapeler.assistive.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bolapeler.assistive.R
import com.bolapeler.assistive.data.PrefsManager
import com.bolapeler.assistive.databinding.ActivityMainBinding
import com.bolapeler.assistive.service.BolaAccessibilityService
import com.bolapeler.assistive.service.FloatingAssistiveService
import com.google.android.material.checkbox.MaterialCheckBox

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private var suppressMasterSwitch = false

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionBadges() }

    private val gestureOptions = listOf(
        PrefsManager.ACTION_NONE to "Tidak Ada",
        PrefsManager.ACTION_SCREENSHOT to "Screenshot",
        PrefsManager.ACTION_HOME to "Home",
        PrefsManager.ACTION_BACK to "Kembali",
        PrefsManager.ACTION_RECENTS to "Recent Apps",
        PrefsManager.ACTION_LOCK to "Kunci Layar",
        PrefsManager.ACTION_NOTIFICATIONS to "Notifikasi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        setupControls()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBadges()
        refreshMasterState()
    }

    private fun setupControls() {
        binding.rowPermOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.rowPermA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Pilih 'Bola Peler (Navigasi)' lalu aktifkan",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.rowPermNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (suppressMasterSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    suppressMasterSwitch = true
                    binding.switchMaster.isChecked = false
                    suppressMasterSwitch = false
                    Toast.makeText(
                        this,
                        "Aktifkan izin Tampil di Atas Aplikasi Lain terlebih dahulu",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.rowPermOverlay.performClick()
                    return@setOnCheckedChangeListener
                }
                prefs.isServiceEnabled = true
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, FloatingAssistiveService::class.java)
                )
            } else {
                prefs.isServiceEnabled = false
                stopService(Intent(this, FloatingAssistiveService::class.java))
            }
            refreshMasterState()
        }

        binding.sliderBallSize.value = prefs.ballSizeDp.toFloat()
        binding.tvBallSizeLabel.text = "Ukuran Bola: ${prefs.ballSizeDp} dp"
        binding.sliderBallSize.addOnChangeListener { _, value, _ ->
            val dp = value.toInt()
            prefs.ballSizeDp = dp
            binding.tvBallSizeLabel.text = "Ukuran Bola: $dp dp"
            notifyConfigChanged()
        }

        binding.sliderBallAlpha.value = prefs.ballAlphaPercent.toFloat()
        binding.tvBallAlphaLabel.text = "Opasitas Bola: ${prefs.ballAlphaPercent}%"
        binding.sliderBallAlpha.addOnChangeListener { _, value, _ ->
            val alpha = value.toInt()
            prefs.ballAlphaPercent = alpha
            binding.tvBallAlphaLabel.text = "Opasitas Bola: $alpha%"
            notifyConfigChanged()
        }

        binding.tvDoubleTapValue.text = labelForGesture(prefs.doubleTapAction)
        binding.rowDoubleTap.setOnClickListener {
            showGesturePicker("Pilih Aksi Ketuk 2x", prefs.doubleTapAction) { selected ->
                prefs.doubleTapAction = selected
                binding.tvDoubleTapValue.text = labelForGesture(selected)
            }
        }

        binding.tvLongPressValue.text = labelForGesture(prefs.longPressAction)
        binding.rowLongPress.setOnClickListener {
            showGesturePicker("Pilih Aksi Tekan Lama", prefs.longPressAction) { selected ->
                prefs.longPressAction = selected
                binding.tvLongPressValue.text = labelForGesture(selected)
            }
        }

        binding.switchAutoStart.isChecked = prefs.autoStartOnBoot
        binding.switchAutoStart.setOnCheckedChangeListener { _, checked ->
            prefs.autoStartOnBoot = checked
        }

        binding.btnManageFavorites.setOnClickListener {
            showFavoriteAppsPicker()
        }
    }

    private fun refreshPermissionBadges() {
        val overlayOk = Settings.canDrawOverlays(this)
        binding.tvPermOverlayBadge.text = if (overlayOk) "Aktif" else "Atur"
        binding.tvPermOverlayBadge.setTextColor(
            ContextCompat.getColor(this, if (overlayOk) R.color.status_ok else R.color.status_warn)
        )

        val a11yOk = BolaAccessibilityService.isConnected()
        binding.tvPermA11yBadge.text = if (a11yOk) "Aktif" else "Atur"
        binding.tvPermA11yBadge.setTextColor(
            ContextCompat.getColor(this, if (a11yOk) R.color.status_ok else R.color.status_warn)
        )

        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        binding.tvPermNotifBadge.text = if (notifOk) "Aktif" else "Atur"
        binding.tvPermNotifBadge.setTextColor(
            ContextCompat.getColor(this, if (notifOk) R.color.status_ok else R.color.status_warn)
        )
    }

    private fun refreshMasterState() {
        val active = prefs.isServiceEnabled && Settings.canDrawOverlays(this)
        suppressMasterSwitch = true
        binding.switchMaster.isChecked = active
        suppressMasterSwitch = false
        binding.tvServiceStatus.text = if (active) "Aktif di layar" else "Nonaktif"
        binding.tvServiceStatus.setTextColor(
            ContextCompat.getColor(this, if (active) R.color.status_ok else R.color.text_secondary)
        )
    }

    private fun notifyConfigChanged() {
        sendBroadcast(Intent(FloatingAssistiveService.ACTION_REFRESH_CONFIG).apply {
            setPackage(packageName)
        })
    }

    private fun labelForGesture(key: String): String {
        return gestureOptions.firstOrNull { it.first == key }?.second ?: "Tidak Ada"
    }

    private fun showGesturePicker(title: String, currentKey: String, onSelect: (String) -> Unit) {
        val labels = gestureOptions.map { it.second }.toTypedArray()
        val checkedIndex = gestureOptions.indexOfFirst { it.first == currentKey }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                onSelect(gestureOptions[which].first)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showFavoriteAppsPicker() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(mainIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val selected = prefs.favoritePackages.toMutableSet()

        val listView = ListView(this)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = resolved.size
            override fun getItem(position: Int) = resolved[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_favorite_app_picker, parent, false)
                val info = resolved[position]
                val pkg = info.activityInfo.packageName
                view.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(info.loadIcon(pm))
                view.findViewById<TextView>(R.id.tvAppName).text = info.loadLabel(pm)
                view.findViewById<MaterialCheckBox>(R.id.cbAppSelected).isChecked = selected.contains(pkg)
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = resolved[position].activityInfo.packageName
            if (selected.contains(pkg)) {
                selected.remove(pkg)
            } else if (selected.size < 8) {
                selected.add(pkg)
            } else {
                Toast.makeText(this, "Maksimal 8 aplikasi favorit", Toast.LENGTH_SHORT).show()
            }
            adapter.notifyDataSetChanged()
        }

        AlertDialog.Builder(this)
            .setTitle("Pilih Aplikasi Favorit")
            .setView(listView)
            .setPositiveButton("Simpan") { _, _ ->
                prefs.favoritePackages = selected.toList()
                Toast.makeText(this, "${selected.size} aplikasi disimpan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
