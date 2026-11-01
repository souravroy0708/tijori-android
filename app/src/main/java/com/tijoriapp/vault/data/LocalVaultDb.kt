package com.tijoriapp.vault.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tijoriapp.vault.crypto.AesGcm
import com.tijoriapp.vault.crypto.Argon2id
import com.tijoriapp.vault.crypto.Hkdf
import com.tijoriapp.vault.crypto.PassphraseGenerator
import com.tijoriapp.vault.crypto.ShamirSecretSharing
import java.security.SecureRandom
import java.util.UUID

/**
 * Encrypted on-device SQLite database (`tijori_vault.db`) implementing the Two-Layer Security model (Spec Page 6):
 * - Layer 1: Vault-level table structures, metadata, and timestamps.
 * - Layer 2: Per-field AES-256-GCM encryption with Room Keys (Everyday, Money, Legacy) binding `vaultId:itemId:room`.
 * - Memory hygiene: Money room key dropped after 60s idle.
 */
class LocalVaultDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "tijori_vault.db", null, 1) {

    companion object {
        @Volatile private var instance: LocalVaultDb? = null

        fun get(context: Context): LocalVaultDb =
            instance ?: synchronized(this) { instance ?: LocalVaultDb(context).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE vault_header (
                id INTEGER PRIMARY KEY,
                format_version INTEGER NOT NULL DEFAULT 1,
                vault_id TEXT NOT NULL,
                salt_hex TEXT NOT NULL,
                kdf_memory_kib INTEGER NOT NULL DEFAULT 32768,
                kdf_iterations INTEGER NOT NULL DEFAULT 3,
                wrapped_vault_key_hex TEXT NOT NULL,
                wrapped_legacy_key_hex TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                room TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                rotate_every_days INTEGER NOT NULL DEFAULT 90,
                expires_on TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_items_room ON items(room)")

        db.execSQL(
            """
            CREATE TABLE fields (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                custom_label TEXT NOT NULL DEFAULT '',
                value_cipher_hex TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_fields_item ON fields(item_id)")

        db.execSQL(
            """
            CREATE TABLE app_bindings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                signer_digest TEXT NOT NULL DEFAULT '',
                web_domain TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                epoch_millis INTEGER NOT NULL,
                kind TEXT NOT NULL,
                item_id INTEGER,
                detail TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_audit_time ON audit_events(epoch_millis DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    // ---------- Header ----------

    fun getHeader(): VaultHeader? {
        readableDatabase.rawQuery("SELECT format_version, vault_id, salt_hex, kdf_memory_kib, kdf_iterations, wrapped_vault_key_hex, wrapped_legacy_key_hex FROM vault_header WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) {
                return VaultHeader(
                    formatVersion = c.getInt(0),
                    vaultId = c.getString(1),
                    saltHex = c.getString(2),
                    kdfMemoryKiB = c.getInt(3),
                    kdfIterations = c.getInt(4),
                    wrappedVaultKeyHex = c.getString(5),
                    wrappedLegacyKeyHex = c.getString(6),
                )
            }
        }
        return null
    }

    @Synchronized
    fun saveHeader(h: VaultHeader) {
        writableDatabase.insertWithOnConflict(
            "vault_header",
            null,
            ContentValues().apply {
                put("id", 1)
                put("format_version", h.formatVersion)
                put("vault_id", h.vaultId)
                put("salt_hex", h.saltHex)
                put("kdf_memory_kib", h.kdfMemoryKiB)
                put("kdf_iterations", h.kdfIterations)
                put("wrapped_vault_key_hex", h.wrappedVaultKeyHex)
                put("wrapped_legacy_key_hex", h.wrappedLegacyKeyHex)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // ---------- Items & Fields ----------

    fun allItems(room: RoomType? = null): List<VaultItem> {
        val query = if (room != null) "SELECT id, room, type, title, tags, created_at, updated_at, rotate_every_days, expires_on FROM items WHERE room = ? ORDER BY title COLLATE NOCASE"
        else "SELECT id, room, type, title, tags, created_at, updated_at, rotate_every_days, expires_on FROM items ORDER BY title COLLATE NOCASE"
        val args = if (room != null) arrayOf(room.name) else null

        val list = ArrayList<VaultItem>()
        readableDatabase.rawQuery(query, args).use { c ->
            while (c.moveToNext()) {
                val itemId = c.getLong(0)
                list.add(
                    VaultItem(
                        id = itemId,
                        room = runCatching { RoomType.valueOf(c.getString(1)) }.getOrDefault(RoomType.EVERYDAY),
                        type = runCatching { ItemType.valueOf(c.getString(2)) }.getOrDefault(ItemType.CUSTOM),
                        title = c.getString(3),
                        tags = c.getString(4),
                        createdAt = c.getLong(5),
                        updatedAt = c.getLong(6),
                        rotateEveryDays = c.getInt(7),
                        expiresOn = c.getString(8),
                        fields = getFieldsForItem(itemId),
                    )
                )
            }
        }
        return list
    }

    fun getItem(itemId: Long): VaultItem? {
        readableDatabase.rawQuery("SELECT id, room, type, title, tags, created_at, updated_at, rotate_every_days, expires_on FROM items WHERE id = ?", arrayOf(itemId.toString())).use { c ->
            if (c.moveToFirst()) {
                return VaultItem(
                    id = c.getLong(0),
                    room = runCatching { RoomType.valueOf(c.getString(1)) }.getOrDefault(RoomType.EVERYDAY),
                    type = runCatching { ItemType.valueOf(c.getString(2)) }.getOrDefault(ItemType.CUSTOM),
                    title = c.getString(3),
                    tags = c.getString(4),
                    createdAt = c.getLong(5),
                    updatedAt = c.getLong(6),
                    rotateEveryDays = c.getInt(7),
                    expiresOn = c.getString(8),
                    fields = getFieldsForItem(itemId),
                )
            }
        }
        return null
    }

    fun getFieldsForItem(itemId: Long): List<VaultField> {
        val list = ArrayList<VaultField>()
        readableDatabase.rawQuery("SELECT id, item_id, kind, custom_label, value_cipher_hex FROM fields WHERE item_id = ? ORDER BY id", arrayOf(itemId.toString())).use { c ->
            while (c.moveToNext()) {
                list.add(
                    VaultField(
                        id = c.getLong(0),
                        itemId = c.getLong(1),
                        kind = runCatching { FieldKind.valueOf(c.getString(2)) }.getOrDefault(FieldKind.PASSWORD),
                        customLabel = c.getString(3),
                        valueCipherHex = c.getString(4),
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun saveItem(item: VaultItem): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("room", item.room.name)
            put("type", item.type.name)
            put("title", item.title.trim())
            put("tags", item.tags.trim())
            put("updated_at", System.currentTimeMillis())
            put("rotate_every_days", item.rotateEveryDays)
            put("expires_on", item.expiresOn)
        }
        val itemId = if (item.id > 0L) {
            db.update("items", cv, "id = ?", arrayOf(item.id.toString()))
            item.id
        } else {
            cv.put("created_at", System.currentTimeMillis())
            db.insert("items", null, cv)
        }

        // Replace fields
        db.delete("fields", "item_id = ?", arrayOf(itemId.toString()))
        item.fields.forEach { f ->
            db.insert("fields", null, ContentValues().apply {
                put("item_id", itemId)
                put("kind", f.kind.name)
                put("custom_label", f.customLabel.trim())
                put("value_cipher_hex", f.valueCipherHex)
            })
        }
        return itemId
    }

    @Synchronized
    fun deleteItem(itemId: Long) {
        writableDatabase.delete("items", "id = ?", arrayOf(itemId.toString()))
        writableDatabase.delete("fields", "item_id = ?", arrayOf(itemId.toString()))
        writableDatabase.delete("app_bindings", "item_id = ?", arrayOf(itemId.toString()))
    }

    // ---------- Audit Events (Spec Page 8: "Local log; never holds secrets") ----------

    @Synchronized
    fun logEvent(kind: String, itemId: Long? = null, detail: String = "") {
        writableDatabase.insert("audit_events", null, ContentValues().apply {
            put("epoch_millis", System.currentTimeMillis())
            put("kind", kind)
            if (itemId != null) put("item_id", itemId)
            put("detail", detail.take(120))
        })
    }

    fun recentUnlockLogs(limit: Int = 20): List<AuditEvent> {
        val list = ArrayList<AuditEvent>()
        readableDatabase.rawQuery("SELECT id, epoch_millis, kind, item_id, detail FROM audit_events ORDER BY epoch_millis DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AuditEvent(
                        id = c.getLong(0),
                        epochMillis = c.getLong(1),
                        kind = c.getString(2),
                        itemId = if (c.isNull(3)) null else c.getLong(3),
                        detail = c.getString(4),
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun wipeAll() {
        for (t in listOf("vault_header", "items", "fields", "app_bindings", "audit_events")) {
            writableDatabase.delete(t, null, null)
        }
    }
}
