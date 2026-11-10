package com.tijoriapp.vault.core.vault

import com.tijoriapp.vault.core.crypto.Aead
import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.Bytes
import com.tijoriapp.vault.core.crypto.Hkdf
import com.tijoriapp.vault.core.crypto.wipe
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

/**
 * What's stored about the vault itself. No secrets: only a salt, KDF settings and room keys that are
 * sealed under the Account Unlock Key (which needs BOTH your passphrase and your Secret Key).
 */
data class VaultHeader(
    val version: Int = 2,
    val vaultId: String,
    val salt: ByteArray,
    val kdf: Argon2.Params,
    val wrapped: Map<Room, ByteArray>,
    /** Lets a nominee confirm their rebuilt Family key before decrypting anything. */
    val legacyCheck: ByteArray,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("vaultId", vaultId)
        .put("salt", Bytes.b64(salt))
        .put("kdf", JSONObject().put("alg", "argon2id").put("m", kdf.memoryKiB).put("t", kdf.iterations).put("p", kdf.parallelism))
        .put("wrapped", JSONObject().apply { wrapped.forEach { (r, w) -> put(r.name, Bytes.b64(w)) } })
        .put("legacyCheck", Bytes.b64(legacyCheck))
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(j: JSONObject): VaultHeader {
            require(j.getInt("version") == 2) { "Unsupported vault version" }
            val k = j.getJSONObject("kdf")
            require(k.getString("alg") == "argon2id")
            val w = j.getJSONObject("wrapped")
            return VaultHeader(
                version = 2,
                vaultId = j.getString("vaultId"),
                salt = Bytes.unb64(j.getString("salt")),
                kdf = Argon2.Params(k.getInt("m"), k.getInt("t"), k.getInt("p")),
                wrapped = Room.entries.associateWith { Bytes.unb64(w.getString(it.name)) },
                legacyCheck = Bytes.unb64(j.getString("legacyCheck")),
                createdAt = j.optLong("createdAt"),
            )
        }
    }
}

/** Room keys held in memory while unlocked. Wiped on lock. */
class RoomKeys(private val keys: MutableMap<Room, ByteArray> = mutableMapOf()) {
    operator fun get(room: Room): ByteArray? = keys[room]
    operator fun set(room: Room, key: ByteArray) { keys[room]?.wipe(); keys[room] = key }
    fun has(room: Room) = keys.containsKey(room)
    fun drop(room: Room) { keys.remove(room)?.wipe() }
    fun wipe() { keys.values.forEach { it.wipe() }; keys.clear() }
    fun copyOf(vararg rooms: Room): RoomKeys = RoomKeys(rooms.mapNotNull { r -> keys[r]?.let { r to it.copyOf() } }.toMap().toMutableMap())
}

class WrongPassphraseException : Exception("Passphrase or Secret Key is not right")

object VaultCrypto {
    /**
     * Default for new vaults: 64 MiB, 2 passes (OWASP's minimum is 19 MiB / 2 passes).
     * The settings are stored in the header, so they can be raised later without breaking old vaults.
     */
    val DEFAULT_KDF = Argon2.Params(memoryKiB = 65_536, iterations = 2, parallelism = 1)
    /** Fallback for very low-RAM phones (ActivityManager.isLowRamDevice). Still above OWASP's floor. */
    val LOW_RAM_KDF = Argon2.Params(memoryKiB = 32_768, iterations = 4, parallelism = 1)

    /** Trim, collapse runs of spaces, Unicode NFKC: so the same words always give the same key. */
    fun normalize(passphrase: String): ByteArray =
        Normalizer.normalize(passphrase.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)

    /**
     * Account Unlock Key = HKDF(Argon2id(passphrase, salt), salt = SecretKey).
     * A stolen backup can't be brute-forced: the attacker also needs the 128-bit Secret Key.
     */
    fun unlockKey(passphrase: String, secretKey: ByteArray, header: VaultHeader): ByteArray =
        unlockKey(passphrase, secretKey, header.vaultId, header.salt, header.kdf)

