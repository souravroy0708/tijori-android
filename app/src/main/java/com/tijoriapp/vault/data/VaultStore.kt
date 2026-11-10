package com.tijoriapp.vault.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.SealedRow
import com.tijoriapp.vault.core.vault.VaultHeader
import org.json.JSONObject

/**
 * On-disk storage. Every item row is one AES-256-GCM blob: titles, usernames, notes, everything.
 * The only things readable without your keys are: which room a row is in, when it changed, and row ids.
 */
class VaultStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {

    data class AuditEntry(val at: Long, val kind: String, val detail: String)

    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) {
        // Deleted rows are overwritten with zeros instead of lingering in free pages.
        // This PRAGMA returns a row, so it must go through rawQuery: execSQL throws on it
        // ("Queries can be performed using SQLiteDatabase query or rawQuery methods only").
        db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, room TEXT NOT NULL, ct BLOB NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX items_room ON items(room)")
        db.execSQL("CREATE TABLE audit (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 schema is the first release of this format; add real migrations here for future versions.
    }

    // ---------------- meta ----------------

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun setMeta(key: String, value: String?) {
        if (value == null) writableDatabase.delete("meta", "k = ?", arrayOf(key))
        else writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun header(): VaultHeader? = meta(K_HEADER)?.let { runCatching { VaultHeader.fromJson(JSONObject(it)) }.getOrNull() }
    fun saveHeader(h: VaultHeader) = setMeta(K_HEADER, h.toJson().toString())

    // ---------------- items ----------------

    fun rows(room: Room? = null): List<SealedRow> {
        val sql = "SELECT id, room, ct, updated_at FROM items" + if (room != null) " WHERE room = ?" else ""
        return readableDatabase.rawQuery(sql, room?.let { arrayOf(it.name) }).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val r = runCatching { Room.valueOf(c.getString(1)) }.getOrNull() ?: continue
                    add(SealedRow(c.getString(0), r, c.getBlob(2), c.getLong(3)))
                }
            }
        }
    }

    fun count(room: Room): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM items WHERE room = ?", arrayOf(room.name)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun put(row: SealedRow) {
        writableDatabase.insertWithOnConflict("items", null, ContentValues().apply {
            put("id", row.id); put("room", row.room.name); put("ct", row.sealed); put("updated_at", row.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putAll(rows: List<SealedRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try { rows.forEach(::put); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    fun delete(id: String) { writableDatabase.delete("items", "id = ?", arrayOf(id)) }

    /** Replaces everything (restore). Runs in one transaction so a failure leaves the old vault intact. */
    fun replaceAll(header: VaultHeader, rows: List<SealedRow>, extraMeta: Map<String, String?>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("items", null, null)
            db.delete("meta", null, null)
            saveHeader(header)
            extraMeta.forEach { (k, v) -> setMeta(k, v) }
            rows.forEach(::put)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------------- audit (never holds secrets or titles) ----------------

    fun log(kind: String, detail: String = "") {
        writableDatabase.insert("audit", null, ContentValues().apply {
            put("at", System.currentTimeMillis()); put("kind", kind); put("detail", detail.take(80))
        })
        writableDatabase.execSQL("DELETE FROM audit WHERE id NOT IN (SELECT id FROM audit ORDER BY id DESC LIMIT 200)")
    }

    fun audit(limit: Int = 30): List<AuditEntry> =
        readableDatabase.rawQuery("SELECT at, kind, detail FROM audit ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(AuditEntry(c.getLong(0), c.getString(1), c.getString(2))) }
        }

    fun wipe() {
        val db = writableDatabase
        db.delete("items", null, null)
        db.delete("meta", null, null)
        db.delete("audit", null, null)
        db.execSQL("VACUUM")
    }

    companion object {
        const val DB_NAME = "tijori_v2.db"
        const val K_HEADER = "header"
        const val K_SECRET_KEY = "secret_key_device"
        const val K_BIO_MAIN = "bio_main"
        const val K_BIO_MONEY = "bio_money"

        /** The pre-release prototype kept the Secret Key in plain SharedPreferences. Remove all of it. */
        fun purgePrototype(context: Context) {
            context.deleteDatabase("tijori_vault.db")
            context.getSharedPreferences("tijori_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            runCatching { context.deleteSharedPreferences("tijori_prefs") }
        }
    }
}
