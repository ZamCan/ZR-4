package com.zamcan.zr4.radio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.zamcan.zr4.engine.ObservationDataUnit
import com.zamcan.zr4.engine.ObservationRecord
import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSourceType
import java.util.UUID

/**
 * Reads Android-provided Wi-Fi scan metadata without persisting SSIDs/BSSIDs.
 * This is a snapshot of discrete scan results, not raw IQ, continuous spectrum,
 * calibrated receiver power, or a device-identification service.
 */
class AndroidRadioObservationCollector(private val context: Context) {
    data class WifiSummary(
        val observation: ObservationRecord,
        val networkCount: Int,
        val strongestRssiDbm: Int?,
        val frequenciesMhz: List<Int>,
        val scanResultsFresh: Boolean,
        val statusMessage: String
    )

    fun collectWifiSnapshot(nowEpochMillis: Long = System.currentTimeMillis()): WifiSummary {
        val missing = missingWifiPermission()
        if (missing != null) return WifiSummary(
            observation(ObservationSourceType.ANDROID_WIFI, nowEpochMillis, ObservationDataUnit.COUNT,
                ObservationRecordState.INCOMPLETE, 0, listOf("Missing permission: " + missing)),
            0, null, emptyList(), false, "Permission required: " + missing
        )

        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return WifiSummary(
                observation(ObservationSourceType.ANDROID_WIFI, nowEpochMillis, ObservationDataUnit.COUNT,
                    ObservationRecordState.INCOMPLETE, 0, listOf("Wi-Fi service unavailable")),
                0, null, emptyList(), false, "Wi-Fi service unavailable on this device."
            )

        return try {
            val results = manager.scanResults.orEmpty()
            val scanTimestampMicros = results.maxOfOrNull { it.timestamp } ?: 0L
            val ageMillis = if (scanTimestampMicros > 0L) {
                ((SystemClock.elapsedRealtimeNanos() / 1_000L - scanTimestampMicros).coerceAtLeast(0L) / 1_000L)
            } else Long.MAX_VALUE
            val fresh = ageMillis <= MAX_WIFI_RESULT_AGE_MILLIS
            val frequencies = results.map { it.frequency }.filter { it > 0 }.distinct().sorted()
            val strongest = results.maxOfOrNull { it.level }
            val notes = listOf(
                "Android Wi-Fi scan-result metadata; not continuous RF spectrum.",
                if (fresh) "Results are recent." else "Results may be stale or have no usable timestamp.",
                "RSSI is handset-reported and is not calibrated receiver spectrum power.",
                "Network identifiers are not persisted."
            )
            WifiSummary(
                observation = observation(
                    ObservationSourceType.ANDROID_WIFI, nowEpochMillis,
                    if (strongest == null) ObservationDataUnit.COUNT else ObservationDataUnit.RSSI_DBM,
                    if (results.isEmpty()) ObservationRecordState.INCOMPLETE
                    else if (fresh) ObservationRecordState.VALID else ObservationRecordState.STALE,
                    results.size, notes,
                    mapOf("result_age_ms" to ageMillis.toString(), "fresh" to fresh.toString(),
                        "frequency_count" to frequencies.size.toString())
                ),
                networkCount = results.size,
                strongestRssiDbm = strongest,
                frequenciesMhz = frequencies,
                scanResultsFresh = fresh,
                statusMessage = when {
                    !manager.isWifiEnabled -> "Wi-Fi is disabled. No scan was performed."
                    results.isEmpty() -> "No scan results available. Android may require location services, permission, and a successful scan."
                    !fresh -> "Existing scan results are stale; treat them as historical metadata."
                    else -> "Recent Wi-Fi scan results read successfully."
                }
            )
        } catch (_: SecurityException) {
            WifiSummary(
                observation(ObservationSourceType.ANDROID_WIFI, nowEpochMillis, ObservationDataUnit.COUNT,
                    ObservationRecordState.INCOMPLETE, 0, listOf("Android denied access to Wi-Fi scan results")),
                0, null, emptyList(), false, "Android denied Wi-Fi scan access. Check permissions and location services."
            )
        }
    }

    private fun missingWifiPermission(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            return Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return null
    }

    private fun observation(
        source: ObservationSourceType,
        timestamp: Long,
        unit: ObservationDataUnit,
        state: ObservationRecordState,
        count: Int,
        notes: List<String>,
        config: Map<String, String> = emptyMap()
    ) = ObservationRecord(
        id = UUID.randomUUID().toString(),
        sourceType = source,
        capturedAtEpochMillis = timestamp,
        unit = unit,
        state = state,
        sampleCount = count,
        hardwareDescription = "Android handset radio API",
        acquisitionConfig = config,
        notes = notes
    )

    companion object {
        const val MAX_WIFI_RESULT_AGE_MILLIS = 120_000L
    }
}
