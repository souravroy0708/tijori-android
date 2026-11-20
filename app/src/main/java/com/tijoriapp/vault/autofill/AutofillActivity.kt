package com.tijoriapp.vault.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.vault.Field
import com.tijoriapp.vault.core.vault.FieldKind
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.Urls
import com.tijoriapp.vault.core.vault.VaultItem
import com.tijoriapp.vault.data.ThemeMode
import com.tijoriapp.vault.data.VaultSession
import com.tijoriapp.vault.ui.Host
import com.tijoriapp.vault.ui.LocalHost
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.screens.ItemRow
import com.tijoriapp.vault.ui.screens.MoneyGate
import com.tijoriapp.vault.ui.screens.biometricThen
import com.tijoriapp.vault.ui.screens.describe
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.crypto.Cipher

/** The only place autofill touches the vault: in the foreground, after the user unlocks. */
class AutofillActivity : FragmentActivity(), Host {
    private val app get() = application as TijoriApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val userId = parcel(EXTRA_USER_ID)
        val passId = parcel(EXTRA_PASS_ID)
        val saving = intent.getBooleanExtra(EXTRA_SAVE, false)
        val appLabel = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        setResult(Activity.RESULT_CANCELED)

        setContent {
            val s by app.prefs.settings.collectAsStateWithLifecycle()
            val dark = when (s.theme) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
            TijoriTheme(dark) {
                CompositionLocalProvider(LocalHost provides this) {
                    val state by app.session.state.collectAsStateWithLifecycle()
                    Column(Modifier.fillMaxSize().background(Paper)) {
                        TopBar(if (saving) "Save to Tijori" else "Fill with Tijori", onBack = { finish() })
                        ScreenColumn {
                            when (state.phase) {
                                VaultSession.Phase.NoVault -> EmptyState(Icons.Filled.Shield, "No vault yet", "Open Tijori and create your vault first.")
                                VaultSession.Phase.Locked -> UnlockCard()
                                VaultSession.Phase.Unlocked -> if (saving) SaveContent(state, pkg, domain, appLabel)
                                    else FillContent(state, pkg, domain, appLabel, userId, passId)
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun parcel(key: String): AutofillId? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(key, AutofillId::class.java) else intent.getParcelableExtra(key)

    @Composable
    private fun UnlockCard() {
        val scope = rememberCoroutineScope()
        var pass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val bio = app.session.biometricReady && biometricAvailable
        fun bioUnlock() = biometricThen(app, this, "Unlock Tijori", "To fill your login", scope, use = { app.session.unlockWithBiometric(it) }, onError = { error = it })
        LaunchedEffect(Unit) { if (bio) bioUnlock() }
        AppCard(title = "Unlock to continue") {
            if (bio) BigButton("Use fingerprint", icon = Icons.Filled.Fingerprint, color = Haldi) { bioUnlock() }
            SecretInput(pass, { pass = it; error = null }, "Master passphrase", isError = error != null, supporting = error)
            BigButton("Unlock", loading = busy, enabled = pass.isNotBlank()) {
                busy = true
                scope.launch { error = describe(app.session.unlockWithPassphrase(pass)); busy = false }
            }
        }
    }

    @Composable
    private fun FillContent(state: VaultSession.State, pkg: String, domain: String?, appLabel: String, userId: AutofillId?, passId: AutofillId?) {
        val scope = rememberCoroutineScope()
        var showAll by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var linkAsk by remember { mutableStateOf<VaultItem?>(null) }
        val matches = state.items.filter { item ->
            if (domain != null) item.hosts.any { Urls.sameSite(it, domain) } else pkg in item.apps
        }
        val where = domain ?: appLabel
        StatusRow(where, if (domain != null) "Website in ${appLabel}" else pkg, true)
        if (matches.isNotEmpty()) {
            SectionLabel("Saved for $where")
            matches.forEach { item -> ItemRow(item) { fill(item, userId, passId) } }
        } else Banner("Nothing saved for $where yet.", icon = Icons.Filled.Info)
        if (!state.moneyOpen && (state.counts[Room.MONEY] ?: 0) > 0) MoneyGate(app, compact = true)

        if (!showAll) TextLink("Choose another login…") { showAll = true }
        else {
            Banner(if (domain != null) "Only continue if this really is $domain. Check the address bar." else "Only continue if you trust $appLabel. Phishing apps copy real names and logos.",
                BadBg, BadText, Icons.Filled.Warning)
            PlainInput(query, { query = it }, "Search")
            state.items.filter { it.password != null && it.matches(query) && it !in matches }.sortedBy { it.title.lowercase() }.take(30).forEach { item ->
                ItemRow(item) { linkAsk = item }
            }
        }
        linkAsk?.let { item ->
            ConfirmDialog("Fill \"${item.title}\"?", if (domain != null) "Tijori will also remember $domain for this login." else "Tijori will remember $appLabel for this login, so it shows up first next time.",
                "Fill", onConfirm = {
                    linkAsk = null
                    val updated = if (domain != null) item.copy(fields = item.fields + Field("Website", FieldKind.URL, "https://$domain")) else item.copy(apps = (item.apps + pkg).distinct())
                    scope.launch { app.session.save(updated); fill(updated, userId, passId) }
                }, onDismiss = { linkAsk = null })
        }
    }

    @Composable
    private fun SaveContent(state: VaultSession.State, pkg: String, domain: String?, appLabel: String) {
        val scope = rememberCoroutineScope()
        val user = intent.getStringExtra(EXTRA_USER_VALUE).orEmpty()
        val pass = intent.getStringExtra(EXTRA_PASS_VALUE).orEmpty()
        val existing = state.items.firstOrNull { item ->
            (if (domain != null) item.hosts.any { Urls.sameSite(it, domain) } else pkg in item.apps) && item.username == user && user.isNotEmpty()
        }
        var title by remember { mutableStateOf(existing?.title ?: (domain ?: appLabel)) }
        var room by remember { mutableStateOf(existing?.room ?: Room.EVERYDAY) }
        if (existing != null) Banner("You already have \"${existing.title}\" for this account. Saving updates its password.", GoodBg, GoodText, Icons.Filled.Info)
        PlainInput(title, { title = it }, "Title")
        AppCard {
            Text("Username", style = MaterialTheme.typography.labelMedium, color = Muted)
            Text(user.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
            Text("Password", style = MaterialTheme.typography.labelMedium, color = Muted)
            Text("•".repeat(10), style = MaterialTheme.typography.bodyLarge)
        }
        Segmented(Room.entries.map { it.label() }, Room.entries.indexOf(room)) { room = Room.entries[it] }
        if (!app.session.canWrite(room)) MoneyGate(app, compact = true)
        BigButton(if (existing != null) "Update password" else "Save login", enabled = title.isNotBlank() && app.session.canWrite(room)) {
            val item = existing?.copy(title = title, room = room, fields = existing.fields.map { if (it.kind == FieldKind.PASSWORD && it.value == existing.password) it.copy(value = pass) else it })
                ?: VaultItem(room = room, type = ItemType.LOGIN, title = title, fields = buildList {
                    add(Field("Username", FieldKind.USERNAME, user))
                    add(Field("Password", FieldKind.PASSWORD, pass))
                    if (domain != null) add(Field("Website", FieldKind.URL, "https://$domain"))
                }, apps = if (domain == null) listOf(pkg) else emptyList())
            scope.launch {
                if (app.session.save(item)) { setResult(Activity.RESULT_OK); finish() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fill(item: VaultItem, userId: AutofillId?, passId: AutofillId?) {
        val b = Dataset.Builder()
        var any = false
        val username = item.username
        val password = item.password
        if (userId != null && username != null) { b.setValue(userId, AutofillValue.forText(username)); any = true }
        if (passId != null && password != null) { b.setValue(passId, AutofillValue.forText(password)); any = true }
        if (!any) return
        app.session.markUsed(item.id)
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, b.build()))
        finish()
    }

    // ----- Host (only what autofill needs) -----
    override val biometricAvailable: Boolean
        get() = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    override val autofillEnabled: Boolean get() = true

    override fun biometric(title: String, subtitle: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFail: (String?) -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess) ?: onFail("Use your passphrase.")
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) null else errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG).setNegativeButtonText("Use passphrase").setConfirmationRequired(false).build()
        runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }.onFailure { onFail("Fingerprint isn't available right now.") }
    }

    override fun createFile(fileName: String, mime: String, write: suspend (OutputStream) -> Unit, onDone: (Boolean) -> Unit) = onDone(false)
    override fun openFile(mimes: Array<String>, onRead: (ByteArray?) -> Unit) = onRead(null)
    override fun openAutofillSettings() {}
    override fun openUrl(url: String) {}

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_DOMAIN = "domain"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_PASS_ID = "pass_id"
        private const val EXTRA_SAVE = "save"
        private const val EXTRA_USER_VALUE = "user_value"
        private const val EXTRA_PASS_VALUE = "pass_value"

        fun fillIntent(ctx: Context, form: FormParser.Form): Intent = Intent(ctx, AutofillActivity::class.java)
            .putExtra(EXTRA_PKG, form.packageName)
            .putExtra(EXTRA_DOMAIN, form.webDomain)
            .putExtra(EXTRA_USER_ID, form.username)
            .putExtra(EXTRA_PASS_ID, form.password)

        fun saveIntent(ctx: Context, form: FormParser.Form, user: String, pass: String): Intent = fillIntent(ctx, form)
            .putExtra(EXTRA_SAVE, true)
            .putExtra(EXTRA_USER_VALUE, user)
            .putExtra(EXTRA_PASS_VALUE, pass)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
