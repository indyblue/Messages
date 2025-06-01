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
import org.fossify.messages.extensions.config
import android.view.MenuItem

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
    private lateinit var portText: TextView
    private lateinit var portEditButton: android.widget.ImageButton
    private lateinit var portRefreshButton: android.widget.ImageButton
    private var serverRunning = false
    private var webServer: SimpleWebServer? = null
    private var serverPort: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webserver_status)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.webserver_toolbar)
        setSupportActionBar(toolbar)

        // Add back arrow to the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.webserver_status_screen)

        statusText = findViewById(R.id.webserver_status_text)
        toggleButton = findViewById(R.id.webserver_toggle_button)
        portText = findViewById(R.id.webserver_port_text)
        portEditButton = findViewById(R.id.webserver_port_edit_button)
        portRefreshButton = findViewById(R.id.webserver_port_refresh_button)

        // Load port from config
        serverPort = applicationContext.config.webServerPort
        updatePortText()

        portEditButton.setOnClickListener {
            showEditPortDialog()
        }
        portRefreshButton.setOnClickListener {
            val newPort = (20000..30000).random()
            setPortAndResetServer(newPort)
        }

        // Initial status update
        if (serverRunning && webServer != null) {
            updateStatusTextRunning()
        } else {
            updateStatusTextStopped()
        }
        toggleButton.setOnClickListener {
            serverRunning = !serverRunning
            if (serverRunning) {
                startWebServer()
            } else {
                stopWebServer()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setPortAndResetServer(newPort: Int) {
        if (serverPort == newPort) return
        if (serverRunning) {
            stopWebServer()
            serverRunning = false
        }
        serverPort = newPort
        applicationContext.config.webServerPort = newPort
        updatePortText()
        updateStatusTextStopped()
    }

    private fun updatePortText() {
        portText.text = "Port: $serverPort"
    }

    private fun showEditPortDialog() {
        val editText = android.widget.EditText(this)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.setText(serverPort.toString())
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Set Web Server Port")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val input = editText.text.toString()
                val port = input.toIntOrNull()
                if (port != null && port in 1025..65535) {
                    setPortAndResetServer(port)
                } else {
                    android.widget.Toast.makeText(this, "Invalid port (1025-65535)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun startWebServer() {
        if (webServer == null) {
            android.util.Log.i("WebServerStatus", "Attempting to start web server on port $serverPort")
            webServer = SimpleWebServer(serverPort)
            try {
                webServer?.start()
                android.util.Log.i("WebServerStatus", "Web server started on port $serverPort")
                updateStatusTextRunning()
            } catch (e: IOException) {
                serverRunning = false
                val errorMsg = "Failed to start server on port $serverPort: ${e.message}"
                statusText.text = errorMsg
                android.util.Log.e("WebServerStatus", errorMsg, e)
                webServer = null
                updateStatusTextStopped()
                return
            }
        }
    }

    private fun stopWebServer() {
        webServer?.stop()
        webServer = null
        updateStatusTextStopped()
    }

    private fun getDeviceIpAddresses(): List<String> {
        val addresses = mutableSetOf<String>()
        // Always include 127.0.0.1
        addresses.add("127.0.0.1")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
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
        return addresses.filter { it.isNotBlank() }
    }

    private fun updateStatusTextRunning() {
        updatePortText()
        val ips = getDeviceIpAddresses()
        if (ips.isNotEmpty()) {
            // Show only the IPs, but copy full http://ip:port on click
            val displayText = getString(R.string.webserver_status_running) + "\n" + ips.joinToString("\n")
            statusText.text = displayText
            val spannable = android.text.SpannableString(displayText)
            val baseLen = getString(R.string.webserver_status_running).length + 1 // +1 for the newline
            var start = baseLen
            for (i in ips.indices) {
                val ip = ips[i]
                val end = start + ip.length
                val fullUrl = "http://$ip:$serverPort"
                val clickable = object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: android.view.View) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Web server address", fullUrl)
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
    }

    private fun updateStatusTextStopped() {
        updatePortText()
        statusText.text = getString(R.string.webserver_status_stopped)
        toggleButton.text = getString(R.string.webserver_start)
        statusText.setOnClickListener(null)
        statusText.movementMethod = null
    }
}
