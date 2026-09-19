package com.vncandroid.free.util

import android.net.Uri as URI
import androidx.core.net.toUri

object QrCode {

    class InvalidQrCodeException : Exception()

    sealed class Content {
        data class Json(val json: String) : Content()
        data class Uri(val uri: URI) : Content()
    }

    /**
     * Parses VNC QR content of the form `VNC:TYPE[:option]*;<payload>`.
     */
    fun decode(content: String): Content {
        val sep = content.indexOf(';')
        val header = if (sep >= 0) content.substring(0, sep) else content
        val payload = if (sep >= 0) content.substring(sep + 1) else ""

        return when {
            header == "VNC:DATA" || header.startsWith("VNC:DATA:") || header == "VNC:DATA" || header.startsWith("VNC:DATA:") -> Content.Json(payload)
            header == "VNC:URI" || header.startsWith("VNC:URI:") || header == "VNC:URI" || header.startsWith("VNC:URI:") -> Content.Uri(payload.toUri())
            else -> throw InvalidQrCodeException()
        }
    }

    /**
     * Encodes the given [json] as VNC QR content of the form `VNC:DATA;<json>`.
     */
    fun encode(json: String): String = "VNC:DATA;$json"

}
