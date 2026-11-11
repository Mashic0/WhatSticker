package com.whatsticker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.whatsticker.app.data.local.ColorAccent
import com.whatsticker.app.data.local.ThemeMode

private fun getLightScheme(primaryColor: Color): ColorScheme {
    return lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = primaryColor.copy(alpha = 0.2f),
        onPrimaryContainer = primaryColor,
        background = LightBackground,
        surface = LightSurface,
        surfaceVariant = LightSurfaceVariant,
        onBackground = Color(0xFF191C1D),
        onSurface = Color(0xFF191C1D)
    )
}

private fun getDarkScheme(primaryColor: Color): ColorScheme {
    return darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        primaryContainer = primaryColor.copy(alpha = 0.25f),
        onPrimaryContainer = Color.White,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        onBackground = Color(0xFFE2E2E5),
        onSurface = Color(0xFFE2E2E5)
    )
}

@Composable
fun WhatStickerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorAccent: ColorAccent = ColorAccent.WHATSAPP_GREEN,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        colorAccent == ColorAccent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val primary = when (colorAccent) {
                ColorAccent.DYNAMIC, ColorAccent.WHATSAPP_GREEN -> WhatsAppGreen
                ColorAccent.OCEAN_BLUE -> AccentOceanBlue
                ColorAccent.DEEP_PURPLE -> AccentDeepPurple
                ColorAccent.CORAL -> AccentCoral
                ColorAccent.AMBER -> AccentAmber
            }
            if (darkTheme) getDarkScheme(primary) else getLightScheme(primary)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
