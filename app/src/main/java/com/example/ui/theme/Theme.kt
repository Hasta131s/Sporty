package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.viewmodel.AppColorTheme

@Composable
fun MyApplicationTheme(
    theme: AppColorTheme = AppColorTheme.SPOTIFY_GREEN,
    content: @Composable () -> Unit,
) {
    val accentColor = when (theme) {
        AppColorTheme.SPOTIFY_GREEN -> Color(0xFF1DB954)
        AppColorTheme.COSMIC_INDIGO -> Color(0xFF673AB7)
        AppColorTheme.CYBERPUNK_AMBER -> Color(0xFFFFB300)
        AppColorTheme.NEON_PINK -> Color(0xFFE91E63)
        AppColorTheme.CRIMSON_RED -> Color(0xFFD50000)
    }

    val customDarkColorScheme = darkColorScheme(
        primary = accentColor,
        secondary = Color(0xFF1A1A1A),
        background = Color(0xFF090909),
        surface = Color(0xFF121212),
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF242424),
        onSurfaceVariant = Color(0xFFB3B3B3)
    )

    MaterialTheme(
        colorScheme = customDarkColorScheme,
        typography = Typography,
        content = content
    )
}
