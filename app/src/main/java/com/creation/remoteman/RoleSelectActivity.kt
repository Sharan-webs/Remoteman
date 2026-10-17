package com.creation.remoteman

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.creation.remoteman.databinding.ActivityRoleSelectBinding
import com.creation.remoteman.util.AppUtils

class RoleSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ambientRing.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_ambient))

        binding.btnHost.setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        binding.btnConnect.setOnClickListener {
            startActivity(Intent(this, ConnectActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnCornerStop.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_RemoteMan)
                .setTitle("Stop RemoteMan?")
                .setMessage("Ends any active session right now and closes the app completely.")
                .setPositiveButton("Stop") { _, _ -> AppUtils.forceStop(applicationContext) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
