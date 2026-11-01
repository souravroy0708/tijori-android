package com.tijoriapp.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.AppController
import com.tijoriapp.vault.data.FieldKind
import com.tijoriapp.vault.data.RoomType
import com.tijoriapp.vault.data.VaultItem

@Composable
fun RoomsHomeScreen(app: AppController, actions: PhoneActions) {
    val lang = app.language
    val health = remember(app.items) { app.computePasswordHealth() }

    val filteredItems = remember(app.items, app.selectedRoom, app.searchQuery) {
        app.items.filter { item ->
            val matchRoom = item.room == app.selectedRoom
            val q = app.searchQuery.trim().lowercase()
            val matchSearch = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.tags.lowercase().contains(q)
            matchRoom && matchSearch
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Three Rooms Switcher Cards (Spec Page 3 & 4)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoomType.entries.forEach { room ->
                val isSelected = app.selectedRoom == room
                val count = app.items.count { it.room == room }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) VaultGold else CardBg)
                        .border(1.5.dp, if (isSelected) VaultGold else PaperLine, RoundedCornerShape(18.dp))
                        .clickable { app.selectedRoom = room }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(room.icon, fontSize = 24.sp)
                    Text(
                        room.labelEn,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                        color = if (isSelected) VaultNavy else TextMain,
                        maxLines = 1,
                    )
                    Text(
                        "$count items",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = if (isSelected) VaultNavy else Muted,
                    )
                }
            }
        }

        // 2. Room Lock Status Banner
        when (app.selectedRoom) {
            RoomType.MONEY -> {
                if (app.isMoneyRoomUnlocked) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(VaultEmeraldSoft)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, tint = Color(0xFF065F46))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Money Room Unlocked", color = Color(0xFF065F46), style = MaterialTheme.typography.titleMedium)
                                Text("Auto-drops in ${app.moneyLockSecondsLeft}s idle", color = Color(0xFF047857), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        SmallPill("Lock Now", coral = true) { app.dropMoneyKey() }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(VaultGoldSoft)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultGoldDark)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Money Room Is Locked", color = VaultGoldDark, style = MaterialTheme.typography.titleMedium)
                                Text("Secrets dropped after 60s idle (Spec Page 5)", color = VaultGoldDark, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        SmallPill("Unlock Money", gold = true) { app.unlockMoneyRoomExplicitly() }
                    }
                }
            }
            RoomType.LEGACY -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF3E8FF))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Legacy Room (Continuity for Family)", color = Color(0xFF6B21A8), style = MaterialTheme.typography.titleMedium)
                        Text("Protected by 2-of-3 Shamir Nominee Kit shares for your heirs", color = Color(0xFF7E22CE), style = MaterialTheme.typography.bodySmall)
                    }
                    SmallPill("Nominee Kit →", gold = true) { app.tab = com.tijoriapp.vault.AppTab.Nominee }
                }
            }
            else -> {}
        }

        // 3. Search Bar
        OutlinedTextField(
            value = app.searchQuery,
            onValueChange = { app.searchQuery = it },
            placeholder = { Text(t("Search your vault…")) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (app.searchQuery.isNotBlank()) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.clickable { app.searchQuery = "" })
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = VaultGold,
                unfocusedBorderColor = PaperLine,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // 4. Password Health Alert (if weak or reused passwords exist)
        if (health.weakCount > 0 || health.reusedCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VaultGoldSoft)
                    .clickable { app.tab = com.tijoriapp.vault.AppTab.Trust }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = VaultGoldDark)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${health.weakCount} weak & ${health.reusedCount} reused passwords need attention",
                        color = VaultGoldDark,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Audit on Trust page · Never sent anywhere", color = VaultGoldDark, style = MaterialTheme.typography.bodySmall)
                }
                Text("→", color = VaultGoldDark, style = MaterialTheme.typography.titleLarge)
            }
        }

        // 5. Items List for Selected Room
        AppCard(
            title = "${app.selectedRoom.labelEn} Logins (${filteredItems.size})",
            subtitle = when (app.selectedRoom) {
                RoomType.EVERYDAY -> "Email, social, shopping, Wi-Fi. Quick biometric unlock."
                RoomType.MONEY -> "Net banking, PPF, EPF/UAN, income-tax portal, cards."
                RoomType.LEGACY -> "Insurance, bank locker, will, instructions for heirs."
            }
        ) {
            if (filteredItems.isEmpty()) {
                Text(
                    "No items in this room matching '${app.searchQuery}'. Tap + Add New Login below to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            } else {
                filteredItems.forEach { item ->
                    VaultItemRow(
                        item = item,
                        app = app,
                        onClick = { app.openItemId = item.id },
                    )
                }
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun VaultItemRow(
    item: VaultItem,
    app: AppController,
    onClick: () -> Unit,
) {
    val usernameField = item.fields.firstOrNull { it.kind == FieldKind.USERNAME || it.kind == FieldKind.CUSTOMER_ID || it.kind == FieldKind.PAN }
    val isMoneyLocked = item.room == RoomType.MONEY && !app.isMoneyRoomUnlocked

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Paper2),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.type.icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (usernameField != null && !isMoneyLocked) {
                    val clearUser = app.decryptFieldValue(usernameField, item)
                    Text(clearUser.ifBlank { item.type.labelEn }, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
                } else {
                    Text(item.type.labelEn, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoomBadge(item.room)
            Text("›", style = MaterialTheme.typography.headlineMedium, color = VaultGold)
        }
    }
}
