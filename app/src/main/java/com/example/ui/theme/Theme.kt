package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CosmicSlate = Color(0xFF0F0E17)
val CardSlate = Color(0xFF1D1B26)
val PrimaryNeonViolet = Color(0xFF9F2BFF)
val SecondaryNeonBlue = Color(0xFF00E5FF)
val ActivePink = Color(0xFFFF007F)
val TypographyWhite = Color(0xFFFFFFFE)
val SubtitleWhite = Color(0xFFA7A9BE)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNeonViolet,
    secondary = SecondaryNeonBlue,
    tertiary = ActivePink,
    background = CosmicSlate,
    surface = CardSlate,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = TypographyWhite,
    onSurface = TypographyWhite
)

@Composable
fun FlofysTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
