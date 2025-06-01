package org.fossify.messages.activities

import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.TextView
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import java.util.concurrent.Executors
import fi.iki.elonen.NanoHTTPD
import java.net.InetAddress
import java.net.ServerSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class SimpleWebServer(port: Int) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        android.util.Log.i("WebServerStatus", "Received request: ${session.uri}")
        return when (session.uri) {
            "/test" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "Web server is running!")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}

class WebServerStatusActivity : SimpleActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var serverRunning = false
    private var webServer: SimpleWebServer? = null
    private var serverPort: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webserver_status)
        statusText = findViewById(R.id.webserver_status_text)
        toggleButton = findViewById(R.id.webserver_toggle_button)

        updateStatus()
        toggleButton.setOnClickListener {
            serverRunning = !serverRunning
            if (serverRunning) {
                startWebServer()
            } else {
                stopWebServer()
            }
            updateStatus()
        }
        toggleButton.setOnLongClickListener {
            startMinimalServerSocketTest()
            true
        }
    }

    private fun startWebServer() {
        if (webServer == null) {
            // Use a random high port to avoid conflicts
            serverPort = (20000..30000).random()
            android.util.Log.i("WebServerStatus", "Attempting to start web server on port $serverPort")
            webServer = SimpleWebServer(serverPort)
            try {
                webServer?.start()
                android.util.Log.i("WebServerStatus", "Web server started on port $serverPort")
                statusText.text = "Web server started on port $serverPort"
            } catch (e: IOException) {
                serverRunning = false
                val errorMsg = "Failed to start server on port $serverPort: ${e.message}"
                statusText.text = errorMsg
                android.util.Log.e("WebServerStatus", errorMsg, e)
                webServer = null
                return
            }
        }
    }

    private fun stopWebServer() {
        webServer?.stop()
        webServer = null
    }

    private fun getDeviceIpAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                // Only consider interfaces that are up and not loopback
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        addresses.add(addr.hostAddress ?: "")
                    }
                }
            }
        } catch (e: Exception) {
            // Optionally log the exception
        }
        // Fallback: if no address found, try to show all interfaces/addresses for debugging
        if (addresses.isEmpty()) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (intf in interfaces) {
                    val addrs = intf.inetAddresses
                    for (addr in addrs) {
                        addresses.add("[${intf.displayName}] ${addr.hostAddress}")
                    }
                }
            } catch (_: Exception) {}
        }
        return addresses
    }

    private fun updateStatus() {
        if (serverRunning && webServer != null) {
            val ips = getDeviceIpAddresses()
            if (ips.isNotEmpty()) {
                // Build a clickable list of IPs with http:// prefix
                val ipLinks = ips.map { "http://$it:$serverPort" }
                val displayText = getString(R.string.webserver_status_running) + "\n" + ipLinks.joinToString("\n")
                statusText.text = displayText
                // Make each IP:port clickable
                val spannable = android.text.SpannableString(displayText)
                val baseLen = getString(R.string.webserver_status_running).length + 1 // +1 for the newline
                var start = baseLen
                for (i in ipLinks.indices) {
                    val ip = ipLinks[i]
                    val end = start + ip.length
                    val clickable = object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Web server address", ip)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(this@WebServerStatusActivity, getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    spannable.setSpan(clickable, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    start = end + 1 // +1 for the newline
                }
                statusText.text = spannable
                statusText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            } else {
                statusText.text = getString(R.string.webserver_status_running) + "\nNo IP found (port: $serverPort)"
                statusText.setOnClickListener(null)
                statusText.movementMethod = null
            }
            toggleButton.text = getString(R.string.webserver_stop)
        } else {
            statusText.text = getString(R.string.webserver_status_stopped) + "\nPort: $serverPort"
            toggleButton.text = getString(R.string.webserver_start)
            statusText.setOnClickListener(null)
            statusText.movementMethod = null
        }
    }

    private fun startMinimalServerSocketTest() {
        Thread {
            try {
                val testPort = (40000..50000).random()
                val serverSocket = ServerSocket(testPort, 0, InetAddress.getByName("127.0.0.1"))
                android.util.Log.i("WebServerStatus", "Minimal ServerSocket bound to 127.0.0.1:$testPort")
                runOnUiThread {
                    statusText.text = "Minimal ServerSocket bound to 127.0.0.1:$testPort"
                }
                val client = serverSocket.accept()
                client.getOutputStream().write("Hello from minimal server!\n".toByteArray())
                client.close()
                serverSocket.close()
            } catch (e: Exception) {
                android.util.Log.e("WebServerStatus", "Minimal ServerSocket failed: ${e.message}", e)
                runOnUiThread {
                    statusText.text = "Minimal ServerSocket failed: ${e.message}"
                }
            }
        }.start()
    }
}
