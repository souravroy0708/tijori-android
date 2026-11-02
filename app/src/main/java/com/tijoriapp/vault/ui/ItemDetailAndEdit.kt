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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.tijoriapp.vault.crypto.PassphraseGenerator
import com.tijoriapp.vault.data.FieldKind
import com.tijoriapp.vault.data.IndiaTemplates
import com.tijoriapp.vault.data.ItemType
import com.tijoriapp.vault.data.RoomType
import com.tijoriapp.vault.data.VaultField
import com.tijoriapp.vault.data.VaultItem

/**
 * Item Detail Screen displaying decrypted fields with strict memory hygiene,
 * 30-second auto-masking, and Money room auto-lock enforcement.
 */
@Composable
fun ItemDetailScreen(
    item: VaultItem,
    app: AppController,
    actions: PhoneActions,
    onBack: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        // Top app bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(VaultNavy)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Paper)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper,
                    maxLines = 1,
                )
                Text(
                    "${item.room.labelEn} Room · ${item.type.labelEn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultGold,
                )
            }
            IconButton(onClick = { app.editingItem = item }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = VaultGold)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = VaultCoral)
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Room badge & status banner
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoomBadge(item.room)
                    SmallPill(
                        text = item.type.labelEn,
                        color = VaultGold,
                        bg = CardBg,
                    )
                }
            }

            // Money Room lock status banner
            if (item.room == RoomType.MONEY) {
                item {
                    if (!app.isMoneyRoomUnlocked) {
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultCoral)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t("Money Room Locked"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextMain,
                                    )
                                    Text(
                                        t("Tap unlock to verify biometric/passphrase for 60 seconds"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Muted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            BigButton(text = t("Unlock Money Room (60s)"), gold = true) {
                                actions.onTriggerBiometricUnlock()
                            }
                        }
                    } else {
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, tint = VaultEmerald)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        t("Money Room Active"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VaultEmerald,
                                    )
                                    Text(
                                        "${t("Auto-drops in")} ${app.moneyLockSecondsLeft}s ${t("idle")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expiry / Password Rotation warning
            if (item.expiresOn.isNotBlank()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VaultGold.copy(alpha = 0.15f))
                            .border(1.dp, VaultGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = VaultGold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${t("Expires on")}: ${item.expiresOn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                        )
                    }
                }
            }

            // Fields list
            if (item.fields.isEmpty()) {
                item {
                    Text(
                        t("No fields saved for this item."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            } else {
                items(item.fields) { field ->
                    val isSecret = field.kind.isSecret || field.kind == FieldKind.PASSWORD || field.kind == FieldKind.PIN_HINT
                    val decrypted = app.decryptFieldValue(field, item)

                    if (isSecret) {
                        MaskedFieldRow(
                            label = field.customLabel.ifBlank { field.kind.labelEn },
                            clearValue = decrypted,
                            isSecret = true,
                            onCopy = { secret ->
                                actions.onCopySensitive(field.kind.labelEn, secret)
                            },
                        )
                    } else {
                        FieldDisplayRow(
                            label = field.customLabel.ifBlank { field.kind.labelEn },
                            value = decrypted,
                            onCopy = { valText ->
                                actions.onCopySensitive(field.kind.labelEn, valText)
                            },
                        )
                    }
                }
            }

            // Security assurance note
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = VaultEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t("Encrypted on-device with AES-256-GCM. Never leaves your hardware."),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardBg,
            title = { Text(t("Delete Item?"), color = TextMain) },
            text = { Text("${t("Are you sure you want to permanently delete")} '${item.title}'?", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    app.db.deleteItem(item.id)
                    app.message = "Item deleted ✓"
                    app.reload()
                    showDeleteConfirm = false
                    onBack()
                }) {
                    Text(t("Delete"), color = VaultCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(t("Cancel"), color = TextMain)
                }
            },
        )
    }
}

@Composable
private fun FieldDisplayRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, PaperLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(2.dp))
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = TextMain)
        }
        if (value.isNotBlank() && !value.startsWith("[")) {
            IconButton(onClick = { onCopy(value) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = VaultGold)
            }
        }
    }
}

