package org.fossify.messages.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ScrollView
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.webserver.WebServerManager

class WebServerStatusActivity : SimpleActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var portText: TextView
    private lateinit var portEditButton: ImageButton
    private lateinit var portRefreshButton: ImageButton
    private lateinit var apiKeyText: TextView
    private lateinit var apiKeyEditButton: ImageButton
    private lateinit var apiKeyRefreshButton: ImageButton
    private lateinit var apiKeyVisibilityButton: Button
    private lateinit var logTextView: TextView

    companion object {
        val serverRunning: Boolean
            get() = webServer?.isAlive == true
        var webServer: WebServerManager? = null
        private const val NOTIFICATION_ID = 1
    }

    private var serverPort: Int = 0
    private var apiKey: String = ""
    private var isApiKeyVisible: Boolean = false
    private var logData: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webserver_status)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.webserver_toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.webserver_status_screen)

        statusText = findViewById(R.id.webserver_status_text)
        toggleButton = findViewById(R.id.webserver_toggle_button)
        portText = findViewById(R.id.webserver_port_text)
        portEditButton = findViewById(R.id.webserver_port_edit_button)
        portRefreshButton = findViewById(R.id.webserver_port_refresh_button)
        apiKeyText = findViewById(R.id.webserver_api_key_text)
        apiKeyEditButton = findViewById(R.id.webserver_api_key_edit_button)
        apiKeyRefreshButton = findViewById(R.id.webserver_api_key_refresh_button)
        apiKeyVisibilityButton = findViewById(R.id.webserver_api_key_visibility_button)
        logTextView = findViewById(R.id.webserver_log_text)
        logData = savedInstanceState?.getString("logData") ?: ""
        logTextView.text = logData
        serverPort = applicationContext.config.webServerPort
        updatePortText()

        apiKey = applicationContext.config.webServerApiKey ?: generateRandomApiKey()
        updateApiKeyText()

        portEditButton.setOnClickListener {
            showEditPortDialog()
        }
        portRefreshButton.setOnClickListener {
            val newPort = (20000..30000).random()
            setPortAndResetServer(newPort)
        }

        apiKeyEditButton.setOnClickListener {
            showEditApiKeyDialog()
        }
        apiKeyRefreshButton.setOnClickListener {
            val newApiKey = generateRandomApiKey()
            setApiKey(newApiKey)
        }

        apiKeyVisibilityButton.setOnClickListener {
            isApiKeyVisible = !isApiKeyVisible
            updateApiKeyText()
        }

        val startServer = intent.getBooleanExtra("START_SERVER", false)
        if (startServer && !serverRunning) {
            startWebServer()
        }
        if (intent.action == "STOP_SERVER") {
            stopWebServer()
        }

        // Initial status update
        if (serverRunning && webServer != null) {
            updateStatusTextRunning()
        } else {
            updateStatusTextStopped()
        }
        toggleButton.setOnClickListener {
            if (serverRunning) {
                stopWebServer()
            } else {
                startWebServer()
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
        stopWebServer()
        serverPort = newPort
        applicationContext.config.webServerPort = newPort
        updatePortText()
        updateStatusTextStopped()
    }

    private fun setApiKey(newApiKey: String) {
        if (apiKey == newApiKey) return
        stopWebServer()
        apiKey = newApiKey
        applicationContext.config.webServerApiKey = newApiKey
        updateApiKeyText()
    }

    private fun updatePortText() {
        portText.text = "Port: $serverPort"
    }

    private fun updateApiKeyText() {
        apiKeyVisibilityButton.text = "\uD83D\uDC41" // Eye-open UTF-8 glyph
        if (isApiKeyVisible) {
            apiKeyText.text = "API Key: $apiKey"
        } else {
            apiKeyText.text = "API Key: **********"
        }
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

    private fun showEditApiKeyDialog() {
        val editText = android.widget.EditText(this)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        editText.setText(apiKey)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Set API Key")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val input = editText.text.toString()
                setApiKey(input)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun generateRandomApiKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = java.security.SecureRandom()
        return (1..10)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun startWebServer() {
        if (serverRunning) return

        webServer = WebServerManager(applicationContext, serverPort, apiKey) { logMessage ->
            appendLog(logMessage)
        }
        try {
            webServer?.start()
            updateStatusTextRunning()
            showServerNotification()
        } catch (e: IOException) {
            updateStatusTextStopped()
        }
    }

    private fun stopWebServer() {
        if (!serverRunning) return

        webServer?.stop()
        webServer = null
        updateStatusTextStopped()
        if (!serverRunning) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun getDeviceIpAddresses(): List<String> {
        val addresses = mutableSetOf<String>()
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
        } catch (e: Exception) {}
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
                val fullUrl = "http://$ip:$serverPort/test?key=$apiKey"
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

    // Update the showServerNotification method to create a dismissable notification
    private fun showServerNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "web_server_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Web Server",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, WebServerStatusActivity::class.java).apply {
            action = "STOP_SERVER"
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Web Server Running")
            .setContentText("Dismiss to stop the server")
            // .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setSmallIcon(R.drawable.ic_messenger)
            .setAutoCancel(true) // Make the notification dismissable
            .setDeleteIntent(stopPendingIntent) // Stop the server when dismissed
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun appendLog(text: String) {
        logData += "$text\n"
        logTextView.append("$text\n")
        val scrollView = findViewById<ScrollView>(R.id.webserver_log_scroll)
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
