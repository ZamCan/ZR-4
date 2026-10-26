package com.zamcan.zr4

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Centralized ZR 4 colour system (Master Prompt §8).
 *
 * One token vocabulary, two complete palettes. Components keep using the
 * familiar token names (`Gold`, `Paper`, `Muted`, …); each token is a getter
 * over [activeZrPalette], a snapshot state, so switching palettes recomposes
 * every reader — including non-composable helpers and Canvas draw code that
 * read tokens while drawing. The dark palette is byte-identical to the
 * original Observatory Glass values; the light palette is a warm-paper
 * instrument theme whose text-capable accents (gold, mint, amber, stone,
 * muted) are deepened to keep >= ~4.5:1 contrast on the paper canvas.
 *
 * The spectrum plot itself stays a dark instrument screen in BOTH palettes
 * (its grid token, [PlotLine], is deliberately theme-independent) so trace
 * legibility never depends on the surrounding theme.
 */
internal enum class ThemeMode(val storageValue: String, val label: String, val detail: String) {
    System("system", "Follow system", "Use this device's light/dark setting"),
    Light("light", "Light", "Warm paper instrument theme"),
    Dark("dark", "Dark", "Observatory night theme"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            values().firstOrNull { it.storageValue == value } ?: System
    }
}

internal data class ZrPalette(
    val dark: Boolean,
    val ink: Color,
    val inkDeep: Color,
    val emerald: Color,
    val inkLow: Color,
    val gold: Color,
    val goldLight: Color,
    val goldDeep: Color,
    val amber: Color,
    val mint: Color,
    val stone: Color,
    val paper: Color,
    val muted: Color,
    val glassLine: Color,
    val glassMid: Color,
    val glassLow: Color,
    val borderHi: Color,
    val borderMid: Color,
)

/** Original Observatory Glass night palette — unchanged values. */
internal val ZrDarkPalette = ZrPalette(
    dark = true,
    ink = Color(0xFF090D0A),
    inkDeep = Color(0xFF03070B),
    emerald = Color(0xFF151B16),
    inkLow = Color(0xFF07130D),
    gold = Color(0xFFD6B56D),
    goldLight = Color(0xFFF0D493),
    goldDeep = Color(0xFFB98F3E),
    amber = Color(0xFFFFB86B),
    mint = Color(0xFF6EE7B7),
    stone = Color(0xFFC9BBA6),
    paper = Color(0xFFF2F5EC),
    muted = Color(0xFFAFBDB0),
    glassLine = Color.White.copy(alpha = 0.16f),
    glassMid = Color.White.copy(alpha = 0.07f),
    glassLow = Color.Black.copy(alpha = 0.18f),
    borderHi = Color.White.copy(alpha = 0.42f),
    borderMid = Color.White.copy(alpha = 0.08f),
)

/** Warm-paper day palette: same roles, light canvas, deepened text accents. */
internal val ZrLightPalette = ZrPalette(
    dark = false,
    ink = Color(0xFFF6F2E6),
    inkDeep = Color(0xFFE7E1CF),
    emerald = Color(0xFFE9EDDF),
    inkLow = Color(0xFFFCF9F0),
    gold = Color(0xFF86651B),
    goldLight = Color(0xFFDDB95E),
    goldDeep = Color(0xFF6B5010),
    amber = Color(0xFF99590C),
    mint = Color(0xFF0B7A54),
    stone = Color(0xFF68573F),
    paper = Color(0xFF1A241C),
    muted = Color(0xFF52635A),
    glassLine = Color.Black.copy(alpha = 0.20f),
    glassMid = Color.White.copy(alpha = 0.62f),
    glassLow = Color(0xFFD8CFB6).copy(alpha = 0.55f),
    borderHi = Color.White.copy(alpha = 0.90f),
    borderMid = Color.Black.copy(alpha = 0.10f),
)

internal fun resolveZrPalette(mode: ThemeMode, systemDark: Boolean): ZrPalette = when (mode) {
    ThemeMode.Light -> ZrLightPalette
    ThemeMode.Dark -> ZrDarkPalette
    ThemeMode.System -> if (systemDark) ZrDarkPalette else ZrLightPalette
}

/**
 * The palette token getters read from. Written by the app root whenever the
 * resolved palette changes (mode switch or system flip); snapshot state makes
 * every composable and draw reader update. Defaults to dark so the first
 * frame matches the launch window background before preferences load.
 */
internal var activeZrPalette: ZrPalette by mutableStateOf(ZrDarkPalette)

internal val Ink: Color get() = activeZrPalette.ink
internal val InkDeep: Color get() = activeZrPalette.inkDeep
internal val Emerald: Color get() = activeZrPalette.emerald
internal val InkLow: Color get() = activeZrPalette.inkLow
internal val Gold: Color get() = activeZrPalette.gold
internal val GoldLight: Color get() = activeZrPalette.goldLight
internal val GoldDeep: Color get() = activeZrPalette.goldDeep
internal val Amber: Color get() = activeZrPalette.amber
internal val Mint: Color get() = activeZrPalette.mint
internal val Stone: Color get() = activeZrPalette.stone
internal val Paper: Color get() = activeZrPalette.paper
internal val Muted: Color get() = activeZrPalette.muted
internal val GlassLine: Color get() = activeZrPalette.glassLine
internal val GlassMid: Color get() = activeZrPalette.glassMid
internal val GlassLow: Color get() = activeZrPalette.glassLow
internal val BorderHi: Color get() = activeZrPalette.borderHi
internal val BorderMid: Color get() = activeZrPalette.borderMid

/** Spectrum-plot grid: the plot screen is dark in both themes. */
internal val PlotLine: Color = Color.White.copy(alpha = 0.16f)
