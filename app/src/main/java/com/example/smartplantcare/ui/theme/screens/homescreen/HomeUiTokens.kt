package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartplantcare.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared design tokens and cached helpers for all screens inside the homescreen package.
 * Keeps colors, typography, spacing, and formatting consistent without allocating per-frame objects.
 */
object HomeColors {
    val PageBg = Color(0xFFF6F9F7)
    val SurfaceWhite = Color(0xFFFFFFFF)
    val SoftMint = Color(0xFFEAF4EE)
    val SoftMintStrong = Color(0xFFD7EBE0)
    val CardBorder = Color(0xFFE3ECE6)
    val DividerLine = Color(0xFFE8EFEA)

    val ForestPrimary = Color(0xFF1B4332)
    val ForestDark = Color(0xFF0F291E)
    val ForestMedium = Color(0xFF2D6A4F)
    val EmeraldAccent = Color(0xFF10B981)
    val MintGlow = Color(0xFF34D399)

    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF4B5563)
    val TextMuted = Color(0xFF9CA3AF)

    val SuccessGreen = Color(0xFF059669)
    val SuccessBg = Color(0xFFECFDF5)

    val AlertRed = Color(0xFFDC2626)
    val AlertBg = Color(0xFFFEF2F2)

    val WarningAmber = Color(0xFFD97706)
    val WarningBg = Color(0xFFFFFBEB)

    val WaterBlue = Color(0xFF0284C7)
    val WaterBg = Color(0xFFF0F9FF)

    val PlaceholderBg = Color(0xFFEFF3F0)
    val NavInactive = Color(0xFF94A39D)
}

object HomeDimens {
    /** Unified horizontal screen gutter across all post-login screens. */
    val ScreenHorizontalPadding = 20.dp
    /** Unified top breathing room below status bar. */
    val ScreenTopPadding = 16.dp
    /** Standard spacing between major vertical sections. */
    val SectionSpacing = 18.dp
    /** Standard spacing between sibling cards. */
    val CardSpacing = 12.dp
    /** Extra bottom clearance so scrollable tabs never hide content behind FloatingBottomBar. */
    val BottomBarClearance = 116.dp
}

val HomeInterFont = FontFamily(
    Font(R.font.inter_24pt_regular, FontWeight.Normal),
    Font(R.font.inter_24pt_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.interbold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black)
)

object HomeDateFormatter {
    private val dateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val d = dateFormatter.get()?.format(date).orEmpty()
        val t = timeFormatter.get()?.format(date).orEmpty()
        return "$d • $t"
    }
}
