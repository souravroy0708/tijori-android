package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.crypto.Bytes
import com.tijoriapp.vault.core.crypto.NomineeCard
import com.tijoriapp.vault.core.crypto.Shamir
import com.tijoriapp.vault.core.crypto.wipe
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.LegacyPackage
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.VaultItem
import com.tijoriapp.vault.data.PdfKits
import com.tijoriapp.vault.data.VaultSession.UnlockResult
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.launch

private val SCHEMES = listOf(2 to 3, 3 to 5, 2 to 2)

@Composable
fun FamilyTab(app: TijoriApp) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val state by app.session.state.collectAsStateWithLifecycle()
    val s by app.prefs.settings.collectAsStateWithLifecycle()
    val family = state.items.filter { it.room == Room.LEGACY }
    var scheme by remember { mutableIntStateOf(SCHEMES.indexOf(s.familyK to s.familyN).coerceAtLeast(0)) }
    var reauthForCards by remember { mutableStateOf(false) }
    var rotate by remember { mutableStateOf(false) }
    val outdated = s.familyExportAt > 0 && family.any { it.updatedAt > s.familyExportAt }

    HeroPage(hero = {
        Text(t("Family"), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text("If something happens to you, your nominees can open the Family room with their cards. Only that room: your banking and everyday logins stay sealed.",
            color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderTile("In Family room", "${family.size}", "items", Mint, Modifier.weight(1f))
            HeaderTile("Cards", if (s.cardsMadeAt > 0) "${s.familyK} of ${s.familyN}" else "—", if (s.cardsMadeAt > 0) "made ${relativeTime(s.cardsMadeAt)}" else "not made yet", Haldi, Modifier.weight(1f))
        }
    }) {
        AppCard(title = "1. Fill the Family room") {
            Text("Think about what your family would need to find: policies, the locker, the will, who to call.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ItemType.INSURANCE, ItemType.LOCKER, ItemType.LETTER).forEach { type ->
                    Pill("+ ${type.label()}", color = CardBg2, textColor = TextMain) { nav.push(Dest.Edit(null, type)) }
                }
            }
            if (family.isNotEmpty()) TextLink("See all ${family.size} items") { nav.push(Dest.RoomList(Room.LEGACY)) }
        }

        AppCard(title = "2. Make nominee cards") {
            Text("Each card holds one piece of the Family key (Shamir's secret sharing). Fewer cards than needed reveal nothing at all.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Segmented(SCHEMES.map { "${it.first} of ${it.second}" }, scheme) { scheme = it }
            Text(when (SCHEMES[scheme]) {
                2 to 3 -> "Recommended. E.g. spouse, sibling and your lawyer or CA. Any two together can open it."
                3 to 5 -> "For larger families. Any three of five people together."
                else -> "Both people must agree, e.g. two children. If one card is lost, the room can't be opened."
            }, style = MaterialTheme.typography.bodySmall)
            BigButton(if (s.cardsMadeAt > 0) "Make a new set of cards (PDF)" else "Make cards (PDF)", icon = Icons.Filled.Print, color = Mint) { reauthForCards = true }
            if (s.cardsMadeAt > 0) Text("Older card sets keep working. To cancel them, use \"Cancel all cards\" below.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        AppCard(title = "3. Save the Family package") {
            Text("An encrypted file with only the Family room. Give it to one nominee (pen drive, their own Google Drive or email: it's useless without the cards).",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            when {
                s.familyExportAt == 0L -> Banner("Not saved yet", icon = Icons.Filled.Info)
                outdated -> Banner("You changed the Family room after the last package. Save and share it again.", icon = Icons.Filled.Warning)
                else -> StatusRow("Up to date", "Saved ${relativeTime(s.familyExportAt)}", true)
            }
            BigButton("Save Family package", icon = Icons.Filled.Save, enabled = family.isNotEmpty()) {
                val file = "Tijori-Family-${s.ownerName.ifBlank { "vault" }.replace(Regex("[^A-Za-z0-9]"), "")}.tijori-family"
                host.createFile(file, "application/octet-stream", write = { it.write(app.session.familyPackageBytes()) },
                    onDone = { ok -> if (ok) { app.prefs.markFamilyExport(); Toasts.show("Family package saved") } })
            }
        }

        AppCard(title = "4. Practice like your family would") {
            Text("Walk through the nominee screen with your own cards, so you can explain it calmly. Nothing is changed.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Pill("Start practice", color = Mint) {
                scope.launch { nav.push(Dest.Nominee(practice = app.session.familyPackageBytes())) }
            }
        }

        SectionLabel("If a card is lost")
        AppCard {
            SettingRow(Icons.Filled.LinkOff, "Cancel all cards", "Makes a new Family key. Every old card and Family package stops working.", onClick = { rotate = true })
        }
    }

    if (reauthForCards) ReauthDialog(app, "Make nominee cards", "Cards can open your Family room. Confirm it's you.",
        onDismiss = { reauthForCards = false }) {
        reauthForCards = false
        val (k, n) = SCHEMES[scheme]
        val cards = app.session.makeCards(k, n)
        host.createFile("Tijori-Nominee-Cards-$k-of-$n.pdf", "application/pdf",
            write = { PdfKits(app).nomineeCards(it, app.prefs.now.ownerName, cards) },
            onDone = { ok -> if (ok) { app.prefs.markCards(k, n); Toasts.show("Cards saved. Print them, then delete the PDF from your phone.") } })
    }

    if (rotate) {
        var pass by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) rotate = false },
            title = { Text("Cancel all nominee cards?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Old cards and Family packages stop working. Afterwards, make new cards and save a new Family package.")
                    SecretInput(pass, { pass = it; error = null }, t("Master passphrase"), isError = error != null, supporting = error)
                }
            },
            confirmButton = {
                TextButton(enabled = pass.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = app.session.rotateFamilyKey(pass)
                        busy = false
                        if (r is UnlockResult.Ok) { rotate = false; Toasts.show("Done. Make new cards now.") } else error = describe(r)
                    }
                }) { Text(if (busy) "Working…" else "Cancel cards", color = Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { rotate = false }, enabled = !busy) { Text("Keep them") } },
        )
    }
}

