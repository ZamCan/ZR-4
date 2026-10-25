package com.zamcan.zr4.radio

import android.Manifest
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
                strongest?.let { "Strongest observed RSSI: " + it + " dBm." } ?: "No RSSI value available.",
                if (frequencies.isEmpty()) "No Wi-Fi frequencies reported." else "Observed frequencies in MHz: " + frequencies.joinToString(", ") + ".",
                "Network identifiers are not persisted."
            )
            WifiSummary(
                observation = observation(
                    ObservationSourceType.ANDROID_WIFI, nowEpochMillis,
                    if (strongest == null) ObservationDataUnit.COUNT else ObservationDataUnit.RSSI_DBM,
                    if (results.isEmpty()) ObservationRecordState.INCOMPLETE
                    else if (fresh) ObservationRecordState.VALID else ObservationRecordState.STALE,
                    results.size, notes,
                    mapOf(
                        "result_age_ms" to ageMillis.toString(),
                        "fresh" to fresh.toString(),
                        "frequency_count" to frequencies.size.toString(),
                        "strongest_rssi_dbm" to (strongest?.toString() ?: ""),
                        "frequencies_mhz" to frequencies.joinToString(",")
                    )
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

    data class BluetoothSummary(
        val observation: ObservationRecord,
        val advertisementResultCount: Int,
        val strongestRssiDbm: Int?,
        val statusMessage: String
    )

    /** Bounded BLE scan; counts callbacks, not unique devices, and retains no identifiers/payloads. */
    fun collectBluetoothLe(
        timeoutMillis: Long = DEFAULT_BLE_SCAN_MILLIS,
        onComplete: (BluetoothSummary) -> Unit
    ): () -> Unit {
        require(timeoutMillis in 1_000L..15_000L) { "BLE scan window must be between 1 and 15 seconds." }
        val startedAt = System.currentTimeMillis()
        val missing = missingBluetoothPermission()
        if (missing != null) {
            onComplete(BluetoothSummary(
                observation(ObservationSourceType.ANDROID_BLUETOOTH, startedAt, ObservationDataUnit.COUNT,
                    ObservationRecordState.INCOMPLETE, 0, listOf("Missing permission: " + missing)),
                0, null, "Permission required: " + missing
            ))
            return {}
        }
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = try { manager?.adapter } catch (_: SecurityException) { null }
        val bluetoothEnabled = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }
        val scanner = try { adapter?.bluetoothLeScanner } catch (_: SecurityException) { null }
        if (adapter == null || !bluetoothEnabled || scanner == null) {
            onComplete(BluetoothSummary(
                observation(ObservationSourceType.ANDROID_BLUETOOTH, startedAt, ObservationDataUnit.COUNT,
                    ObservationRecordState.INCOMPLETE, 0, listOf("Bluetooth LE scanner unavailable")),
                0, null, "Bluetooth LE scanning is unavailable or Bluetooth is disabled."
            ))
            return {}
        }
        val count = java.util.concurrent.atomic.AtomicInteger(0)
        val strongest = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
        val errorCode = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var callback: android.bluetooth.le.ScanCallback
        fun finish(cancelled: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            try { scanner.stopScan(callback) } catch (_: SecurityException) { errorCode.compareAndSet(Int.MIN_VALUE, -1) }
            handler.removeCallbacksAndMessages(callback)
            val resultCount = count.get()
            val peak = strongest.get().takeIf { it != Int.MIN_VALUE }
            val error = errorCode.get()
            val success = !cancelled && error == Int.MIN_VALUE
            val notes = listOf(
                "Android Bluetooth Low Energy scan-result metadata; not continuous RF spectrum or raw IQ.",
                "Result count counts scan callbacks, not unique devices.",
                "Device addresses, advertised names, and payloads are not retained.",
                "RSSI is handset-reported and not calibrated receiver power.",
                if (success) "Scan window: " + timeoutMillis + " ms." else "Scan incomplete or failed; error code: " + error + "."
            )
            val record = observation(
                ObservationSourceType.ANDROID_BLUETOOTH, System.currentTimeMillis(),
                if (peak == null) ObservationDataUnit.COUNT else ObservationDataUnit.RSSI_DBM,
                if (!success || resultCount == 0) ObservationRecordState.INCOMPLETE else ObservationRecordState.VALID,
                resultCount, notes,
                mapOf("scan_window_ms" to timeoutMillis.toString(), "scan_complete" to success.toString(),
                    "strongest_rssi_dbm" to (peak?.toString() ?: ""),
                    "error_code" to if (error == Int.MIN_VALUE) "" else error.toString())
            )
            onComplete(BluetoothSummary(record, resultCount, peak, when {
                cancelled -> "Bluetooth scan cancelled."
                !success -> "Bluetooth scan failed or was interrupted (code " + error + ")."
                resultCount == 0 -> "BLE scan window completed with no reported scan results."
                else -> "BLE scan captured " + resultCount + " result callback(s) over " + (timeoutMillis / 1_000L) + " seconds."
            }))
        }
        callback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                count.incrementAndGet()
                strongest.updateAndGet { previous -> maxOf(previous, result.rssi) }
            }
            override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
                results.forEach {
                    count.incrementAndGet()
                    strongest.updateAndGet { previous -> maxOf(previous, it.rssi) }
                }
            }
            override fun onScanFailed(code: Int) { errorCode.set(code); finish(cancelled = false) }
        }
        try {
            scanner.startScan(callback)
            handler.postAtTime({ finish(cancelled = false) }, callback, android.os.SystemClock.uptimeMillis() + timeoutMillis)
        } catch (_: SecurityException) {
            errorCode.set(-1); finish(cancelled = false)
        } catch (_: IllegalStateException) {
            errorCode.set(-2); finish(cancelled = false)
        }
        return { finish(cancelled = true) }
    }

    private fun missingBluetoothPermission(): String? {
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return Manifest.permission.BLUETOOTH_CONNECT
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Manifest.permission.ACCESS_FINE_LOCATION
        }
        return null
    }

    companion object {
        const val DEFAULT_BLE_SCAN_MILLIS = 5_000L
        const val MAX_WIFI_RESULT_AGE_MILLIS = 120_000L
    }
}
