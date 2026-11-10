@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.crypto.Passphrase
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.crypto.Totp
import com.tijoriapp.vault.core.vault.*
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// =========================================================================================
// Detail
// =========================================================================================

@Composable
fun ItemDetailScreen(app: TijoriApp, id: String) {
    val nav = LocalNav.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    val state by app.session.state.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    if (item == null) {
        Column(Modifier.fillMaxSize().background(Paper)) {
            TopBar("", onBack = { nav.pop() })
            Column(Modifier.padding(16.dp)) {
                if (!state.moneyOpen) MoneyGate(app) else EmptyState(Icons.Filled.SearchOff, "This item is gone", "It may have been deleted.", "Back") { nav.pop() }
            }
        }
        return
    }
    val touch: () -> Unit = { if (item.room == Room.MONEY) app.session.touchMoney() }
    val findings = remember(state.items, item) { Health.analyze(state.items, words = app.words).findings.filter { it.item.id == item.id } }

    fun copy(label: String, value: String, sensitive: Boolean) {
        app.copy(label, value, sensitive)
        app.session.markUsed(item.id)
        touch()
        Toasts.show(if (sensitive) t("Copied. Clears in %s s", app.prefs.now.clipboardSeconds) else "$label copied")
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(item.room.label(), onBack = { nav.pop() }, accent = item.room.accent) {
            IconButton(onClick = { scope.launch { app.session.save(item.copy(favorite = !item.favorite)) } }) {
                Icon(if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (item.favorite) "Remove from favourites" else "Add to favourites",
                    tint = if (item.favorite) Haldi else TextMain)
            }
            IconButton(onClick = { touch(); nav.push(Dest.Edit(item.id)) }) { Icon(Icons.Filled.Edit, contentDescription = t("Edit")) }
        }
        ScreenColumn {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(item.type, item.room, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.headlineMedium)
                    Text(item.type.label(), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            if (item.room == Room.MONEY) MoneyCountdown(app)
            findings.filter { it.issue != Health.Issue.No2FA }.forEach { f ->
                Banner(f.detail, if (f.issue == Health.Issue.Expired || f.issue == Health.Issue.Weak) BadBg else WarnBg,
                    if (f.issue == Health.Issue.Expired || f.issue == Health.Issue.Weak) BadText else WarnText, Icons.Filled.Warning)
            }

            AppCard {
                val shown = item.fields.filter { it.value.isNotBlank() }
                if (shown.isEmpty()) Text("No fields yet. Tap edit to add some.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                shown.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = PaperLine)
                    FieldView(f, onCopy = { v, s -> copy(f.label, v, s) }, onOpen = { host.openUrl(it); app.session.markUsed(item.id) }, onReveal = touch)
                }
            }
            if (item.notes.isNotBlank()) AppCard(title = "Notes") {
                Text(item.notes, style = MaterialTheme.typography.bodyLarge)
            }
            if (item.apps.isNotEmpty()) AppCard(title = "Fills in these apps") {
                val pm = LocalContext.current.packageManager
                item.apps.forEach { pkg ->
                    val label = remember(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                    SettingRow(Icons.Filled.Apps, label, pkg, trailing = {
                        IconButton(onClick = { scope.launch { app.session.save(item.copy(apps = item.apps - pkg)) } }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "Stop filling in $label")
                        }
                    })
                }
            }
            AppCard {
                InfoLine("Added", relativeTime(item.createdAt))
                InfoLine("Last changed", relativeTime(item.updatedAt))
                if (item.password != null) InfoLine("Password age", relativeTime(item.passwordChangedAt))
                if (item.lastUsedAt > 0) InfoLine("Last used", relativeTime(item.lastUsedAt))
            }
            GhostButton(t("Delete"), icon = Icons.Filled.Delete) { confirmDelete = true }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete \"${item.title}\"?", "It will be removed from this phone. Older backups still contain it.", t("Delete"), destructive = true,
        onConfirm = { confirmDelete = false; scope.launch { app.session.delete(item.id); nav.pop(); Toasts.show("Deleted") } },
        onDismiss = { confirmDelete = false })
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row { Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun FieldView(f: Field, onCopy: (String, Boolean) -> Unit, onOpen: (String) -> Unit, onReveal: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) { if (revealed) { delay(30_000); revealed = false } } // auto-hide after 30 s

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.weight(1f))
            if (f.kind == FieldKind.PIN_HINT) Chip("Hint only", GoodBg, GoodText)
        }
        when (f.kind) {
            FieldKind.TOTP -> TotpView(f.value, onCopy = { onCopy(it, true) })
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val masked = f.isSecret && !revealed
                Text(
                    if (masked) "•".repeat(10) else f.value,
                    style = if (f.isSecret) MaterialTheme.typography.titleMedium.copy(fontFamily = Mono, letterSpacing = 0.5.sp) else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    maxLines = if (f.kind == FieldKind.NOTE) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (f.kind == FieldKind.EXPIRY || f.kind == FieldKind.DATE) DateChip(f.value)
                if (f.isSecret) IconButton(onClick = { revealed = !revealed; onReveal() }) {
                    Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (revealed) t("Hide") else t("Show"))
                }
                if (f.kind == FieldKind.URL && Urls.host(f.value) != null) IconButton(onClick = { onOpen(f.value) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open website")
                }
                IconButton(onClick = { onCopy(f.value, f.isSecret) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "${t("Copy")} ${f.label}") }
            }
        }
    }
}

