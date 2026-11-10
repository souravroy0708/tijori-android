package com.tijoriapp.vault.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { System, Light, Dark }

/**
 * Non-secret settings only. Nothing in here helps an attacker: secrets live in the encrypted vault
 * and in the Android Keystore. Excluded from cloud backup by data_extraction_rules.xml.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tijori_settings_v2", Context.MODE_PRIVATE)

    data class Settings(
        val ownerName: String,
        val language: String,
        val theme: ThemeMode,
        val autoLockSeconds: Int,
        val clipboardSeconds: Int,
        val moneyIdleSeconds: Int,
        val blockScreenshots: Boolean,
        val biometric: Boolean,
        val lastBackupAt: Long,
        val familyK: Int,
        val familyN: Int,
        val cardsMadeAt: Long,
        val familyExportAt: Long,
        val kitSavedAt: Long,
    )

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings
    val now: Settings get() = _settings.value

    private fun read() = Settings(
        ownerName = sp.getString("owner", "") ?: "",
        language = sp.getString("lang", "en") ?: "en",
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System),
        autoLockSeconds = sp.getInt("auto_lock", 60),
        clipboardSeconds = sp.getInt("clip", 30),
        moneyIdleSeconds = sp.getInt("money_idle", 60),
        blockScreenshots = sp.getBoolean("block_shots", true),
        biometric = sp.getBoolean("bio", false),
        lastBackupAt = sp.getLong("last_backup", 0L),
        familyK = sp.getInt("family_k", 2),
        familyN = sp.getInt("family_n", 3),
        cardsMadeAt = sp.getLong("cards_at", 0L),
        familyExportAt = sp.getLong("family_export", 0L),
        kitSavedAt = sp.getLong("kit_at", 0L),
    )

    fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        sp.edit(commit = true) { block() }
        _settings.value = read()
    }

    fun setOwner(v: String) = update { putString("owner", v.trim().take(40)) }
    fun setLanguage(v: String) = update { putString("lang", v) }
    fun setTheme(v: ThemeMode) = update { putString("theme", v.name) }
    fun setAutoLock(v: Int) = update { putInt("auto_lock", v) }
    fun setClipboard(v: Int) = update { putInt("clip", v) }
    fun setMoneyIdle(v: Int) = update { putInt("money_idle", v) }
    fun setBlockScreenshots(v: Boolean) = update { putBoolean("block_shots", v) }
    fun setBiometric(v: Boolean) = update { putBoolean("bio", v) }
    fun markBackup() = update { putLong("last_backup", System.currentTimeMillis()) }
    fun markKitSaved() = update { putLong("kit_at", System.currentTimeMillis()) }
    fun markCards(k: Int, n: Int) = update { putInt("family_k", k); putInt("family_n", n); putLong("cards_at", System.currentTimeMillis()) }
    fun markFamilyExport() = update { putLong("family_export", System.currentTimeMillis()) }

    // ----- Brute-force throttling (persisted, so killing the app doesn't reset it) -----
    val failedAttempts: Int get() = sp.getInt("fails", 0)
    val lockedUntil: Long get() = sp.getLong("locked_until", 0L)

    fun recordFailure(): Long {
        val n = failedAttempts + 1
        val wait = when {
            n < 5 -> 0L
            n == 5 -> 30_000L
            n == 6 -> 60_000L
            n == 7 -> 5 * 60_000L
            else -> 15 * 60_000L
        }
        sp.edit(commit = true) { putInt("fails", n); putLong("locked_until", System.currentTimeMillis() + wait) }
        return wait
    }

    fun clearFailures() = sp.edit(commit = true) { putInt("fails", 0); putLong("locked_until", 0L) }

    fun wipe() { sp.edit(commit = true) { clear() }; _settings.value = read() }
}
