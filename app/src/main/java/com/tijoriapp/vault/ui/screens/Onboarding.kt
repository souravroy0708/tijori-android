package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.vault.BackupFile
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.data.PdfKits
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.ui.Lang
import com.tijoriapp.vault.ui.LocalHost
import com.tijoriapp.vault.ui.Toasts
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.t
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.launch

private enum class Step { Welcome, Name, Passphrase, Confirm, Creating, Kit, Biometric, Autofill, Restore, Nominee }

@Composable
fun OnboardingFlow(app: TijoriApp, holder: UiHolder) {
    var step by rememberSaveable { mutableStateOf(if (holder.finishingSetup) Step.Kit else Step.Welcome) }
    var name by rememberSaveable { mutableStateOf(app.prefs.now.ownerName) }
    var phrase by remember { mutableStateOf(app.words.words(6, separator = " ")) }
    var custom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bioAvailable = LocalHost.current.biometricAvailable

    fun finish() {
        holder.setupPassphrase = ""
        holder.setupSecretKey = ""
        holder.finishingSetup = false
    }

    val back: (() -> Unit)? = when (step) {
        Step.Name, Step.Restore, Step.Nominee -> ({ step = Step.Welcome })
        Step.Passphrase -> ({ step = Step.Name })
        Step.Confirm -> ({ step = Step.Passphrase })
        else -> null
    }
    BackHandler(enabled = back != null) { back?.invoke() }

    when (step) {
        Step.Welcome -> Welcome(
            onCreate = { step = Step.Name },
            onRestore = { step = Step.Restore },
            onNominee = { step = Step.Nominee },
            onLanguage = { app.prefs.setLanguage(it) },
        )
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
                    Text("6 random words from a 7,776-word list ≈ 77 bits. Would take a modern GPU farm centuries.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("New words", color = CardBg2, textColor = TextMain) { phrase = app.words.words(6, separator = " ") }
                        Pill("I'll write my own", color = CardBg2, textColor = TextMain) { custom = true; phrase = "" }
                    }
                }
                Banner("Write these words on paper now, in order. Separate them with spaces when you type.", icon = Icons.Filled.EditNote)
                BigButton("I've written them down") { step = Step.Confirm }
            } else {
                val res = remember(phrase) { Strength.check(phrase, app.words) }
                SecretInput(phrase, { phrase = it }, t("Master passphrase"))
                if (phrase.isNotEmpty()) StrengthMeter(res)
                Text("Use 4+ unrelated words or 14+ mixed characters. Avoid names, birthdays and phone numbers.", style = MaterialTheme.typography.bodySmall, color = Muted)
                BigButton(t("Continue"), enabled = res.level >= Strength.Level.Strong) { step = Step.Confirm }
                TextLink("Use suggested words instead") { custom = false; phrase = app.words.words(6, separator = " ") }
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
                            .onSuccess { holder.setupSecretKey = it; step = Step.Kit }
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
        Step.Kit -> KitStep(app, holder) { step = if (bioAvailable) Step.Biometric else Step.Autofill }
        Step.Biometric -> BiometricStep(app, holder) { step = Step.Autofill }
        Step.Autofill -> AutofillStep { finish() }
        Step.Restore -> RestoreScreen(app, onBack = { step = Step.Welcome })
        Step.Nominee -> NomineeScreen(app, practice = null, onClose = { step = Step.Welcome })
    }
}

@Composable
private fun Welcome(onCreate: () -> Unit, onRestore: () -> Unit, onNominee: () -> Unit, onLanguage: (String) -> Unit) {
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
        Promise(Icons.Filled.CloudOff, "No internet permission", "Android itself blocks Tijori from the network. Nothing is ever uploaded.")
        Promise(Icons.Filled.Key, "Only you hold the keys", "No account, no server, no reset link. Not even we can open it.")
        Promise(Icons.Filled.FamilyRestroom, "Your family isn't locked out", "Nominee cards let them open the Family room, and only that.")
        Spacer(Modifier.height(8.dp))
        BigButton(t("Create my Tijori"), color = Haldi, onClick = onCreate)
        GhostButton(t("Restore from backup"), onDark = true, icon = Icons.Filled.Restore, onClick = onRestore)
        TextLink(t("I'm a nominee"), color = Mint, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onNominee)
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
    SetupPage("Almost done", "Your Emergency Kit", "Your Secret Key is the second half of your vault's key. You'll need it only on a new phone, together with your backup file.", null) {
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
        BigButton(t("Continue"), enabled = saved || wrote) { onNext() }
        Text("You can save it again later from Settings.", style = MaterialTheme.typography.bodySmall, color = Muted)
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
    var summary by remember { mutableStateOf<String?>(null) }
    var sk by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    SetupPage("Restore", "Bring your vault back", "You need three things: your .tijori backup file, the Secret Key from your Emergency Kit, and your passphrase.", onBack) {
        AppCard(title = "1. Backup file") {
            if (summary != null) StatusRow("File looks good", summary!!, true)
            Pill(if (file == null) "Choose backup file" else "Choose a different file") {
                host.openFile(arrayOf("*/*")) { bytes ->
                    if (bytes == null) return@openFile
                    runCatching { BackupFile.read(bytes) }
                        .onSuccess { p ->
                            file = bytes; error = null
                            summary = "${p.rows.size} items · made ${relativeTime(p.exportedAt)}"
                        }
                        .onFailure { file = null; summary = null; error = it.message }
                }
            }
        }
        AppCard(title = "2. Secret Key") {
            val ok = SecretKey.parse(sk) != null
            PlainInput(sk, { sk = it.uppercase() }, "Secret Key", placeholder = "TJ-XXXX-XXXX-…", mono = true,
                supporting = when { sk.isBlank() -> "From your printed Emergency Kit"; ok -> "Looks right ✓"; else -> "Keep typing. Each group has 4 characters." },
                isError = sk.length > 36 && !ok)
        }
        AppCard(title = "3. Master passphrase") {
            SecretInput(pass, { pass = it }, t("Master passphrase"))
        }
        if (error != null) Banner(error!!, BadBg, BadText, Icons.Filled.Warning)
        BigButton("Restore my vault", loading = busy, enabled = file != null && SecretKey.parse(sk) != null && pass.isNotBlank()) {
            busy = true; error = null
            scope.launch {
                val r = app.session.restore(file!!, sk, pass)
                busy = false
                if (r is UnlockResult.Ok) { Toasts.show("Welcome back. Your vault is restored."); onRestored() } else error = describe(r)
            }
        }
    }
}
