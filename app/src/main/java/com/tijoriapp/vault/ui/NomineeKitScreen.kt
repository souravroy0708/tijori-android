package com.tijoriapp.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.AppController
import com.tijoriapp.vault.crypto.ShamirSecretSharing
import java.security.SecureRandom

/**
 * Nominee Kit Screen (Spec Page 3, 5, 8 & 9):
 * - Shamir's Secret Sharing over GF(256) (default 2-of-3, configurable up to 3-of-5).
 * - Printable Nominee Share Cards PDF export.
 * - On-device Practice Drill (tests secret reconstruction without exposing actual vault secrets).
 * - Heir Nominee Unlock flow.
 */
@Composable
fun NomineeKitScreen(app: AppController, actions: PhoneActions) {
    var kThreshold by remember { mutableIntStateOf(2) }
    var nShares by remember { mutableIntStateOf(3) }
    var showDrillDialog by remember { mutableStateOf(false) }
    var showHeirUnlockDialog by remember { mutableStateOf(false) }
    var drillPassed by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Header
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(VaultGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.FamilyRestroom, contentDescription = null, tint = VaultGold, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("Nominee Kit"),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextMain,
                        )
                        Text(
                            t("Shamir's Secret Sharing for Family & Heirs"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    t("Split your Legacy Room key into physical cards using mathematics. No single card can open your vault. Any k cards together unlock your emergency assets when needed."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                )
            }
        }

        // Scheme Configuration (k-of-n)
        item {
            AppCard(title = t("Secret Sharing Scheme")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${kThreshold}-of-${nShares} Scheme",
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultGold,
                        fontWeight = FontWeight.Bold,
                    )
                    SmallPill(
                        text = if (kThreshold == 2 && nShares == 3) "Recommended" else "Custom",
                        color = VaultEmerald,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Threshold (k): $kThreshold shares required to unlock",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Slider(
                    value = kThreshold.toFloat(),
                    onValueChange = {
                        kThreshold = it.toInt()
                        if (kThreshold > nShares) nShares = kThreshold
                    },
                    valueRange = 2f..4f,
                    steps = 1,
                    colors = SliderDefaults.colors(thumbColor = VaultGold, activeTrackColor = VaultGold),
                )

                Text(
                    "Total Shares (n): $nShares nominee cards to print",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Slider(
                    value = nShares.toFloat(),
                    onValueChange = {
                        nShares = it.toInt()
                        if (nShares < kThreshold) kThreshold = nShares
                    },
                    valueRange = 3f..5f,
                    steps = 1,
                    colors = SliderDefaults.colors(thumbColor = VaultGold, activeTrackColor = VaultGold),
                )

                Text(
                    "Mathematical guarantee: Any ${kThreshold - 1} cards give 0 bits of information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultEmerald,
                )
            }
        }

        // PDF Export Trigger
        item {
            AppCard(title = t("Printable Nominee Share Cards")) {
                Text(
                    t("Generate an official printable PDF with $nShares separate cards. Each card contains its unique cryptographic token and plain-language instructions in English and Hindi."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Spacer(Modifier.height(6.dp))
                BigButton(text = t("Export Nominee Share Cards PDF"), gold = true) {
                    actions.onExportNomineeCardsPdf()
                }
            }
        }

        // Practice Drill
        item {
            AppCard(
                title = t("On-Device Practice Drill"),
                trailing = {
                    if (drillPassed) {
                        SmallPill(text = "Drill Passed ✓", color = VaultEmerald)
                    }
                }
            ) {
                Text(
                    t("Test secret reconstruction on your own device without risking real passwords. Tijori will generate a test key, split it into 2-of-3 shares, and let you combine them to verify it works."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Spacer(Modifier.height(6.dp))
                BigButton(
                    text = if (drillPassed) t("Run Practice Drill Again") else t("Start Practice Drill"),
                    emerald = drillPassed,
                    gold = !drillPassed,
                ) {
                    showDrillDialog = true
                }
            }
        }

        // Heir Unlock Trigger
        item {
            AppCard(title = t("Unlock Nominee Room (Heirs)")) {
                Text(
                    t("If an heir or family member holds $kThreshold nominee cards, enter the card tokens here to unlock the Legacy room and access insurance, locker, and will documents."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                )
                Spacer(Modifier.height(6.dp))
                GhostButton(text = t("Enter Nominee Tokens to Unlock")) {
                    showHeirUnlockDialog = true
                }
            }
        }
    }

    // Practice Drill Dialog
    if (showDrillDialog) {
        PracticeDrillDialog(
            onDismiss = { showDrillDialog = false },
            onPassed = {
                drillPassed = true
                showDrillDialog = false
                app.message = "Nominee Practice Drill Passed Successfully! ✓"
            }
        )
    }

    // Heir Unlock Dialog
    if (showHeirUnlockDialog) {
        HeirUnlockDialog(
            app = app,
            k = kThreshold,
            onDismiss = { showHeirUnlockDialog = false },
            onSuccess = {
                showHeirUnlockDialog = false
                app.tab = AppTab.Rooms
                app.selectedRoom = com.tijoriapp.vault.data.RoomType.LEGACY
                app.message = "Legacy Room Unlocked via Nominee Shares! ✓"
            }
        )
    }
}

/**
 * Interactive on-device Shamir practice drill (Spec Page 3 & 9).
 */
@Composable
private fun PracticeDrillDialog(
    onDismiss: () -> Unit,
    onPassed: () -> Unit,
) {
    // Generate sample drill key and 2-of-3 shares
    val drillData = remember {
        val testKey = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val testShares = ShamirSecretSharing.split(testKey, 2, 3)
        Triple(testKey, testShares, testShares.map { ShamirSecretSharing.shareToMnemonic(it) })
    }

    val (_, rawShares, mnemonics) = drillData
    var selectedShareA by remember { mutableIntStateOf(0) }
    var selectedShareB by remember { mutableIntStateOf(1) }
    var drillResult by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(t("Nominee Practice Drill"), color = TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Simulated 2-of-3 Shamir split. Select any 2 cards to test mathematical reconstruction:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )

                mnemonics.forEachIndexed { index, phrase ->
                    val isPicked = index == selectedShareA || index == selectedShareB
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPicked) VaultGold.copy(alpha = 0.2f) else Paper)
                            .border(1.dp, if (isPicked) VaultGold else PaperLine, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Nominee Card #${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPicked) VaultGold else TextMain,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                                if (isPicked) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VaultGold, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                phrase.take(36) + "...",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Muted,
                            )
                        }
                    }
                }

                if (drillResult != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (success) VaultEmerald.copy(alpha = 0.15f) else VaultCoral.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (success) VaultEmerald else VaultCoral,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            drillResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!success) {
                TextButton(
                    onClick = {
                        try {
                            val chosenShares = listOf(rawShares[selectedShareA], rawShares[selectedShareB])
                            val reconstructed = ShamirSecretSharing.combine(chosenShares, 2)
                            if (reconstructed.contentEquals(drillData.first)) {
                                success = true
                                drillResult = "Success! Lagrange interpolation over GF(256) reconstructed the key with 100% precision."
                            } else {
                                drillResult = "Reconstruction failed."
                            }
                        } catch (e: Exception) {
                            drillResult = "Error: ${e.message}"
                        }
                    }
                ) {
                    Text(t("Test Reconstruction"), color = VaultGold, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onPassed) {
                    Text(t("Done"), color = VaultEmerald, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Close"), color = Muted)
            }
        },
    )
}

/**
 * Heir Nominee Token Unlock Dialog (Spec Page 3 & 5).
 */
@Composable
private fun HeirUnlockDialog(
    app: AppController,
    k: Int,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var tokenA by remember { mutableStateOf("") }
    var tokenB by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(t("Enter Nominee Tokens"), color = TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the secret word tokens from any $k physical Nominee Share Cards:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                AppField(
                    label = "Card 1 Word Token",
                    value = tokenA,
                    onChange = { tokenA = it },
                    placeholder = "Paste or type card #1 token",
                )
                AppField(
                    label = "Card 2 Word Token",
                    value = tokenB,
                    onChange = { tokenB = it },
                    placeholder = "Paste or type card #2 token",
                )
                if (errorMessage != null) {
                    Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = VaultCoral)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val shareA = ShamirSecretSharing.shareFromMnemonic(tokenA.trim())
                        val shareB = ShamirSecretSharing.shareFromMnemonic(tokenB.trim())
                        if (shareA == null || shareB == null) {
                            errorMessage = "Invalid word token format. Please check the words printed on the card."
                            return@TextButton
                        }
                        val ok = app.unlockLegacyRoomWithShares(listOf(shareA, shareB), 2)
                        if (ok) {
                            onSuccess()
                        } else {
                            errorMessage = "Nominee tokens could not open Legacy room."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error combining shares: ${e.message}"
                    }
                }
            ) {
                Text(t("Unlock Legacy Room"), color = VaultGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Cancel"), color = Muted)
            }
        },
    )
}
