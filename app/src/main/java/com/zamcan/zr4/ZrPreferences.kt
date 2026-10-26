package com.zamcan.zr4

import android.content.Context

/**
 * Genuine on-device preferences only (Master Prompt §15): the appearance
 * mode and the last-used RTL-TCP receiver endpoint. Backed by private
 * SharedPreferences — no account, no sync, nothing leaves the device.
 * Every value stored here is read back by real behaviour (theme resolution
 * in the app root, SDR capture field defaults in Sessions).
 */
internal object ZrPreferences {
    private const val FILE = "zr4_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_RECEIVER_HOST = "receiver_host"
    private const val KEY_RECEIVER_PORT = "receiver_port"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadThemeMode(context: Context): ThemeMode =
        ThemeMode.fromStorage(prefs(context).getString(KEY_THEME_MODE, null))

    fun saveThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
    }

    fun loadReceiverHost(context: Context): String =
        prefs(context).getString(KEY_RECEIVER_HOST, "") ?: ""

    fun loadReceiverPort(context: Context): String =
        prefs(context).getString(KEY_RECEIVER_PORT, "1234") ?: "1234"

    fun saveReceiverEndpoint(context: Context, host: String, port: String) {
        prefs(context).edit()
            .putString(KEY_RECEIVER_HOST, host)
            .putString(KEY_RECEIVER_PORT, port)
            .apply()
    }
}
