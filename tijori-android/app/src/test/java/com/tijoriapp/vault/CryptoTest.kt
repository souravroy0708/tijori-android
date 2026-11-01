package com.tijoriapp.vault

import com.tijoriapp.vault.crypto.AesGcm
import com.tijoriapp.vault.crypto.Argon2id
import com.tijoriapp.vault.crypto.Hkdf
import com.tijoriapp.vault.crypto.PassphraseGenerator
import com.tijoriapp.vault.crypto.ShamirSecretSharing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class CryptoTest {

    @Test
    fun testHkdfSha256ExtractAndExpand() {
        val ikm = "SuperSecretInputKeyMaterial".toByteArray()
        val salt = "SaltValue12345678".toByteArray()
        val info = "tijori:room:money".toByteArray()

        val derivedKey1 = Hkdf.deriveKey(salt, ikm, info, 32)
        val derivedKey2 = Hkdf.deriveKey(salt, ikm, info, 32)

        assertEquals(32, derivedKey1.size)
        assertArrayEquals(derivedKey1, derivedKey2)

        // Different info derives different key
        val differentInfo = "tijori:room:everyday".toByteArray()
        val derivedKey3 = Hkdf.deriveKey(salt, ikm, differentInfo, 32)
        var matches = true
        for (i in 0 until 32) {
            if (derivedKey1[i] != derivedKey3[i]) {
                matches = false
                break
            }
        }
        assertTrue("Different info must yield distinct keys", !matches)
    }

    @Test
    fun testAes256GcmRoundTripAndTamperDetection() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plaintext = "HDFC NetBanking Password #12345".toByteArray(Charsets.UTF_8)
        val aad = AesGcm.makeAad("vault-uuid-001", 42L, "MONEY", 1)

        val ciphertext = AesGcm.encrypt(key, plaintext, aad)
        assertTrue(ciphertext.size > plaintext.size + 12) // 12-byte IV + 16-byte tag + plain

        // Decrypt successfully
        val decrypted = AesGcm.decrypt(key, ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
        assertEquals("HDFC NetBanking Password #12345", String(decrypted, Charsets.UTF_8))

        // Tamper test: Alter one byte of ciphertext
        val tampered = ciphertext.clone()
        tampered[tampered.size - 5] = (tampered[tampered.size - 5].toInt() xor 0xFF).toByte()

        var caughtException = false
        try {
            AesGcm.decrypt(key, tampered, aad)
        } catch (_: Exception) {
            caughtException = true
        }
        assertTrue("Tampered ciphertext must fail authentication tag check", caughtException)

        // Wrong AAD test: Decrypting with altered AAD must fail
        val alteredAad = AesGcm.makeAad("vault-uuid-001", 42L, "EVERYDAY", 1)
        var caughtAadException = false
        try {
            AesGcm.decrypt(key, ciphertext, alteredAad)
        } catch (_: Exception) {
            caughtAadException = true
        }
        assertTrue("Mismatched AAD must fail authentication tag check", caughtAadException)
    }

    @Test
    fun testShamirSecretSharing2Of3() {
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val k = 2
        val n = 3

        val shares = ShamirSecretSharing.split(secret, k, n)
        assertEquals(3, shares.size)

        // Combining shares (1, 2)
        val recombined12 = ShamirSecretSharing.combine(listOf(shares[0], shares[1]), k)
        assertArrayEquals(secret, recombined12)

        // Combining shares (2, 3)
        val recombined23 = ShamirSecretSharing.combine(listOf(shares[1], shares[2]), k)
        assertArrayEquals(secret, recombined23)

        // Combining shares (1, 3)
        val recombined13 = ShamirSecretSharing.combine(listOf(shares[0], shares[2]), k)
        assertArrayEquals(secret, recombined13)

        // Combining all 3 shares
        val recombinedAll = ShamirSecretSharing.combine(shares, k)
        assertArrayEquals(secret, recombinedAll)
    }

    @Test
    fun testShamirMnemonicEncodingAndDecoding() {
        val secret = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val shares = ShamirSecretSharing.split(secret, 2, 3)

        val mnemonic0 = ShamirSecretSharing.shareToMnemonic(shares[0])
        val parsedShare0 = ShamirSecretSharing.shareFromMnemonic(mnemonic0)
        assertNotNull(parsedShare0)
        assertEquals(shares[0].x, parsedShare0!!.x)
        assertArrayEquals(shares[0].y, parsedShare0.y)

        val mnemonic1 = ShamirSecretSharing.shareToMnemonic(shares[1])
        val parsedShare1 = ShamirSecretSharing.shareFromMnemonic(mnemonic1)
        assertNotNull(parsedShare1)

        val combined = ShamirSecretSharing.combine(listOf(parsedShare0, parsedShare1!!), 2)
        assertArrayEquals(secret, combined)
    }

    @Test
    fun testDicewareAndEntropyEstimation() {
        val passphrase = PassphraseGenerator.generateDiceware(6)
        val words = passphrase.split("-")
        assertEquals(6, words.size)

        val (entropy, label) = PassphraseGenerator.estimateStrength(passphrase)
        assertTrue(entropy >= 70) // 6 words * ~12.9 bits = ~77.5 bits
        assertEquals("Very Strong", label)

        val secretKey = PassphraseGenerator.generateSecretKey128()
        assertEquals(19, secretKey.length) // 4 blocks of 4 hex chars separated by dashes: XXXX-XXXX-XXXX-XXXX
        val clean = secretKey.replace("-", "")
        assertEquals(16, clean.length)
    }

    @Test
    fun testArgon2idKeyDerivation() {
        val pass = "correct-horse-battery-staple".toCharArray()
        val salt = "0123456789abcdef".toByteArray()
        val params = Argon2id.KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1, outputLengthBytes = 32)

        val key1 = Argon2id.derive(pass, salt, params)
        val key2 = Argon2id.derive(pass, salt, params)

        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)
    }
}
