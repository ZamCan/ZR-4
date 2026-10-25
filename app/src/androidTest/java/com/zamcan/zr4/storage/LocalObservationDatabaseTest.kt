package com.zamcan.zr4.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.zamcan.zr4.engine.AnalysisRunOutcome
import com.zamcan.zr4.engine.AnalysisRunRecord
import com.zamcan.zr4.engine.ObservationDataUnit
import com.zamcan.zr4.engine.ObservationRecord
import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSession
import com.zamcan.zr4.engine.ObservationSessionState
import com.zamcan.zr4.engine.ProvenancedAnalysis
import com.zamcan.zr4.engine.ObservationSourceType
import com.zamcan.zr4.engine.TraceableFinding

/** Runs on an Android device/emulator because SQLiteOpenHelper uses the Android framework. */
@RunWith(AndroidJUnit4::class)
class LocalObservationDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: LocalObservationDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(LocalObservationDatabase.DATABASE_NAME)
        store = LocalObservationDatabase(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalObservationDatabase.DATABASE_NAME)
    }

    @Test
    fun testSessionAndTraceableRecordsSurviveDatabaseReopen() {
        store.createSession(session())
        store.addObservation("session-1", observation())
        store.addAnalysisRun("session-1", run())
        store.addFinding("session-1", finding())

        store.close()
        store = LocalObservationDatabase(context)
        val snapshot = store.loadSession("session-1")

        assertNotNull(snapshot)
        assertEquals(ObservationSessionState.OPEN, snapshot!!.session.state)
        assertEquals(mapOf("window" to "HANN", "note" to "comma, colon: and Unicode λ"), snapshot.observations.single().acquisitionConfig)
        assertEquals(listOf("first.note", "second:value", "Unicode λ"), snapshot.observations.single().notes)
        assertEquals(AnalysisRunOutcome.SUCCESS, snapshot.analysisRuns.single().outcome)
        assertEquals(listOf("local maximum", "threshold > baseline"), snapshot.findings.single().evidence)
        assertEquals("run-1", snapshot.findings.single().analysisRunId)
    }

    @Test
    fun testAndroidBluetoothObservationMetadataSurvivesDatabaseReopen() {
        store.createSession(session())
        val radioObservation = ObservationRecord(
            id = "ble-snapshot-1",
            sourceType = ObservationSourceType.ANDROID_BLUETOOTH,
            capturedAtEpochMillis = 1_800_000_001_500L,
            unit = ObservationDataUnit.RSSI_DBM,
            state = ObservationRecordState.VALID,
            sampleCount = 7,
            hardwareDescription = "Android handset radio API",
            acquisitionConfig = mapOf(
                "scan_window_ms" to "5000",
                "scan_complete" to "true",
                "strongest_rssi_dbm" to "-61"
            ),
            notes = listOf(
                "BLE scan-result callbacks, not unique devices.",
                "Device addresses, names, and advertisement payloads are not retained."
            )
        )
        store.addObservation("session-1", radioObservation)
        store.close()
        store = LocalObservationDatabase(context)

        val saved = store.loadSession("session-1")!!.observations.single()
        assertEquals(ObservationSourceType.ANDROID_BLUETOOTH, saved.sourceType)
        assertEquals(7, saved.sampleCount)
        assertEquals(ObservationDataUnit.RSSI_DBM, saved.unit)
        assertEquals("-61", saved.acquisitionConfig["strongest_rssi_dbm"])
        assertTrue(saved.notes.any { it.contains("not unique devices") })
        assertTrue(saved.notes.none { it.contains("address=") || it.contains("device_name=") })
    }

    @Test
    fun testRejectsFindingLinkedToDifferentObservation() {
        store.createSession(session())
        store.addObservation("session-1", observation())
        store.addObservation("session-1", observation("obs-2"))
        store.addAnalysisRun("session-1", run())

        try {
            store.addFinding("session-1", finding(observationId = "obs-2"))
            fail("Mismatched finding provenance should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("must match"))
        }
    }

    @Test
    fun testClosedSessionRejectsFurtherWritesAndCanBeLoaded() {
        store.createSession(session())
        val closed = store.closeSession("session-1", 1_800_000_003_000L)
        assertEquals(ObservationSessionState.CLOSED, closed.session.state)
        try {
            store.addObservation("session-1", observation())
            fail("Closed session must reject writes")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("closed"))
        }
        assertEquals(ObservationSessionState.CLOSED, store.loadSession("session-1")!!.session.state)
    }

    @Test
    fun testCompletedAnalysisAndFindingsAreSavedTogether() {
        store.createSession(session())
        store.addObservation("session-1", observation())
        store.saveAnalysis("session-1", ProvenancedAnalysis(run(), listOf(finding())))

        val snapshot = store.loadSession("session-1")!!
        assertEquals(AnalysisRunOutcome.SUCCESS, snapshot.analysisRuns.single().outcome)
        assertEquals("run-1", snapshot.findings.single().analysisRunId)
        assertEquals("obs-1", snapshot.findings.single().observationId)
    }

    @Test
    fun testAnalysisSaveRollsBackRunAndFirstFindingWhenLaterFindingFails() {
        store.createSession(session())
        store.addObservation("session-1", observation())

        try {
            store.saveAnalysis(
                "session-1",
                ProvenancedAnalysis(run(), listOf(finding(), finding()))
            )
            fail("Duplicate finding id should roll back the full analysis transaction")
        } catch (expected: RuntimeException) {
            // SQLite rejects the duplicate primary key; transaction must roll back.
        }

        val snapshot = store.loadSession("session-1")!!
        assertTrue(snapshot.analysisRuns.isEmpty())
        assertTrue(snapshot.findings.isEmpty())
    }

    @Test
    fun testUnsuccessfulAnalysisCannotCarryFindings() {
        store.createSession(session())
        store.addObservation("session-1", observation())
        try {
            store.saveAnalysis(
                "session-1",
                ProvenancedAnalysis(run().copy(outcome = AnalysisRunOutcome.FAILED), listOf(finding()))
            )
            fail("Failed analysis must not carry findings")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Only successful"))
        }
        val snapshot = store.loadSession("session-1")!!
        assertTrue(snapshot.analysisRuns.isEmpty())
        assertTrue(snapshot.findings.isEmpty())
    }

    @Test
    fun testMissingSessionLoadsAsNull() {
        assertNull(store.loadSession("does-not-exist"))
    }

    private fun session() = ObservationSession(
        id = "session-1",
        title = "Storage verification",
        startedAtEpochMillis = 1_800_000_000_000L
    )

    private fun observation(id: String = "obs-1") = ObservationRecord(
        id = id,
        sourceType = ObservationSourceType.EXTERNAL_SDR,
        capturedAtEpochMillis = 1_800_000_001_000L,
        unit = ObservationDataUnit.DBFS,
        state = ObservationRecordState.UNCALIBRATED,
        frequencyStartHz = 100_000_000.0,
        frequencyEndHz = 101_000_000.0,
        centerFrequencyHz = 100_500_000.0,
        sampleRateHz = 2_000_000.0,
        sampleCount = 4096,
        hardwareDescription = "Test receiver",
        acquisitionConfig = mapOf("window" to "HANN", "note" to "comma, colon: and Unicode λ"),
        notes = listOf("first.note", "second:value", "Unicode λ")
    )

    private fun run() = AnalysisRunRecord(
        id = "run-1",
        observationId = "obs-1",
        algorithmName = "adaptive-spectrum-detector",
        algorithmVersion = "1.0",
        startedAtEpochMillis = 1_800_000_002_000L,
        completedAtEpochMillis = 1_800_000_002_100L,
        outcome = AnalysisRunOutcome.SUCCESS,
        parameters = mapOf("minimumPeakExcessDb" to "8.0")
    )

    private fun finding(observationId: String = "obs-1") = TraceableFinding(
        id = "finding-1",
        observationId = observationId,
        analysisRunId = "run-1",
        kind = "SPECTRAL_PEAK_CANDIDATE",
        createdAtEpochMillis = 1_800_000_002_100L,
        frequencyHz = 100_125_000.0,
        level = -32.5,
        unit = ObservationDataUnit.DBFS,
        evidence = listOf("local maximum", "threshold > baseline"),
        limitations = listOf("Not a confirmed transmitter")
    )
}
