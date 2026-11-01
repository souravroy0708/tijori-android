package com.tijoriapp.vault.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * RFC 5869 HKDF implementation using HMAC-SHA256 (Spec Page 5 & 6: "Derived at unlock: HKDF-SHA256").
 */
object Hkdf {
    private const val HASH_LEN = 32 // SHA-256 output length

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "Requested HKDF length is too large" }
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(prk, "HmacSHA256")
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        val n = ceil(length.toDouble() / HASH_LEN).toInt()

        for (i in 1..n) {
            mac.init(key)
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val bytesToCopy = minOf(HASH_LEN, length - offset)
            System.arraycopy(t, 0, okm, offset, bytesToCopy)
            offset += bytesToCopy
        }
        return okm
    }

    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