/**
 * Add or Edit Item Screen with dynamic template prefill, Diceware/Random password generator modal,
 * and India-first field validation (Spec Page 7 & 8).
 */
@Composable
fun AddOrEditItemScreen(
    app: AppController,
    template: IndiaTemplates.TemplateDef?,
    existingItem: VaultItem?,
    onClose: () -> Unit,
) {
    var title by remember {
        mutableStateOf(existingItem?.title ?: template?.title ?: "")
    }
    var selectedRoom by remember {
        mutableStateOf(existingItem?.room ?: template?.room ?: RoomType.EVERYDAY)
    }
    var selectedType by remember {
        mutableStateOf(existingItem?.type ?: template?.type ?: ItemType.CUSTOM)
    }

    // Editable fields: list of (FieldKind, Label, Value)
    val fieldStates = remember {
        mutableStateListOf<FieldEditState>().apply {
            if (existingItem != null) {
                existingItem.fields.forEach { f ->
                    val decrypted = app.decryptFieldValue(f, existingItem)
                    add(FieldEditState(f.kind, f.customLabel.ifBlank { f.kind.labelEn }, decrypted))
                }
            } else if (template != null) {
                template.fields.forEach { def ->
                    add(FieldEditState(def.kind, def.label, ""))
                }
            } else {
                add(FieldEditState(FieldKind.USERNAME, "Username / ID", ""))
                add(FieldEditState(FieldKind.PASSWORD, "Password", ""))
                add(FieldEditState(FieldKind.URL, "Website URL / App", ""))
                add(FieldEditState(FieldKind.NOTE, "Notes", ""))
            }
        }
    }

    var showPasswordGenDialog by remember { mutableStateOf(false) }
    var targetPasswordFieldIdx by remember { mutableIntStateOf(-1) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        // App bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(VaultNavy)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Paper)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (existingItem != null) t("Edit Login") else t("Add New Login"),
                style = MaterialTheme.typography.titleMedium,
                color = Paper,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        app.message = "Please enter a title"
                        return@TextButton
                    }
                    val rawPairs = fieldStates.map { it.kind to it.value }
                    app.saveItemWithFields(
                        title = title,
                        room = selectedRoom,
                        type = selectedType,
                        rawFields = rawPairs,
                        existingId = existingItem?.id ?: 0L,
                    )
                    onClose()
                }
            ) {
                Text(t("Save"), color = VaultGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Room Selection (Everyday, Money, Legacy)
            item {
                AppCard(title = t("Select Vault Room")) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoomType.values().forEach { r ->
                            val isSelected = selectedRoom == r
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) VaultGold else CardBg)
                                    .border(1.dp, if (isSelected) VaultGold else PaperLine, RoundedCornerShape(12.dp))
                                    .clickable { selectedRoom = r }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    r.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) VaultNavy else TextMain,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Title Field
            item {
                AppField(
                    label = t("Item Title"),
                    value = title,
                    onChange = { title = it },
                    placeholder = "e.g. HDFC NetBanking / Personal Gmail",
                )
            }

            // Card PIN Hint Notice (Spec Page 3 & 7)
            if (selectedType == ItemType.CARD) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VaultGold.copy(alpha = 0.12f))
                            .border(1.dp, VaultGold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = VaultGold)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t("Security rule: Do not save full 4-digit ATM/UPI PIN. Store a PIN hint only you understand."),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                        )
                    }
                }
            }

            // Dynamic Fields
            items(fieldStates.indices.toList()) { index ->
                val fieldState = fieldStates[index]
                val isPasswordField = fieldState.kind == FieldKind.PASSWORD || fieldState.kind == FieldKind.PIN_HINT

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppField(
                        label = fieldState.label,
                        value = fieldState.value,
                        onChange = { fieldState.value = it },
                        isSecret = isPasswordField,
                    )
                    if (isPasswordField) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    targetPasswordFieldIdx = index
                                    showPasswordGenDialog = true
                                }
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VaultGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Generate Strong Password"), color = VaultGold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Save Button
            item {
                Spacer(Modifier.height(10.dp))
                BigButton(text = t("Save to Tijori"), gold = true) {
                    if (title.isBlank()) {
                        app.message = "Please enter a title"
                        return@BigButton
                    }
                    val rawPairs = fieldStates.map { it.kind to it.value }
                    app.saveItemWithFields(
                        title = title,
                        room = selectedRoom,
                        type = selectedType,
                        rawFields = rawPairs,
                        existingId = existingItem?.id ?: 0L,
                    )
                    onClose()
                }
            }
        }
    }

    // Password Generator Dialog
    if (showPasswordGenDialog && targetPasswordFieldIdx in fieldStates.indices) {
        PasswordGeneratorDialog(
            onDismiss = { showPasswordGenDialog = false },
            onApply = { generated ->
                fieldStates[targetPasswordFieldIdx].value = generated
                showPasswordGenDialog = false
            }
        )
    }
}