@Composable
private fun DateChip(value: String) {
    val d = Dates.parse(value) ?: return
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    when {
        days < 0 -> Chip("Expired", BadBg, BadText)
        days <= 45 -> Chip("$days days left", WarnBg, WarnText)
        else -> {}
    }
}

@Composable
private fun TotpView(secret: String, onCopy: (String) -> Unit) {
    val totp = remember(secret) { Totp.parse(secret) }
    if (totp == null) { Text("Not a valid 2FA key", color = Coral, style = MaterialTheme.typography.bodySmall); return }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(totp) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val code = totp.codeAt(now)
    val left = totp.secondsLeft(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Mono), modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { left / totp.periodSeconds.toFloat() }, modifier = Modifier.size(32.dp),
                color = if (left <= 5) Coral else Mint, trackColor = PaperLine, strokeWidth = 3.dp)
            Text("$left", style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = { onCopy(code) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code") }
    }
}

// =========================================================================================
// Pick a type
// =========================================================================================

@Composable
fun PickTypeScreen() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("What are you saving?", onBack = { nav.pop() })
        ScreenColumn {
            Room.entries.forEach { room ->
                SectionLabel(room.label())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                    ItemType.entries.filter { it.room == room }.forEach { type ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(CardBg)
                                .clickable(role = Role.Button) { nav.replace(Dest.Edit(null, type)) }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ItemIcon(type, room, 40.dp)
                            Text(type.label(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// Edit
// =========================================================================================

@Composable
fun EditItemScreen(app: TijoriApp, dest: Dest.Edit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val state by app.session.state.collectAsStateWithLifecycle()
    val original = remember(dest) {
        dest.id?.let { id -> state.items.firstOrNull { it.id == id } }
            ?: Templates.newItem(dest.type ?: if (dest.room == Room.LEGACY) ItemType.INSURANCE else if (dest.room == Room.MONEY) ItemType.NET_BANKING else ItemType.LOGIN)
                .let { if (dest.room != null) it.copy(room = dest.room) else it }
    }
    val isNew = dest.id == null
    var title by remember { mutableStateOf(original.title) }
    var room by remember { mutableStateOf(original.room) }
    var notes by remember { mutableStateOf(original.notes) }
    val fields = remember { mutableStateListOf<Field>().apply { addAll(original.fields) } }
    var genFor by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val draft = original.copy(title = title, room = room, notes = notes, fields = fields.toList())
    val dirty = draft.title != original.title || draft.room != original.room || draft.notes != original.notes || draft.fields != original.fields
    fun goBack() { if (dirty) confirmDiscard = true else nav.pop() }
    BackHandler { goBack() }
    LaunchedEffect(fields.toList(), title) { if (room == Room.MONEY) app.session.touchMoney() }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar(if (isNew) "New ${original.type.label()}" else t("Edit"), onBack = { goBack() }, accent = room.accent) {
            TextButton(onClick = {
                saving = true
                scope.launch {
                    val clean = draft.copy(fields = fields.filter { it.value.isNotBlank() || isNew.not() }.map { it.copy(value = it.value.trimEnd()) })
                    if (app.session.save(clean)) { nav.pop(); if (isNew) nav.push(Dest.Item(clean.id)); Toasts.show("Saved in ${room.label()}") }
                    else Toasts.show("Open the ${room.label()} room first")
                    saving = false
                }
            }, enabled = title.isNotBlank() && !saving && app.session.canWrite(room)) { Text(t("Save"), fontWeight = FontWeight.Bold, color = Link) }
        }
        ScreenColumn {
            val tip = Templates.of(original.type).tip
            if (isNew) Banner(tip, CardBg2, Muted, Icons.Filled.Lightbulb)
            PlainInput(title, { title = it }, "Title", placeholder = when (original.type) {
                ItemType.NET_BANKING -> "e.g. HDFC NetBanking"; ItemType.INSURANCE -> "e.g. LIC Jeevan Anand"; ItemType.EMAIL -> "e.g. Personal Gmail"; else -> "Name it"
            })
            Text("Room", style = MaterialTheme.typography.labelMedium, color = Muted)
            Segmented(Room.entries.map { it.label() }, Room.entries.indexOf(room)) { room = Room.entries[it] }
            if (!app.session.canWrite(room)) MoneyGate(app, compact = true)

            fields.forEachIndexed { index, f ->
                FieldEditor(
                    f, words = app.words,
                    onChange = { fields[index] = it },
                    onRemove = { fields.removeAt(index) },
                    onGenerate = { genFor = f.id },
                )
            }
            Box {
                GhostButton("Add a field", icon = Icons.Filled.Add) { addMenu = true }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    listOf(
                        "Text" to FieldKind.TEXT, "Password" to FieldKind.PASSWORD, "Hidden text" to FieldKind.SECRET,
                        "Website" to FieldKind.URL, "Username" to FieldKind.USERNAME, "Phone" to FieldKind.PHONE, "Number" to FieldKind.NUMBER,
                        "PIN hint" to FieldKind.PIN_HINT, "2FA code" to FieldKind.TOTP, "Expiry date" to FieldKind.EXPIRY,
                    ).forEach { (label, kind) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { fields.add(Field(label, kind, "")); addMenu = false })
                    }
                }
            }
            PlainInput(notes, { notes = it }, "Notes", singleLine = false, minLines = 3)
        }
    }

    genFor?.let { fid ->
        GeneratorSheet(app, onDismiss = { genFor = null }, onUse = { value ->
            val i = fields.indexOfFirst { it.id == fid }
            if (i >= 0) fields[i] = fields[i].copy(value = value)
            genFor = null
        })
    }
    if (confirmDiscard) ConfirmDialog("Discard changes?", "What you typed here won't be saved.", "Discard", destructive = true,
        onConfirm = { confirmDiscard = false; nav.pop() }, onDismiss = { confirmDiscard = false })
}

@Composable
private fun FieldEditor(f: Field, words: Passphrase, onChange: (Field) -> Unit, onRemove: () -> Unit, onGenerate: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, style = MaterialTheme.typography.labelMedium, color = Muted,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "Rename field") { renaming = true }.padding(vertical = 6.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = "Remove ${f.label}", modifier = Modifier.size(18.dp)) }
        }
        when (f.kind) {
            FieldKind.PASSWORD, FieldKind.SECRET -> {
                SecretInput(f.value, { onChange(f.copy(value = it)) }, f.label, trailingExtra = {
                    if (f.kind == FieldKind.PASSWORD) IconButton(onClick = onGenerate) { Icon(Icons.Filled.Casino, contentDescription = t("Generate password")) }
                })
                if (f.kind == FieldKind.PASSWORD && f.value.isNotEmpty()) StrengthMeter(remember(f.value) { Strength.check(f.value, words) })
            }
            FieldKind.TOTP -> {
                val ok = f.value.isBlank() || Totp.parse(f.value) != null
                PlainInput(f.value, { onChange(f.copy(value = it.trim())) }, f.label, mono = true, isError = !ok,
                    supporting = if (ok) "Paste the setup key or the otpauth:// link from the site's QR page" else "That doesn't look like a 2FA setup key")
            }
            FieldKind.NOTE -> PlainInput(f.value, { onChange(f.copy(value = it)) }, f.label, singleLine = false, minLines = 3)
            FieldKind.PIN_HINT -> {
                val looksReal = Regex("^\\d{4,6}$").matches(f.value.trim())
                PlainInput(f.value, { onChange(f.copy(value = it)) }, f.label, isError = looksReal,
                    supporting = if (looksReal) "That looks like a real PIN. Save a clue only you understand." else "A clue only you understand, never the PIN itself")
            }
            else -> {
                val kt = when (f.kind) { FieldKind.PHONE -> KeyboardType.Phone; FieldKind.NUMBER -> KeyboardType.Number; FieldKind.EMAIL -> KeyboardType.Email; FieldKind.URL -> KeyboardType.Uri; else -> KeyboardType.Text }
                val cardWarn = f.label.contains("Last 4") && f.value.filter { it.isDigit() }.length > 4
                val badDate = (f.kind == FieldKind.EXPIRY || f.kind == FieldKind.DATE) && f.value.isNotBlank() && Dates.parse(f.value) == null
                PlainInput(f.value, { onChange(f.copy(value = it)) }, f.label, keyboardType = kt,
                    isError = cardWarn || badDate,
                    placeholder = if (f.kind == FieldKind.EXPIRY) "MM/YY or DD/MM/YYYY" else null,
                    supporting = when { cardWarn -> "Save only the last 4 digits of a card"; badDate -> "Try MM/YY, MM/YYYY or DD/MM/YYYY"; else -> null })
            }
        }
    }
    if (renaming) {
        var label by remember { mutableStateOf(f.label) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Field name") },
            text = { PlainInput(label, { label = it.take(40) }, "Name") },
            confirmButton = { TextButton(onClick = { onChange(f.copy(label = label.ifBlank { f.label })); renaming = false }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(t("Cancel")) } })
    }
}

