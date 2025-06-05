/*
import java.io.File
import java.io.FileOutputStream
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.AttachmentSelection
import org.fossify.messages.models.SimpleContact
import org.fossify.messages.messaging.sendMessageCompat

private fun handleSendAttachmentEndpoint(session: IHTTPSession): Response? {
    // Example: POST /send_attachment?threadId=123&number=+123456789&filename=pic.jpg
    if (session.uri != "/send_attachment" || session.method != Method.POST) return null

    val threadId = session.parameters["threadId"]?.firstOrNull()?.toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing threadId")
    val number = session.parameters["number"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing number")
    val filename = session.parameters["filename"]?.firstOrNull() ?: "attachment"
    val mimetype = session.parameters["mimetype"]?.firstOrNull() ?: "application/octet-stream"
    val text = session.parameters["text"]?.firstOrNull() ?: ""

    // Save the uploaded file stream to a temp file
    val tempFile = File(context.cacheDir, filename)
    session.inputStream.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }

    // Build the attachment
    val attachment = Attachment(
        id = null,
        messageId = -1L,
        uriString = tempFile.toURI().toString(),
        mimetype = mimetype,
        width = 0,
        height = 0,
        filename = filename
    )

    // Build recipient/contact
    val phoneNumber = org.fossify.commons.models.PhoneNumber(number, 0, "", number)
    val contact = SimpleContact(
        rawId = 0,
        contactId = 0,
        name = number,
        photoUri = "",
        phoneNumbers = arrayListOf(phoneNumber),
        birthdays = ArrayList(),
        anniversaries = ArrayList()
    )

    // Send the message with attachment
    try {
        sendMessageCompat(
            text = text,
            addresses = listOf(number),
            subscriptionId = -1, // or get from SIM selection logic
            attachments = listOf(attachment),
            resendMessageId = null
        )
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Message sent")
    } catch (e: Exception) {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Send failed: ${e.message}")
    }
}
*/