package com.tijoriapp.vault.data

import android.app.ActivityManager
import android.content.Context
import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.Bytes
import com.tijoriapp.vault.core.crypto.NomineeCard
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.crypto.wipe
import com.tijoriapp.vault.core.vault.BackupFile
import com.tijoriapp.vault.core.vault.LegacyPackage
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.RoomKeys
import com.tijoriapp.vault.core.vault.SealedRow
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.core.vault.VaultHeader
import com.tijoriapp.vault.core.vault.VaultItem
import com.tijoriapp.vault.core.vault.WrongPassphraseException
import com.tijoriapp.vault.security.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * The single source of truth for the vault while the app runs. Owns the in-memory room keys and the
 * decrypted items. Shared by the main screen and the autofill screen (same process).
 */
class VaultSession(
    private val context: Context,
    private val store: VaultStore,
    private val device: DeviceKeys,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    enum class Phase { NoVault, Locked, Unlocked }

    data class State(
        val phase: Phase,
        val items: List<VaultItem> = emptyList(),
        val moneyOpen: Boolean = false,
        val moneyLocksAt: Long = 0L,
        val counts: Map<Room, Int> = emptyMap(),
        /** Rows that failed to decrypt (should never happen; shown honestly if it does). */
        val unreadable: Int = 0,
        val vaultId: String = "",
    )

    sealed interface UnlockResult {
        data object Ok : UnlockResult
        data class Wrong(val waitMillis: Long, val attempts: Int) : UnlockResult
        data class Throttled(val untilMillis: Long) : UnlockResult
        data class Failed(val reason: String) : UnlockResult
    }

    private val mutex = Mutex()
    private var header: VaultHeader? = store.header()
    private val keys = RoomKeys()
    private var moneyJob: Job? = null
    private var backgroundJob: Job? = null
    private var externalGraceUntil = 0L

    private val _state = MutableStateFlow(State(if (header == null) Phase.NoVault else Phase.Locked, counts = counts(), vaultId = header?.vaultId ?: ""))
    val state: StateFlow<State> = _state

    val isUnlocked: Boolean get() = _state.value.phase == Phase.Unlocked
    val biometricReady: Boolean get() = prefs.now.biometric && device.hasBiometricKey() && store.meta(VaultStore.K_BIO_MAIN) != null

    private fun counts() = Room.entries.associateWith { store.count(it) }

    private fun deviceSecretKey(): ByteArray {
        val sealed = store.meta(VaultStore.K_SECRET_KEY) ?: error("Secret Key missing on this phone")
        return device.openWithDevice(Bytes.unb64(sealed))
    }

    private fun recommendedKdf(): Argon2.Params {
        val am = context.getSystemService(ActivityManager::class.java)
        return if (am?.isLowRamDevice == true) VaultCrypto.LOW_RAM_KDF else VaultCrypto.DEFAULT_KDF
    }

    // =====================================================================================
    // Create / restore
    // =====================================================================================

    /** Creates a new vault and returns the printable Secret Key. Leaves the vault unlocked. */
    suspend fun create(passphrase: String, ownerName: String): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            VaultStore.purgePrototype(context)
            val sk = SecretKey.generate()
            val created = VaultCrypto.create(passphrase, sk, recommendedKdf())
            store.replaceAll(created.header, emptyList(), mapOf(VaultStore.K_SECRET_KEY to Bytes.b64(device.sealWithDevice(sk))))
            store.log("VAULT_CREATED")
            prefs.setOwner(ownerName)
            prefs.setBiometric(false)
            device.deleteBiometricKey()
            header = created.header
            Room.entries.forEach { r -> keys[r] = created.keys[r]!!.copyOf() }
            created.keys.wipe()
            val text = SecretKey.format(sk)
            sk.wipe()
            publishUnlocked()
            startMoneyTimer()
            text
        }
    }

    /** Restores a .tijori backup on this phone. Needs the Secret Key from the Emergency Kit and the passphrase. */
    suspend fun restore(file: ByteArray, secretKeyText: String, passphrase: String): UnlockResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val parsed = try { BackupFile.read(file) } catch (e: Exception) { return@withContext UnlockResult.Failed(e.message ?: "Can't read this file") }
            val sk = SecretKey.parse(secretKeyText) ?: return@withContext UnlockResult.Failed("That Secret Key has a typo. Check each character on your Emergency Kit.")
            val opened = try { VaultCrypto.unlock(parsed.header, passphrase, sk, Room.entries.toSet()) } catch (_: WrongPassphraseException) {
                sk.wipe(); return@withContext UnlockResult.Failed("The passphrase or Secret Key doesn't match this backup.")
            }
            VaultStore.purgePrototype(context)
            moneyJob?.cancel()
            keys.wipe() // drop the old vault's keys without flashing the lock screen
            store.replaceAll(parsed.header, parsed.rows, mapOf(VaultStore.K_SECRET_KEY to Bytes.b64(device.sealWithDevice(sk))))
            store.log("VAULT_RESTORED", "${parsed.rows.size} items")
            sk.wipe()
            device.deleteBiometricKey()
            prefs.setBiometric(false)
            header = parsed.header
            keys[Room.EVERYDAY] = opened[Room.EVERYDAY]!!.copyOf()
            keys[Room.LEGACY] = opened[Room.LEGACY]!!.copyOf()
            opened.wipe()
            publishUnlocked()
            UnlockResult.Ok
        }
    }

    // =====================================================================================
    // Unlock / lock
    // =====================================================================================

    suspend fun unlockWithPassphrase(passphrase: String): UnlockResult = mutex.withLock {
        val h = header ?: return UnlockResult.Failed("No vault")
        val until = prefs.lockedUntil
        if (System.currentTimeMillis() < until) return UnlockResult.Throttled(until)
        withContext(Dispatchers.Default) {
            val sk = runCatching { deviceSecretKey() }.getOrElse {
                return@withContext UnlockResult.Failed("This phone's secure key storage was reset, so Tijori can't read its copy of your Secret Key. Restore your backup with your Emergency Kit.")
            }
            try {
                val opened = VaultCrypto.unlock(h, passphrase, sk, setOf(Room.EVERYDAY, Room.LEGACY))
                prefs.clearFailures()
                keys[Room.EVERYDAY] = opened[Room.EVERYDAY]!!.copyOf()
                keys[Room.LEGACY] = opened[Room.LEGACY]!!.copyOf()
                opened.wipe()
                store.log("UNLOCK_PASSPHRASE")
                publishUnlocked()
                UnlockResult.Ok
            } catch (_: WrongPassphraseException) {
                val wait = prefs.recordFailure()
                store.log("UNLOCK_FAILED")
                UnlockResult.Wrong(wait, prefs.failedAttempts)
            } finally { sk.wipe() }
        }
    }

    fun prepareBiometric(): DeviceKeys.Prepared {
        val p = device.prepareBiometricDecrypt()
        if (p is DeviceKeys.Prepared.Invalidated) disableBiometric("BIO_INVALIDATED")
        return p
    }

    /** [cipher] comes back from a successful BiometricPrompt: it can do exactly one decryption. */
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean = mutex.withLock {
        withContext(Dispatchers.Default) {
            val blob = store.meta(VaultStore.K_BIO_MAIN) ?: return@withContext false
            val both = runCatching { cipher.doFinal(Bytes.unb64(blob)) }.getOrNull() ?: return@withContext false
            keys[Room.EVERYDAY] = both.copyOfRange(0, 32)
            keys[Room.LEGACY] = both.copyOfRange(32, 64)
            both.wipe()
            prefs.clearFailures()
            store.log("UNLOCK_BIOMETRIC")
            publishUnlocked()
            true
        }
    }

    /** Proves "it's really you" for sensitive screens: the biometric-bound key must decrypt our blob. */
    suspend fun verifyBiometric(cipher: Cipher): Boolean = withContext(Dispatchers.Default) {
        val blob = store.meta(VaultStore.K_BIO_MAIN) ?: return@withContext false
        runCatching { cipher.doFinal(Bytes.unb64(blob)).wipe(); true }.getOrDefault(false)
    }

    suspend fun openMoneyWithPassphrase(passphrase: String): UnlockResult = mutex.withLock {
        val h = header ?: return UnlockResult.Failed("No vault")
        if (!isUnlocked) return UnlockResult.Failed("Locked")
        val until = prefs.lockedUntil
        if (System.currentTimeMillis() < until) return UnlockResult.Throttled(until)
        withContext(Dispatchers.Default) {
            val sk = deviceSecretKey()
            try {
                val opened = VaultCrypto.unlock(h, passphrase, sk, setOf(Room.MONEY))
                keys[Room.MONEY] = opened[Room.MONEY]!!.copyOf()
                opened.wipe()
                prefs.clearFailures()
                store.log("MONEY_OPENED")
                publishUnlocked(); startMoneyTimer()
                UnlockResult.Ok
            } catch (_: WrongPassphraseException) {
                UnlockResult.Wrong(prefs.recordFailure(), prefs.failedAttempts)
            } finally { sk.wipe() }
        }
    }

    suspend fun openMoneyWithBiometric(cipher: Cipher): Boolean = mutex.withLock {
        if (!isUnlocked) return false
        withContext(Dispatchers.Default) {
            val blob = store.meta(VaultStore.K_BIO_MONEY) ?: return@withContext false
            val mk = runCatching { cipher.doFinal(Bytes.unb64(blob)) }.getOrNull() ?: return@withContext false
            keys[Room.MONEY] = mk
            store.log("MONEY_OPENED")
            publishUnlocked(); startMoneyTimer()
            true
        }
    }

    /** Any Money-room action keeps it open a little longer. */
    fun touchMoney() {
        if (_state.value.moneyOpen) _state.update { it.copy(moneyLocksAt = System.currentTimeMillis() + prefs.now.moneyIdleSeconds * 1000L) }
    }

    fun closeMoney() {
        moneyJob?.cancel()
        keys.drop(Room.MONEY)
        if (isUnlocked) _state.update { s -> s.copy(moneyOpen = false, moneyLocksAt = 0L, items = s.items.filter { it.room != Room.MONEY }) }
    }

    private fun startMoneyTimer() {
        moneyJob?.cancel()
        touchMoney()
        moneyJob = scope.launch {
            while (isActive) {
                delay(1000)
                val s = _state.value
                if (!s.moneyOpen) break
                if (System.currentTimeMillis() >= s.moneyLocksAt) { closeMoney(); break }
            }
        }
    }

    fun lock() {
        scope.launch { mutex.withLock { lockInternal() } }
    }

    private fun lockInternal() {
        moneyJob?.cancel()
        keys.wipe()
        _state.value = State(if (header == null) Phase.NoVault else Phase.Locked, counts = counts(), vaultId = header?.vaultId ?: "")
    }

    private fun publishUnlocked() {
        val h = header ?: return
        val open = Room.entries.filter { keys.has(it) }
        var bad = 0
        val items = store.rows().filter { it.room in open }.mapNotNull { row ->
            runCatching { VaultCrypto.open(keys[row.room]!!, h.vaultId, row.id, row.room, row.sealed) }.getOrElse { bad++; null }
        }
        _state.value = State(
            phase = Phase.Unlocked, items = items,
            moneyOpen = keys.has(Room.MONEY),
            moneyLocksAt = if (keys.has(Room.MONEY)) System.currentTimeMillis() + prefs.now.moneyIdleSeconds * 1000L else 0L,
            counts = counts(), unreadable = bad, vaultId = h.vaultId,
        )
    }

    // ----- auto-lock when the app leaves the screen -----

    /** Call before opening the file picker or share sheet, so auto-lock doesn't fire mid-export. */
    fun expectExternalScreen() { externalGraceUntil = System.currentTimeMillis() + 3 * 60_000L }

    fun onAppBackground() {
        if (!isUnlocked) return
        closeMoney()
        if (System.currentTimeMillis() < externalGraceUntil) return
        val seconds = prefs.now.autoLockSeconds
        backgroundJob?.cancel()
        if (seconds <= 0) lock() else backgroundJob = scope.launch { delay(seconds * 1000L); lock() }
    }

    fun onAppForeground() {
        backgroundJob?.cancel()
        externalGraceUntil = 0L
    }

    // =====================================================================================
    // Items
    // =====================================================================================

    fun canWrite(room: Room) = keys.has(room)

    suspend fun save(item: VaultItem): Boolean = mutex.withLock {
        val h = header ?: return false
        val key = keys[item.room] ?: return false
        withContext(Dispatchers.Default) {
            val old = _state.value.items.firstOrNull { it.id == item.id }
            val now = System.currentTimeMillis()
            val pwChanged = old == null || old.password != item.password
            val toSave = item.copy(
                title = item.title.trim().ifEmpty { item.type.en },
                updatedAt = now,
                createdAt = old?.createdAt ?: now,
                passwordChangedAt = if (pwChanged) now else old!!.passwordChangedAt,
            )
            store.put(SealedRow(toSave.id, toSave.room, VaultCrypto.seal(key, h.vaultId, toSave), now))
            _state.update { s -> s.copy(items = s.items.filter { it.id != toSave.id } + toSave, counts = counts()) }
            if (toSave.room == Room.MONEY) touchMoney()
            true
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            store.delete(id)
            _state.update { s -> s.copy(items = s.items.filter { it.id != id }, counts = counts()) }
        }
    }

    fun markUsed(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        scope.launch {
            mutex.withLock {
                val h = header ?: return@withLock
                val key = keys[item.room] ?: return@withLock
                val updated = item.copy(lastUsedAt = System.currentTimeMillis())
                withContext(Dispatchers.Default) { store.put(SealedRow(updated.id, updated.room, VaultCrypto.seal(key, h.vaultId, updated), updated.updatedAt)) }
                _state.update { s -> s.copy(items = s.items.map { if (it.id == id) updated else it }) }
            }
        }
        if (item.room == Room.MONEY) touchMoney()
    }

    // =====================================================================================
    // Settings that need keys
    // =====================================================================================

    suspend fun verifyPassphrase(passphrase: String): Boolean = mutex.withLock {
        val h = header ?: return false
        withContext(Dispatchers.Default) {
            val sk = deviceSecretKey()
            try { VaultCrypto.unlock(h, passphrase, sk, setOf(Room.EVERYDAY)).wipe(); true }
            catch (_: WrongPassphraseException) { prefs.recordFailure(); false }
            finally { sk.wipe() }
        }
    }

    /** Only call right after the user proved it's them (passphrase or biometric). */
    fun secretKeyText(): String {
        val sk = deviceSecretKey()
        try { return SecretKey.format(sk) } finally { sk.wipe() }
    }

    /** Turns fingerprint/face unlock on. Needs every room key, so pass the passphrase unless Money is open. */
    suspend fun enableBiometric(passphrase: String?): UnlockResult = mutex.withLock {
        val h = header ?: return UnlockResult.Failed("No vault")
        withContext(Dispatchers.Default) {
            var temp: RoomKeys? = null
            try {
                val mk = keys[Room.MONEY]?.copyOf() ?: run {
                    if (passphrase == null) return@withContext UnlockResult.Failed("Passphrase needed")
                    val sk = deviceSecretKey()
                    try { temp = VaultCrypto.unlock(h, passphrase, sk, setOf(Room.MONEY)) }
                    catch (_: WrongPassphraseException) { return@withContext UnlockResult.Wrong(prefs.recordFailure(), prefs.failedAttempts) }
                    finally { sk.wipe() }
                    temp!![Room.MONEY]!!.copyOf()
                }
                val ek = keys[Room.EVERYDAY] ?: return@withContext UnlockResult.Failed("Locked")
                val lk = keys[Room.LEGACY] ?: return@withContext UnlockResult.Failed("Locked")
                device.createBiometricKey()
                val main = ek + lk
                store.setMeta(VaultStore.K_BIO_MAIN, Bytes.b64(device.sealForBiometric(main)))
                store.setMeta(VaultStore.K_BIO_MONEY, Bytes.b64(device.sealForBiometric(mk)))
                main.wipe(); mk.wipe()
                prefs.setBiometric(true)
                store.log("BIO_ENABLED")
                UnlockResult.Ok
            } catch (e: Exception) {
                device.deleteBiometricKey()
                UnlockResult.Failed(e.message ?: "This phone couldn't create a biometric key")
            } finally { temp?.wipe() }
        }
    }

    fun disableBiometric(reason: String = "BIO_DISABLED") {
        device.deleteBiometricKey()
        store.setMeta(VaultStore.K_BIO_MAIN, null)
        store.setMeta(VaultStore.K_BIO_MONEY, null)
        prefs.setBiometric(false)
        store.log(reason)
    }

    suspend fun changePassphrase(current: String, next: String): UnlockResult = mutex.withLock {
        val h = header ?: return UnlockResult.Failed("No vault")
        withContext(Dispatchers.Default) {
            val sk = deviceSecretKey()
            try {
                val nh = VaultCrypto.changePassphrase(h, current, next, sk, recommendedKdf())
                store.saveHeader(nh); header = nh
                store.log("PASSPHRASE_CHANGED")
                UnlockResult.Ok
            } catch (_: WrongPassphraseException) { UnlockResult.Wrong(prefs.recordFailure(), prefs.failedAttempts) }
            finally { sk.wipe() }
        }
    }

    // =====================================================================================
    // Backup and family
    // =====================================================================================

    suspend fun backupBytes(): ByteArray = mutex.withLock {
        val h = header ?: error("No vault")
        withContext(Dispatchers.IO) { BackupFile.write(h, store.rows()).also { store.log("BACKUP_EXPORTED") } }
    }

    suspend fun familyPackageBytes(): ByteArray = mutex.withLock {
        val h = header ?: error("No vault")
        withContext(Dispatchers.IO) {
            val s = prefs.now
            LegacyPackage.write(h, s.ownerName, s.familyK, s.familyN, store.rows(Room.LEGACY)).also { store.log("FAMILY_EXPORTED") }
        }
    }

    /** Makes a fresh set of k-of-n cards for the Family key. Older card sets keep working until you rotate. */
    fun makeCards(k: Int, n: Int): List<NomineeCard> {
        val lk = keys[Room.LEGACY] ?: error("Locked")
        store.log("CARDS_MADE", "$k of $n")
        return NomineeCard.makeSet(lk, k, n)
    }

    /**
     * New Family key: every old nominee card and Family package stops working. Re-encrypts Family items.
     * Needs the passphrase because all three room keys are re-sealed.
     */
    suspend fun rotateFamilyKey(passphrase: String): UnlockResult = mutex.withLock {
        val h = header ?: return UnlockResult.Failed("No vault")
        withContext(Dispatchers.Default) {
            val sk = deviceSecretKey()
            val all = try { VaultCrypto.unlock(h, passphrase, sk, Room.entries.toSet()) }
            catch (_: WrongPassphraseException) { sk.wipe(); return@withContext UnlockResult.Wrong(prefs.recordFailure(), prefs.failedAttempts) }
            try {
                val oldLk = all[Room.LEGACY]!!
                val newLk = Bytes.random(32)
                val rows = store.rows(Room.LEGACY).map { r ->
                    val item = VaultCrypto.open(oldLk, h.vaultId, r.id, Room.LEGACY, r.sealed)
                    SealedRow(r.id, Room.LEGACY, VaultCrypto.seal(newLk, h.vaultId, item), r.updatedAt)
                }
                all[Room.LEGACY] = newLk.copyOf()
                val nh = VaultCrypto.rewrap(h, all, passphrase, sk)
                store.putAll(rows)
                store.saveHeader(nh); header = nh
                keys[Room.LEGACY] = newLk
                if (biometricReady) {
                    val main = all[Room.EVERYDAY]!! + newLk
                    store.setMeta(VaultStore.K_BIO_MAIN, Bytes.b64(device.sealForBiometric(main)))
                    main.wipe()
                }
                prefs.update { putLong("cards_at", 0L); putLong("family_export", 0L) }
                store.log("FAMILY_KEY_ROTATED")
                publishUnlocked()
                UnlockResult.Ok
            } finally { all.wipe(); sk.wipe() }
        }
    }

    fun audit() = store.audit()

    fun wipeEverything() {
        keys.wipe()
        store.wipe()
        device.wipeAll()
        prefs.wipe()
        header = null
        _state.value = State(Phase.NoVault)
    }
}
