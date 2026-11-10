package com.tijoriapp.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.security.DeviceKeys
import com.tijoriapp.vault.ui.Host
import com.tijoriapp.vault.ui.LocalHost
import com.tijoriapp.vault.ui.Toasts
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.t
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BIO_INVALIDATED_MSG =
    "A new fingerprint or face was added to this phone, so fingerprint unlock was switched off to keep you safe. " +
        "Unlock with your passphrase, then turn it on again in Settings."

/** What a failed passphrase attempt should tell the user. */
fun describe(r: UnlockResult): String? = when (r) {
    UnlockResult.Ok -> null
    is UnlockResult.Wrong -> if (r.waitMillis > 0) "Not right. Too many tries, so wait ${(r.waitMillis / 1000).coerceAtLeast(1)} seconds."
        else "That passphrase isn't right. Check spaces and dashes between words."
    is UnlockResult.Throttled -> "Too many tries. Try again in ${((r.untilMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(1)} seconds."
    is UnlockResult.Failed -> r.reason
}

/** Runs the crypto-bound BiometricPrompt for [which] blob and hands the authorised cipher to [use]. */
fun biometricThen(
    app: TijoriApp, host: Host, title: String, subtitle: String,
    scope: CoroutineScope, use: suspend (javax.crypto.Cipher) -> Boolean, onError: (String) -> Unit, onOk: () -> Unit = {},
) {
    when (val p = app.session.prepareBiometric()) {
        is DeviceKeys.Prepared.Ready -> host.biometric(title, subtitle, p.cipher,
            onSuccess = { c -> scope.launch { if (use(c)) onOk() else onError("Fingerprint unlock failed. Use your passphrase.") } },
            onFail = { m -> if (m != null) onError(m) })
        DeviceKeys.Prepared.Invalidated -> onError(BIO_INVALIDATED_MSG)
        DeviceKeys.Prepared.Missing -> onError("Fingerprint unlock isn't set up. Use your passphrase.")
    }
}

/** Shown in place of the Money room while it's locked. */
@Composable
fun MoneyGate(app: TijoriApp, compact: Boolean = false) {
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bio = app.session.biometricReady && host.biometricAvailable

    fun openBio() = biometricThen(app, host, "Open Money room", "Banking, cards and investments", scope,
        use = { app.session.openMoneyWithBiometric(it) }, onError = { error = it })

    LaunchedEffect(Unit) { if (bio && !compact) openBio() }

    AppCard(background = CardBg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIconBox()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Money room is locked"), style = MaterialTheme.typography.titleLarge)
                Text("It opens only with a fresh check and closes by itself after ${app.prefs.now.moneyIdleSeconds} seconds idle.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (bio) BigButton(t("Open Money room"), icon = Icons.Filled.Fingerprint, color = Haldi) { openBio() }
        SecretInput(pass, { pass = it; error = null }, t("Master passphrase"), imeAction = ImeAction.Go, isError = error != null,
            supporting = error, onIme = {
                if (pass.isNotBlank() && !busy) scope.launch { busy = true; error = describe(app.session.openMoneyWithPassphrase(pass)); busy = false; if (error == null) pass = "" }
            })
        if (!bio || pass.isNotEmpty()) BigButton(if (bio) "Open with passphrase" else t("Open Money room"), loading = busy, enabled = pass.isNotBlank()) {
            scope.launch { busy = true; error = describe(app.session.openMoneyWithPassphrase(pass)); busy = false; if (error == null) pass = "" }
        }
    }
}

@Composable
private fun ItemIconBox() {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        com.tijoriapp.vault.ui.components.ItemIcon(com.tijoriapp.vault.core.vault.ItemType.NET_BANKING, com.tijoriapp.vault.core.vault.Room.MONEY, 48.dp)
    }
}

/**
 * "Is it really you?" before showing the Secret Key, printing nominee cards or other sensitive actions.
 * Fingerprint (crypto-bound) or the passphrase.
 */
@Composable
fun ReauthDialog(app: TijoriApp, title: String, reason: String, onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bio = app.session.biometricReady && host.biometricAvailable

    fun check() = scope.launch {
        busy = true
        val ok = app.session.verifyPassphrase(pass)
        busy = false
        if (ok) { pass = ""; onConfirmed() } else error = "That passphrase isn't right."
    }

    LaunchedEffect(Unit) {
        if (bio) biometricThen(app, host, title, reason, scope, use = { app.session.verifyBiometric(it) },
            onError = { error = it }, onOk = onConfirmed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = Muted)
                SecretInput(pass, { pass = it; error = null }, t("Master passphrase"), imeAction = ImeAction.Go,
                    onIme = { if (pass.isNotBlank()) check() }, isError = error != null, supporting = error)
                if (bio) TextLink("Use fingerprint instead") {
                    biometricThen(app, host, title, reason, scope, use = { app.session.verifyBiometric(it) }, onError = { error = it }, onOk = onConfirmed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { check() }, enabled = pass.isNotBlank() && !busy) { Text(if (busy) "Checking…" else "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

/** Live "locks in 0:42" chip for the Money room. */
@Composable
fun MoneyCountdown(app: TijoriApp) {
    val state by app.session.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.moneyOpen) { while (state.moneyOpen) { now = System.currentTimeMillis(); delay(500) } }
    if (!state.moneyOpen) return
    val left = ((state.moneyLocksAt - now) / 1000).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chip("${t("Locks in")} ${left / 60}:${(left % 60).toString().padStart(2, '0')}", WarnBg, WarnText, Icons.Filled.Timer)
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = { app.session.closeMoney(); Toasts.show("Money room locked") }) { Text(t("Lock"), color = Link) }
    }
}
