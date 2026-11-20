package com.tijoriapp.vault.core.vault

import com.tijoriapp.vault.core.crypto.Aead
import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.Bytes
import com.tijoriapp.vault.core.crypto.Hkdf
import com.tijoriapp.vault.core.crypto.wipe
import org.json.JSONObject

/**
 * "Easy restore": the vault's Secret Key, sealed with a key stretched from the passphrase ONLY.
 *
 * Why: after an uninstall or on a new phone, most people have their Drive backup but not their printed
 * Emergency Kit. With this envelope inside the backup, the passphrase alone brings the vault back.
 *
 * The trade-off is stated honestly in the app: whoever gets the backup file can now try to guess the
 * passphrase (instead of needing the passphrase AND a 128-bit Secret Key). That's why the envelope uses
 * a heavier Argon2id setting than unlock, and Tijori only accepts Strong passphrases (60+ bits).
 * Opt-out keeps the original two-secret design.
 */
data class RecoveryEnvelope(
    val salt: ByteArray,
    val kdf: Argon2.Params,
    /** AES-256-GCM(recovery key, Secret Key), bound to the vault id. */
    val sealedSecretKey: ByteArray,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("salt", Bytes.b64(salt))
        .put("kdf", JSONObject().put("alg", "argon2id").put("m", kdf.memoryKiB).put("t", kdf.iterations).put("p", kdf.parallelism))
        .put("sk", Bytes.b64(sealedSecretKey))

    companion object {
        fun fromJson(j: JSONObject): RecoveryEnvelope {
            require(j.optInt("v") == 1) { "Unsupported recovery version" }
            val k = j.getJSONObject("kdf")
            require(k.getString("alg") == "argon2id")
            return RecoveryEnvelope(Bytes.unb64(j.getString("salt")), Argon2.Params(k.getInt("m"), k.getInt("t"), k.getInt("p")), Bytes.unb64(j.getString("sk")))
        }
    }
}

object Recovery {
    /** One more pass than unlock: a stolen cloud copy is the attack this slows down. */
    fun kdfFor(unlockKdf: Argon2.Params): Argon2.Params = unlockKdf.copy(iterations = unlockKdf.iterations + 1)

    private fun aad(vaultId: String) = "tijori/v2/recovery/$vaultId".toByteArray()

    private fun recoveryKey(passphrase: String, vaultId: String, salt: ByteArray, kdf: Argon2.Params): ByteArray {
        val pw = VaultCrypto.normalize(passphrase)
        val stretched = try { Argon2.derive(pw, salt, kdf) } finally { pw.wipe() }
        try { return Hkdf.derive(stretched, null, "tijori/v2/recovery-key/$vaultId") } finally { stretched.wipe() }
    }

    fun seal(passphrase: String, secretKey: ByteArray, vaultId: String, kdf: Argon2.Params): RecoveryEnvelope {
        val salt = Bytes.random(16)
        val rk = recoveryKey(passphrase, vaultId, salt, kdf)
        try { return RecoveryEnvelope(salt, kdf, Aead.seal(rk, secretKey, aad(vaultId))) } finally { rk.wipe() }
    }

    /** The Secret Key, or null when the passphrase is wrong. */
    fun open(envelope: RecoveryEnvelope, passphrase: String, vaultId: String): ByteArray? {
        val rk = recoveryKey(passphrase, vaultId, envelope.salt, envelope.kdf)
        return try { Aead.open(rk, envelope.sealedSecretKey, aad(vaultId)) } catch (_: javax.crypto.AEADBadTagException) { null } finally { rk.wipe() }
    }
}
