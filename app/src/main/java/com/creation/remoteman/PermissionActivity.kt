package com.creation.remoteman

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.creation.remoteman.databinding.ActivityPermissionBinding
import com.creation.remoteman.databinding.ItemPermissionCardBinding

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1000L

    private val poller = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, pollInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.liveDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))

        setupCard(binding.cardOverlay, R.string.perm_overlay_title, R.string.perm_overlay_desc) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        setupCard(binding.cardAccessibility, R.string.perm_accessibility_title, R.string.perm_accessibility_desc) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setupCard(binding.cardNotifications, R.string.perm_notification_title, R.string.perm_notification_desc) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
        setupCard(binding.cardBattery, R.string.perm_battery_title, R.string.perm_battery_desc) {
            requestIgnoreBatteryOptimizations()
        }
        setupCard(binding.cardCapture, R.string.perm_capture_title, R.string.perm_capture_desc) {
            // Screen capture consent is requested fresh each session (Android requirement);
            // nothing to toggle here ahead of time.
        }

        binding.btnEnter.setOnClickListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poller)
    }

    override fun onPause() {
        handler.removeCallbacks(poller)
        super.onPause()
    }

    private fun setupCard(card: ItemPermissionCardBinding, title: Int, desc: Int, onClick: () -> Unit) {
        card.title.setText(title)
        card.desc.setText(desc)
        card.root.setOnClickListener { onClick() }
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        setStatus(binding.cardOverlay.status, overlayOk)

        val accessibilityOk = isAccessibilityServiceEnabled()
        setStatus(binding.cardAccessibility.status, accessibilityOk)

        val notificationsOk = NotificationManagerCompat.from(this).areNotificationsEnabled()
        setStatus(binding.cardNotifications.status, notificationsOk)

        val batteryOk = isIgnoringBatteryOptimizations()
        setStatus(binding.cardBattery.status, batteryOk)

        // Capture consent happens per-session; shown as ready once the others are.
        setStatus(binding.cardCapture.status, true)
        binding.cardCapture.status.text = "Requested per session"

        val allReady = overlayOk && accessibilityOk && notificationsOk
        binding.btnEnter.isEnabled = allReady
        binding.btnEnter.alpha = if (allReady) 1f else 0.5f
    }

    private fun setStatus(view: TextView, enabled: Boolean) {
        view.text = if (enabled) "Enabled" else "Not enabled"
        view.setTextColor(getColor(if (enabled) R.color.status_green else R.color.status_red))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${com.creation.remoteman.service.RemoteAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}
