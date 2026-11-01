package com.tijoriapp.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.AppController
import com.tijoriapp.vault.crypto.PassphraseGenerator

/**
 * Setup Wizard (Spec Page 8, 9 & 12):
 * 1. 6-word Diceware Master Passphrase generation (~77.5 bits entropy) + Entropy strength gauge
 * 2. 128-bit Secret Key reveal (Spec Page 5: "Secret Key kept on paper in Emergency Kit")
 * 3. Emergency Kit Quiz (confirms owner understands permanent loss if both are forgotten)
 * 4. Optional biometric setup
 */
@Composable
fun SetupScreen(app: AppController, actions: PhoneActions) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var passphraseInput by rememberSaveable {
        mutableStateOf(PassphraseGenerator.generatePassphrase(6))
    }
    var secretKey128 by rememberSaveable {
        mutableStateOf(PassphraseGenerator.generateSecretKey128())
    }
    var quiz1 by rememberSaveable { mutableStateOf(false) }
    var quiz2 by rememberSaveable { mutableStateOf(false) }
    var quiz3 by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(VaultNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Step progress
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) VaultGold else VaultNavy3)
                )
            }
        }

        when (step) {
            0 -> {
                Box(Modifier.size(56.dp).clip(CircleShape).background(VaultGold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = VaultNavy, modifier = Modifier.size(28.dp))
                }
                Text("Step 1: Master Passphrase", color = VaultGold, style = MaterialTheme.typography.titleLarge)
                Text("Create Your Master Key", color = Color.White, style = MaterialTheme.typography.displayMedium)
                Text(
                    "Tijori recommends 6 random words (Diceware). It gives ~77 bits of cryptographic entropy — far stronger than complex symbols, yet memorable.",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Passphrase Card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VaultNavy2)
                        .border(1.5.dp, VaultGold, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Suggested 6-Word Passphrase", color = VaultGold, style = MaterialTheme.typography.labelMedium)
                        Row(
                            Modifier.clickable {
                                passphraseInput = PassphraseGenerator.generatePassphrase(6)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = VaultGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Words", color = VaultGold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(
                        passphraseInput,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    val (entropy, rating) = PassphraseGenerator.estimateStrength(passphraseInput)
                    Text(
                        "Strength: $rating",
                        color = if (entropy >= 50) VaultEmerald else VaultCoral,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                AppField(
                    label = "Or Type Your Own Passphrase",
                    value = passphraseInput,
                    onChange = { passphraseInput = it },
                )

                BigButton("Next: Reveal 128-Bit Secret Key →", gold = true) {
                    step = 1
                }
            }

            1 -> {
                Box(Modifier.size(56.dp).clip(CircleShape).background(VaultEmerald), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = VaultNavy, modifier = Modifier.size(28.dp))
                }
                Text("Step 2: 128-Bit Secret Key", color = VaultEmerald, style = MaterialTheme.typography.titleLarge)
                Text("Your Second Key Factor", color = Color.White, style = MaterialTheme.typography.displayMedium)
                Text(
                    "Spec Page 5: 'A stolen vault file cannot be cracked by guessing the passphrase alone.' This 128-bit Secret Key is kept in your paper Emergency Kit.",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VaultNavy2)
                        .border(1.5.dp, VaultEmerald, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("SECRET KEY (PRINT / KEEP ON PAPER):", color = VaultEmerald, style = MaterialTheme.typography.labelMedium)
                    Text(
                        secretKey128,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Stored wrapped inside your phone's Android Keystore. You will also print it in the Emergency Kit PDF.",
                        color = MutedOnDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                GhostButton("📄 Export & Print Emergency Kit PDF") {
                    actions.onExportEmergencyKitPdf()
                }

                BigButton("Next: Security Quiz →", gold = true) {
                    step = 2
                }
            }

            else -> {
                Box(Modifier.size(56.dp).clip(CircleShape).background(VaultGold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultNavy, modifier = Modifier.size(28.dp))
                }
                Text("Step 3: Emergency Kit Quiz", color = VaultGold, style = MaterialTheme.typography.titleLarge)
                Text("Confirm You Understand", color = Color.White, style = MaterialTheme.typography.displayMedium)
                Text(
                    "Spec Page 12: 'Setup quiz for the Emergency Kit prevents permanent lockout.'",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VaultNavy2)
                        .border(1.dp, VaultLine, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuizItem(
                        checked = quiz1,
                        text = "I understand Tijori has NO server and NO customer support can ever reset my password.",
                        onToggle = { quiz1 = it },
                    )
                    QuizItem(
                        checked = quiz2,
                        text = "I understand that if I lose both my Master Passphrase and Secret Key, my vault data is permanently lost.",
                        onToggle = { quiz2 = it },
                    )
                    QuizItem(
                        checked = quiz3,
                        text = "I understand I can export an encrypted backup file (.tijori) and keep it in any pen drive or safe storage.",
                        onToggle = { quiz3 = it },
                    )
                }

                val canFinish = quiz1 && quiz2 && quiz3 && passphraseInput.isNotBlank()
                BigButton("Initialize Tijori Vault & Start →", gold = true, enabled = canFinish) {
                    app.createVault(
                        passphraseChars = passphraseInput.toCharArray(),
                        secretKeyStr = secretKey128,
                        ownerName = "Vault Owner",
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizItem(checked: Boolean, text: String, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = VaultGold, checkmarkColor = VaultNavy),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * Vault Unlock Screen (Spec Page 8 & 9):
 * "Biometric or passphrase; clear lock state; no hints."
 */
@Composable
fun UnlockScreen(app: AppController, actions: PhoneActions) {
    var passInput by rememberSaveable { mutableStateOf("") }
    var secretKeyInput by rememberSaveable {
        mutableStateOf(app.ctx.getSharedPreferences("tijori_prefs", android.content.Context.MODE_PRIVATE).getString("secret_key_backup", "") ?: "")
    }
    var failedAttempt by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(VaultNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.size(72.dp).clip(CircleShape).background(VaultGold), contentAlignment = Alignment.Center) {
            Text("🗝️", fontSize = 34.sp)
        }
        Text("Tijori Vault Locked", color = Color.White, style = MaterialTheme.typography.displayMedium)
        Text(
            t("Chaabi sirf aapke paas."),
            color = VaultGold,
            style = MaterialTheme.typography.bodyMedium,
        )

        // Biometric Quick Unlock button (Class 3 Hardware Biometric bound - Spec Page 5 & 6)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(VaultNavy2)
                .border(1.dp, VaultLine, RoundedCornerShape(22.dp))
                .clickable { actions.onTriggerBiometricUnlock() }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = VaultEmerald, modifier = Modifier.size(44.dp))
            Text("Unlock with Biometrics", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Class 3 Biometric bound to Android Keystore", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }

        Text("— OR ENTER PASSPHRASE —", color = Muted, style = MaterialTheme.typography.labelMedium)

        AppField(
            label = "Master Passphrase",
            value = passInput,
            onChange = { passInput = it; failedAttempt = false },
            isSecret = true,
            placeholder = "Enter 6-word passphrase",
        )

        if (secretKeyInput.isBlank()) {
            AppField(
                label = "128-Bit Secret Key (From Emergency Kit)",
                value = secretKeyInput,
                onChange = { secretKeyInput = it },
                placeholder = "XXXX-XXXX-XXXX-XXXX",
            )
        }

        if (failedAttempt) {
            Text("Incorrect passphrase or secret key. Decryption failed.", color = VaultCoral, style = MaterialTheme.typography.bodyMedium)
        }

        BigButton("Unlock Vault →", gold = true, enabled = passInput.isNotBlank()) {
            val ok = app.unlockWithPassphrase(passInput.toCharArray(), secretKeyInput)
            if (!ok) {
                failedAttempt = true
            }
        }
    }
}
