package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationSessionLedgerTest {
    private fun session() = ObservationSession(
        id = "session-1",
        title = "Field observation",
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
        acquisitionConfig = mapOf("window" to "HANN")
    )

    private fun successfulRun(
        id: String = "run-1",
        observationId: String = "obs-1"
    ) = AnalysisRunRecord(
        id = id,
        observationId = observationId,
        algorithmName = "adaptive-spectrum-detector",
        algorithmVersion = "1.0",
        startedAtEpochMillis = 1_800_000_002_000L,
        completedAtEpochMillis = 1_800_000_002_100L,
        outcome = AnalysisRunOutcome.SUCCESS,
        parameters = mapOf("minimumPeakExcessDb" to "8.0")
    )

    private fun finding(
        id: String = "finding-1",
        observationId: String = "obs-1",
        analysisRunId: String = "run-1"
    ) = TraceableFinding(
        id = id,
        observationId = observationId,
        analysisRunId = analysisRunId,
        kind = "SPECTRAL_PEAK_CANDIDATE",
        createdAtEpochMillis = 1_800_000_002_100L,
        frequencyHz = 100_125_000.0,
        level = -32.5,
        unit = ObservationDataUnit.DBFS,
        evidence = listOf("Local maximum above threshold"),
        limitations = listOf("Not a confirmed transmitter")
    )

    @Test
    fun storesObservationRunAndFindingWithTraceableLinks() {
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(observation())
        ledger.addAnalysisRun(successfulRun())
        ledger.addFinding(finding())

        val snapshot = ledger.snapshot()
        assertEquals(1, snapshot.observations.size)
        assertEquals(1, snapshot.analysisRuns.size)
        assertEquals(1, snapshot.findings.size)
        assertEquals("obs-1", snapshot.findings.single().observationId)
        assertEquals("run-1", snapshot.findings.single().analysisRunId)
        assertEquals("obs-1", snapshot.analysisRuns.single().observationId)
    }

    @Test
    fun rejectsAnalysisRunForObservationOutsideSession() {
        val ledger = ObservationSessionLedger(session())
        try {
            ledger.addAnalysisRun(successfulRun())
            throw AssertionError("Run with missing observation should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("observation"))
        }
    }

    @Test
    fun rejectsFindingForMissingAnalysisRun() {
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(observation())
        try {
            ledger.addFinding(finding())
            throw AssertionError("Finding with missing run should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("analysis run"))
        }
    }

    @Test
    fun rejectsFindingWhoseObservationDiffersFromRun() {
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(observation())
        ledger.addObservation(observation("obs-2"))
        ledger.addAnalysisRun(successfulRun())
        try {
            ledger.addFinding(finding(observationId = "obs-2"))
            throw AssertionError("Mismatched provenance should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("must match"))
        }
    }

    @Test
    fun rejectsDuplicateObservationIds() {
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(observation())
        try {
            ledger.addObservation(observation())
            throw AssertionError("Duplicate observation should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Duplicate"))
        }
    }

    @Test
    fun rejectsInvalidFrequencyRange() {
        try {
            observation().copy(frequencyStartHz = 101_000_000.0, frequencyEndHz = 100_000_000.0)
            throw AssertionError("Reversed frequency range should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("must not exceed"))
        }
    }

    @Test
    fun rejectsNonFiniteSampleRate() {
        try {
            observation().copy(sampleRateHz = Double.NaN)
            throw AssertionError("NaN sample rate should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Sample rate"))
        }
    }

    @Test
    fun closesSessionAndPreservesSnapshot() {
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(observation())
        val snapshot = ledger.close(1_800_000_003_000L)

        assertEquals(ObservationSessionState.CLOSED, snapshot.session.state)
        assertEquals(1_800_000_003_000L, snapshot.session.closedAtEpochMillis)
        assertEquals(1, snapshot.observations.size)
    }

    @Test
    fun closedSessionCannotBeModified() {
        val ledger = ObservationSessionLedger(session())
        ledger.close(1_800_000_003_000L)
        try {
            ledger.addObservation(observation())
            throw AssertionError("Closed session must be immutable through ledger")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("closed"))
        }
    }

    @Test
    fun snapshotDoesNotExposeMutableInputCollections() {
        val config = mutableMapOf("window" to "HANN")
        val notes = mutableListOf("captured locally")
        val record = observation().copy(acquisitionConfig = config, notes = notes)
        val ledger = ObservationSessionLedger(session())
        ledger.addObservation(record)

        config["window"] = "CHANGED"
        notes += "changed after insertion"

        val saved = ledger.snapshot().observations.single()
        assertEquals("HANN", saved.acquisitionConfig["window"])
        assertFalse(saved.notes.contains("changed after insertion"))
    }
}
