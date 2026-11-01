package com.tijoriapp.vault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.data.RoomType
import kotlinx.coroutines.delay

@Composable
fun AppCard(
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .border(1.dp, PaperLine, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun BigButton(
    text: String,
    enabled: Boolean = true,
    gold: Boolean = true,
    emerald: Boolean = false,
    coral: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        coral -> VaultCoral
        emerald -> VaultEmerald
        gold -> VaultGold
        else -> VaultNavy3
    }
    val fg = when {
        coral -> Color.White
        else -> VaultNavy
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(
    text: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (danger) VaultCoral else VaultGold
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, color),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun SmallPill(
    text: String,
    gold: Boolean = false,
    emerald: Boolean = false,
    coral: Boolean = false,
    onClick: () -> Unit,
) {
    val (bg, fg) = when {
        gold -> VaultGoldSoft to VaultGoldDark
        emerald -> VaultEmeraldSoft to Color(0xFF065F46)
        coral -> VaultCoralSoft to Color(0xFF991B1B)
        else -> Paper2 to TextMain
    }
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun RoomBadge(room: RoomType) {
    val (bg, fg) = when (room) {
        RoomType.EVERYDAY -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
        RoomType.MONEY -> VaultGoldSoft to VaultGoldDark
        RoomType.LEGACY -> Color(0xFFF3E8FF) to Color(0xFF6B21A8)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("${room.icon} ${room.labelEn}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = fg)
    }
}

@Composable
fun AppField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    hint: String? = null,
    singleLine: Boolean = true,
    isSecret: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(!isSecret) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        supportingText = hint?.let { { Text(it) } },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = if (isSecret) {
            {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = Muted,
                    modifier = Modifier.clickable { passwordVisible = !passwordVisible },
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedBorderColor = VaultGold,
            unfocusedBorderColor = PaperLine,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Masked Secret Field Row (Spec Page 2, 5 & 8):
 * Masked by default; tap to reveal; sensitive clipboard copy that clears in 30 seconds.
 */
@Composable
fun MaskedFieldRow(
    label: String,
    clearValue: String,
    isSecret: Boolean,
    onCopy: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(!isSecret) }
    var secondsLeft by remember { mutableIntStateOf(30) }

    // Auto-hide secret after 30 seconds of reveal (Spec Page 5)
    LaunchedEffect(revealed) {
        if (revealed && isSecret) {
            secondsLeft = 30
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            revealed = false
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .border(1.dp, PaperLine, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(
                text = if (revealed) clearValue else "••••••••••••",
                style = if (revealed) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontFamily = if (revealed && isSecret) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (revealed && isSecret) {
                Text("Hiding in ${secondsLeft}s", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = VaultGold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isSecret) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Paper2)
                        .clickable { revealed = !revealed },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Reveal",
                        tint = VaultGold,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Paper2)
                    .clickable { onCopy(clearValue) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = VaultEmerald,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
