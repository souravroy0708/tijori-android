package com.tijoriapp.vault.core.crypto

/**
 * The 128-bit Secret Key (the second unlock factor, like 1Password's).
 * It lives on this phone (wrapped by the Android Keystore) and on the printed Emergency Kit.
 * You only type it when restoring a backup on a new phone.
 *
 * Printed as "TJ-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX": 16 key bytes + 1 checksum byte in Crockford Base32,
 * so a single mistyped character is caught before any slow key derivation runs.
 */
object SecretKey {
    const val PREFIX = "TJ"
    private const val KEY_BYTES = 16

    fun generate(): ByteArray = Bytes.random(KEY_BYTES)

    fun format(key: ByteArray): String {
        require(key.size == KEY_BYTES)
        val withCheck = key + checksum(key)
        val code = Base32.Crockford.encode(withCheck) // 136 bits -> 28 chars
        return PREFIX + "-" + code.chunked(4).joinToString("-")
    }

    /** Parses a typed key. Returns null if it's malformed or the checksum doesn't match. */
    fun parse(input: String): ByteArray? {
        var clean = Base32.Crockford.normalize(input)
        if (clean.startsWith(PREFIX)) clean = clean.removePrefix(PREFIX)
        if (clean.length != 28) return null
        val bytes = Base32.Crockford.decode(clean, KEY_BYTES + 1) ?: return null
        val key = bytes.copyOf(KEY_BYTES)
        return if (bytes[KEY_BYTES] == checksum(key)) key else null
    }

    private fun checksum(key: ByteArray): Byte = Bytes.sha256("tijori-sk".toByteArray(), key)[0]
}