// =========================================================================================
// Generator
// =========================================================================================

@Composable
fun GeneratorSheet(app: TijoriApp, onDismiss: () -> Unit, onUse: ((String) -> Unit)?) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableIntStateOf(0) }
    var words by remember { mutableFloatStateOf(5f) }
    var length by remember { mutableFloatStateOf(20f) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var seed by remember { mutableIntStateOf(0) }
    val value = remember(mode, words.toInt(), length.toInt(), digits, symbols, seed) {
        when (mode) {
            0 -> app.words.words(words.toInt(), separator = "-", capitalize = true, addDigit = true)
            1 -> Passphrase.random(length.toInt(), digits, symbols)
            else -> Passphrase.pin(length.toInt().coerceIn(4, 8))
        }
    }
    val bits = when (mode) {
        0 -> app.words.wordsEntropy(words.toInt(), true)
        1 -> Passphrase.randomEntropy(length.toInt(), digits, symbols)
        else -> (length.toInt().coerceIn(4, 8) * 3.32).toInt()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("Generate password"), style = MaterialTheme.typography.titleLarge)
            Segmented(listOf("Words", "Random", "PIN"), mode) { mode = it; length = when (it) { 2 -> 6f; 1 -> 20f; else -> length } }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).padding(18.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono), color = TextMain)
            }
            Text("≈ $bits bits · " + when (mode) { 0 -> "easy to type, hard to guess"; 1 -> "best for sites that allow long passwords"; else -> "only for lockers or devices, never for bank cards" },
                style = MaterialTheme.typography.bodySmall, color = Muted)
            when (mode) {
                0 -> { Text("${words.toInt()} words", style = MaterialTheme.typography.labelMedium); Slider(words, { words = it }, valueRange = 3f..8f, steps = 4) }
                1 -> {
                    Text("${length.toInt()} characters", style = MaterialTheme.typography.labelMedium)
                    Slider(length, { length = it }, valueRange = 12f..40f, steps = 27)
                    SwitchRow(Icons.Filled.Pin, "Numbers", null, digits) { digits = it }
                    SwitchRow(Icons.Filled.Tag, "Symbols", "Turn off for sites that reject them", symbols) { symbols = it }
                }
                else -> { Text("${length.toInt().coerceIn(4, 8)} digits", style = MaterialTheme.typography.labelMedium); Slider(length, { length = it }, valueRange = 4f..8f, steps = 3) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("New", Modifier.weight(1f), icon = Icons.Filled.Refresh) { seed++ }
                if (onUse != null) BigButton("Use this", Modifier.weight(1f)) { onUse(value) }
                else BigButton(t("Copy"), Modifier.weight(1f)) { app.copy("Password", value, true); Toasts.show(t("Copied. Clears in %s s", app.prefs.now.clipboardSeconds)) }
            }
        }
    }
}
