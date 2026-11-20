package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.vault.BackupFile
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.data.PdfKits
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.data.DriveBackup
import com.tijoriapp.vault.ui.Dest
import com.tijoriapp.vault.ui.Lang
import com.tijoriapp.vault.ui.LocalHost
import com.tijoriapp.vault.ui.Toasts
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.t
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.launch

private enum class Step { Intro, Welcome, Name, Passphrase, Confirm, Creating, Drive, Kit, Biometric, Autofill, Done, Restore, Nominee, Help }

@Composable
fun OnboardingFlow(app: TijoriApp, holder: UiHolder) {
    var step by rememberSaveable {
        mutableStateOf(when { holder.finishingSetup -> Step.Drive; !app.prefs.now.introSeen -> Step.Intro; else -> Step.Welcome })
    }
    var name by rememberSaveable { mutableStateOf(app.prefs.now.ownerName) }
    var wordCount by rememberSaveable { mutableIntStateOf(5) }
    var phrase by remember { mutableStateOf(app.words.words(5, separator = " ")) }
    var custom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bioAvailable = LocalHost.current.biometricAvailable

    fun finish() {
        holder.setupPassphrase = ""
        holder.setupSecretKey = ""
        holder.finishingSetup = false
    }

    val back: (() -> Unit)? = when (step) {
        Step.Name, Step.Restore, Step.Nominee, Step.Help -> ({ step = Step.Welcome })
        Step.Passphrase -> ({ step = Step.Name })
        Step.Confirm -> ({ step = Step.Passphrase })
        else -> null
    }
    BackHandler(enabled = back != null) { back?.invoke() }

    when (step) {
        Step.Intro -> IntroPager(onDone = { app.prefs.setIntroSeen(); step = Step.Welcome })
        Step.Welcome -> Welcome(
            onCreate = { step = Step.Name },
            onRestore = { step = Step.Restore },
            onNominee = { step = Step.Nominee },
            onHelp = { step = Step.Help },
            onLanguage = { app.prefs.setLanguage(it) },
        )
        Step.Help -> HelpScreen("reinstall", onBack = { step = Step.Welcome })
        Step.Name -> SetupPage("1 of 3", "What should we call you?", "Printed on your Emergency Kit and family cards. Stays on this phone.", back) {
            PlainInput(name, { name = it.take(40) }, "Your name", placeholder = "e.g. Sourav")
            Spacer(Modifier.height(8.dp))
            BigButton(t("Continue")) { app.prefs.setOwner(name); step = Step.Passphrase }
        }
        Step.Passphrase -> SetupPage("2 of 3", "Your master passphrase",
            "The one thing you remember. Tijori can't reset it: no company can open your vault, not even us.", back) {
            if (!custom) {
                AppCard {
                    Text("Suggested for you", style = MaterialTheme.typography.labelMedium, color = Muted)
                    val words = phrase.split(" ")
                    words.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { i, w ->
                                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CardBg2).padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row * 2 + i + 1}", color = Muted, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(8.dp))
                                    Text(w, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono))
                                }
                            }
                        }
                    }
                    Segmented(listOf("5 words · easier", "6 words · strongest"), if (wordCount == 5) 0 else 1) {
                        wordCount = if (it == 0) 5 else 6; phrase = app.words.words(wordCount, separator = " ")
                    }
                    Text(if (wordCount == 5) "5 random words ≈ 64 bits. Easy to remember, and would take attackers many thousands of years to guess."
                        else "6 random words ≈ 77 bits. Would take a modern GPU farm millions of years.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("New words", color = CardBg2, textColor = TextMain) { phrase = app.words.words(wordCount, separator = " ") }
                        Pill("I'll write my own", color = CardBg2, textColor = TextMain) { custom = true; phrase = "" }
                    }
                }
                Banner("Write these words on paper now, in order. This is the ONE thing you must remember: it also restores your Google Drive backup.", icon = Icons.Filled.EditNote)
                BigButton("I've written them down") { step = Step.Confirm }
            } else {
                val res = remember(phrase) { Strength.check(phrase, app.words) }
                SecretInput(phrase, { phrase = it }, t("Master passphrase"))
                if (phrase.isNotEmpty()) StrengthMeter(res)
                Text("Use 4+ unrelated words or 14+ mixed characters. Avoid names, birthdays and phone numbers.", style = MaterialTheme.typography.bodySmall, color = Muted)
                BigButton(t("Continue"), enabled = res.level >= Strength.Level.Strong) { step = Step.Confirm }
                TextLink("Use suggested words instead") { custom = false; phrase = app.words.words(wordCount, separator = " ") }
            }
        }
        Step.Confirm -> {
            var typed by remember { mutableStateOf("") }
            val matches = VaultCrypto.normalize(typed).contentEquals(VaultCrypto.normalize(phrase))
            SetupPage("3 of 3", "Type it once more", "From memory or your paper. This makes sure there's no typo in the key that locks everything.", back) {
                SecretInput(typed, { typed = it }, t("Master passphrase"), imeAction = ImeAction.Done,
                    isError = typed.isNotEmpty() && !matches && typed.length >= phrase.length,
                    supporting = if (typed.isNotEmpty() && matches) "Matches ✓" else null)
                BigButton("Create my vault", enabled = matches) {
                    step = Step.Creating
                    holder.finishingSetup = true
                    holder.setupPassphrase = phrase
                    scope.launch {
                        runCatching { app.session.create(phrase, name) }
                            .onSuccess { holder.setupSecretKey = it; step = Step.Drive }
                            .onFailure { holder.finishingSetup = false; step = Step.Confirm; Toasts.show("Couldn't create the vault: ${it.message}") }
                    }
                }
            }
        }
        Step.Creating -> Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = Haldi)
                Text("Forging your keys…", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Argon2id is deliberately slow and memory-hungry,\nso guessing your passphrase is too.", color = MutedOnDark,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        Step.Drive -> DriveStep(app, holder) { step = Step.Kit }
        Step.Kit -> KitStep(app, holder) { step = if (bioAvailable) Step.Biometric else Step.Autofill }
        Step.Biometric -> BiometricStep(app, holder) { step = Step.Autofill }
        Step.Autofill -> AutofillStep { step = Step.Done }
        Step.Done -> DoneStep(app) { next ->
            finish()
            if (next != null) holder.nav.push(next)
        }
        Step.Restore -> RestoreScreen(app, onBack = { step = Step.Welcome })
        Step.Nominee -> NomineeScreen(app, practice = null, onClose = { step = Step.Welcome })
    }
}

