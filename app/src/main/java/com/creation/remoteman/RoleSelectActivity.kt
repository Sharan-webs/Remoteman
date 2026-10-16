package com.creation.remoteman

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.creation.remoteman.databinding.ActivityRoleSelectBinding

class RoleSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHost.setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        binding.btnConnect.setOnClickListener {
            startActivity(Intent(this, ConnectActivity::class.java))
        }
    }
}
