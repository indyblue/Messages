package org.fossify.messages.webserver

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okio.ByteString

class WebSocketClient(
    private val context: Context,
    private val url: String,
    private val apiKey: String,
    private val logFunction: (String) -> Unit
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    @Volatile
    private var alive: Boolean = false

    val isAlive: Boolean
        get() = alive

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                alive = true
                logFunction("WebSocket connected: $url")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                logFunction("WebSocket message: $text")
                ws.send("you sent me: $text")
            }
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                logFunction("WebSocket binary message: ${bytes.hex()}")
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                logFunction("WebSocket closing: $code / $reason")
                ws.close(code, reason)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                alive = false
                logFunction("WebSocket closed: $code / $reason")
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                alive = false
                logFunction("WebSocket error: ${t.message}")
            }
        })
    }

    fun send(text: String) {
        webSocket?.send(text)
    }

    fun close(code: Int = 1000, reason: String = "") {
        alive = false
        webSocket?.close(code, reason)
    }
}
