package com.tijoriapp.vault.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.BuildConfig
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.data.PdfKits
import com.tijoriapp.vault.data.ThemeMode
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.security.DeviceKeys
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsTab(app: TijoriApp) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<String?>(null) }
    var kitReauth by remember { mutableStateOf(false) }
    var bioPass by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var restoreOpen by remember { mutableStateOf(false) }

    HeroPage(hero = {
        Text(t("Settings"), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text("Tijori ${BuildConfig.VERSION_NAME} · works fully offline", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
    }) {
        SectionLabel("Unlock")
        AppCard {
            if (host.biometricAvailable) SwitchRow(Icons.Filled.Fingerprint, t("Unlock with fingerprint"), "Per-use, hardware-bound. Money room asks again.", app.session.biometricReady) { on ->
                if (on) {
                    if (app.session.state.value.moneyOpen) scope.launch { describe(app.session.enableBiometric(null))?.let { Toasts.show(it) } ?: Toasts.show("Fingerprint unlock is on") }
                    else bioPass = true
                } else { app.session.disableBiometric(); Toasts.show("Fingerprint unlock is off") }
            } else SettingRow(Icons.Filled.Fingerprint, "Fingerprint unlock", "This phone has no strong (Class 3) biometric set up.")
            SettingRow(Icons.Filled.Timer, t("Auto-lock"), "After leaving Tijori: ${lockLabel(s.autoLockSeconds)}", onClick = { picker = "lock" })
            SettingRow(Icons.Filled.AccountBalanceWallet, "Money room closes after", "${s.moneyIdleSeconds} seconds idle", onClick = { picker = "money" })
            SettingRow(Icons.Filled.ContentPaste, "Clear copied secrets after", "${s.clipboardSeconds} seconds", onClick = { picker = "clip" })
            SettingRow(Icons.Filled.Password, "Change master passphrase", null, onClick = { nav.push(Dest.ChangePassphrase) })
        }

        SectionLabel("Privacy")
        AppCard {
            SwitchRow(Icons.Filled.ScreenLockPortrait, "Block screenshots", "Also hides Tijori in the recent-apps screen", s.blockScreenshots) { app.prefs.setBlockScreenshots(it) }
            SettingRow(Icons.Filled.Password, "Autofill service", if (host.autofillEnabled) "Tijori fills logins in apps and browsers" else "Off. Tap to make Tijori your autofill service",
                trailing = { if (host.autofillEnabled) Chip("On", GoodBg, GoodText) }, onClick = { host.openAutofillSettings() })
            SettingRow(Icons.Filled.VerifiedUser, "Trust & proofs", "Check for yourself: no internet, hardware keys, no cloud backup", onClick = { nav.push(Dest.Trust) })
        }

        SectionLabel("Backup")
        AppCard {
            SettingRow(Icons.Filled.Backup, "Save encrypted backup", if (s.lastBackupAt == 0L) "Never backed up" else "Last: ${relativeTime(s.lastBackupAt)}", onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                host.createFile("Tijori-backup-$date.tijori", "application/octet-stream", write = { it.write(app.session.backupBytes()) },
                    onDone = { ok -> if (ok) { app.prefs.markBackup(); Toasts.show("Backup saved. Keep a copy off this phone.") } })
            })
            Text("Save it to a pen drive, laptop or your own cloud folder. It's encrypted: without your Secret Key and passphrase it's just noise.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            SettingRow(Icons.Filled.Print, "Emergency Kit (PDF)", if (s.kitSavedAt == 0L) "Not saved yet" else "Saved ${relativeTime(s.kitSavedAt)}", onClick = { kitReauth = true })
            SettingRow(Icons.Filled.Restore, "Replace with a backup", "Restore a .tijori file over this vault", onClick = { restore = true })
        }

        SectionLabel("Appearance")
        AppCard {
            SettingRow(Icons.Filled.Person, "Your name", s.ownerName.ifBlank { "Not set" }, onClick = { editName = true })
            Text(t("Language"), style = MaterialTheme.typography.labelMedium, color = Muted)
            Segmented(listOf("English", "हिन्दी"), if (s.language == "hi") 1 else 0) { app.prefs.setLanguage(if (it == 1) "hi" else "en") }
            Text(t("Theme"), style = MaterialTheme.typography.labelMedium, color = Muted)
            Segmented(listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(s.theme)) { app.prefs.setTheme(ThemeMode.entries[it]) }
        }

        SectionLabel("About")
        AppCard {
            SettingRow(Icons.Filled.Info, "About, privacy & licences", null, onClick = { nav.push(Dest.About) })
            SettingRow(Icons.Filled.DeleteForever, "Erase this vault", "Deletes everything on this phone. Backups elsewhere are untouched.", onClick = { erase = true })
        }
    }

    picker?.let { which ->
        val (title, options, current) = when (which) {
            "lock" -> Triple(t("Auto-lock"), listOf(0, 30, 60, 300, 900), s.autoLockSeconds)
            "money" -> Triple("Money room closes after", listOf(30, 60, 120), s.moneyIdleSeconds)
            else -> Triple("Clear copied secrets after", listOf(15, 30, 60, 120), s.clipboardSeconds)
        }
        AlertDialog(onDismissRequest = { picker = null }, title = { Text(title) },
            text = {
                Column {
                    options.forEach { v ->
                        SettingRow(if (v == current) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                            if (which == "lock") lockLabel(v) else "$v seconds", onClick = {
                                when (which) { "lock" -> app.prefs.setAutoLock(v); "money" -> app.prefs.setMoneyIdle(v); else -> app.prefs.setClipboard(v) }
                                picker = null
                            })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(t("Done")) } })
    }

    if (kitReauth) ReauthDialog(app, "Show Secret Key", "The Emergency Kit contains your Secret Key.", onDismiss = { kitReauth = false }) {
        kitReauth = false
        val sk = app.session.secretKeyText()
        host.createFile("Tijori-Emergency-Kit.pdf", "application/pdf",
            write = { PdfKits(app).emergencyKit(it, app.prefs.now.ownerName, sk, app.session.state.value.vaultId) },
            onDone = { ok -> if (ok) { app.prefs.markKitSaved(); Toasts.show("Emergency Kit saved. Print it, then delete the file.") } })
    }

    if (bioPass) PassphrasePrompt("Turn on fingerprint unlock", "Your passphrase is needed once to protect the Money room key with your fingerprint.",
        onDismiss = { bioPass = false }) { pass ->
        val r = app.session.enableBiometric(pass)
        if (r is UnlockResult.Ok) { bioPass = false; Toasts.show("Fingerprint unlock is on") }
        describe(r)
    }

    if (restore) AlertDialog(onDismissRequest = { restore = false }, title = { Text("Replace this vault?") },
        text = { Text("Everything currently on this phone will be replaced by the backup. Save a backup of this vault first if you might need it.") },
        confirmButton = { TextButton(onClick = { restore = false; restoreOpen = true }) { Text("Continue", color = Coral) } },
        dismissButton = { TextButton(onClick = { restore = false }) { Text(t("Cancel")) } })
    if (restoreOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { restoreOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Paper)) { RestoreScreen(app, onBack = { restoreOpen = false }, onRestored = { restoreOpen = false }) }
        }
    }

    if (erase) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { erase = false }, title = { Text("Erase this vault?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Every item, key and setting on this phone is destroyed. This can't be undone. Type ERASE to confirm.")
                    PlainInput(typed, { typed = it }, "Type ERASE")
                }
            },
            confirmButton = { TextButton(enabled = typed.trim() == "ERASE", onClick = { erase = false; app.session.wipeEverything() }) { Text("Erase", color = Coral, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { erase = false }) { Text(t("Cancel")) } })
    }

    if (editName) {
        var name by remember { mutableStateOf(s.ownerName) }
        AlertDialog(onDismissRequest = { editName = false }, title = { Text("Your name") },
            text = { PlainInput(name, { name = it.take(40) }, "Name") },
            confirmButton = { TextButton(onClick = { app.prefs.setOwner(name); editName = false }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(t("Cancel")) } })
    }
}

