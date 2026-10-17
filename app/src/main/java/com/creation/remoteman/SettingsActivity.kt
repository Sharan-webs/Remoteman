package com.creation.remoteman

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.creation.remoteman.databinding.ActivitySettingsBinding
import com.creation.remoteman.util.AppUtils

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.rowRestart.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_RemoteMan)
                .setTitle("Restart RemoteMan?")
                .setMessage("This clears the app's cache and restarts it fresh.")
                .setPositiveButton("Restart") { _, _ -> AppUtils.restartApp(applicationContext) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rowForceStop.setOnClickListener { confirmForceStop() }

        binding.rowAdmin.setOnClickListener { openAdmin() }
    }

    private fun confirmForceStop() {
        AlertDialog.Builder(this, R.style.Theme_RemoteMan)
            .setTitle("Force stop RemoteMan?")
            .setMessage("This ends any active session right now and closes the app completely.")
            .setPositiveButton("Force stop") { _, _ -> AppUtils.forceStop(applicationContext) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAdmin() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://instagram.com/_u/sharan.in__")
                ).setPackage("com.instagram.android")
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/sharan.in__"))
            )
        }
    }
}
