package com.forgeservice.app.data.remote

import okhttp3.*
import okio.ByteString

/**
 * WebSocket manager for real-time updates from ForgeService server.
 * Subscribe to specific work orders or global channel.
 *
 * This is one of the "wow" features for the defense.
 */
class WebSocketManager {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connectToWorkOrder(baseUrl: String, workOrderId: Int, onMessage: (String) -> Unit) {
        val url = baseUrl.replace("http", "ws") + "/ws/work-orders/$workOrderId"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // TODO: handle reconnection
            }
        })
    }

    fun connectGlobal(baseUrl: String, onMessage: (String) -> Unit) {
        val url = baseUrl.replace("http", "ws") + "/ws/global"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}