@Composable
private fun Welcome(onCreate: () -> Unit, onRestore: () -> Unit, onNominee: () -> Unit, onHelp: () -> Unit, onLanguage: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Ink2))).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Segmented(listOf("English", "हिन्दी"), if (Lang.current == "hi") 1 else 0, Modifier.width(190.dp)) { onLanguage(if (it == 1) "hi" else "en") }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(52.dp))
        }
        Text("Tijori", color = Color.White, style = MaterialTheme.typography.displayLarge)
        Text("चाबी सिर्फ़ आपके पास", color = Haldi, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Devanagari))
        Text(t("Your vault, only on this phone"), color = MutedOnDark, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Promise(Icons.Filled.Key, "Only you hold the keys", "No account, no server, no reset link. Not even we can open it.")
        Promise(Icons.Filled.CloudDone, "Safe in your Google Drive", "An encrypted copy in your own Drive. Uninstall or lose the phone: your passwords come back.")
        Promise(Icons.Filled.CloudOff, "No internet permission", "Tijori can't connect anywhere. The Drive app carries the locked file.")
        Spacer(Modifier.height(8.dp))
        BigButton(t("Create my Tijori"), color = Haldi, onClick = onCreate)
        GhostButton(t("Restore from Google Drive"), onDark = true, icon = Icons.Filled.CloudDownload, onClick = onRestore)
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextLink(t("I'm a nominee"), color = Mint, onClick = onNominee)
            TextLink("Reinstalled? How to restore", color = MutedOnDark, onClick = onHelp)
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ink3), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Haldi, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(t(title), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(t(body), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SetupPage(stepLabel: String, title: String, subtitle: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    HeroPage(hero = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back"), tint = Color.White)
            }
            Text(stepLabel, color = Haldi, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
    }, modifier = Modifier.imePadding(), sheet = content)
}

@Composable
private fun KitStep(app: TijoriApp, holder: UiHolder, onNext: () -> Unit) {
    val host = LocalHost.current
    val sk = holder.setupSecretKey.ifEmpty { runCatching { app.session.secretKeyText() }.getOrDefault("") }
    var saved by remember { mutableStateOf(app.prefs.now.kitSavedAt > 0) }
    var wrote by remember { mutableStateOf(false) }
    val easy = app.session.easyRestoreOn
    SetupPage("Extra safety net", "Your Emergency Kit",
        if (easy) "Optional. It's a printed copy of your Secret Key: with your passphrase it opens any of your backups, even if easy restore is turned off later."
        else "Your Secret Key is the second half of your vault's key. You'll need it, with your passphrase, to restore your Drive backup.", null) {
        AppCard(background = WarnBg) {
            Text("SECRET KEY", style = MaterialTheme.typography.labelMedium, color = WarnText)
            Text(sk, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 19.sp), color = TextMain)
            Text("A stolen backup file is useless without this key AND your passphrase.", style = MaterialTheme.typography.bodySmall, color = WarnText)
        }
        BigButton(if (saved) "Saved ✓  Save again" else "Save Emergency Kit (PDF)", icon = Icons.Filled.Print, color = if (saved) Mint else null) {
            host.createFile("Tijori-Emergency-Kit.pdf", "application/pdf",
                write = { PdfKits(app).emergencyKit(it, app.prefs.now.ownerName, sk, app.session.state.value.vaultId) },
                onDone = { ok -> if (ok) { app.prefs.markKitSaved(); saved = true; Toasts.show("Emergency Kit saved. Print it and keep it with your papers.") } })
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Checkbox) { wrote = !wrote }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = wrote, onCheckedChange = { wrote = it })
            Text("I wrote the Secret Key on paper instead", style = MaterialTheme.typography.bodyMedium)
        }
        BigButton(t("Continue"), enabled = saved || wrote || easy) { onNext() }
        Text(if (easy && !saved && !wrote) "You can skip this: your passphrase alone restores your Drive backup. Save it later from Settings."
            else "You can save it again later from Settings.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun BiometricStep(app: TijoriApp, holder: UiHolder, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    SetupPage("Faster unlock", "Unlock with your fingerprint?", "Your fingerprint never leaves the phone's secure chip. The Money room still asks again every time.", null) {
        AppCard {
            StatusRow("Hardware-bound key", "Destroyed automatically if someone adds a new fingerprint", true)
            StatusRow("Passphrase still works", "Use it any time, and after every restart of the phone", true)
        }
        BigButton(t("Unlock with fingerprint"), icon = Icons.Filled.Fingerprint, loading = busy) {
            busy = true
            scope.launch {
                val r = app.session.enableBiometric(holder.setupPassphrase.ifEmpty { null })
                busy = false
                describe(r)?.let { Toasts.show(it) } ?: Toasts.show("Fingerprint unlock is on")
                onNext()
            }
        }
        GhostButton("Not now") { onNext() }
    }
}

