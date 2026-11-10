package com.tijoriapp.vault.core.vault

import org.json.JSONArray
import org.json.JSONObject

/** Plaintext item <-> JSON. This JSON only ever exists in memory; on disk it is AES-GCM sealed. */
object Codec {
    fun encode(item: VaultItem): ByteArray = JSONObject()
        .put("v", 1)
        .put("type", item.type.name)
        .put("title", item.title)
        .put("notes", item.notes)
        .put("fav", item.favorite)
        .put("apps", JSONArray(item.apps))
        .put("created", item.createdAt)
        .put("updated", item.updatedAt)
        .put("pwChanged", item.passwordChangedAt)
        .put("used", item.lastUsedAt)
        .put("fields", JSONArray().apply {
            item.fields.forEach { put(JSONObject().put("id", it.id).put("l", it.label).put("k", it.kind.name).put("v", it.value)) }
        })
        .toString().toByteArray(Charsets.UTF_8)

    fun decode(id: String, room: Room, bytes: ByteArray): VaultItem {
        val j = JSONObject(String(bytes, Charsets.UTF_8))
        val fa = j.optJSONArray("fields") ?: JSONArray()
        val fields = (0 until fa.length()).map { i ->
            val f = fa.getJSONObject(i)
            Field(
                label = f.optString("l"),
                kind = runCatching { FieldKind.valueOf(f.optString("k")) }.getOrDefault(FieldKind.TEXT),
                value = f.optString("v"),
                id = f.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
            )
        }
        val apps = j.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return VaultItem(
            id = id,
            room = room,
            type = runCatching { ItemType.valueOf(j.optString("type")) }.getOrDefault(ItemType.CUSTOM),
            title = j.optString("title"),
            fields = fields,
            notes = j.optString("notes"),
            favorite = j.optBoolean("fav"),
            apps = apps,
            createdAt = j.optLong("created"),
            updatedAt = j.optLong("updated"),
            passwordChangedAt = j.optLong("pwChanged", j.optLong("created")),
            lastUsedAt = j.optLong("used"),
        )
    }
}
