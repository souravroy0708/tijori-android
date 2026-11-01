package com.tijoriapp.vault.data

import org.json.JSONArray
import org.json.JSONObject

enum class RoomType(val labelEn: String, val labelHi: String, val icon: String) {
    EVERYDAY("Everyday", "रोज़मर्रा", "📱"),
    MONEY("Money", "बैंक और धन", "💳"),
    LEGACY("Legacy (Family)", "परिवार और वसीयत", "🏛️"),
}

enum class ItemType(val labelEn: String, val labelHi: String, val icon: String, val defaultRoom: RoomType) {
    NET_BANKING("Net Banking", "नेट बैंकिंग", "🏦", RoomType.MONEY),
    PPF("PPF Account", "PPF खाता", "📈", RoomType.MONEY),
    EPF_UAN("EPF / UAN", "PF / UAN पोर्टल", "📜", RoomType.MONEY),
    TAX_PORTAL("Income Tax Portal", "इनकम टैक्स पोर्टल", "🧾", RoomType.MONEY),
    CARD("Debit / Credit Card", "डेबिट / क्रेडिट कार्ड", "💳", RoomType.MONEY),
    INSURANCE("Insurance Policy", "बीमा पॉलिसी", "🛡️", RoomType.LEGACY),
    LOCKER("Bank Locker / Will", "बैंक लॉकर व निर्देश", "🗝️", RoomType.LEGACY),
    EMAIL("Email Account", "ईमेल", "✉️", RoomType.EVERYDAY),
    SOCIAL("Social & Apps", "सोशल व ऐप्स", "💬", RoomType.EVERYDAY),
    WIFI("Wi-Fi Router", "वाई-फ़ाई", "📶", RoomType.EVERYDAY),
    NOTE("Secure Note", "सुरक्षित नोट", "📝", RoomType.EVERYDAY),
    CUSTOM("Custom Login", "कस्टम लॉगिन", "🔐", RoomType.EVERYDAY),
}

enum class FieldKind(val labelEn: String, val isSecret: Boolean) {
    USERNAME("Username / Login ID", false),
    PASSWORD("Password", true),
    URL("Website / Login URL", false),
    PIN_HINT("PIN Hint (Only you know)", false), // Spec Page 4: default field is a PIN hint
    FULL_PIN("Full PIN / CVV", true),           // Behind Advanced warning in Money room
    ACCOUNT_NUMBER("Account Number", false),
    CUSTOMER_ID("Customer ID", false),
    IFSC("IFSC Code", false),
    PAN("PAN Number", false),
    UAN("12-Digit UAN", false),
    POLICY_NUMBER("Policy Number", false),
    NOTE("Secure Note", true),
    TOTP_SECRET("TOTP Authenticator Secret", true),
}

/** Entity 1: VaultHeader (Spec Page 8) */
data class VaultHeader(
    val formatVersion: Int = 1,
    val vaultId: String,
    val saltHex: String,
    val kdfMemoryKiB: Int = 32768,
    val kdfIterations: Int = 3,
    val wrappedVaultKeyHex: String,
    val wrappedLegacyKeyHex: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("vaultId", vaultId)
        .put("saltHex", saltHex)
        .put("kdfMemoryKiB", kdfMemoryKiB)
        .put("kdfIterations", kdfIterations)
        .put("wrappedVaultKeyHex", wrappedVaultKeyHex)
        .put("wrappedLegacyKeyHex", wrappedLegacyKeyHex)

    companion object {
        fun fromJson(j: JSONObject) = VaultHeader(
            formatVersion = j.optInt("formatVersion", 1),
            vaultId = j.optString("vaultId"),
            saltHex = j.optString("saltHex"),
            kdfMemoryKiB = j.optInt("kdfMemoryKiB", 32768),
            kdfIterations = j.optInt("kdfIterations", 3),
            wrappedVaultKeyHex = j.optString("wrappedVaultKeyHex"),
            wrappedLegacyKeyHex = j.optString("wrappedLegacyKeyHex"),
        )
    }
}

/** Entity 2: Item (Spec Page 8) */
data class VaultItem(
    val id: Long = 0L,
    val room: RoomType = RoomType.EVERYDAY,
    val type: ItemType = ItemType.CUSTOM,
    val title: String,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val rotateEveryDays: Int = 90,
    val expiresOn: String = "", // e.g. "2028-12" or "2026-11-30"
    val fields: List<VaultField> = emptyList(),
) {
    fun getField(kind: FieldKind): VaultField? = fields.firstOrNull { it.kind == kind }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("room", room.name)
        .put("type", type.name)
        .put("title", title)
        .put("tags", tags)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("rotateEveryDays", rotateEveryDays)
        .put("expiresOn", expiresOn)
        .put("fields", JSONArray(fields.map { it.toJson() }))

    companion object {
        fun fromJson(j: JSONObject): VaultItem {
            val fArr = j.optJSONArray("fields") ?: JSONArray()
            val fList = (0 until fArr.length()).map { VaultField.fromJson(fArr.getJSONObject(it)) }
            return VaultItem(
                id = j.optLong("id"),
                room = runCatching { RoomType.valueOf(j.optString("room")) }.getOrDefault(RoomType.EVERYDAY),
                type = runCatching { ItemType.valueOf(j.optString("type")) }.getOrDefault(ItemType.CUSTOM),
                title = j.optString("title"),
                tags = j.optString("tags", ""),
                createdAt = j.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
                rotateEveryDays = j.optInt("rotateEveryDays", 90),
                expiresOn = j.optString("expiresOn", ""),
                fields = fList,
            )
        }
    }
}

/** Entity 3: Field (Spec Page 8: Per-field AES-256-GCM encryption with AAD binding) */
data class VaultField(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val kind: FieldKind,
    val customLabel: String = "",
    val valueCipherHex: String = "", // Encrypted with Room Key + AAD
    val clearTextTransient: String = "", // In-memory only, never saved unencrypted
) {
    val displayLabel: String
        get() = customLabel.ifBlank { kind.labelEn }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("itemId", itemId)
        .put("kind", kind.name)
        .put("customLabel", customLabel)
        .put("valueCipherHex", valueCipherHex)

    companion object {
        fun fromJson(j: JSONObject) = VaultField(
            id = j.optLong("id"),
            itemId = j.optLong("itemId"),
            kind = runCatching { FieldKind.valueOf(j.optString("kind")) }.getOrDefault(FieldKind.PASSWORD),
            customLabel = j.optString("customLabel", ""),
            valueCipherHex = j.optString("valueCipherHex", ""),
        )
    }
}

/** Entity 4: AppBinding for Autofill (Spec Page 8) */
data class AppBinding(
    val id: Long = 0L,
    val itemId: Long,
    val packageName: String,
    val signerDigest: String = "",
    val webDomain: String = "",
)

/** Entity 5: AuditEvent (Spec Page 8: "Local log; never holds secrets") */
data class AuditEvent(
    val id: Long = 0L,
    val epochMillis: Long = System.currentTimeMillis(),
    val kind: String, // UNLOCK_PASS, UNLOCK_BIO, UNLOCK_FAIL, REVEAL, COPY, EXPORT, NOMINEE_DRILL
    val itemId: Long? = null,
    val detail: String = "",
)

/** Entity 6: Settings (Spec Page 8) */
data class VaultSettings(
    val autoLockSeconds: Int = 45, // Spec: 30-60s
    val clipboardTimeoutSeconds: Int = 30, // Spec: 30s
    val language: String = "en",
    val biometricEnabled: Boolean = false,
    val wipeAfterAttempts: Int = 0, // 0 = disabled (Spec Page 6)
)
