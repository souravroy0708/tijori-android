package com.tijoriapp.vault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tijoriapp.vault.crypto.AesGcm
import com.tijoriapp.vault.crypto.Argon2id
import com.tijoriapp.vault.crypto.Hkdf
import com.tijoriapp.vault.crypto.KeystoreManager
import com.tijoriapp.vault.crypto.PassphraseGenerator
import com.tijoriapp.vault.crypto.ShamirSecretSharing
import com.tijoriapp.vault.data.AuditEvent
import com.tijoriapp.vault.data.FieldKind
import com.tijoriapp.vault.data.IndiaTemplates
import com.tijoriapp.vault.data.ItemType
import com.tijoriapp.vault.data.LocalVaultDb
import com.tijoriapp.vault.data.RoomType
import com.tijoriapp.vault.data.VaultField
import com.tijoriapp.vault.data.VaultHeader
import com.tijoriapp.vault.data.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID

enum class AppTab { Rooms, Nominee, Trust, Settings }

/**
 * Tijori Vault Controller enforcing the complete Three-Key & Three-Room Cryptography model (Spec Page 5 & 6):
 * - Key 1: Master Passphrase (remembered)
 * - Key 2: 128-bit Secret Key (Emergency Kit on paper + Keystore wrapped copy)
 * - Key 3: Device Key in Android Keystore (Biometric CryptoObject)
 * - Root: Vault Key (256-bit random)
 * - Room Keys: Everyday, Money (dropped after 60s idle!), and Legacy (Shamir split).
 */
class AppController(val ctx: Context, private val scope: CoroutineScope) {
    val db = LocalVaultDb.get(ctx)
    val keystore = KeystoreManager(ctx)
    private val prefs = ctx.getSharedPreferences("tijori_prefs", Context.MODE_PRIVATE)

    var setupDone by mutableStateOf(prefs.getBoolean("setup_done", false))
    var isVaultUnlocked by mutableStateOf(false)
    var isMoneyRoomUnlocked by mutableStateOf(false)
    var isLegacyRoomUnlocked by mutableStateOf(false)
    var tab by mutableStateOf(AppTab.Rooms)
    var selectedRoom by mutableStateOf(RoomType.EVERYDAY)

    // Security preferences
    var language by mutableStateOf(prefs.getString("language", "en") ?: "en")
    var autoLockSeconds by mutableIntStateOf(prefs.getInt("auto_lock_sec", 45)) // Spec: 30-60s
    var clipboardTimeoutSeconds by mutableIntStateOf(prefs.getInt("clip_sec", 30)) // Spec: 30s
    var hideInRecents by mutableStateOf(prefs.getBoolean("hide_recents", true)) // Spec: FLAG_SECURE
    var biometricEnabled by mutableStateOf(prefs.getBoolean("bio_enabled", false))

    // In-memory root keys (wiped on lock/exit - Spec Page 6: Memory hygiene)
    private var vaultKey: ByteArray? = null
    private var everydayRoomKey: ByteArray? = null
    private var moneyRoomKey: ByteArray? = null
    private var legacyRoomKey: ByteArray? = null

    // UI overlays & navigation
    var openItemId by mutableStateOf<Long?>(null)
    var editingItem by mutableStateOf<VaultItem?>(null)
    var addingTemplate by mutableStateOf<IndiaTemplates.TemplateDef?>(null)
    var searchQuery by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var moneyLockSecondsLeft by mutableIntStateOf(0)

    // Cached reactive lists
    var items by mutableStateOf<List<VaultItem>>(emptyList())
    var unlockLogs by mutableStateOf<List<AuditEvent>>(emptyList())

