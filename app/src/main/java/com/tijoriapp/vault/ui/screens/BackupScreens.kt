package com.tijoriapp.vault.ui.screens

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.vault.CsvImport
import com.tijoriapp.vault.data.DriveBackup
import com.tijoriapp.vault.data.PdfKits
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================================================================
// Google Drive backup
// =========================================================================================

/** The 3-line explanation shown before the system "Save to…" screen opens. */
@Composable
fun DrivePickerHint() {
    AppCard(background = CardBg2) {
        Text("On the next screen", style = MaterialTheme.typography.labelMedium, color = Muted)
        HintStep(1, "Tap ☰ (top-left) and choose Drive")
        HintStep(2, "Open any folder, or stay in My Drive")
        HintStep(3, "Tap Save. That's it: Tijori keeps this one file up to date")
        Text("Don't see Drive? Install the Google Drive app and sign in with your Gmail, then try again.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun HintStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
            Text("$n", color = Ink, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Opens the picker, optionally turns on easy restore, then connects and makes the first backup.
 * [setupPassphrase] is only known during first-run setup; elsewhere easy restore is switched on separately.
 */
fun connectDrive(
    app: TijoriApp, host: Host, scope: kotlinx.coroutines.CoroutineScope,
    setupPassphrase: String?, easyRestore: Boolean,
    onBusy: (Boolean) -> Unit, onResult: (String?) -> Unit,
) {
    host.pickBackupDestination { uri: Uri? ->
        if (uri == null) { onResult(null); return@pickBackupDestination }
        onBusy(true)
        scope.launch {
            if (easyRestore && setupPassphrase != null && !app.session.easyRestoreOn) app.session.enableEasyRestore(setupPassphrase)
            val r = app.drive.connect(uri)
            onBusy(false)
            when (r) {
                DriveBackup.Outcome.Ok -> {
                    Toasts.show(if (DriveBackup.isGoogleDrive(uri)) "Backed up to Google Drive" else "Backed up. For uninstall safety, Google Drive is best.")
                    onResult(null)
                }
                is DriveBackup.Outcome.Failed -> onResult(r.message)
                DriveBackup.Outcome.Off -> onResult("Couldn't turn on the backup")
            }
        }
    }
}

@Composable
fun DriveBackupScreen(app: TijoriApp) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var easy by remember { mutableStateOf(app.session.easyRestoreOn) }
    var askPass by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var kitReauth by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(t("Google Drive backup"), onBack = { nav.pop() }) {
            IconButton(onClick = { nav.push(Dest.Help("drive")) }) { Icon(Icons.Filled.Help, contentDescription = "How Drive backup works") }
        }
        ScreenColumn {
            // ---------- status ----------
            val (bg, fg, icon, title, body) = when {
                !s.driveOn -> Quint(WarnBg, WarnText, Icons.Filled.CloudOff, "Not backed up yet",
                    "If you uninstall Tijori or lose this phone, everything in it is gone. Turn this on once and forget about it.")
                s.driveError.isNotEmpty() -> Quint(BadBg, BadText, Icons.Filled.SyncProblem, "Backup needs attention", s.driveError)
                s.drivePending -> Quint(GoodBg, GoodText, Icons.Filled.Sync, "Backing up…", s.driveLabel)
                else -> Quint(GoodBg, GoodText, Icons.Filled.CloudDone, "Backed up ${relativeTime(s.driveBackupAt)}", s.driveLabel)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = fg)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = fg)
                }
            }
            error?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }

            if (!s.driveOn) {
                DrivePickerHint()
                BigButton("Back up to Google Drive", icon = Icons.Filled.CloudUpload, loading = busy, color = Haldi) {
                    error = null
                    connectDrive(app, host, scope, setupPassphrase = null, easyRestore = false, onBusy = { busy = it }) { error = it }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Back up now", Modifier.weight(1f), loading = busy, icon = Icons.Filled.Sync) {
                        busy = true; error = null
                        scope.launch {
                            val r = app.drive.backupNow()
                            busy = false
                            if (r is DriveBackup.Outcome.Failed) error = r.message else Toasts.show("Backed up")
                        }
                    }
                    GhostButton("Change", Modifier.weight(1f), icon = Icons.Filled.FolderOpen) {
                        error = null
                        connectDrive(app, host, scope, setupPassphrase = null, easyRestore = false, onBusy = { busy = it }) { error = it }
                    }
                }
            }

            // ---------- easy restore ----------
            SectionLabel("After an uninstall or on a new phone")
            AppCard {
                SwitchRow(Icons.Filled.Key, "Restore with passphrase only", if (easy) "Recommended for most people. No Emergency Kit needed." else "Off: restoring needs your passphrase AND your Secret Key.", easy) { on ->
                    if (on) askPass = true else { app.session.disableEasyRestore(); easy = false; Toasts.show("Restoring now needs your Secret Key too") }
                }
                Text(
                    if (easy) "Your Drive copy is locked by your master passphrase, stretched with Argon2id so each guess is slow. " +
                        "Keep a strong passphrase (Tijori won't accept a weak one)."
                    else "Maximum protection: someone with your Drive file would also need the 26-character Secret Key printed on your Emergency Kit. Keep that kit safe, or you can't restore.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                SettingRow(Icons.Filled.Print, "Emergency Kit (PDF)", if (s.kitSavedAt == 0L) "Not saved yet. A good extra safety net." else "Saved ${relativeTime(s.kitSavedAt)}",
                    onClick = { kitReauth = true })
            }

            // ---------- how it works ----------
            SectionLabel("What happens")
            AppCard {
                StatusRow("Encrypted before it leaves", "Google only stores scrambled data. It can't read a single title or password.", true)
                StatusRow("Updates itself", "A few seconds after every change. One small file, always the latest.", true)
                StatusRow("Survives uninstall", "The file lives in your Drive. Reinstall Tijori → Restore from Google Drive.", true)
                StatusRow("Still no internet permission", "The Google Drive app does the upload. Tijori can't connect anywhere.", true)
            }

            SectionLabel("Other copies")
            AppCard {
                SettingRow(Icons.Filled.GetApp, "Save a copy somewhere else", "Pen drive, laptop or another cloud", onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    host.createFile("Tijori-backup-$date.tijori", "application/octet-stream", write = { it.write(app.session.backupBytes()) },
                        onDone = { ok -> if (ok) { app.prefs.markBackup(); Toasts.show("Copy saved") } })
                })
                if (s.driveOn) SettingRow(Icons.Filled.CloudOff, "Stop Drive backup", "The file stays in Drive; Tijori stops updating it", onClick = { confirmStop = true })
            }
        }
    }

    if (askPass) PassphrasePrompt("Restore with passphrase only", "Type your master passphrase once to seal your Secret Key inside your backups.",
        onDismiss = { askPass = false }) { pass ->
        val r = app.session.enableEasyRestore(pass)
        if (r is UnlockResult.Ok) { askPass = false; easy = true; Toasts.show("Done. Your passphrase alone can restore your backup.") }
        describe(r)
    }
    if (confirmStop) ConfirmDialog("Stop Drive backup?", "New passwords won't be backed up. If you uninstall Tijori after this, anything added later is lost.",
        "Stop", destructive = true, onConfirm = { confirmStop = false; app.drive.disconnect() }, onDismiss = { confirmStop = false })
    if (kitReauth) ReauthDialog(app, "Show Secret Key", "The Emergency Kit contains your Secret Key.", onDismiss = { kitReauth = false }) {
        kitReauth = false
        val sk = app.session.secretKeyText()
        host.createFile("Tijori-Emergency-Kit.pdf", "application/pdf",
            write = { PdfKits(app).emergencyKit(it, app.prefs.now.ownerName, sk, app.session.state.value.vaultId) },
            onDone = { ok -> if (ok) { app.prefs.markKitSaved(); Toasts.show("Emergency Kit saved. Print it, then delete the file.") } })
    }
}

