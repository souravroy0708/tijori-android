package com.tijoriapp.vault.core.crypto

/**
 * Two Base32 flavours:
 * - [Crockford]: for codes people type from paper (Secret Key, nominee shares). No I, L, O, U;
 *   reading is forgiving (O→0, I/L→1, spaces and dashes ignored).
 * - [Rfc4648]: for authenticator (TOTP) secrets, which use the standard alphabet.
 */
object Base32 {
    object Crockford {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        fun encode(data: ByteArray): String = encodeWith(ALPHABET, data)

        fun normalize(input: String): String = input.uppercase()
            .replace('O', '0').replace('I', '1').replace('L', '1')
            .filter { it.isLetterOrDigit() }

        /** Returns null when the text has characters outside the alphabet. */
        fun decode(input: String, byteCount: Int): ByteArray? {
            val clean = normalize(input)
            if (clean.any { ALPHABET.indexOf(it) < 0 }) return null
            return decodeWith(ALPHABET, clean, byteCount)
        }
    }

    object Rfc4648 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        fun decode(input: String): ByteArray? {
            val clean = input.uppercase().filter { it != '=' && !it.isWhitespace() && it != '-' }
            if (clean.isEmpty() || clean.any { ALPHABET.indexOf(it) < 0 }) return null
            return decodeWith(ALPHABET, clean, clean.length * 5 / 8)
        }

        fun encode(data: ByteArray): String = encodeWith(ALPHABET, data)
    }

    private fun encodeWith(alphabet: String, data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(alphabet[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun decodeWith(alphabet: String, clean: String, byteCount: Int): ByteArray? {
        if (clean.length * 5 < byteCount * 8) return null
        val out = ByteArray(byteCount)
        var buffer = 0
        var bits = 0
        var pos = 0
        for (ch in clean) {
            buffer = (buffer shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                if (pos < byteCount) out[pos++] = (buffer shr (bits - 8)).toByte()
                bits -= 8
            }
            buffer = buffer and 0xFF
        }
        return if (pos == byteCount) out else null
    }
}
