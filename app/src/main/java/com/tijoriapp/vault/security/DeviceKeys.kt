package com.tijoriapp.vault.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Android Keystore keys. Key material never leaves the secure hardware (StrongBox, else TEE).
 *
 * - Device key (AES-GCM, no user auth): keeps this phone's copy of your Secret Key, so you only
 *   type the passphrase day to day.
 * - Biometric key (RSA-OAEP, private half needs a Class 3 biometric for EVERY use and is destroyed when
 *   a new fingerprint/face is enrolled). Sealing uses the public half, so turning fingerprint unlock on
 *   needs no extra prompt; every unlock is a fresh, crypto-bound BiometricPrompt.
 */
class DeviceKeys(private val context: Context) {
    private val ks: KeyStore get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val hasStrongBox: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    // ---------------- device key ----------------

    private fun deviceKey(): SecretKey {
        (ks.getKey(DEVICE_ALIAS, null) as? SecretKey)?.let { return it }
        fun gen(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(DEVICE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .apply { if (strongBox) setIsStrongBoxBacked(true) }
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(spec); generateKey() }
        }
        return if (hasStrongBox) try { gen(true) } catch (_: StrongBoxUnavailableException) { gen(false) } else gen(false)
    }

    fun sealWithDevice(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_GCM)
        c.init(Cipher.ENCRYPT_MODE, deviceKey())
        return c.iv + c.doFinal(plain)
    }

    fun openWithDevice(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_GCM)
        c.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    // ---------------- biometric key ----------------

    fun hasBiometricKey(): Boolean = runCatching { ks.containsAlias(BIO_ALIAS) }.getOrDefault(false)

    fun createBiometricKey() {
        deleteBiometricKey()
        fun gen(strongBox: Boolean) {
            val spec = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) // 0 = every single use
                    }
                    if (strongBox) setIsStrongBoxBacked(true)
                }
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).run { initialize(spec); generateKeyPair() }
        }
        if (hasStrongBox) try { gen(true) } catch (_: StrongBoxUnavailableException) { gen(false) } else gen(false)
    }

    fun deleteBiometricKey() { runCatching { ks.deleteEntry(BIO_ALIAS) } }

    /** Seals with the public key: works without a prompt. */
    fun sealForBiometric(plain: ByteArray): ByteArray {
        val pub = ks.getCertificate(BIO_ALIAS).publicKey
        // Copy the public key out of the Keystore provider so this works on all devices without auth.
        val portable = KeyFactory.getInstance(pub.algorithm).generatePublic(X509EncodedKeySpec(pub.encoded))
        val c = Cipher.getInstance(RSA_OAEP)
        c.init(Cipher.ENCRYPT_MODE, portable, OAEP)
        return c.doFinal(plain)
    }

    sealed interface Prepared {
        class Ready(val cipher: Cipher) : Prepared
        /** A new fingerprint/face was added, or screen lock was removed. Fingerprint unlock must be set up again. */
        data object Invalidated : Prepared
        data object Missing : Prepared
    }

    /** A decrypt cipher to hand to BiometricPrompt as its CryptoObject. */
    fun prepareBiometricDecrypt(): Prepared {
        return try {
            val key = ks.getKey(BIO_ALIAS, null) as? PrivateKey ?: return Prepared.Missing
            val c = Cipher.getInstance(RSA_OAEP)
            c.init(Cipher.DECRYPT_MODE, key, OAEP)
            Prepared.Ready(c)
        } catch (_: KeyPermanentlyInvalidatedException) {
            Prepared.Invalidated
        } catch (e: Exception) {
            if (e.javaClass.simpleName.contains("Invalidated")) Prepared.Invalidated else Prepared.Missing
        }
    }

    // ---------------- trust page ----------------

    enum class Level { StrongBox, TEE, Software, Unknown }

    fun securityLevel(): Level = runCatching {
        val key = deviceKey()
        val info = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> Level.StrongBox
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> Level.TEE
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> Level.Software
            else -> Level.Unknown
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) (if (hasStrongBox) Level.StrongBox else Level.TEE) else Level.Software
        }
    }.getOrDefault(Level.Unknown)

    fun wipeAll() {
        runCatching { ks.deleteEntry(DEVICE_ALIAS) }
        deleteBiometricKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_ALIAS = "tijori.device.v2"
        private const val BIO_ALIAS = "tijori.biometric.v2"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        // Keystore supports MGF1 with SHA-1 on every device; SHA-256 digest + SHA-1 MGF is the portable combo.
        private val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