private class FieldEditState(
    val kind: FieldKind,
    val label: String,
    initialValue: String,
) {
    var value by mutableStateOf(initialValue)
}

/**
 * Diceware and Random Password Generator modal (Spec Page 7: 6-word Diceware ~77 bits entropy).
 */
@Composable
private fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var modeDiceware by remember { mutableStateOf(true) }
    var dicewareWords by remember { mutableIntStateOf(5) }
    var randomLength by remember { mutableIntStateOf(18) }
    var includeSymbols by remember { mutableStateOf(true) }
    var currentPassword by remember {
        mutableStateOf(PassphraseGenerator.generateDiceware(5))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(t("Password Generator"), color = TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Generated Password Box
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .border(1.dp, VaultGold, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        currentPassword,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = VaultGold,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Strength Estimate
                val (bits, label) = PassphraseGenerator.estimateStrength(currentPassword)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Strength: $label", style = MaterialTheme.typography.bodySmall, color = VaultEmerald)
                    Text("~$bits bits entropy", style = MaterialTheme.typography.bodySmall, color = Muted)
                }

                // Mode Toggle
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (modeDiceware) VaultGold else Paper)
                            .clickable {
                                modeDiceware = true
                                currentPassword = PassphraseGenerator.generateDiceware(dicewareWords)
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Diceware Words",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (modeDiceware) VaultNavy else TextMain,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!modeDiceware) VaultGold else Paper)
                            .clickable {
                                modeDiceware = false
                                currentPassword = PassphraseGenerator.generateRandom(randomLength, symbols = includeSymbols)
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Random Characters",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (!modeDiceware) VaultNavy else TextMain,
                        )
                    }
                }

                // Controls based on mode
                if (modeDiceware) {
                    Column {
                        Text("Word count: $dicewareWords words", style = MaterialTheme.typography.bodySmall, color = TextMain)
                        Slider(
                            value = dicewareWords.toFloat(),
                            onValueChange = {
                                dicewareWords = it.toInt()
                                currentPassword = PassphraseGenerator.generateDiceware(dicewareWords)
                            },
                            valueRange = 4f..7f,
                            steps = 2,
                            colors = SliderDefaults.colors(thumbColor = VaultGold, activeTrackColor = VaultGold),
                        )
                    }
                } else {
                    Column {
                        Text("Length: $randomLength chars", style = MaterialTheme.typography.bodySmall, color = TextMain)
                        Slider(
                            value = randomLength.toFloat(),
                            onValueChange = {
                                randomLength = it.toInt()
                                currentPassword = PassphraseGenerator.generateRandom(randomLength, symbols = includeSymbols)
                            },
                            valueRange = 12f..32f,
                            steps = 19,
                            colors = SliderDefaults.colors(thumbColor = VaultGold, activeTrackColor = VaultGold),
                        )
                    }
                }

                // Refresh Button
                TextButton(
                    onClick = {
                        currentPassword = if (modeDiceware) {
                            PassphraseGenerator.generateDiceware(dicewareWords)
                        } else {
                            PassphraseGenerator.generateRandom(randomLength, symbols = includeSymbols)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, tint = VaultGold)
                    Spacer(Modifier.width(6.dp))
                    Text(t("Roll Again"), color = VaultGold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(currentPassword) }) {
                Text(t("Use This Password"), color = VaultGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Cancel"), color = Muted)
            }
        },
    )
}
