package com.tijoriapp.vault.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM. Output = 12-byte random nonce || ciphertext || 16-byte tag.
 * Associated data binds every ciphertext to where it belongs (vault, item, room),
 * so rows cannot be swapped or moved between rooms without failing authentication.
 */
object Aead {
    private const val NONCE = 12
    private const val TAG_BITS = 128

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 needs a 32-byte key" }
        val nonce = Bytes.random(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    /** Throws [javax.crypto.AEADBadTagException] when the key, data or AAD is wrong. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 needs a 32-byte key" }
        require(sealed.size >= NONCE + 16) { "Ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
        c.updateAAD(aad)
        return c.doFinal(sealed, NONCE, sealed.size - NONCE)
    }
}
