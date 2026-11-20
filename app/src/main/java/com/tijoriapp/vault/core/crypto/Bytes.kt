package com.tijoriapp.vault.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Small byte helpers shared by the crypto layer. Pure JVM: unit-testable without a device. */
object Bytes {
    val rng: SecureRandom by lazy { SecureRandom() }

    fun random(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) append("%02x".format(b.toInt() and 0xFF))
    }

    fun unhex(text: String): ByteArray {
        val clean = text.filter { !it.isWhitespace() }
        require(clean.length % 2 == 0) { "Odd hex length" }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Constant-time comparison, so checks never leak how many bytes matched. */
    fun equal(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun utf8(chars: CharArray): ByteArray {
        val bb = Charsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(chars))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }
}

/** Overwrites secret material. Best effort on the JVM, but it shortens the window a lot. */
fun ByteArray?.wipe() { this?.fill(0) }
fun CharArray?.wipe() { this?.fill('\u0000') }
