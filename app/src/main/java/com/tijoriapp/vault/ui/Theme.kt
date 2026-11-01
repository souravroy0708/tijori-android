package com.tijoriapp.vault.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.R

// Tijori Brass Safe Palette: Deep Slate Navy (#0B1320), Burnished Gold (#EAB308), Emerald (#10B981)
val VaultNavy = Color(0xFF0B1320)
val VaultNavy2 = Color(0xFF141E30)
val VaultNavy3 = Color(0xFF1E2D44)
val VaultLine = Color(0xFF283A54)
val VaultGold = Color(0xFFEAB308)
val VaultGoldDark = Color(0xFFB45309)
val VaultGoldSoft = Color(0xFFFEF3C7)
val VaultEmerald = Color(0xFF10B981)
val VaultEmeraldSoft = Color(0xFFD1FAE5)
val VaultCoral = Color(0xFFEF4444)
val VaultCoralSoft = Color(0xFFFEE2E2)

object ThemeState { var dark by mutableStateOf(true) } // Tijori defaults to dark safe mode

val Paper: Color get() = if (ThemeState.dark) Color(0xFF070C14) else Color(0xFFF6F8FB)
val Paper2: Color get() = if (ThemeState.dark) Color(0xFF0E1724) else Color(0xFFEBF0F7)
val CardBg: Color get() = if (ThemeState.dark) Color(0xFF121D2D) else Color.White
val PaperLine: Color get() = if (ThemeState.dark) Color(0xFF202E42) else Color(0xFFD9E2EE)
val TextMain: Color get() = if (ThemeState.dark) Color(0xFFF1F5F9) else Color(0xFF0B1320)
val Muted: Color get() = if (ThemeState.dark) Color(0xFF94A3B8) else Color(0xFF52637A)
val MutedOnDark = Color(0xFFCBD5E1)

val Display = FontFamily(
    Font(R.font.anek_bold, FontWeight.Bold),
    Font(R.font.anek_extrabold, FontWeight.ExtraBold),
)

val Body = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp),
)

@Composable
fun TijoriTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    ThemeState.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = VaultGold,
            onPrimary = VaultNavy,
            secondary = VaultEmerald,
            onSecondary = VaultNavy,
            background = Color(0xFF070C14),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF121D2D),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF0E1724),
            outline = Color(0xFF202E42),
        ) else lightColorScheme(
            primary = VaultNavy,
            onPrimary = Color.White,
            secondary = VaultGold,
            onSecondary = VaultNavy,
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF0B1320),
            surface = Color.White,
            onSurface = Color(0xFF0B1320),
            surfaceVariant = Color(0xFFEBF0F7),
            outline = Color(0xFFD9E2EE),
        ),
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides TextMain, content = content)
    }
}
