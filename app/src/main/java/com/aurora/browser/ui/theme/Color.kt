package com.aurora.browser.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand seed (spec B-2: #1B6EF3). Used as the fallback when dynamic color is
 * unavailable (below API 31); on API 31+ the system wallpaper palette wins.
 */
val SeedBlue = Color(0xFF1B6EF3)

val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = SeedBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2A5E),
    secondary = Color(0xFF4A6FA5),
    onSecondary = Color.White,
    tertiary = Color(0xFF2E9E8B),
    background = Color(0xFFFDFDFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFDFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF44474E),
    outlineVariant = Color(0xFFC9CFDB),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0A2A5E),
    primaryContainer = Color(0xFF14408F),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF9DB9DD),
    onSecondary = Color(0xFF0E2A4A),
    tertiary = Color(0xFF6FD3C0),
    background = Color(0xFF141518),
    onBackground = Color(0xFFE3E3E6),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFE3E3E6),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFC9CCD2),
    outlineVariant = Color(0xFF3E434C),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