// =========================================================================================
// Nominee: open a Family package with k cards. Works on any phone, with or without a vault.
// =========================================================================================

@Composable
fun NomineeScreen(app: TijoriApp, practice: ByteArray?, onClose: () -> Unit) {
    val host = LocalHost.current
    var pkg by remember { mutableStateOf(practice?.let { runCatching { LegacyPackage.read(it) }.getOrNull() }) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val codes = remember { mutableStateListOf("", "") }
    var opened by remember { mutableStateOf<List<VaultItem>?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }

    BackHandler { if (viewing != null) viewing = null else onClose() }
    LaunchedEffect(pkg?.k) { pkg?.let { p -> while (codes.size < p.k) codes.add("") } }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(if (practice != null) "Practice: nominee view" else "Nominee", onBack = { if (viewing != null) viewing = null else onClose() }, accent = Mint)
        ScreenColumn {
            val v = viewing
            val items = opened
            when {
                v != null -> ReadOnlyItem(app, v)
                items != null -> {
                    Banner("Read-only. Nothing from this package is saved on this phone. Close this screen when you're done.", GoodBg, GoodText, Icons.Filled.Visibility)
                    Text("${pkg?.owner?.ifBlank { "The owner" }}'s Family room", style = MaterialTheme.typography.headlineMedium)
                    items.sortedBy { it.title.lowercase() }.forEach { ItemRow(it) { viewing = it } }
                    if (items.isEmpty()) EmptyState(Icons.Filled.FamilyRestroom, "Empty", "The package has no items.")
                }
                else -> {
                    AppCard(title = "1. Family package") {
                        val p = pkg
                        if (p == null) {
                            Text("A file ending in .tijori-family. The vault owner gave it to someone in the family.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            Pill("Choose file") {
                                host.openFile(arrayOf("*/*")) { bytes ->
                                    if (bytes != null) runCatching { LegacyPackage.read(bytes) }
                                        .onSuccess { pkg = it; fileError = null }.onFailure { fileError = it.message }
                                }
                            }
                            fileError?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }
                        } else StatusRow("${p.owner.ifBlank { "Owner" }}'s Family package", "${p.rows.size} items · needs any ${p.k} of ${p.n} cards · saved ${relativeTime(p.exportedAt)}", true)
                    }
                    val p = pkg
                    if (p != null) AppCard(title = "2. Type the card codes") {
                        Text("Lower case, spaces or missing dashes are fine.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        val parsed = codes.map { if (it.isBlank()) null else NomineeCard.parse(it) }
                        val good = parsed.filterIsInstance<NomineeCard.Companion.Parsed.Ok>().map { it.card }
                        codes.forEachIndexed { i, code ->
                            val r = parsed[i]
                            val dup = r is NomineeCard.Companion.Parsed.Ok && good.count { it.share.index == r.card.share.index } > 1
                            val other = r is NomineeCard.Companion.Parsed.Ok && good.isNotEmpty() && !good.first().setId.contentEquals(r.card.setId)
                            PlainInput(code, { codes[i] = it; openError = null }, "Card ${i + 1}", singleLine = false, mono = true,
                                isError = (r != null && r !is NomineeCard.Companion.Parsed.Ok) || dup || other,
                                supporting = when {
                                    r == null -> null
                                    r is NomineeCard.Companion.Parsed.Typo -> "There's a typo in this code. Check each character."
                                    r is NomineeCard.Companion.Parsed.Malformed -> "Keep typing. A card code starts with TJN-"
                                    dup -> "This is the same card as another one"
                                    other -> "This card is from a different set"
                                    r is NomineeCard.Companion.Parsed.Ok -> "Card ${r.card.share.index} of ${r.card.n} ✓"
                                    else -> null
                                })
                        }
                        TextLink("+ Another card") { codes.add("") }
                        val distinct = good.distinctBy { it.share.index }.filter { it.setId.contentEquals(good.firstOrNull()?.setId ?: ByteArray(0)) }
                        openError?.let { Banner(it, BadBg, BadText, Icons.Filled.Warning) }
                        BigButton("Open Family room", color = Mint, enabled = distinct.size >= p.k) {
                            val key = Shamir.combine(distinct.map { it.share })
                            val items = runCatching { LegacyPackage.open(p, key) }.getOrNull()
                            key.wipe()
                            if (items == null) openError = "These cards don't open this package. The owner may have made new cards: ask for the latest set and package."
                            else opened = items
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyItem(app: TijoriApp, item: VaultItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemIcon(item.type, item.room, 56.dp); Spacer(Modifier.width(12.dp))
        Column { Text(item.title, style = MaterialTheme.typography.headlineMedium); Text(item.type.label(), color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
    AppCard {
        item.fields.filter { it.value.isNotBlank() }.forEach { f ->
            Column {
                Text(f.label, style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { app.copy(f.label, f.value, f.isSecret); Toasts.show("${f.label} copied") }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy ${f.label}") }
                }
            }
        }
    }
    if (item.notes.isNotBlank()) AppCard(title = "Notes") { Text(item.notes, style = MaterialTheme.typography.bodyLarge) }
}
