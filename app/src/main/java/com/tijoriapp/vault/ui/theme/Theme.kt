package com.tijoriapp.vault.ui.theme

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
import com.tijoriapp.vault.core.vault.Room

// Shared design language with Bas 1 Minute: deep ink hero, haldi-yellow accent, soft paper sheet.
val Ink = Color(0xFF0F1235)
val Ink2 = Color(0xFF181D4C)
val Ink3 = Color(0xFF232963)
val Line = Color(0xFF323A7C)
val Haldi = Color(0xFFFFC21A)
val Violet = Color(0xFF8F99FF)
val Coral = Color(0xFFFF8A65)
val Mint = Color(0xFF5ED6A0)
val MintDark = Color(0xFF0B7A55)
val Blue = Color(0xFF3446C8)
val Danger = Color(0xFFB42318)
val MutedOnDark = Color(0xFFBCC1E0)

/** Theme switch read by the surface colours below, so screens adapt without plumbing. */
object ThemeState { var dark by mutableStateOf(false) }

val Paper: Color get() = if (ThemeState.dark) Color(0xFF0A0C22) else Color(0xFFF4F5FA)
val PaperLine: Color get() = if (ThemeState.dark) Color(0xFF2B3063) else Color(0xFFDCDFEC)
val CardBg: Color get() = if (ThemeState.dark) Color(0xFF161A42) else Color.White
val CardBg2: Color get() = if (ThemeState.dark) Color(0xFF1E2350) else Color(0xFFEEF0F8)
val TextMain: Color get() = if (ThemeState.dark) Color(0xFFEEF0FF) else Color(0xFF0F1235)
val Muted: Color get() = if (ThemeState.dark) Color(0xFFA9AECB) else Color(0xFF4A4F6A)
val Link: Color get() = if (ThemeState.dark) Violet else Blue
val WarnBg: Color get() = if (ThemeState.dark) Color(0xFF3A2E10) else Color(0xFFFFF3C8)
val WarnText: Color get() = if (ThemeState.dark) Color(0xFFFFD866) else Color(0xFF8A6200)
val BadBg: Color get() = if (ThemeState.dark) Color(0xFF3D1A14) else Color(0xFFFFE7DF)
val BadText: Color get() = if (ThemeState.dark) Color(0xFFFFB199) else Color(0xFF8A2A0F)
val GoodBg: Color get() = if (ThemeState.dark) Color(0xFF0F3326) else Color(0xFFE3F7EE)
val GoodText: Color get() = if (ThemeState.dark) Mint else MintDark

/** Each room has its own colour everywhere it appears. */
val Room.accent: Color get() = when (this) { Room.EVERYDAY -> Violet; Room.MONEY -> Haldi; Room.LEGACY -> Mint }
val Room.onAccent: Color get() = Ink

val Display = FontFamily(Font(R.font.anek_bold, FontWeight.Bold), Font(R.font.anek_extrabold, FontWeight.ExtraBold))
val Body = FontFamily(Font(R.font.mukta_regular, FontWeight.Normal), Font(R.font.mukta_medium, FontWeight.Medium), Font(R.font.mukta_bold, FontWeight.Bold))
val Devanagari = FontFamily(Font(R.font.anek_devanagari, FontWeight.SemiBold))
val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun TijoriTheme(dark: Boolean, content: @Composable () -> Unit) {
    ThemeState.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Violet, onPrimary = Ink, secondary = Haldi, onSecondary = Ink,
            background = Color(0xFF0A0C22), onBackground = Color(0xFFEEF0FF),
            surface = Color(0xFF161A42), onSurface = Color(0xFFEEF0FF),
            surfaceVariant = Color(0xFF1E2350), onSurfaceVariant = Color(0xFFA9AECB),
            surfaceContainerHigh = Color(0xFF1E2350), surfaceContainer = Color(0xFF161A42),
            outline = Color(0xFF2B3063), error = Coral,
        ) else lightColorScheme(
            primary = Ink, onPrimary = Color.White, secondary = Haldi, onSecondary = Ink,
            background = Color(0xFFF4F5FA), onBackground = Ink,
            surface = Color.White, onSurface = Ink,
            surfaceVariant = Color(0xFFEEF0F8), onSurfaceVariant = Color(0xFF4A4F6A),
            surfaceContainerHigh = Color.White, surfaceContainer = Color.White,
            outline = Color(0xFFDCDFEC), error = Danger,
        ),
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides TextMain, content = content)
    }
}
