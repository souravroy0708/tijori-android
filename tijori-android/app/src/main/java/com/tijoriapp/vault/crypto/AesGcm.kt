package com.tijoriapp.vault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated AES-256-GCM encryption with fresh 96-bit random nonce and AAD binding
 * (Spec Page 6: "AES-256-GCM with a fresh random 96-bit nonce every time. The authenticated data
 * binds vault id, item id, room and format version, so entries cannot be swapped or downgraded").
 */
object AesGcm {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private val random = SecureRandom()

    fun encrypt(
        keyBytes: ByteArray,
        plainText: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray {
        require(keyBytes.size == 32) { "AES-256 requires 32-byte key" }
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val cipherBytes = cipher.doFinal(plainText)
        return iv + cipherBytes
    }

    fun decrypt(
        keyBytes: ByteArray,
        combinedCipher: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray {
        require(keyBytes.size == 32) { "AES-256 requires 32-byte key" }
        require(combinedCipher.size > GCM_IV_LENGTH + 16) { "Ciphertext too short" }
        val iv = combinedCipher.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = combinedCipher.copyOfRange(GCM_IV_LENGTH, combinedCipher.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        return cipher.doFinal(cipherBytes)
    }

    fun makeAad(vaultId: String, itemId: Long, room: String, formatVersion: Int = 1): ByteArray =
        "tijori:v$formatVersion:$vaultId:$itemId:$room".toByteArray(Charsets.UTF_8)
}
