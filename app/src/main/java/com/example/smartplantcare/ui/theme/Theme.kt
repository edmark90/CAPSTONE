package com.example.smartplantcare.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val SmartPlantColorScheme = lightColorScheme(
    primary          = PrimaryGreen,
    secondary        = AccentGreen,
    tertiary         = DarkGreen,
    background       = White,
    surface          = SurfaceColor,
    onPrimary        = White,
    onBackground     = TextDark,
    onSurface        = TextDark,
    outline          = BorderColor
)

private val SmartPlantTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 30.sp,
        lineHeight   = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 26.sp,
        lineHeight   = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 20.sp,
        lineHeight   = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 13.sp
    )
)

@Composable
fun SmartPlantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartPlantColorScheme,
        typography  = SmartPlantTypography,
        content     = content
    )
}
