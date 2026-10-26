package com.zamcan.zr4

import androidx.compose.ui.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Guards the appearance system: storage round-trips, palette resolution, and
 * the WCAG AA text-contrast floor both palettes were designed against.
 */
class ZrThemeTest {

    @After
    fun restorePalette() {
        activeZrPalette = ZrDarkPalette
    }

    @Test
    fun themeModeStorageRoundTrips() {
        ThemeMode.values().forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun unknownOrMissingStoredModeFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("sepia"))
    }

    @Test
    fun explicitModesIgnoreSystemFlag() {
        assertSame(ZrLightPalette, resolveZrPalette(ThemeMode.Light, systemDark = true))
        assertSame(ZrLightPalette, resolveZrPalette(ThemeMode.Light, systemDark = false))
        assertSame(ZrDarkPalette, resolveZrPalette(ThemeMode.Dark, systemDark = true))
        assertSame(ZrDarkPalette, resolveZrPalette(ThemeMode.Dark, systemDark = false))
    }

    @Test
    fun systemModeFollowsDeviceFlag() {
        assertSame(ZrDarkPalette, resolveZrPalette(ThemeMode.System, systemDark = true))
        assertSame(ZrLightPalette, resolveZrPalette(ThemeMode.System, systemDark = false))
    }

    @Test
    fun tokenGettersFollowActivePalette() {
        activeZrPalette = ZrLightPalette
        assertEquals(ZrLightPalette.gold, Gold)
        assertEquals(ZrLightPalette.paper, Paper)
        activeZrPalette = ZrDarkPalette
        assertEquals(ZrDarkPalette.gold, Gold)
        assertEquals(ZrDarkPalette.paper, Paper)
    }

    @Test
    fun bothPalettesMeetTextContrastFloor() {
        listOf(ZrDarkPalette, ZrLightPalette).forEach { palette ->
            // Ink is the primary canvas colour in both palettes (near-black in
            // dark, warm paper in light); text tokens sit on it directly.
            assertTrue("paper on ink (${palette.dark})", contrast(palette.paper, palette.ink) >= 4.5)
            assertTrue("muted on ink (${palette.dark})", contrast(palette.muted, palette.ink) >= 4.5)
            assertTrue("gold on ink (${palette.dark})", contrast(palette.gold, palette.ink) >= 4.5)
            assertTrue("mint on ink (${palette.dark})", contrast(palette.mint, palette.ink) >= 4.5)
            assertTrue("amber on ink (${palette.dark})", contrast(palette.amber, palette.ink) >= 4.5)
            // Gold button label (Ink) on the gold gradient's mid tone.
            assertTrue("ink on gold (${palette.dark})", contrast(palette.ink, palette.gold) >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double =
            if (channel <= 0.03928f) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
