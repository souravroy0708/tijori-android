package com.tijoriapp.vault.core.crypto

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 time-based one-time passwords, so Tijori can stand in for an authenticator app
 * for accounts you choose. Fully offline: only the phone clock is used.
 */
data class Totp(
    val secret: ByteArray,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: String = "SHA1",
    val issuer: String = "",
    val account: String = "",
) {
    fun codeAt(epochMillis: Long): String {
        val counter = epochMillis / 1000 / periodSeconds
        return hotp(counter)
    }

    fun secondsLeft(epochMillis: Long): Int = periodSeconds - ((epochMillis / 1000) % periodSeconds).toInt()

    fun hotp(counter: Long): String {
        val macName = when (algorithm.uppercase()) { "SHA256" -> "HmacSHA256"; "SHA512" -> "HmacSHA512"; else -> "HmacSHA1" }
        val mac = Mac.getInstance(macName)
        mac.init(SecretKeySpec(secret, macName))
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val h = mac.doFinal(msg)
        val off = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[off].toInt() and 0x7F) shl 24) or ((h[off + 1].toInt() and 0xFF) shl 16) or
            ((h[off + 2].toInt() and 0xFF) shl 8) or (h[off + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    companion object {
        /** Accepts a raw Base32 secret ("JBSW Y3DP …") or an otpauth://totp/… URI from a QR code. */
        fun parse(input: String): Totp? {
            val text = input.trim()
            if (text.startsWith("otpauth://", ignoreCase = true)) return parseUri(text)
            val secret = Base32.Rfc4648.decode(text) ?: return null
            return if (secret.size >= 10) Totp(secret) else null
        }

        private fun parseUri(uri: String): Totp? {
            if (!uri.substring(10).startsWith("totp", ignoreCase = true)) return null
            val q = uri.substringAfter('?', "")
            val params = q.split('&').mapNotNull {
                val k = it.substringBefore('=', "")
                if (k.isEmpty()) null else k.lowercase() to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }.toMap()
            val secret = params["secret"]?.let { Base32.Rfc4648.decode(it) } ?: return null
            val label = URLDecoder.decode(uri.substringAfter("totp/", "").substringBefore('?'), "UTF-8")
            val digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
            val period = params["period"]?.toIntOrNull()?.takeIf { it in 10..120 } ?: 30
            val algo = params["algorithm"]?.uppercase()?.takeIf { it in setOf("SHA1", "SHA256", "SHA512") } ?: "SHA1"
            return Totp(
                secret, digits, period, algo,
                issuer = params["issuer"] ?: label.substringBefore(':', ""),
                account = label.substringAfter(':', label),
            )
        }
    }
}
