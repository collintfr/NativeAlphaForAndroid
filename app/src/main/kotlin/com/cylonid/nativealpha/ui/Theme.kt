package com.cylonid.nativealpha.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightFallback = lightColorScheme(
    primary = Color(0xFF904A43), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF3B0907),
    secondary = Color(0xFF775652), tertiary = Color(0xFF715B2E),
    background = Color(0xFFFFF8F7), surface = Color(0xFFFFF8F7),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFFF0BE6D), onPrimary = Color(0xFF432C00),
    primaryContainer = Color(0xFF604100), onPrimaryContainer = Color(0xFFFFDEA9),
    secondary = Color(0xFFDBC3A1), tertiary = Color(0xFFB5CEA4),
    background = Color(0xFF17130B), surface = Color(0xFF17130B),
)

/** Material 3 theme; Material You dynamic colour on Android 12+. */
@Composable
fun NativeAlphaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