private fun lockLabel(s: Int) = when (s) { 0 -> "Immediately"; in 1..59 -> "$s seconds"; 60 -> "1 minute"; else -> "${s / 60} minutes" }

/** Dialog asking for the passphrase; [onSubmit] returns an error message or null on success. */
@Composable
fun PassphrasePrompt(title: String, body: String, onDismiss: () -> Unit, onSubmit: suspend (String) -> String?) {
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
                SecretInput(pass, { pass = it; error = null }, t("Master passphrase"), isError = error != null, supporting = error)
            }
        },
        confirmButton = {
            TextButton(enabled = pass.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch { error = onSubmit(pass); busy = false }
            }) { Text(if (busy) "Working…" else "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Cancel")) } })
}

@Composable
fun ChangePassphraseScreen(app: TijoriApp) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf(app.words.words(6, separator = " ")) }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val strength = remember(next) { Strength.check(next, app.words) }
    val match = VaultCrypto.normalize(next).contentEquals(VaultCrypto.normalize(confirm))
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Change passphrase", onBack = { nav.pop() })
        ScreenColumn {
            Banner("Backups you made earlier still open with the old passphrase. Make a fresh backup after changing it.", icon = Icons.Filled.Info)
            SecretInput(current, { current = it; error = null }, "Current passphrase")
            SecretInput(next, { next = it }, "New passphrase", trailingExtra = {
                IconButton(onClick = { next = app.words.words(6, separator = " "); confirm = "" }) { Icon(Icons.Filled.Casino, contentDescription = "Suggest words") }
            })
            StrengthMeter(strength)
            SecretInput(confirm, { confirm = it }, "Type the new one again", isError = confirm.isNotEmpty() && !match && confirm.length >= next.length)
            error?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }
            BigButton("Change passphrase", loading = busy, enabled = current.isNotBlank() && match && strength.level >= Strength.Level.Strong) {
                busy = true
                scope.launch {
                    val r = app.session.changePassphrase(current, next)
                    busy = false
                    if (r is UnlockResult.Ok) {
                        nav.pop(); Toasts.show("Passphrase changed. Make a new backup.")
                    } else error = describe(r)
                }
            }
        }
    }
}

