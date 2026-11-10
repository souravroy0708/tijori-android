package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.ui.LocalHost
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.t
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun LockScreen(app: TijoriApp) {
    var nominee by remember { mutableStateOf(false) }
    if (nominee) { NomineeScreen(app, practice = null, onClose = { nominee = false }); return }

    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsState()
    var pass by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var throttleLeft by remember { mutableLongStateOf(0L) }
    val bio = app.session.biometricReady && host.biometricAvailable
    val focus = remember { FocusRequester() }

    // Live countdown while throttled after too many wrong tries.
    LaunchedEffect(error) {
        while (true) {
            throttleLeft = ((app.prefs.lockedUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (throttleLeft == 0L) break
            delay(500)
        }
    }

    fun unlock() {
        if (pass.isBlank() || busy) return
        busy = true; error = null
        scope.launch {
            val r = app.session.unlockWithPassphrase(pass)
            busy = false
            if (r != UnlockResult.Ok) { error = describe(r) } else pass = ""
        }
    }

    fun unlockBio() = biometricThen(app, host, "Unlock Tijori", settings.ownerName.ifBlank { "Confirm it's you" }, scope,
        use = { app.session.unlockWithBiometric(it) }, onError = { error = it })

    // Prompt once on arrival, the way people expect from banking apps.
    LaunchedEffect(Unit) { if (bio) unlockBio() }

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Ink2))).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(88.dp).clip(CircleShape).background(Ink3), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Haldi, modifier = Modifier.size(42.dp))
        }
        Text(if (settings.ownerName.isBlank()) t("Tijori is locked") else "$greeting, ${settings.ownerName}",
            color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(if (settings.ownerName.isBlank()) "Chaabi sirf aapke paas" else t("Tijori is locked"), color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        if (bio) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Haldi).clickable(role = Role.Button, onClickLabel = t("Unlock with fingerprint")) { unlockBio() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Fingerprint, contentDescription = t("Unlock with fingerprint"), tint = Ink, modifier = Modifier.size(46.dp)) }
            Text(t("Unlock with fingerprint"), color = MutedOnDark, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = pass, onValueChange = { pass = it; error = null },
            label = { Text(t("Master passphrase")) }, singleLine = true,
            enabled = throttleLeft == 0L,
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { unlock() }),
            trailingIcon = {
                IconButton(onClick = { shown = !shown }) {
                    Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (shown) t("Hide") else t("Show"), tint = MutedOnDark)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledTextColor = MutedOnDark,
                focusedBorderColor = Haldi, unfocusedBorderColor = Line, cursorColor = Haldi,
                focusedLabelColor = Haldi, unfocusedLabelColor = MutedOnDark,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        val msg = when {
            throttleLeft > 0 -> "Too many tries. Try again in $throttleLeft s."
            else -> error
        }
        if (msg != null) Text(msg, color = Coral, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        BigButton(if (busy) "Checking…" else t("Unlock"), color = Haldi, loading = busy, enabled = pass.isNotBlank() && throttleLeft == 0L) { unlock() }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextLink("Forgot passphrase?", color = MutedOnDark) { forgot = true }
            TextLink(t("I'm a nominee"), color = Mint) { nominee = true }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MutedOnDark, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("No internet permission · everything stays on this phone", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (forgot) AlertDialog(
        onDismissRequest = { forgot = false },
        title = { Text("There's no reset, by design") },
        text = {
            Text("Your passphrase is half of the key that encrypts your vault, and it's never stored anywhere. That's why nobody can break in, and also why nobody can reset it.\n\n" +
                "• Try variations: capital letters, spaces between words, an older passphrase.\n" +
                "• If your family made nominee cards, they can still open the Family room.\n" +
                "• As a last resort, you can erase this vault from Android Settings → Apps → Tijori → Clear storage and start again.")
        },
        confirmButton = { TextButton(onClick = { forgot = false }) { Text("OK") } },
    )
}