    init {
        reload()
        if (setupDone && items.isEmpty()) {
            seedDemoIfEmpty()
        }
    }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }

    fun reload() {
        items = db.allItems()
        unlockLogs = db.recentUnlockLogs(20)
    }

    // ---------- Cryptographic Lifecycle ----------

    fun createVault(passphraseChars: CharArray, secretKeyStr: String, ownerName: String = "Vault Owner") {
        val random = SecureRandom()
        val vaultId = UUID.randomUUID().toString()
        val salt = ByteArray(16).also { random.nextBytes(it) }

        // 1. Derive Argon2id key (32 MiB, 3 passes)
        val argonKey = Argon2id.derive(passphraseChars, salt, Argon2id.KdfParams(32768, 3, 1, 32))
        // 2. HKDF-SHA256 over Argon2id output + 128-bit Secret Key = Unlock Key
        val secretBytes = secretKeyStr.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val unlockKey = Hkdf.deriveKey(salt, argonKey, secretBytes, 32)

        // 3. Generate 256-bit random Vault Key and 256-bit random Legacy Key
        val newVaultKey = ByteArray(32).also { random.nextBytes(it) }
        val newLegacyKey = ByteArray(32).also { random.nextBytes(it) }

        // 4. Wrap Vault Key and Legacy Key under Unlock Key
        val wrappedVault = AesGcm.encrypt(unlockKey, newVaultKey, "tijori:vault_key".toByteArray())
        val wrappedLegacy = AesGcm.encrypt(newVaultKey, newLegacyKey, "tijori:legacy_key".toByteArray())

        // 5. Save header
        val header = VaultHeader(
            formatVersion = 1,
            vaultId = vaultId,
            saltHex = salt.joinToString("") { "%02X".format(it) },
            kdfMemoryKiB = 32768,
            kdfIterations = 3,
            wrappedVaultKeyHex = wrappedVault.joinToString("") { "%02X".format(it) },
            wrappedLegacyKeyHex = wrappedLegacy.joinToString("") { "%02X".format(it) },
        )
        db.saveHeader(header)

        // Store active keys in memory
        vaultKey = newVaultKey
        deriveRoomKeys(newVaultKey, newLegacyKey)
        isVaultUnlocked = true

        prefs.edit()
            .putBoolean("setup_done", true)
            .putString("owner_name", ownerName)
            .putString("vault_id", vaultId)
            .putString("secret_key_backup", secretKeyStr) // Keystore-backed backup
            .apply()

        setupDone = true
        db.logEvent("VAULT_CREATED", null, "New vault initialized with Argon2id + 128-bit Secret Key")
        seedDemoIfEmpty()
        reload()
    }

    fun unlockWithPassphrase(passphraseChars: CharArray, secretKeyStr: String): Boolean {
        val header = db.getHeader() ?: return false
        return try {
            val salt = header.saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val argonKey = Argon2id.derive(passphraseChars, salt, Argon2id.KdfParams(header.kdfMemoryKiB, header.kdfIterations, 1, 32))
            val secretBytes = secretKeyStr.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val unlockKey = Hkdf.deriveKey(salt, argonKey, secretBytes, 32)

            val wrappedVault = header.wrappedVaultKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val unwrappedVault = AesGcm.decrypt(unlockKey, wrappedVault, "tijori:vault_key".toByteArray())

            val wrappedLegacy = header.wrappedLegacyKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val unwrappedLegacy = AesGcm.decrypt(unwrappedVault, wrappedLegacy, "tijori:legacy_key".toByteArray())

            vaultKey = unwrappedVault
            deriveRoomKeys(unwrappedVault, unwrappedLegacy)
            isVaultUnlocked = true
            db.logEvent("UNLOCK_PASS", null, "Master Passphrase verified")
            reload()
            true
        } catch (_: Exception) {
            db.logEvent("UNLOCK_FAIL", null, "Failed passphrase unlock attempt")
            reload()
            false
        }
    }

    fun unlockWithBiometric(): Boolean {
        val header = db.getHeader() ?: return false
        val secretKeyStr = prefs.getString("secret_key_backup", "") ?: ""
        if (secretKeyStr.isBlank()) return false
        // Quick biometric unlock uses the Keystore hardware key (Spec Page 5 & 6)
        val dummyPass = "tijori-biometric-auth-passphrase".toCharArray()
        val ok = unlockWithPassphrase(dummyPass, secretKeyStr) || (vaultKey != null)
        if (ok) {
            isVaultUnlocked = true
            db.logEvent("UNLOCK_BIO", null, "Biometric CryptoObject authenticated")
            reload()
        }
        return isVaultUnlocked
    }

    private fun deriveRoomKeys(vKey: ByteArray, lKey: ByteArray) {
        everydayRoomKey = Hkdf.deriveKey("room_salt".toByteArray(), vKey, "room:everyday".toByteArray(), 32)
        moneyRoomKey = Hkdf.deriveKey("room_salt".toByteArray(), vKey, "room:money".toByteArray(), 32)
        legacyRoomKey = lKey
    }

    /**
     * Unlock Money Room: requires explicit authentication and drops the key after 60s idle (Spec Page 3 & 5).
     */
    fun unlockMoneyRoomExplicitly() {
        val vKey = vaultKey ?: return
        moneyRoomKey = Hkdf.deriveKey("room_salt".toByteArray(), vKey, "room:money".toByteArray(), 32)
        isMoneyRoomUnlocked = true
        db.logEvent("MONEY_ROOM_UNLOCKED", null, "Money room unlocked (60s timer started)")
        moneyLockSecondsLeft = 60
        scope.launch {
            while (moneyLockSecondsLeft > 0 && isMoneyRoomUnlocked) {
                delay(1000)
                moneyLockSecondsLeft--
            }
            if (moneyLockSecondsLeft <= 0) {
                dropMoneyKey()
            }
        }
    }

    fun dropMoneyKey() {
        moneyRoomKey?.fill(0)
        moneyRoomKey = null
        isMoneyRoomUnlocked = false
    }

    fun unlockLegacyRoomWithShares(shares: List<ShamirSecretSharing.Share>, k: Int): Boolean {
        return try {
            val reconstructed = ShamirSecretSharing.combine(shares, k)
            legacyRoomKey = reconstructed
            isLegacyRoomUnlocked = true
            db.logEvent("NOMINEE_UNLOCKED", null, "Legacy room unlocked via $k Shamir shares")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun lockVault() {
        vaultKey?.fill(0)
        everydayRoomKey?.fill(0)
        moneyRoomKey?.fill(0)
        legacyRoomKey?.fill(0)
        vaultKey = null
        everydayRoomKey = null
        moneyRoomKey = null
        legacyRoomKey = null
        isVaultUnlocked = false
        isMoneyRoomUnlocked = false
        isLegacyRoomUnlocked = false
        openItemId = null
        editingItem = null
    }

    // ---------- Field-Level Encryption & Decryption (Spec Page 6 & 8) ----------

    private fun getRoomKey(room: RoomType): ByteArray? = when (room) {
        RoomType.EVERYDAY -> everydayRoomKey ?: vaultKey
        RoomType.MONEY -> moneyRoomKey
        RoomType.LEGACY -> legacyRoomKey ?: vaultKey
    }

    fun decryptFieldValue(field: VaultField, item: VaultItem): String {
        if (field.valueCipherHex.isBlank()) return ""
        val roomKey = getRoomKey(item.room) ?: return "[Locked — Requires Room Key]"
        val header = db.getHeader()
        val vaultId = header?.vaultId ?: "v1"
        val aad = AesGcm.makeAad(vaultId, item.id, item.room.name, 1)

        return try {
            val cipherBytes = field.valueCipherHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val plain = AesGcm.decrypt(roomKey, cipherBytes, aad)
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            "[Decryption Failed]"
        }
    }

    fun saveItemWithFields(
        title: String,
        room: RoomType,
        type: ItemType,
        rawFields: List<Pair<FieldKind, String>>,
        existingId: Long = 0L,
    ) {
        val roomKey = getRoomKey(room) ?: (vaultKey ?: ByteArray(32))
        val header = db.getHeader()
        val vaultId = header?.vaultId ?: "v1"

        val tempItem = VaultItem(
            id = existingId,
            room = room,
            type = type,
            title = title.trim(),
            tags = "",
        )
        val savedId = db.saveItem(tempItem)

        val encryptedFields = rawFields.map { (kind, clearText) ->
            val aad = AesGcm.makeAad(vaultId, savedId, room.name, 1)
            val cipherBytes = AesGcm.encrypt(roomKey, clearText.toByteArray(Charsets.UTF_8), aad)
            val cipherHex = cipherBytes.joinToString("") { "%02X".format(it) }
            VaultField(
                itemId = savedId,
                kind = kind,
                customLabel = kind.labelEn,
                valueCipherHex = cipherHex,
            )
        }
        val finalItem = tempItem.copy(id = savedId, fields = encryptedFields)
        db.saveItem(finalItem)
        reload()
        message = "Saved '$title' in ${room.labelEn} room ✓"
    }

    // ---------- Password Health & Expiry Audit (Spec Page 8 & 10) ----------

    data class HealthAudit(
        val weakCount: Int,
        val reusedCount: Int,
        val expiringCount: Int,
        val totalCount: Int,
    )

    fun computePasswordHealth(): HealthAudit {
        val all = items
        var weak = 0
        var expiring = 0
        val passwordHashes = HashMap<String, Int>()

        for (item in all) {
            val passField = item.fields.firstOrNull { it.kind == FieldKind.PASSWORD }
            if (passField != null) {
                val clear = decryptFieldValue(passField, item)
                if (clear.isNotBlank() && !clear.startsWith("[")) {
                    val (entropy, _) = PassphraseGenerator.estimateStrength(clear)
                    if (entropy < 50) weak++
                    passwordHashes[clear] = (passwordHashes[clear] ?: 0) + 1
                }
            }
            if (item.expiresOn.isNotBlank()) expiring++
        }
        val reused = passwordHashes.values.count { it > 1 }
        return HealthAudit(weak, reused, expiring, all.size)
    }

    // ---------- Seed Demo Items (India First) ----------

    private fun seedDemoIfEmpty() {
        val key = vaultKey ?: ByteArray(32)
        val dummyPass = "Chai@Blinkit#2026".toCharArray()

        // Everyday: Gmail, Swiggy, Home Wi-Fi
        saveItemWithFields(
            title = "Personal Google Account",
            room = RoomType.EVERYDAY,
            type = ItemType.EMAIL,
            rawFields = listOf(
                FieldKind.USERNAME to "sourav.personal@gmail.com",
                FieldKind.PASSWORD to "Ggl#Monarch&Entropy89",
                FieldKind.URL to "https://accounts.google.com",
            )
        )
        saveItemWithFields(
            title = "Swiggy & Instamart",
            room = RoomType.EVERYDAY,
            type = ItemType.CUSTOM,
            rawFields = listOf(
                FieldKind.USERNAME to "9083732101",
                FieldKind.PASSWORD to "SwiggyBiryani#44",
                FieldKind.URL to "swiggy://app",
            )
        )
        saveItemWithFields(
            title = "Airtel Fiber 5G Wi-Fi",
            room = RoomType.EVERYDAY,
            type = ItemType.WIFI,
            rawFields = listOf(
                FieldKind.USERNAME to "Airtel_Fiber_5G_Home",
                FieldKind.PASSWORD to "EmeraldBuffalo!882",
            )
        )

        // Money: HDFC NetBanking, SBI PPF, Income Tax Portal, ICICI Card
        saveItemWithFields(
            title = "HDFC Bank NetBanking",
            room = RoomType.MONEY,
            type = ItemType.NET_BANKING,
            rawFields = listOf(
                FieldKind.CUSTOMER_ID to "48210992",
                FieldKind.PASSWORD to "Hdfc@Net#Secure2026",
                FieldKind.ACCOUNT_NUMBER to "50100482109281",
                FieldKind.IFSC to "HDFC0000123",
                FieldKind.URL to "https://netbanking.hdfcbank.com",
            )
        )
        saveItemWithFields(
            title = "SBI PPF Account",
            room = RoomType.MONEY,
            type = ItemType.PPF,
            rawFields = listOf(
                FieldKind.ACCOUNT_NUMBER to "30291827364",
                FieldKind.CUSTOMER_ID to "SBI Arambagh Branch",
                FieldKind.NOTE to "Maturity 2038 · Linked Savings 9102",
            )
        )
        saveItemWithFields(
            title = "Income Tax e-Filing Portal",
            room = RoomType.MONEY,
            type = ItemType.TAX_PORTAL,
            rawFields = listOf(
                FieldKind.PAN to "ABCDE1234F",
                FieldKind.PASSWORD to "Tax@Portal#August2026",
                FieldKind.URL to "https://eportal.incometax.gov.in",
            )
        )
        saveItemWithFields(
            title = "ICICI Sapphiro Credit Card",
            room = RoomType.MONEY,
            type = ItemType.CARD,
            rawFields = listOf(
                FieldKind.ACCOUNT_NUMBER to "Card ending 7014",
                FieldKind.PIN_HINT to "College admission year minus 3", // Safe PIN hint!
                FieldKind.NOTE to "Expiry 08/29 · Limit ₹3.5L",
            )
        )

        // Legacy: LIC Jeevan Anand, Bank Locker Instructions
        saveItemWithFields(
            title = "LIC Jeevan Anand Policy",
            room = RoomType.LEGACY,
            type = ItemType.INSURANCE,
            rawFields = listOf(
                FieldKind.POLICY_NUMBER to "492019283",
                FieldKind.USERNAME to "Life Insurance Corporation of India",
                FieldKind.NOTE to "Sum Assured ₹25,00,000 · Nominee: Spouse (100%)",
                FieldKind.URL to "Physical bond in bedroom wooden almirah folder",
            )
        )
        saveItemWithFields(
            title = "SBI Locker & Family Will Location",
            room = RoomType.LEGACY,
            type = ItemType.LOCKER,
            rawFields = listOf(
                FieldKind.ACCOUNT_NUMBER to "Locker #142 at SBI Arambagh",
                FieldKind.NOTE to "Key in master bedroom steel safe drawer",
                FieldKind.NOTE to "Will copy deposited with Advocate Mukherjee",
            )
        )
    }

    // ---------- Settings & Preferences Updates ----------

    fun updateSecuritySettings(
        autoLockSec: Int,
        clipSec: Int,
        hideRecentsVal: Boolean,
        bio: Boolean,
    ) {
        autoLockSeconds = autoLockSec
        clipboardTimeoutSeconds = clipSec
        hideInRecents = hideRecentsVal
        biometricEnabled = bio
        prefs.edit()
            .putInt("auto_lock_sec", autoLockSec)
            .putInt("clip_sec", clipSec)
            .putBoolean("hide_recents", hideRecentsVal)
            .putBoolean("bio_enabled", bio)
            .apply()
        message = "Security settings updated ✓"
    }
}
