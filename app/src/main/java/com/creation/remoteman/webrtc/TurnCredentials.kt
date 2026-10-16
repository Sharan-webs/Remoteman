package com.creation.remoteman.webrtc

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.webrtc.PeerConnection

/**
 * Pulls ICE server list (STUN + TURN) from Metered using your publishable API key.
 * Replace APP_NAME below with the subdomain Metered gave you on your dashboard,
 * e.g. if your dashboard URL is https://yourapp.metered.live/... use "yourapp".
 */
object TurnCredentials {

    private const val METERED_APP_NAME = "remoteman"
    private const val METERED_API_KEY = "pk_live_8b05abe27f34fe090ea60c8cf53721a6065fde22"

    private val client = OkHttpClient()

    fun fetch(): List<PeerConnection.IceServer> {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        try {
            val url = "https://$METERED_APP_NAME.metered.live/api/v1/turn/credentials?apiKey=$METERED_API_KEY"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return servers
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val urls = obj.getString("urls")
                    val username = obj.optString("username", "")
                    val credential = obj.optString("credential", "")
                    servers.add(
                        PeerConnection.IceServer.builder(urls)
                            .setUsername(username)
                            .setPassword(credential)
                            .createIceServer()
                    )
                }
            }
        } catch (e: Exception) {
            // Falls back to the free Open Relay TURN servers if the fetch fails.
            servers.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        }
        return servers
    }
}
