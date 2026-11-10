package com.tijoriapp.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.vault.Health
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.VaultItem
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeTab(app: TijoriApp) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val state by app.session.state.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val report = remember(state.items) { Health.analyze(state.items, words = app.words) }
    val items = state.items

    HeroPage(hero = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                Text(settings.ownerName.trim().take(1).uppercase().ifBlank { "T" }, color = Ink, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${t("Namaste")}${if (settings.ownerName.isBlank()) "" else ", ${settings.ownerName}"}", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ink3).clickable(role = Role.Button) { app.session.lock() }
                .semantics { contentDescription = "Lock Tijori now" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(report.score, Modifier.size(132.dp).clickable { nav.tab = MainTab.Security }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${report.score}", color = Color.White, style = MaterialTheme.typography.displayMedium)
                    Text(t("Security score"), color = MutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderTile(t("Items"), "${state.counts.values.sum()}", "${state.counts[Room.MONEY] ?: 0} in Money", Violet)
                HeaderTile(t("Needs attention"), "${report.needsAttention}", if (report.needsAttention == 0) "All good" else "Tap to fix",
                    if (report.needsAttention == 0) Mint else Coral, onClick = { nav.tab = MainTab.Security })
            }
        }
    }) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(t("Search your vault"), color = Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } },
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBg, unfocusedContainerColor = CardBg, unfocusedBorderColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isNotBlank()) {
            val found = items.filter { it.matches(query) }.sortedBy { it.title.lowercase() }
            SectionLabel("${found.size} found")
            if (!state.moneyOpen && (state.counts[Room.MONEY] ?: 0) > 0) Banner("Money room items are hidden while it's locked.", action = "Open") { nav.push(Dest.RoomList(Room.MONEY)) }
            found.forEach { ItemRow(it) { nav.push(Dest.Item(it.id)) } }
            if (found.isEmpty()) EmptyState(Icons.Filled.SearchOff, "No matches", "Search looks at titles, usernames and websites. Secrets are never searched.")
            return@HeroPage
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Room.entries.forEach { r -> RoomTile(r, state.counts[r] ?: 0, locked = r == Room.MONEY && !state.moneyOpen, modifier = Modifier.weight(1f)) { nav.push(Dest.RoomList(r)) } }
        }
        if (state.moneyOpen) MoneyCountdown(app)
        if (state.unreadable > 0) Banner("${state.unreadable} items couldn't be decrypted. Restore from a backup if this keeps happening.", BadBg, BadText, Icons.Filled.Warning)

        SetupNudges(app, host)

        val favs = items.filter { it.favorite }.sortedBy { it.title.lowercase() }
        if (favs.isNotEmpty()) {
            SectionLabel(t("Favourites"))
            favs.forEach { ItemRow(it) { nav.push(Dest.Item(it.id)) } }
        }
        val recent = items.filter { it.lastUsedAt > 0 && !it.favorite }.sortedByDescending { it.lastUsedAt }.take(5)
        if (recent.isNotEmpty()) {
            SectionLabel(t("Recently used"))
            recent.forEach { ItemRow(it) { nav.push(Dest.Item(it.id)) } }
        }
        if (items.isEmpty()) {
            EmptyState(Icons.Filled.Shield, "Start with what matters", "Add your email first: it unlocks everything else. Then your bank, and your insurance for the family.",
                action = "Add your first item") { nav.push(Dest.PickType) }
        } else if (favs.isEmpty() && recent.isEmpty()) {
            SectionLabel(t("All items"))
            items.sortedByDescending { it.updatedAt }.take(8).forEach { ItemRow(it) { nav.push(Dest.Item(it.id)) } }
        }
    }
}

@Composable
private fun SetupNudges(app: TijoriApp, host: Host) {
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val vault by app.session.state.collectAsStateWithLifecycle()
    val total = vault.counts.values.sum()
    val backupOld = total > 0 && System.currentTimeMillis() - settings.lastBackupAt > 30L * 86_400_000L
    when {
        settings.kitSavedAt == 0L -> Banner("Save your Emergency Kit. Without it you can't restore on a new phone.", action = "Settings") { nav.tab = MainTab.Settings }
        backupOld -> Banner(if (settings.lastBackupAt == 0L) "You have no backup yet. If this phone is lost, so is the vault." else "Last backup was ${relativeTime(settings.lastBackupAt)}.",
            action = "Back up") { nav.tab = MainTab.Settings }
        !host.autofillEnabled && total >= 3 -> Banner("Let Tijori fill logins in apps and Chrome.", GoodBg, GoodText, Icons.Filled.Password, action = "Turn on") { host.openAutofillSettings() }
        (vault.counts[Room.LEGACY] ?: 0) > 0 && settings.cardsMadeAt == 0L ->
            Banner("Your Family room has items but no nominee cards yet.", GoodBg, GoodText, Icons.Filled.FamilyRestroom, action = "Set up") { nav.tab = MainTab.Family }
    }
}

@Composable
fun RoomTile(room: Room, count: Int, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(CardBg).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(room.accent), contentAlignment = Alignment.Center) {
                Icon(room.icon(), contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (locked) Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Muted, modifier = Modifier.size(16.dp))
        }
        Text(room.label(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun ItemRow(item: VaultItem, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(item.type, item.room)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.favorite) Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = Haldi, modifier = Modifier.size(18.dp))
        trailing?.invoke()
    }
}

@Composable
fun RoomScreen(app: TijoriApp, room: Room) {
    val nav = LocalNav.current
    val state by app.session.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val locked = room == Room.MONEY && !state.moneyOpen
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(room.label(), onBack = { nav.pop() }, accent = room.accent) {
            if (!locked) IconButton(onClick = { nav.push(Dest.Edit(null, room = room)) }) { Icon(Icons.Filled.Add, contentDescription = "Add to ${room.en}") }
        }
        if (locked) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { MoneyGate(app) }
        } else RoomList(app, room, state, query, { query = it })
    }
}

@Composable
private fun RoomList(app: TijoriApp, room: Room, state: com.tijoriapp.vault.data.VaultSession.State, query: String, onQuery: (String) -> Unit) {
    val nav = LocalNav.current
    run {
        val list = state.items.filter { it.room == room && it.matches(query) }.sortedWith(compareByDescending<VaultItem> { it.favorite }.thenBy { it.title.lowercase() })
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(roomBlurb(room), style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (room == Room.MONEY) MoneyCountdown(app)
                    if ((state.counts[room] ?: 0) > 6) PlainInput(query, onQuery, "Search ${room.en}")
                }
            }
            items(list, key = { it.id }) { ItemRow(it) { nav.push(Dest.Item(it.id)) } }
            if (list.isEmpty() && query.isBlank()) item {
                EmptyState(room.icon(), t("Nothing here yet"), roomEmpty(room), action = t("Add")) { nav.push(Dest.Edit(null, room = room)) }
            }
        }
    }
}

private fun roomBlurb(room: Room) = when (room) {
    Room.EVERYDAY -> "Email, shopping, streaming, Wi-Fi. Opens with the vault."
    Room.MONEY -> "Banking, cards, UPI, investments, tax. Needs a fresh fingerprint every time and locks itself when you stop using it."
    Room.LEGACY -> "Insurance, locker, will and letters. Your nominees can open this room, and only this room, with their cards."
}

private fun roomEmpty(room: Room) = when (room) {
    Room.EVERYDAY -> "Start with your main email account."
    Room.MONEY -> "Add your net banking login, cards (PIN hints only) and PPF/EPF."
    Room.LEGACY -> "Add insurance policies and where the locker key is. This is what families struggle to find."
}
