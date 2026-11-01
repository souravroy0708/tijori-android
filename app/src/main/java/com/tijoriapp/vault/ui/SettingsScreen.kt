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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.AppController

/**
 * Settings Screen (Spec Page 4, 8, 10):
 * - Auto-lock timeouts (30s, 45s, 60s, 120s).
 * - Clipboard auto-clear timeout (15s, 30s, 60s).
 * - Screenshot & Recent Apps blocking (FLAG_SECURE).
 * - Biometric Unlock toggle with StrongBox/TEE Keystore binding.
 * - Language selection (English / हिन्दी).
 * - Printable Emergency Kit PDF export.
 * - Wipe Vault (danger zone).
 */
@Composable
fun SettingsScreen(app: AppController, actions: PhoneActions) {
    var showWipeConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Security Settings Card
        item {
            AppCard(title = t("Security & Auto-Lock")) {
                // Auto-lock on idle
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = VaultGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Auto-Lock When Idle"), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(30, 45, 60, 120).forEach { sec ->
                            val isSel = app.autoLockSeconds == sec
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) VaultGold else Paper)
                                    .border(1.dp, if (isSel) VaultGold else PaperLine, RoundedCornerShape(10.dp))
                                    .clickable {
                                        app.updateSecuritySettings(
                                            autoLockSec = sec,
                                            clipSec = app.clipboardTimeoutSeconds,
                                            hideRecentsVal = app.hideInRecents,
                                            bio = app.biometricEnabled,
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${sec}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSel) VaultNavy else TextMain,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Clipboard clearing timeout
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Clear Sensitive Clipboard"), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60).forEach { sec ->
                            val isSel = app.clipboardTimeoutSeconds == sec
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) VaultGold else Paper)
                                    .border(1.dp, if (isSel) VaultGold else PaperLine, RoundedCornerShape(10.dp))
                                    .clickable {
                                        app.updateSecuritySettings(
                                            autoLockSec = app.autoLockSeconds,
                                            clipSec = sec,
                                            hideRecentsVal = app.hideInRecents,
                                            bio = app.biometricEnabled,
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${sec}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSel) VaultNavy else TextMain,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Block screenshots & hide in recents toggle
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Block Screenshots & Blank Recents"), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                        Text(t("Enforces Android FLAG_SECURE"), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(
                        checked = app.hideInRecents,
                        onCheckedChange = { checked ->
                            app.updateSecuritySettings(
                                autoLockSec = app.autoLockSeconds,
                                clipSec = app.clipboardTimeoutSeconds,
                                hideRecentsVal = checked,
                                bio = app.biometricEnabled,
                            )
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultGold, checkedTrackColor = VaultNavy),
                    )
                }

                // Biometrics toggle
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Biometric Unlock"), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                        Text(t("Fingerprint / Face with Keystore CryptoObject"), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(
                        checked = app.biometricEnabled,
                        onCheckedChange = { checked ->
                            app.updateSecuritySettings(
                                autoLockSec = app.autoLockSeconds,
                                clipSec = app.clipboardTimeoutSeconds,
                                hideRecentsVal = app.hideInRecents,
                                bio = checked,
                            )
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultGold, checkedTrackColor = VaultNavy),
                    )
                }
            }
        }

        // Language Selection
        item {
            AppCard(title = t("Language / भाषा")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val isEn = app.language == "en"
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isEn) VaultGold else Paper)
                            .border(1.dp, if (isEn) VaultGold else PaperLine, RoundedCornerShape(12.dp))
                            .clickable {
                                app.language = "en"
                                Lang.current = "en"
                                app.ctx.getSharedPreferences("tijori_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("language", "en").apply()
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "English",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isEn) VaultNavy else TextMain,
                            fontWeight = if (isEn) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    val isHi = app.language == "hi"
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isHi) VaultGold else Paper)
                            .border(1.dp, if (isHi) VaultGold else PaperLine, RoundedCornerShape(12.dp))
                            .clickable {
                                app.language = "hi"
                                Lang.current = "hi"
                                app.ctx.getSharedPreferences("tijori_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("language", "hi").apply()
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "हिन्दी",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isHi) VaultNavy else TextMain,
                            fontWeight = if (isHi) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Emergency Kit Backup
        item {
            AppCard(title = t("Emergency Kit Backup")) {
                Text(
                    t("Export a printable 1-page PDF document containing your 128-bit Secret Key, Vault ID, and recovery instructions. Store this on physical paper in a secure home locker."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Spacer(Modifier.height(6.dp))
                BigButton(text = t("Export Emergency Kit PDF"), gold = true) {
                    actions.onExportEmergencyKitPdf()
                }
            }
        }

        // Danger Zone: Wipe Vault
        item {
            AppCard(title = t("Danger Zone")) {
                Text(
                    t("Permanently wipe this vault, remove all encrypted items, keys, and reset setup state. This action cannot be undone."),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultCoral,
                )
                Spacer(Modifier.height(6.dp))
                BigButton(text = t("Wipe Vault & Reset App"), coral = true, gold = false) {
                    showWipeConfirm = true
                }
            }
        }

        // About & Architecture
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Tijori Vault v1.0.0", style = MaterialTheme.typography.titleMedium, color = TextMain)
                Text("Chaabi sirf aapke paas · 100% On-Device", style = MaterialTheme.typography.bodySmall, color = VaultGold)
                Text("Built for Indian families · Zero Trackers · No INTERNET", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            containerColor = CardBg,
            title = { Text(t("Wipe Entire Vault?"), color = VaultCoral) },
            text = {
                Text(
                    t("This will delete all saved passwords, banking notes, nominee cards, and database records. Make sure you have your Emergency Kit if you ever wish to restore."),
                    color = TextMain,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.db.wipeAll()
                        app.ctx.getSharedPreferences("tijori_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        app.lockVault()
                        app.setupDone = false
                        showWipeConfirm = false
                    }
                ) {
                    Text(t("Yes, Wipe Everything"), color = VaultCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text(t("Cancel"), color = TextMain)
                }
            },
        )
    }
}
