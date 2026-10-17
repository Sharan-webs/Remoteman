package com.creation.remoteman.webrtc

import android.content.Context
import android.content.Intent
import android.util.Log
import org.webrtc.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thin wrapper around the stream-webrtc-android PeerConnection APIs.
 *
 * Host (screen owner / "needer" of help) side:
 *   - call attachScreenCapture() with the MediaProjection Intent result to add a video track
 *   - call createOffer() to start the handshake
 *   - incoming control messages arrive via onControlMessage
 *
 * Connector (helper) side:
 *   - call setRemoteOffer(offerSdp) { createAnswer() } — wait for the callback,
 *     don't call createAnswer() right after setRemoteOffer()
 *   - the remote video track arrives via onRemoteVideoTrack
 *   - call sendControl(json) to send tap/swipe commands to the host
 *
 * IMPORTANT: setRemoteDescription()/setLocalDescription() are asynchronous.
 * Every step that depends on the remote description being set (creating an
 * answer, applying ICE candidates) waits for onSetSuccess before running —
 * doing it immediately after the call, without waiting, is what silently
 * breaks the video stream.
 */
class WebRTCClient(
    private val context: Context,
    private val iceServers: List<PeerConnection.IceServer>,
    private val onLocalSdp: (SessionDescription) -> Unit,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit,
    private val onControlMessage: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "WebRTCClient"
    }

    private val eglBase = EglBase.create()

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null
    private var controlChannel: DataChannel? = null
    private var videoCapturer: VideoCapturer? = null

    private var remoteDescriptionSet = false
    private val pendingCandidates = ConcurrentLinkedQueue<IceCandidate>()

    fun init() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                this@WebRTCClient.onIceCandidate(candidate)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                (transceiver?.receiver?.track() as? VideoTrack)?.let(onRemoteVideoTrack)
            }
            override fun onDataChannel(dc: DataChannel) {
                dc.registerObserver(controlObserver(dc))
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    onError("Connection failed — check both devices' internet connection and try again.")
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        if (peerConnection == null) {
            onError("Could not start the connection engine on this device.")
            return
        }

        // Host creates the control channel; connector receives it via onDataChannel above.
        controlChannel = peerConnection?.createDataChannel("control", DataChannel.Init())
        controlChannel?.let { it.registerObserver(controlObserver(it)) }
    }

    private fun controlObserver(dc: DataChannel) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}
        override fun onStateChange() {}
        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            onControlMessage(String(bytes, Charsets.UTF_8))
        }
    }

    fun sendControl(json: String) {
        val buffer = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false
        )
        controlChannel?.send(buffer)
    }

    /** Call with the Intent returned from MediaProjectionManager's permission prompt. */
    fun attachScreenCapture(projectionIntent: Intent, screenWidth: Int, screenHeight: Int) {
        try {
            val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val source = factory.createVideoSource(true)
            videoCapturer = ScreenCapturerAndroid(
                projectionIntent,
                object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() { /* projection revoked by the OS or the user */ }
                }
            )
            videoCapturer?.initialize(surfaceHelper, context, source.capturerObserver)
            videoCapturer?.startCapture(screenWidth, screenHeight, 15)

            val track = factory.createVideoTrack("screen0", source)
            peerConnection?.addTrack(track, listOf("stream0"))
        } catch (e: Exception) {
            Log.e(TAG, "Screen capture failed to start", e)
            onError("Screen capture couldn't start on this device: ${e.message}")
        }
    }

    fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onLocalSdp(sdp)
            }
            override fun onCreateFailure(error: String?) {
                onError("Couldn't start the session: $error")
            }
        }, constraints)
    }

    /** Sets the remote offer, then runs [onReady] only once it's actually applied. */
    fun setRemoteOffer(sdp: String, onReady: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingCandidates()
                onReady()
            }
            override fun onSetFailure(error: String?) {
                onError("Couldn't read the host's connection info: $error")
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onLocalSdp(sdp)
            }
            override fun onCreateFailure(error: String?) {
                onError("Couldn't finish connecting: $error")
            }
        }, constraints)
    }

    fun setRemoteAnswer(sdp: String) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingCandidates()
            }
            override fun onSetFailure(error: String?) {
                onError("Couldn't finish connecting: $error")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    /** Queues candidates that arrive before the remote description is set, applies the rest immediately. */
    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingCandidates.add(candidate)
        }
    }

    private fun drainPendingCandidates() {
        while (pendingCandidates.isNotEmpty()) {
            pendingCandidates.poll()?.let { peerConnection?.addIceCandidate(it) }
        }
    }

    fun eglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun close() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.w(TAG, "stopCapture threw on close", e)
        }
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnection = null
    }
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
