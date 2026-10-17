package com.creation.remoteman

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.creation.remoteman.databinding.ActivityConnectBinding
import com.creation.remoteman.signaling.SignalingRepository
import com.creation.remoteman.webrtc.TurnCredentials
import com.creation.remoteman.webrtc.WebRTCClient
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

class ConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectBinding
    private val signaling = SignalingRepository()
    private var client: WebRTCClient? = null
    private var code: String = ""
    private var isFullscreen = false

    // Must match the capture resolution the host sends with (see HostActivity.attachScreenCapture).
    private val remoteWidth = 720f
    private val remoteHeight = 1480f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmitCode.setOnClickListener {
            val entered = binding.codeInput.text?.toString()?.trim().orEmpty()
            if (entered.length == 6) connect(entered)
            else Toast.makeText(this, "Enter the full 6-digit code", Toast.LENGTH_SHORT).show()
        }
        binding.btnLeaveServer.setOnClickListener { leave() }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
    }

    private fun connect(enteredCode: String) {
        code = enteredCode
        crossfade(binding.entrySection, binding.sessionSection)
        binding.connectLoader.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_loader))

        client = WebRTCClient(
            context = this,
            iceServers = TurnCredentials.fetch(),
            onLocalSdp = { sdp -> signaling.sendAnswer(code, sdp.description) },
            onIceCandidate = { ice -> signaling.sendCandidate(code, "connector", ice.sdp) },
            onRemoteVideoTrack = { track -> runOnUiThread { showVideo(track) } },
            onControlMessage = { /* connector sends control, doesn't receive it */ },
            onError = { message ->
                runOnUiThread {
                    binding.connectStatusText.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        )
        client?.init()
        binding.remoteView.init(client!!.eglBaseContext(), null)
        binding.remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

        signaling.listenForOffer(code) { offerSdp ->
            // Wait for setRemoteDescription to actually finish before creating the
            // answer — calling createAnswer() right away (without waiting) is what
            // was silently breaking the video stream before.
            client?.setRemoteOffer(offerSdp) {
                client?.createAnswer()
            }
        }
        signaling.listenForCandidates(code, "host") { sdp ->
            client?.addRemoteIceCandidate(IceCandidate("", 0, sdp))
        }
    }

    private fun showVideo(track: VideoTrack) {
        track.addSink(binding.remoteView)
        binding.connectingLabel.visibility = View.GONE
        binding.videoContainer.visibility = View.VISIBLE
        binding.videoContainer.alpha = 0f
        binding.videoContainer.animate().alpha(1f).setDuration(260).start()

        binding.videoContainer.setOnTouchListener { view, event -> handleTouch(view, event) }
    }

    private var downX = 0f
    private var downY = 0f

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val nx = (event.x / view.width) * remoteWidth
        val ny = (event.y / view.height) * remoteHeight
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = nx; downY = ny
            }
            MotionEvent.ACTION_UP -> {
                val dist = Math.hypot((nx - downX).toDouble(), (ny - downY).toDouble())
                val json = if (dist < 20) {
                    JSONObject().put("type", "tap").put("x", nx).put("y", ny)
                } else {
                    JSONObject().put("type", "swipe")
                        .put("x1", downX).put("y1", downY)
                        .put("x2", nx).put("y2", ny)
                        .put("duration", 250)
                }
                client?.sendControl(json.toString())
            }
        }
        return true
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            binding.btnLeaveServer.visibility = View.GONE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            binding.btnLeaveServer.visibility = View.VISIBLE
        }
    }

    private fun leave() {
        client?.close()
        finish()
    }

    private fun crossfade(out: View, into: View) {
        out.animate().alpha(0f).setDuration(220).withEndAction { out.visibility = View.GONE }.start()
        into.visibility = View.VISIBLE
        into.alpha = 0f
        into.animate().alpha(1f).setDuration(260).start()
    }

    override fun onDestroy() {
        client?.close()
        super.onDestroy()
    }
}
