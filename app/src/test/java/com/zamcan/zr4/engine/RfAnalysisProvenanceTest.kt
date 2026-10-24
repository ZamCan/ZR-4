package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfAnalysisProvenanceTest {
    @Test
    fun spectrumResultBecomesTraceableRunAndFindings() {
        val detectorFinding = SignalFinding(
            id = "spectrum-bin-7",
            kind = "SPECTRAL_PEAK_CANDIDATE",
            startOffsetSeconds = null,
            durationSeconds = null,
            frequencyHz = 101_000_000.0,
            levelDbfs = -28.0,
            baselineDbfs = -80.0,
            thresholdDbfs = -72.0,
            prominenceDb = 52.0,
            evidence = listOf("Peak above threshold"),
            limitations = listOf("Not a confirmed transmitter")
        )
        val result = RfAnalysisProvenance.fromSpectrumResult(
            observationId = "obs-7",
            runId = "run-7",
            algorithmVersion = "zr4-detection-1",
            startedAtEpochMillis = 1_800_000_000_000L,
            completedAtEpochMillis = 1_800_000_000_100L,
            result = AdaptiveSpectrumResult(
                baselineDbfs = -80.0,
                robustSpreadDb = 1.2,
                thresholdDbfs = -72.0,
                findings = listOf(detectorFinding),
                inputBinCount = 129
            ),
            parameters = mapOf("minimumPeakExcessDb" to "8.0")
        )

        assertEquals(AnalysisRunOutcome.SUCCESS, result.run.outcome)
        assertEquals("adaptive-spectrum-detector", result.run.algorithmName)
        assertEquals("129", result.run.parameters["inputBinCount"])
        assertEquals(1, result.findings.size)
        assertEquals("obs-7", result.findings.single().observationId)
        assertEquals("run-7", result.findings.single().analysisRunId)
        assertEquals(ObservationDataUnit.DBFS, result.findings.single().unit)
        assertTrue(result.findings.single().limitations.any { it.contains("Not a confirmed transmitter") })
    }

    @Test
    fun emptyResultIsRecordedAsNoFindingsNotAsFailure() {
        val result = RfAnalysisProvenance.fromSpectrumResult(
            observationId = "obs-8",
            runId = "run-8",
            algorithmVersion = "zr4-detection-1",
            startedAtEpochMillis = 1_800_000_000_000L,
            completedAtEpochMillis = 1_800_000_000_100L,
            result = AdaptiveSpectrumResult(
                baselineDbfs = -78.0,
                robustSpreadDb = 0.0,
                thresholdDbfs = -70.0,
                findings = emptyList(),
                inputBinCount = 129
            )
        )

        assertEquals(AnalysisRunOutcome.NO_FINDINGS, result.run.outcome)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun burstResultRetainsTimeOffsetsAndDetectorParameters() {
        val detectorFinding = SignalFinding(
            id = "burst-2",
            kind = "ENERGY_BURST_CANDIDATE",
            startOffsetSeconds = 0.25,
            durationSeconds = 0.125,
            frequencyHz = null,
            levelDbfs = -18.0,
            baselineDbfs = -90.0,
            thresholdDbfs = -80.0,
            prominenceDb = 72.0,
            evidence = listOf("Block RMS exceeded threshold"),
            limitations = listOf("Frequency and modulation not identified")
        )
        val result = RfAnalysisProvenance.fromBurstResult(
            observationId = "obs-9",
            runId = "run-9",
            algorithmVersion = "zr4-burst-1",
            startedAtEpochMillis = 1_800_000_000_000L,
            completedAtEpochMillis = 1_800_000_000_100L,
            result = BurstDetectionResult(
                baselineRmsDbfs = -90.0,
                thresholdRmsDbfs = -80.0,
                findings = listOf(detectorFinding),
                blockCount = 16
            ),
            parameters = mapOf("blockSize" to "64")
        )

        assertEquals(AnalysisRunOutcome.SUCCESS, result.run.outcome)
        assertEquals("16", result.run.parameters["blockCount"])
        assertEquals("ENERGY_BURST_CANDIDATE", result.findings.single().kind)
        assertEquals(null, result.findings.single().frequencyHz)
        assertTrue(result.findings.single().limitations.isNotEmpty())
    }
}