@Composable
private fun AutofillStep(onDone: () -> Unit) {
    val host = LocalHost.current
    SetupPage("Last thing", "Fill passwords in apps", "Make Tijori your autofill service. It only fills on the exact app or website you saved, which stops phishing look-alikes.", null) {
        AppCard {
            StatusRow("You stay in control", "Nothing fills until you unlock and tap the login", true)
            StatusRow("Money logins ask again", "Bank logins need a fresh fingerprint before filling", true)
            StatusRow("Chrome users", "In Chrome: Settings → Autofill services → \"Autofill using another service\"", null)
        }
        BigButton("Set up autofill", icon = Icons.Filled.Password) { host.openAutofillSettings(); onDone() }
        GhostButton("Maybe later") { onDone() }
    }
}

@Composable
fun RestoreScreen(app: TijoriApp, onBack: () -> Unit, onRestored: () -> Unit = {}) {
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var fileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var parsed by remember { mutableStateOf<BackupFile.Parsed?>(null) }
    var useKey by remember { mutableStateOf(false) }
    var keepBackingUp by remember { mutableStateOf(true) }
    var sk by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    val p = parsed
    val needKey = p != null && (!p.easyRestore || useKey)
    SetupPage("Restore", "Bring your passwords back",
        "Reinstalled Tijori or got a new phone? Open your backup from Google Drive and type your passphrase.", onBack) {
        AppCard(title = "1. Your backup file") {
            if (p == null) {
                HintLine(Icons.Filled.Menu, "Tap ☰ (top-left) → Drive")
                HintLine(Icons.Filled.Description, "Open ${DriveBackup.FILE_NAME} (or any .tijori file)")
            } else {
                StatusRow("Backup found", "${p.rows.size} items · saved ${relativeTime(p.exportedAt)}" +
                    (fileUri?.let { " · ${DriveBackup.where(it)}" } ?: ""), true)
            }
            Pill(if (p == null) "Find my backup" else "Choose a different file") {
                host.openBackupFile { uri, bytes ->
                    if (bytes == null) { if (uri != null) error = "Couldn't read that file. If it's in Drive, check your internet and try again."; return@openBackupFile }
                    runCatching { BackupFile.read(bytes) }
                        .onSuccess { r -> file = bytes; fileUri = uri; parsed = r; error = null; useKey = false }
                        .onFailure { file = null; parsed = null; error = it.message }
                }
            }
        }
        if (p != null) {
            AppCard(title = "2. Your master passphrase") {
                SecretInput(pass, { pass = it; error = null }, t("Master passphrase"))
                if (p.easyRestore && !useKey) {
                    Text("This backup opens with your passphrase alone.", style = MaterialTheme.typography.bodySmall, color = GoodText)
                    TextLink("Use my Secret Key instead") { useKey = true }
                }
            }
            if (needKey) AppCard(title = "3. Secret Key") {
                val ok = SecretKey.parse(sk) != null
                PlainInput(sk, { sk = it.uppercase() }, "Secret Key", placeholder = "TJ-XXXX-XXXX-…", mono = true,
                    supporting = when { sk.isBlank() -> "Printed on your Emergency Kit"; ok -> "Looks right ✓"; else -> "Keep typing. Each group has 4 characters." },
                    isError = sk.length > 36 && !ok)
            }
            if (fileUri != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Checkbox) { keepBackingUp = !keepBackingUp },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepBackingUp, onCheckedChange = { keepBackingUp = it })
                Text("Keep backing up to this file automatically", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (error != null) Banner(error!!, BadBg, BadText, Icons.Filled.Warning)
        BigButton("Restore my vault", loading = busy, color = Haldi,
            enabled = file != null && pass.isNotBlank() && (!needKey || SecretKey.parse(sk) != null)) {
            busy = true; error = null
            scope.launch {
                val r = app.session.restore(file!!, if (needKey) sk else "", pass)
                if (r is UnlockResult.Ok) {
                    val uri = fileUri
                    if (keepBackingUp && uri != null) app.drive.connect(uri)
                    busy = false
                    Toasts.show("Welcome back. Your vault is restored.")
                    onRestored()
                } else { busy = false; error = describe(r) }
            }
        }
        Text("Restoring takes a few seconds: Tijori deliberately makes each passphrase guess slow.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun HintLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// =========================================================================================
// New first-run steps
// =========================================================================================

private data class Slide(val icon: ImageVector, val accent: Color, val title: String, val body: String)

private val SLIDES = listOf(
    Slide(Icons.Filled.Shield, Haldi, "All your passwords. One tap away.",
        "Gmail, banking, shopping, Wi-Fi. Tijori remembers them and fills them in apps and Chrome, so you only remember one passphrase."),
    Slide(Icons.Filled.CloudDone, Mint, "Uninstall? Lose the phone? Nothing is lost.",
        "An encrypted copy lives in your own Google Drive. Reinstall, type your passphrase, and everything comes back."),
    Slide(Icons.Filled.Lock, Violet, "Nobody else can open it. Not even us.",
        "Everything is locked on your phone before it's saved anywhere. No account, no server, no ads, no tracking."),
)

@Composable
private fun IntroPager(onDone: () -> Unit) {
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextLink("Skip", color = MutedOnDark, onClick = onDone)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val sl = SLIDES[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(150.dp).clip(CircleShape).background(Ink3), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(100.dp).clip(CircleShape).background(sl.accent), contentAlignment = Alignment.Center) {
                        Icon(sl.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(54.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(sl.title, color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(sl.body, color = MutedOnDark, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(SLIDES.size) { i ->
                Box(Modifier.padding(4.dp).height(8.dp).width(if (pager.currentPage == i) 24.dp else 8.dp).clip(CircleShape)
                    .background(if (pager.currentPage == i) Haldi else Line))
            }
        }
        val last = pager.currentPage == SLIDES.lastIndex
        BigButton(if (last) "Get started" else "Next", color = Haldi) {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

@Composable
private fun DriveStep(app: TijoriApp, holder: UiHolder, onNext: () -> Unit) {
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var easy by remember { mutableStateOf(true) }
    var skipWarn by remember { mutableStateOf(false) }
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    SetupPage("Most important step", "Back up to Google Drive",
        "So an uninstall, a reset or a lost phone never takes your passwords with it. Set it once; Tijori keeps the file up to date.", null) {
        if (s.driveOn && s.driveError.isEmpty()) {
            StatusRow("Backed up", s.driveLabel, true)
            BigButton(t("Continue"), color = Haldi) { onNext() }
            return@SetupPage
        }
        DrivePickerHint()
        AppCard {
            SwitchRow(Icons.Filled.Key, "Restore with passphrase only", if (easy) "Recommended. After a reinstall you only need your passphrase."
                else "Restoring will need your passphrase AND your Emergency Kit.", easy) { easy = it }
        }
        error?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }
        BigButton("Back up to Google Drive", icon = Icons.Filled.CloudUpload, color = Haldi, loading = busy) {
            error = null
            connectDrive(app, host, scope, setupPassphrase = holder.setupPassphrase.ifEmpty { null }, easyRestore = easy, onBusy = { busy = it }) { err ->
                error = err
                if (err == null && app.prefs.now.driveOn) onNext()
            }
        }
        GhostButton("Not now") { skipWarn = true }
    }
    if (skipWarn) ConfirmDialog("Skip the backup?",
        "Without it, uninstalling Tijori or losing this phone deletes every password in it, for good. You can turn it on later in Settings.",
        "Skip anyway", destructive = true, onConfirm = { skipWarn = false; onNext() }, onDismiss = { skipWarn = false })
}

@Composable
private fun DoneStep(app: TijoriApp, onFinish: (Dest?) -> Unit) {
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    SetupPage("All set", "Your Tijori is ready", "Here's the fastest way to fill it.", null) {
        AppCard {
            StatusRow("Vault created", "Locked with your passphrase", true)
            StatusRow("Google Drive backup", if (s.driveOn) "On · updates after every change" else "Off · turn it on in Settings", s.driveOn)
            StatusRow("Easy restore", if (app.session.easyRestoreOn) "Your passphrase alone brings it back" else "Needs passphrase + Emergency Kit", if (app.session.easyRestoreOn) true else null)
        }
        BigButton("Import from Google Password Manager", icon = Icons.Filled.Description, color = Haldi) { onFinish(Dest.Import) }
        GhostButton("Add my Gmail", icon = Icons.Filled.Email) { onFinish(Dest.Edit(null, com.tijoriapp.vault.core.vault.ItemType.EMAIL)) }
        TextLink("Open my vault") { onFinish(null) }
    }
}