    private fun unlockKey(passphrase: String, secretKey: ByteArray, vaultId: String, salt: ByteArray, kdf: Argon2.Params): ByteArray {
        val pw = normalize(passphrase)
        val stretched = try { Argon2.derive(pw, salt, kdf) } finally { pw.wipe() }
        try { return Hkdf.derive(stretched, secretKey, "tijori/v2/auk/$vaultId") } finally { stretched.wipe() }
    }

    fun wrapAad(vaultId: String, room: Room) = "tijori/v2/wrap/$vaultId/${room.name}".toByteArray()
    fun itemAad(vaultId: String, itemId: String, room: Room) = "tijori/v2/item/$vaultId/$itemId/${room.name}".toByteArray()
    fun legacyCheck(legacyKey: ByteArray): ByteArray = Hkdf.derive(legacyKey, null, "tijori/v2/legacy-check", 16)

    data class Created(val header: VaultHeader, val keys: RoomKeys)

    fun create(passphrase: String, secretKey: ByteArray, kdf: Argon2.Params = DEFAULT_KDF): Created {
        val vaultId = UUID.randomUUID().toString()
        val salt = Bytes.random(16)
        val auk = unlockKey(passphrase, secretKey, vaultId, salt, kdf)
        try {
            val keys = RoomKeys()
            Room.entries.forEach { keys[it] = Bytes.random(32) }
            val wrapped = Room.entries.associateWith { Aead.seal(auk, keys[it]!!, wrapAad(vaultId, it)) }
            val header = VaultHeader(
                vaultId = vaultId, salt = salt, kdf = kdf, wrapped = wrapped,
                legacyCheck = legacyCheck(keys[Room.LEGACY]!!), createdAt = System.currentTimeMillis(),
            )
            return Created(header, keys)
        } finally { auk.wipe() }
    }

    /** Opens only the [rooms] asked for. The Money key stays sealed until the Money room is opened. */
    fun unlock(header: VaultHeader, passphrase: String, secretKey: ByteArray, rooms: Set<Room>): RoomKeys {
        val auk = unlockKey(passphrase, secretKey, header)
        try { return openWith(header, auk, rooms) } finally { auk.wipe() }
    }

    fun openWith(header: VaultHeader, auk: ByteArray, rooms: Set<Room>): RoomKeys {
        val keys = RoomKeys()
        try {
            rooms.forEach { r -> keys[r] = Aead.open(auk, header.wrapped.getValue(r), wrapAad(header.vaultId, r)) }
        } catch (e: javax.crypto.AEADBadTagException) {
            keys.wipe(); throw WrongPassphraseException()
        }
        return keys
    }

    /** New passphrase: fresh salt, same room keys, so items don't need re-encrypting. */
    fun changePassphrase(header: VaultHeader, current: String, next: String, secretKey: ByteArray, kdf: Argon2.Params = header.kdf): VaultHeader {
        val all = unlock(header, current, secretKey, Room.entries.toSet())
        try { return rewrap(header, all, next, secretKey, kdf) } finally { all.wipe() }
    }

    fun rewrap(header: VaultHeader, all: RoomKeys, passphrase: String, secretKey: ByteArray, kdf: Argon2.Params = header.kdf): VaultHeader {
        val salt = Bytes.random(16)
        val auk = unlockKey(passphrase, secretKey, header.vaultId, salt, kdf)
        try {
            val wrapped = Room.entries.associateWith { r ->
                Aead.seal(auk, requireNotNull(all[r]) { "All room keys needed" }, wrapAad(header.vaultId, r))
            }
            return header.copy(salt = salt, kdf = kdf, wrapped = wrapped, legacyCheck = legacyCheck(all[Room.LEGACY]!!))
        } finally { auk.wipe() }
    }

    fun seal(key: ByteArray, vaultId: String, item: VaultItem): ByteArray {
        val plain = Codec.encode(item)
        try { return Aead.seal(key, plain, itemAad(vaultId, item.id, item.room)) } finally { plain.wipe() }
    }

    fun open(key: ByteArray, vaultId: String, id: String, room: Room, sealed: ByteArray): VaultItem {
        val plain = Aead.open(key, sealed, itemAad(vaultId, id, room))
        try { return Codec.decode(id, room, plain) } finally { plain.wipe() }
    }
}
