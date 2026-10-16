package com.creation.remoteman

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.creation.remoteman.databinding.ActivityPermissionBinding
import com.creation.remoteman.databinding.ItemPermissionCardBinding

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        setupCard(binding.cardCapture, R.string.perm_capture_title, R.string.perm_capture_desc) {
            // Screen capture consent is requested fresh each session (Android requirement);
            // nothing to toggle here ahead of time.
        }

        binding.btnEnter.setOnClickListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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

        // Capture consent happens per-session; shown as ready once the others are.
        setStatus(binding.cardCapture.status, true)
        binding.cardCapture.status.text = "Requested per session"

        binding.btnEnter.isEnabled = overlayOk && accessibilityOk && notificationsOk
        binding.btnEnter.alpha = if (binding.btnEnter.isEnabled) 1f else 0.5f
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
}
