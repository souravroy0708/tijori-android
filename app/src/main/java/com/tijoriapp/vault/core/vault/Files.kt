package com.tijoriapp.vault.core.vault

import com.tijoriapp.vault.core.crypto.Bytes
import org.json.JSONArray
import org.json.JSONObject

/** One encrypted row, exactly as stored on the phone. */
data class SealedRow(val id: String, val room: Room, val sealed: ByteArray, val updatedAt: Long)

/**
 * The full backup (.tijori). It's a copy of the already-encrypted rows plus the header, so making one
 * never decrypts anything. Restoring needs this file + Secret Key + passphrase.
 */
object BackupFile {
    const val FORMAT = "tijori.backup"

    fun write(header: VaultHeader, rows: List<SealedRow>): ByteArray = JSONObject()
        .put("format", FORMAT)
        .put("version", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("header", header.toJson())
        .put("items", JSONArray().apply {
            rows.forEach { put(JSONObject().put("id", it.id).put("room", it.room.name).put("ct", Bytes.b64(it.sealed)).put("updated", it.updatedAt)) }
        })
        .toString().toByteArray(Charsets.UTF_8)

    data class Parsed(val header: VaultHeader, val rows: List<SealedRow>, val exportedAt: Long)

    /** Throws IllegalArgumentException with a friendly message when the file isn't a Tijori backup. */
    fun read(bytes: ByteArray): Parsed {
        val j = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
            ?: throw IllegalArgumentException("This file isn't a Tijori backup")
        require(j.optString("format") == FORMAT) {
            if (j.optString("format") == LegacyPackage.FORMAT) "This is a Family package. Open it from \"I'm a nominee\"." else "This file isn't a Tijori backup"
        }
        require(j.optInt("version") == 2) { "This backup was made by a newer Tijori. Please update the app." }
        val header = VaultHeader.fromJson(j.getJSONObject("header"))
        return Parsed(header, readRows(j.getJSONArray("items")), j.optLong("exportedAt"))
    }

    internal fun readRows(a: JSONArray): List<SealedRow> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        SealedRow(o.getString("id"), Room.valueOf(o.optString("room", Room.LEGACY.name)), Bytes.unb64(o.getString("ct")), o.optLong("updated"))
    }
}

/**
 * The Family package (.tijori-family): only the Family room's encrypted items. Useless on its own.
 * With any k nominee cards, a nominee can read it in Tijori on any phone, offline.
 */
object LegacyPackage {
    const val FORMAT = "tijori.family"

    fun write(header: VaultHeader, ownerName: String, k: Int, n: Int, rows: List<SealedRow>): ByteArray = JSONObject()
        .put("format", FORMAT)
        .put("version", 2)
        .put("vaultId", header.vaultId)
        .put("owner", ownerName)
        .put("k", k).put("n", n)
        .put("check", Bytes.b64(header.legacyCheck))
        .put("exportedAt", System.currentTimeMillis())
        .put("items", JSONArray().apply {
            rows.filter { it.room == Room.LEGACY }.forEach {
                put(JSONObject().put("id", it.id).put("room", Room.LEGACY.name).put("ct", Bytes.b64(it.sealed)).put("updated", it.updatedAt))
            }
        })
        .toString().toByteArray(Charsets.UTF_8)

    data class Parsed(val vaultId: String, val owner: String, val k: Int, val n: Int, val check: ByteArray, val rows: List<SealedRow>, val exportedAt: Long)

    fun read(bytes: ByteArray): Parsed {
        val j = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
            ?: throw IllegalArgumentException("This file isn't a Tijori Family package")
        require(j.optString("format") == FORMAT) { "This file isn't a Tijori Family package" }
        return Parsed(
            j.getString("vaultId"), j.optString("owner"), j.optInt("k", 2), j.optInt("n", 3),
            Bytes.unb64(j.getString("check")), BackupFile.readRows(j.getJSONArray("items")), j.optLong("exportedAt"),
        )
    }

    /** Rebuilds the Family key from cards and opens the package. Returns null when the cards don't fit. */
    fun open(pkg: Parsed, legacyKey: ByteArray): List<VaultItem>? {
        if (!Bytes.equal(VaultCrypto.legacyCheck(legacyKey), pkg.check)) return null
        return pkg.rows.map { VaultCrypto.open(legacyKey, pkg.vaultId, it.id, Room.LEGACY, it.sealed) }
    }
}
