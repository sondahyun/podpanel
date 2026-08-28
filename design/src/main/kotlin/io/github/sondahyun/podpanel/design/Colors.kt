package io.github.sondahyun.podpanel.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The iOS system palette.
 *
 * Two things here decide whether the result reads as Apple or as Android, and neither is
 * obvious. First, the greys are not neutral — they carry a blue bias (`#F2F2F7`, and labels
 * built on `60,60,67`), and a neutral grey immediately reads as Material. Second, most of
 * the palette is *translucent*: separators, fills and secondary labels are alpha over
 * whatever sits behind them, which is why they hold up on both the grouped background and
 * on a card.
 */
@Immutable
data class PodColors(
    /** The page. iOS calls it systemGroupedBackground. */
    val background: Color,
    /** Cards and list groups sitting on [background]. */
    val card: Color,
    /** A surface nested inside a card — a segmented control's selected pill. */
    val cardRaised: Color,
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    /** Hairline between rows. Drawn at 0.5dp, inset from the leading edge. */
    val separator: Color,
    /** Track behind a segmented control or an unfilled battery ring. */
    val fill: Color,
    val fillStrong: Color,
    val accent: Color,
    val positive: Color,
    val warning: Color,
    val critical: Color,
    val isDark: Boolean,
) {
    /** Battery colour follows charge, the way iOS does it. */
    fun batteryTint(percent: Int, charging: Boolean): Color = when {
        charging -> positive
        percent <= 10 -> critical
        percent <= 20 -> warning
        else -> positive
    }
}

val LightPodColors = PodColors(
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43),
    labelTertiary = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F787880),
    fillStrong = Color(0x33787880),
    accent = Color(0xFF007AFF),
    positive = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    critical = Color(0xFFFF3B30),
    isDark = false,
)

val DarkPodColors = PodColors(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardRaised = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5),
    labelTertiary = Color(0x52EBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x3D787880),
    fillStrong = Color(0x5C787880),
    accent = Color(0xFF0A84FF),
    positive = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    critical = Color(0xFFFF453A),
    isDark = true,
)
