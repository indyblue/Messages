package org.fossify.messages.webserver

import android.content.Context
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.security.MessageDigest
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.webserver.SerializationUtils.serializeToJson
import org.fossify.messages.webserver.SerializationUtils.tryCatch

class WebServerManager(
    private val context: Context,
    private val port: Int,
    private val apiKey: String,
    private val logFunction: (String) -> Unit
) : NanoHTTPD(port) {

    companion object {
        const val salt = "FQrxNELXwGoN4F4Qs8lYuZaA"
    }

    private var handlers: List<(IHTTPSession) -> Response?> = emptyList()

    override fun start(timeout: Int, daemon: Boolean) {
        handlers = listOf(
            ::handleTestEndpoint,
            ::handleTokenEndpoint,
            ::handleGenerateTokenEndpoint,
            ::handleThreadsEndpoint,
            ::handleThreadEndpoint,
            ::handleMmsAttachmentsEndpoint,
        )

        super.start(timeout, daemon)
    }

    override fun serve(session: IHTTPSession): Response {
        logFunction("- ${session.uri}")

        for (handler in handlers) {
            val response = handler(session)
            if (response != null) {
                return response
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }

    private fun handleTokenEndpoint(session: IHTTPSession): Response? {
        val queryParams = session.parameters["key"]
        val authHeader = session.headers["authorization"]

        val token = when {
            queryParams != null && queryParams.isNotEmpty() -> queryParams.first()
            authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true) -> authHeader.substring(7)
            else -> null
        }
        val valid = token?.let { it == apiKey || validateGeneratedToken(it) } == true

        return if (valid) {
            null
        } else {
            newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid token")
        }
    }

    private fun handleGenerateTokenEndpoint(session: IHTTPSession): Response? {
        val regex = Regex("^/token/(\\d+)$")
        return regex.matchEntire(session.uri)?.let { match ->
            val expirationSeconds = match.groupValues[1].toLongOrNull() ?: -1
            if (expirationSeconds <= 0) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid expiration time")
            }

            val expirationEpoch = currentEpoch() + expirationSeconds
            val hash = calcHash(expirationEpoch)

            val base36Epoch = expirationEpoch.toString(36)
            val token = "$base36Epoch:$hash"
            return newFixedLengthResponse(Response.Status.OK, "text/plain", token)
        }
    }

    private fun currentEpoch(): Long {
        val epochSeconds = java.time.LocalDateTime.of(2025, 1, 1, 0, 0)
            .atZone(java.time.ZoneOffset.UTC)
            .toEpochSecond()
        return System.currentTimeMillis() / 1000 - epochSeconds
    }

    private fun calcHash(token: Any): String {
        val tokenStr = token.toString()
        val dataToHash = "$tokenStr:$apiKey:$salt"
        // MD5 SHA-1 SHA-224 SHA-256 SHA-384 SHA-512
        return MessageDigest.getInstance("SHA-512")
            .digest(dataToHash.toByteArray())
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }
            .substring(0, 6)
    }

    private fun validateGeneratedToken(token: String): Boolean {
        val parts = token.split(":")
        if (parts.size != 2) return false

        val expirationEpoch = parts[0].toLongOrNull(36) ?: return false
        val providedHash = parts[1]

        val nowEpoch = currentEpoch()
        if (nowEpoch > expirationEpoch) return false

        return providedHash == calcHash(expirationEpoch)
    }

    private fun handleTestEndpoint(session: IHTTPSession): Response? {
        return if (session.uri == "/test") {
            newFixedLengthResponse(Response.Status.OK, "text/plain", "Web server is running!")
        } else null
    }

    private fun handleThreadsEndpoint(session: IHTTPSession): Response? {
        val threadRegex = Regex("^/threads/?(\\d*)$")
        return threadRegex.matchEntire(session.uri)?.let { match ->
            val threadId = match.groupValues[1].toLongOrNull()
            val (conversations, error) = tryCatch {
                context.getConversations(threadId).sortedByDescending { it.date }
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", serializeToJson(conversations, error?.let { Exception(it) }))
        }
    }

    private fun handleThreadEndpoint(session: IHTTPSession): Response? {
        val threadRegex = Regex("^/thread/(\\d+)(?:/(\\d+))?$")
        return threadRegex.matchEntire(session.uri)?.let { match ->
            val threadId = match.groupValues[1].toLongOrNull() ?: -1
            val beforeDate = match.groupValues[2].toIntOrNull() ?: -1
            val (messages, error) = tryCatch {
                context.getMessages(threadId, true, beforeDate).takeIf { !it.isNullOrEmpty() }
                    ?: listOf("Empty", threadId)
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", serializeToJson(messages, error?.let { Exception(it) }))
        }
    }

    private fun handleMmsAttachmentsEndpoint(session: IHTTPSession): Response? {
        val threadRegex = Regex("^/attachment/([^/]+)/(.+)$")
        return threadRegex.matchEntire(session.uri)?.let { match ->
            val ctype = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
            val uri = String(Base64.decode(match.groupValues[2], Base64.DEFAULT))
            val (stream, error) = tryCatch {
                val partUri = android.net.Uri.parse(uri)
                context.applicationContext.contentResolver.openInputStream(partUri)
            }
            if (stream != null) {
                return newChunkedResponse(Response.Status.OK, ctype, stream)
            } else {
                newFixedLengthResponse(Response.Status.OK, "application/json", serializeToJson(null, error?.let { Exception(it) }))
            }
        }
    }
}