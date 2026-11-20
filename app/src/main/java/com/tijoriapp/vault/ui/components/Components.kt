@file:OptIn(ExperimentalMaterial3Api::class)

package com.tijoriapp.vault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.ui.t
import com.tijoriapp.vault.ui.theme.*

// ------------------------------------------------------------------------------------------
// Layout scaffolds
// ------------------------------------------------------------------------------------------

/** Bas-1-Minute style page: dark hero on top, rounded paper sheet sliding over it. */
@Composable
fun HeroPage(
    hero: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Ink, Ink2)))
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 44.dp),
            content = hero,
        )
        Column(
            Modifier.offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Paper)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = sheet,
        )
    }
}

/** Top bar for pushed screens. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, accent: Color? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Paper).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back")) }
        if (accent != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(accent)); Spacer(Modifier.width(8.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ------------------------------------------------------------------------------------------
// Cards, buttons, rows
// ------------------------------------------------------------------------------------------

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    background: Color = CardBg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
              color: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = color ?: if (ThemeState.dark) Violet else Ink
    val fg = if (color == Haldi || color == Mint || color == Violet || (color == null && ThemeState.dark)) Ink else Color.White
    Button(
        onClick = onClick, enabled = enabled && !loading, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        else {
            if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onDark: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, shape = RoundedCornerShape(50),
        border = BorderStroke(2.dp, if (onDark || ThemeState.dark) Line else Ink),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        val c = if (onDark) Color.White else TextMain
        if (icon != null) { Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c)
    }
}

@Composable
fun TextLink(text: String, color: Color = Link, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, color = color, style = MaterialTheme.typography.labelLarge,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp))
}

@Composable
fun Pill(text: String, color: Color = Haldi, textColor: Color = Ink, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(CardBg2).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
                color = if (on) (if (ThemeState.dark) Ink else Color.White) else Muted,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) (if (ThemeState.dark) Violet else Ink) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 10.dp, vertical = 10.dp))
        }
    }
}

/** One check line with a status badge and optional fix-it pill (setup + trust page). */
@Composable
fun StatusRow(title: String, subtitle: String, ok: Boolean?, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val (bg, fg, sym) = when (ok) { true -> Triple(GoodBg, GoodText, "✓"); false -> Triple(WarnBg, WarnText, "!"); null -> Triple(CardBg2, Muted, "i") }
        Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text(sym, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (action != null && onAction != null) { Spacer(Modifier.width(8.dp)); Pill(action, onClick = onAction) }
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CardBg2), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextMain, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, trailing = {
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = if (ThemeState.dark) Violet else Ink))
    }, onClick = { onChange(!checked) })
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted,
        modifier = modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() })
}

@Composable
fun Banner(text: String, bg: Color = WarnBg, fg: Color = WarnText, icon: ImageVector = Icons.Filled.Info, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextLink(action, color = fg, onClick = onAction)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(CardBg2), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) Pill(action, onClick = onAction)
    }
}

// ------------------------------------------------------------------------------------------
// Vault-specific visuals
// ------------------------------------------------------------------------------------------

fun ItemType.icon(): ImageVector = when (this) {
    ItemType.LOGIN -> Icons.Filled.Key
    ItemType.EMAIL -> Icons.Filled.Email
    ItemType.WIFI -> Icons.Filled.Wifi
    ItemType.NOTE -> Icons.Filled.EditNote
    ItemType.ID_DOC -> Icons.Filled.Badge
    ItemType.NET_BANKING -> Icons.Filled.AccountBalance
    ItemType.UPI -> Icons.Filled.CurrencyRupee
    ItemType.CARD -> Icons.Filled.CreditCard
    ItemType.INVESTMENT -> Icons.Filled.Savings
    ItemType.TAX -> Icons.Filled.Receipt
    ItemType.INSURANCE -> Icons.Filled.HealthAndSafety
    ItemType.LOCKER -> Icons.Filled.Lock
    ItemType.LETTER -> Icons.Filled.Favorite
    ItemType.CUSTOM -> Icons.Filled.Category
}

fun Room.icon(): ImageVector = when (this) {
    Room.EVERYDAY -> Icons.Filled.Smartphone
    Room.MONEY -> Icons.Filled.AccountBalanceWallet
    Room.LEGACY -> Icons.Filled.FamilyRestroom
}

fun Room.label(): String = if (com.tijoriapp.vault.ui.Lang.current == "hi") hi else en
fun ItemType.label(): String = if (com.tijoriapp.vault.ui.Lang.current == "hi") hi else en

/** Rounded tile with the item's type icon tinted by its room. */
@Composable
fun ItemIcon(type: ItemType, room: Room, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(room.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Icon(type.icon(), contentDescription = null, tint = if (ThemeState.dark) room.accent else Ink, modifier = Modifier.size(size * 0.5f))
    }
}

/** Animated ring for the security score (the "DualRing" idea from Bas 1 Minute, single ring here). */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, track: Color = Ink3, center: @Composable () -> Unit) {
    val frac by animateFloatAsState((score / 100f).coerceIn(0f, 1f), tween(900), label = "score")
    val color = when { score >= 80 -> Mint; score >= 50 -> Haldi; else -> Coral }
    Box(modifier.aspectRatio(1f).semantics(mergeDescendants = true) { contentDescription = "Security score $score out of 100" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.09f
            val inset = w / 2
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            if (frac > 0.001f) drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

/** Small stat tile on the dark hero. */
@Composable
fun HeaderTile(label: String, value: String, hint: String, dot: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Ink2).border(1.dp, Line, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp))
            Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
        Text(hint, color = MutedOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StrengthMeter(result: Strength.Result, modifier: Modifier = Modifier) {
    val (label, color, steps) = when (result.level) {
        Strength.Level.VeryWeak -> Triple("Very weak", Coral, 1)
        Strength.Level.Weak -> Triple("Weak", Coral, 2)
        Strength.Level.Fair -> Triple("Fair", Haldi, 3)
        Strength.Level.Strong -> Triple("Strong", Mint, 4)
        Strength.Level.VeryStrong -> Triple("Very strong", Mint, 5)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < steps) color else PaperLine)) }
        }
        Text("$label · ~${result.bits} bits" + (result.warning?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/** Password-style input with show/hide and IME action. Never offers autocorrect or suggestions. */
@Composable
fun SecretInput(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done, onIme: () -> Unit = {},
    isError: Boolean = false, supporting: String? = null, enabled: Boolean = true,
    trailingExtra: (@Composable () -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onIme() }, onGo = { onIme() }, onNext = { onIme() }),
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = {
            Row {
                trailingExtra?.invoke()
                IconButton(onClick = { shown = !shown }) {
                    Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (shown) t("Hide") else t("Show"))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PlainInput(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, placeholder: String? = null,
    supporting: String? = null, isError: Boolean = false, minLines: Int = 1, mono: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        placeholder = placeholder?.let { { Text(it, color = Muted) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        supportingText = supporting?.let { { Text(it) } }, isError = isError,
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono) else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth(),
    )
}

/** Confirm dialog with an optional destructive look. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) Coral else Link, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0L) return "never"
    val d = (now - ms) / 1000
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86_400 -> "${d / 3600} h ago"
        d < 86_400 * 30 -> "${d / 86_400} days ago"
        d < 86_400 * 365 -> "${d / (86_400 * 30)} months ago"
        else -> "${d / (86_400 * 365)} years ago"
    }
}