@Composable
fun TrustScreen(app: TijoriApp) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val host = LocalHost.current
    val pm = ctx.packageManager
    val noInternet = pm.checkPermission(android.Manifest.permission.INTERNET, ctx.packageName) != PackageManager.PERMISSION_GRANTED
    val backupOff = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
    val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val level = remember { app.deviceKeys.securityLevel() }
    val screenLock = remember { ctx.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true }
    val signer = remember { signerSha256(ctx) }
    val audit = remember { app.session.audit() }
    val s by app.prefs.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Trust & proofs", onBack = { nav.pop() })
        ScreenColumn {
            Text("Live checks of this installed copy. Don't take our word for it: you can verify each one in Android Settings.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            AppCard {
                StatusRow("No internet permission", if (noInternet) "Android blocks every network connection from Tijori" else "This build CAN use the network. Don't trust it.", noInternet)
                StatusRow("Cloud & device-transfer backup off", if (backupOff) "Google backup never copies your vault" else "Backup flag is on", backupOff)
                StatusRow("Keys in secure hardware", when (level) {
                    DeviceKeys.Level.StrongBox -> "StrongBox: a separate security chip"
                    DeviceKeys.Level.TEE -> "Trusted Execution Environment"
                    DeviceKeys.Level.Software -> "This phone has no secure hardware for keys"
                    DeviceKeys.Level.Unknown -> "Couldn't read the key's security level"
                }, level == DeviceKeys.Level.StrongBox || level == DeviceKeys.Level.TEE)
                StatusRow("Phone screen lock set", if (screenLock) "Required for hardware-bound fingerprint keys" else "Set a PIN or pattern in Android Settings", screenLock)
                StatusRow("Screenshots blocked", if (s.blockScreenshots) "On" else "Off", s.blockScreenshots)
                StatusRow("Fingerprint unlock", if (app.session.biometricReady) "On, per-use, invalidated on new enrolment" else "Off", if (host.biometricAvailable) true else null)
                StatusRow("Release build", if (debuggable) "Debug build: for testing only" else "Not debuggable", !debuggable)
            }
            AppCard(title = "How your vault is locked") {
                Text("• Argon2id (64 MiB) stretches your passphrase, combined with a 128-bit Secret Key via HKDF-SHA256.\n" +
                    "• Each room has its own random 256-bit key. Every item is sealed with AES-256-GCM, bound to its vault, item and room.\n" +
                    "• The Money key only enters memory after a fresh fingerprint or passphrase, and is wiped when the room closes.\n" +
                    "• The Family key is split with Shamir's secret sharing for nominee cards.\n" +
                    "• Titles and usernames are encrypted too: the database only shows how many rows each room has.",
                    style = MaterialTheme.typography.bodySmall)
            }
            AppCard(title = "App signature") {
                Text("Compare this with the fingerprint published by the developer to be sure your copy is genuine.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(signer, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono))
            }
            AppCard(title = "Recent activity on this phone") {
                if (audit.isEmpty()) Text("Nothing yet", color = Muted)
                audit.take(15).forEach { e ->
                    Row { Text(e.kind.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(relativeTime(e.at), style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
        }
    }
}

private fun signerSha256(ctx: android.content.Context): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    else throw IllegalStateException()
    val sig = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return@runCatching "Unknown"
    MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
}.getOrDefault("Unknown")

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("About Tijori", onBack = { nav.pop() })
        ScreenColumn {
            AppCard(title = "Privacy policy") {
                Text("Tijori collects no data. It has no internet permission, no analytics, no ads, no crash reporting and no accounts. " +
                    "Everything you save stays encrypted on this phone, and in backup files only you create and control. " +
                    "Clipboard copies of secrets are marked sensitive and cleared automatically. Contact: ${BuildConfig.SUPPORT_EMAIL}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            AppCard(title = "Open-source components") {
                Text("• Bouncy Castle (Argon2id) — MIT licence\n• EFF Large Wordlist — CC BY 3.0, Electronic Frontier Foundation\n" +
                    "• Anek & Mukta fonts — SIL Open Font Licence 1.1\n• AndroidX, Jetpack Compose, Kotlin — Apache 2.0", style = MaterialTheme.typography.bodySmall)
            }
            AppCard(title = "Version") { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
