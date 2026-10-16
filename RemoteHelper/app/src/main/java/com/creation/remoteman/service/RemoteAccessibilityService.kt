package com.creation.remoteman.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/**
 * Executes remote-control commands on THIS device only while a session is active.
 * Commands arrive over the WebRTC data channel as JSON, e.g.:
 *   {"type":"tap","x":540,"y":1200}
 *   {"type":"swipe","x1":200,"y1":1800,"x2":200,"y2":600,"duration":250}
 *   {"type":"back"} / {"type":"home"} / {"type":"recents"}
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun handleControlMessage(json: String) {
        val obj = JSONObject(json)
        when (obj.optString("type")) {
            "tap" -> tap(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
            "swipe" -> swipe(
                obj.getDouble("x1").toFloat(), obj.getDouble("y1").toFloat(),
                obj.getDouble("x2").toFloat(), obj.getDouble("y2").toFloat(),
                obj.optLong("duration", 250)
            )
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