private data class Quint(val bg: Color, val fg: Color, val icon: ImageVector, val title: String, val body: String)

/** One line for the Home hero and Settings: where the latest backup is. */
@Composable
fun BackupStatusChip(app: TijoriApp, onDark: Boolean, onClick: () -> Unit) {
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    val (icon, text, ok) = when {
        !s.driveOn -> Triple(Icons.Filled.CloudOff, "Not backed up · Turn on Drive backup", false)
        s.driveError.isNotEmpty() -> Triple(Icons.Filled.SyncProblem, "Drive backup needs attention", false)
        s.drivePending -> Triple(Icons.Filled.Sync, "Backing up to Drive…", true)
        else -> Triple(Icons.Filled.CloudDone, "Backed up ${relativeTime(s.driveBackupAt)}", true)
    }
    val fg = when { ok && onDark -> Mint; ok -> GoodText; onDark -> Haldi; else -> WarnText }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (onDark) Ink3 else CardBg2).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (onDark) Color.White else TextMain, style = MaterialTheme.typography.labelMedium)
    }
}

// =========================================================================================
// Import from Google Password Manager / other apps
// =========================================================================================

@Composable
fun ImportScreen(app: TijoriApp) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val state by app.session.state.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<CsvImport.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var imported by remember { mutableIntStateOf(-1) }
    var source by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(t("Import passwords"), onBack = { nav.pop() })
        ScreenColumn {
            if (imported >= 0) {
                EmptyState(Icons.Filled.CheckCircle, "$imported passwords imported", "They're in your Everyday room, encrypted like everything else.")
                Banner("Now delete the exported CSV file from Downloads (and from your laptop). It isn't encrypted: anyone who opens it can read every password.",
                    BadBg, BadText, Icons.Filled.Warning)
                BigButton("Check them for weak or reused passwords", icon = Icons.Filled.HealthAndSafety) { nav.reset(MainTab.Security) }
                GhostButton("Done") { nav.pop() }
                return@ScreenColumn
            }

            val r = result
            if (r == null) {
                Text("Moving from Google, Chrome or another app? Bring all your passwords across in one go. The file is read on this phone, offline.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
                Segmented(listOf("Google / Chrome", "Other apps"), source) { source = it }
                if (source == 0) AppCard(title = "From Google Password Manager") {
                    Text("On this phone", style = MaterialTheme.typography.labelMedium, color = Muted)
                    HintStep(1, "Open Google Password Manager (Android Settings → Google → Autofill → Google Password Manager)")
                    HintStep(2, "Tap ⚙ Settings → Export passwords → Export")
                    HintStep(3, "Save the file to Downloads, then come back and tap Choose file")
                    Text("On a computer: open passwords.google.com → ⚙ Settings → Export passwords, then copy the file to your phone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                } else AppCard(title = "From another password manager") {
                    Text("• Bitwarden: Tools → Export vault → .csv\n• 1Password: File → Export → CSV\n• LastPass: Advanced options → Export\n" +
                        "• Firefox: Passwords → ⋯ → Export logins\n• Samsung Pass: export to Chrome or Google first, then export from there",
                        style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }
                BigButton("Choose file", icon = Icons.Filled.Description, loading = busy) {
                    error = null
                    host.openFile(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel", "*/*")) { bytes ->
                        if (bytes == null) return@openFile
                        busy = true
                        scope.launch {
                            val parsed = withContext(Dispatchers.Default) {
                                runCatching { CsvImport.parse(String(bytes, Charsets.UTF_8), existing = state.items) }.also { bytes.fill(0) }
                            }
                            busy = false
                            parsed.onSuccess { result = it }.onFailure { error = it.message ?: "Couldn't read this file" }
                        }
                    }
                }
                StatusRow("Stays on this phone", "Tijori has no internet permission. The file is read here and never sent anywhere.", true)
            } else {
                AppCard {
                    Text("Found in ${r.source.label}", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text("${r.items.size} new passwords", style = MaterialTheme.typography.headlineMedium)
                    if (r.duplicates > 0) Text("${r.duplicates} already in Tijori, skipped", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (r.skipped > 0) Text("${r.skipped} rows without a password (cards, notes), skipped", style = MaterialTheme.typography.bodySmall, color = Muted)
                    val apps = r.items.count { it.apps.isNotEmpty() }
                    if (apps > 0) Text("$apps app logins will autofill in their apps", style = MaterialTheme.typography.bodySmall, color = GoodText)
                }
                if (r.items.isEmpty()) {
                    EmptyState(Icons.Filled.Inbox, "Nothing new to add", "Everything in this file is already in your vault.")
                    GhostButton("Choose another file") { result = null }
                } else {
                    SectionLabel("Preview")
                    r.items.sortedBy { it.title.lowercase() }.take(8).forEach { ItemRow(it) {} }
                    if (r.items.size > 8) Text("…and ${r.items.size - 8} more", style = MaterialTheme.typography.bodySmall, color = Muted)
                    BigButton("Import ${r.items.size} passwords", loading = busy, color = Haldi, icon = Icons.Filled.GetApp) {
                        busy = true
                        scope.launch { imported = app.session.importItems(r.items); busy = false; result = null }
                    }
                    GhostButton(t("Cancel")) { result = null }
                }
            }
        }
    }
}

// =========================================================================================
// Help & guide
// =========================================================================================

private data class HelpTopic(val id: String, val icon: ImageVector, val title: String, val body: String)

private val HELP = listOf(
    HelpTopic("start", Icons.Filled.Flag, "Getting started in 2 minutes",
        "1. Add your Gmail first: tap + Add → Email account. Your email resets every other password, so it matters most.\n" +
            "2. Turn on Google Drive backup (Settings → Google Drive backup).\n" +
            "3. Import the rest from Google Password Manager (Settings → Import passwords).\n" +
            "4. Turn on autofill so Tijori fills logins in apps and Chrome."),
    HelpTopic("drive", Icons.Filled.CloudUpload, "Google Drive backup",
        "Tijori keeps one encrypted file, Tijori-backup.tijori, in your Google Drive and updates it a few seconds after every change.\n\n" +
            "• Google can't read it: titles, usernames and passwords are all encrypted on the phone first.\n" +
            "• Tijori still has no internet permission. The Google Drive app does the upload.\n" +
            "• Set it up once: Settings → Google Drive backup → Back up to Google Drive → ☰ → Drive → Save.\n" +
            "• If the status says \"needs attention\", tap Change and pick the file again."),
    HelpTopic("reinstall", Icons.Filled.Restore, "Uninstalled? Reinstall and restore",
        "1. Install Tijori again from Google Play.\n2. On the first screen tap Restore from Google Drive.\n" +
            "3. Tap ☰ → Drive → open Tijori-backup.tijori.\n4. Type your master passphrase. That's it.\n\n" +
            "If you switched off \"Restore with passphrase only\", you'll also need the Secret Key from your Emergency Kit.\n" +
            "Keep \"Keep backing up to this file\" ticked so backups continue."),
    HelpTopic("newphone", Icons.Filled.PhoneAndroid, "Moving to a new phone",
        "Same as a reinstall: install Tijori on the new phone, tap Restore from Google Drive, pick your backup, type your passphrase. " +
            "Turn fingerprint unlock and autofill on again on the new phone (they're tied to the phone's hardware)."),
    HelpTopic("rooms", Icons.Filled.MeetingRoom, "The three rooms",
        "• Everyday: Gmail, shopping, social, Wi-Fi. Opens with the vault.\n" +
            "• Money: net banking, cards, UPI, investments. Asks for your fingerprint again every time, and locks itself after a minute.\n" +
            "• Family: insurance, locker, will and letters. Your nominees can open this room, and only this one, with their cards."),
    HelpTopic("autofill", Icons.Filled.Password, "Autofill in apps and Chrome",
        "Settings → Autofill service → choose Tijori.\nIn Chrome: ⋮ → Settings → Autofill services → \"Autofill using another service\", then restart Chrome.\n\n" +
            "Tijori fills only on the exact app or website you saved, so look-alike phishing sites get nothing."),
    HelpTopic("import", Icons.Filled.Description, "Import from Google Password Manager",
        "Settings → Import passwords. Export from Google Password Manager (⚙ Settings → Export passwords), then choose that file. " +
            "Duplicates are skipped. Delete the exported CSV afterwards: it isn't encrypted."),
    HelpTopic("fingerprint", Icons.Filled.Fingerprint, "Fingerprint unlock",
        "Settings → Unlock with fingerprint. The key lives in your phone's secure chip and is destroyed if someone adds a new fingerprint, " +
            "so you'd use your passphrase once and turn it on again."),
    HelpTopic("forgot", Icons.Filled.LockReset, "Forgot your passphrase?",
        "Nobody can reset it, not even us: that's what keeps your vault safe. Try variations (capitals, spaces between words, an older passphrase). " +
            "Your nominees can still open the Family room with their cards."),
    HelpTopic("family", Icons.Filled.FamilyRestroom, "Nominee cards for your family",
        "Family tab → make 2-of-3 cards and give them to people you trust. Any two together can open the Family room if something happens to you. " +
            "One card alone reveals nothing."),
    HelpTopic("security", Icons.Filled.HealthAndSafety, "Security check",
        "The Security tab finds weak, reused and old passwords and documents about to expire. It runs on the phone: nothing is sent anywhere to compare."),
)

@Composable
fun HelpScreen(topic: String?, onBack: () -> Unit) {
    var open by remember { mutableStateOf(topic ?: "start") }
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(t("Help & guide"), onBack = onBack)
        ScreenColumn {
            Text("Short answers to what people ask most. Everything here also works offline.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            HELP.forEach { h ->
                val expanded = open == h.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg)
                        .clickable(role = Role.Button) { open = if (expanded) "" else h.id }
                        .padding(16.dp).animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (expanded) Haldi else CardBg2), contentAlignment = Alignment.Center) {
                            Icon(h.icon, contentDescription = null, tint = if (expanded) Ink else TextMain, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(h.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand", tint = Muted)
                    }
                    if (expanded) Text(h.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            AppCard(background = CardBg2) {
                Text("Still stuck?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Write to ${com.tijoriapp.vault.BuildConfig.SUPPORT_EMAIL}. Never send your passphrase, Secret Key or any password: we will never ask for them.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}
