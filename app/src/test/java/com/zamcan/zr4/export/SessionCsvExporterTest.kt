package com.zamcan.zr4.export

import com.zamcan.zr4.engine.AnalysisRunOutcome
import com.zamcan.zr4.engine.AnalysisRunRecord
import com.zamcan.zr4.engine.ObservationDataUnit
import com.zamcan.zr4.engine.ObservationRecord
import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSession
import com.zamcan.zr4.engine.ObservationSessionSnapshot
import com.zamcan.zr4.engine.ObservationSourceType
import com.zamcan.zr4.engine.TraceableFinding
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCsvExporterTest {
    @Test
    fun exportsSessionAndObservationWithoutInventingSamples() {
        val session = ObservationSession(
            id = "session-1",
            title = "Lab, north wing",
            startedAtEpochMillis = 1_700_000_000_000
        )
        val observation = ObservationRecord(
            id = "obs-1",
            sourceType = ObservationSourceType.ANDROID_WIFI,
            capturedAtEpochMillis = 1_700_000_001_000,
            unit = ObservationDataUnit.RSSI_DBM,
            state = ObservationRecordState.VALID,
            notes = listOf("ssid=Lab, north wing")
        )
        val csv = SessionCsvExporter.export(
            ObservationSessionSnapshot(session, listOf(observation), emptyList(), emptyList())
        )

        assertTrue(csv.startsWith("record_type,session_id"))
        assertTrue(csv.contains("\"Lab, north wing\""))
        assertTrue(csv.contains("OBSERVATION,session-1"))
        assertTrue(csv.contains("ANDROID_WIFI"))
        assertTrue(csv.contains("RSSI_DBM"))
        assertTrue(!csv.contains("simulated"))
    }

    @Test
    fun exportsAnalysisAndFindingTraceability() {
        val session = ObservationSession("s2", "Field test", 1_700_000_000_000)
        val observation = ObservationRecord(
            id = "o2",
            sourceType = ObservationSourceType.EXTERNAL_SDR,
            capturedAtEpochMillis = 1_700_000_001_000,
            unit = ObservationDataUnit.DBFS,
            state = ObservationRecordState.UNCALIBRATED,
            sampleRateHz = 2_000_000.0,
            sampleCount = 1024
        )
        val run = AnalysisRunRecord(
            id = "run2",
            observationId = "o2",
            algorithmName = "adaptive-spectrum-detector",
            algorithmVersion = "1",
            startedAtEpochMillis = 1_700_000_002_000,
            completedAtEpochMillis = 1_700_000_003_000,
            outcome = AnalysisRunOutcome.SUCCESS,
            parameters = mapOf("threshold" to "-70 dBFS")
        )
        val finding = TraceableFinding(
            id = "f2",
            observationId = "o2",
            analysisRunId = "run2",
            kind = "SPECTRAL_PEAK_CANDIDATE",
            createdAtEpochMillis = 1_700_000_003_000,
            frequencyHz = 433_920_000.0,
            level = -45.0,
            unit = ObservationDataUnit.DBFS,
            evidence = listOf("local maximum"),
            limitations = listOf("not a confirmed transmitter")
        )
        val csv = SessionCsvExporter.export(
            ObservationSessionSnapshot(session, listOf(observation), listOf(run), listOf(finding))
        )

        assertTrue(csv.contains("ANALYSIS_RUN"))
        assertTrue(csv.contains("adaptive-spectrum-detector"))
        assertTrue(csv.contains("SPECTRAL_PEAK_CANDIDATE"))
        assertTrue(csv.contains("433920000"))
        assertTrue(csv.contains("not a confirmed transmitter"))
    }
}
