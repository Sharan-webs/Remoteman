package com.creation.remoteman.webrtc

import android.content.Context
import android.content.Intent
import org.webrtc.*

/**
 * Thin wrapper around the stream-webrtc-android PeerConnection APIs.
 *
 * Host (screen owner / "needer" of help) side:
 *   - call attachScreenCapture() with the MediaProjection Intent result to add a video track
 *   - call createOffer() to start the handshake
 *   - incoming control messages arrive via onControlMessage
 *
 * Connector (helper) side:
 *   - call createAnswer(offerSdp) after receiving the offer
 *   - the remote video track arrives via onRemoteVideoTrack
 *   - call sendControl(json) to send tap/swipe commands to the host
 */
class WebRTCClient(
    private val context: Context,
    private val iceServers: List<PeerConnection.IceServer>,
    private val onLocalSdp: (SessionDescription) -> Unit,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit,
    private val onControlMessage: (String) -> Unit
) {
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

    fun init() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
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
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
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

        // Host creates the control channel; connector receives it via onDataChannel above.
        controlChannel = peerConnection?.createDataChannel("control", DataChannel.Init())
        controlChannel?.registerObserver(controlObserver(controlChannel!!))
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
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val source = factory.createVideoSource(true)
        videoCapturer = ScreenCapturerAndroid(projectionIntent, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() { /* projection revoked */ }
        })
        videoCapturer?.initialize(surfaceHelper, context, source.capturerObserver)
        videoCapturer?.startCapture(screenWidth, screenHeight, 15)

        val track = factory.createVideoTrack("screen0", source)
        peerConnection?.addTrack(track, listOf("stream0"))
    }

    fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onLocalSdp(sdp)
            }
        }, constraints)
    }

    fun setRemoteOffer(sdp: String) {
        peerConnection?.setRemoteDescription(
            SdpObserverAdapter(), SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
    }

    fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onLocalSdp(sdp)
            }
        }, constraints)
    }

    fun setRemoteAnswer(sdp: String) {
        peerConnection?.setRemoteDescription(
            SdpObserverAdapter(), SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun eglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun close() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.close()
    }
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
