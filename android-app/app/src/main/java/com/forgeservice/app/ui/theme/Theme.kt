package com.forgeservice.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium dark automotive theme — very "пафасно"
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B35),        // Energetic orange (brake caliper vibe)
    onPrimary = Color.White,
    secondary = Color(0xFF2E8B57),      // Professional green
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
)

@Composable
fun ForgeServiceTheme(
    darkTheme: Boolean = true, // Force premium dark by default
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}