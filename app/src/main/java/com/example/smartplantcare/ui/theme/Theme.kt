package com.smartplantcare.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.smartplantcare.ui.theme.*


private val LightColorScheme = lightColorScheme(
    primary            = DarkGreen,
    onPrimary          = White,
    primaryContainer   = SoftGreen,
    onPrimaryContainer = DarkGreen,
    secondary          = LightGreen,
    onSecondary        = White,
    secondaryContainer = SoftGreen,
    onSecondaryContainer = MediumGreen,
    tertiary           = AccentGreen,
    onTertiary         = DarkGreen,
    background         = BackgroundWhite,
    onBackground       = TextPrimary,
    surface            = SurfaceWhite,
    onSurface          = TextPrimary,
    surfaceVariant     = SoftGreen,
    onSurfaceVariant   = TextSecondary,
    outline            = DividerGray,
    error              = ErrorRed,
    onError            = White
)

// ─── Dark Color Scheme (optional, future-ready) ───────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = AccentGreen,
    onPrimary          = DarkGreen,
    primaryContainer   = MediumGreen,
    onPrimaryContainer = SoftGreen,
    secondary          = LightGreen,
    onSecondary        = DarkGreen,
    background         = Color(0xFF0D1B13),
    onBackground       = White,
    surface            = Color(0xFF12261A),
    onSurface          = White
)

// ─── App Theme ────────────────────────────────────────────────────────────────
@Composable
fun SmartPlantCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PlantCareTypography,
        content     = content
    )
}

// Convenience: expose Color(0xFF...) inside theme package without extra import
internal fun Color(value: Long) = androidx.compose.ui.graphics.Color(value)