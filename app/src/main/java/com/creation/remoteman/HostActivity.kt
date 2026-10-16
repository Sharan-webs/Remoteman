package com.creation.remoteman

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.creation.remoteman.databinding.ActivityHostBinding
import com.creation.remoteman.service.RemoteAccessibilityService
import com.creation.remoteman.service.ScreenCaptureService
import com.creation.remoteman.signaling.SignalingRepository
import com.creation.remoteman.webrtc.TurnCredentials
import com.creation.remoteman.webrtc.WebRTCClient
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class HostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHostBinding
    private val signaling = SignalingRepository()
    private var client: WebRTCClient? = null
    private var code: String = ""
    private val scope = CoroutineScope(Dispatchers.Main)

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // The foreground service was already started (and given time to settle)
            // before this permission request was launched, so it's safe to start
            // capturing immediately here.
            client?.attachScreenCapture(result.data!!, 720, 1480)
            client?.createOffer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartHosting.setOnClickListener { startHosting() }
        binding.btnStopServer.setOnClickListener { stopHosting() }
    }

    private fun startHosting() {
        crossfade(binding.warningSection, binding.hostingSection)
        binding.loaderIcon.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_loader))

        code = signaling.generateCode()
        signaling.createSession(code) { ok ->
            scope.launch {
                delay(600) // brief "hosting..." beat before the tick-in
                binding.loaderIcon.clearAnimation()
                binding.loaderIcon.visibility = View.GONE
                binding.loaderLabel.visibility = View.GONE
                binding.codeText.text = code
                crossfade(null, binding.codeSection, hideFirst = false)
                if (ok) beginSignalingAndCapture()
            }
        }
    }

    private fun beginSignalingAndCapture() {
        client = WebRTCClient(
            context = this,
            iceServers = TurnCredentials.fetch(),
            onLocalSdp = { sdp -> signaling.sendOffer(code, sdp.description) },
            onIceCandidate = { ice -> signaling.sendCandidate(code, "host", ice.sdp) },
            onRemoteVideoTrack = { /* host doesn't render video itself */ },
            onControlMessage = { json ->
                RemoteAccessibilityService.instance?.handleControlMessage(json)
            }
        )
        client?.init()

        signaling.listenForAnswer(code) { sdp ->
            client?.setRemoteAnswer(sdp)
        }
        signaling.listenForCandidates(code, "connector") { sdp ->
            client?.addRemoteIceCandidate(IceCandidate("", 0, sdp))
        }
        signaling.listenForHelperJoined(code) {
            runOnUiThread {
                binding.statusDot.setBackgroundResource(R.drawable.bg_pill_green)
                binding.statusText.text = "Helper connected"
                binding.statusText.setTextColor(getColor(R.color.status_green))
            }
        }

        startForegroundService(Intent(this, ScreenCaptureService::class.java))

        scope.launch {
            delay(400) // give the foreground service time to fully start first
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun stopHosting() {
        signaling.endSession(code)
        client?.close()
        finish()
    }

    private fun crossfade(out: View?, into: View, hideFirst: Boolean = true) {
        if (hideFirst) {
            out?.animate()?.alpha(0f)?.setDuration(220)?.withEndAction { out.visibility = View.GONE }?.start()
        }
        into.visibility = View.VISIBLE
        into.alpha = 0f
        into.animate().alpha(1f).setDuration(260).start()
    }

    override fun onDestroy() {
        client?.close()
        super.onDestroy()
    }
}
