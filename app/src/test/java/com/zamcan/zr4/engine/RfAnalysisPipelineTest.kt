package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RfAnalysisPipelineTest {
    @Test fun spectrumConnectsDspDetectionAndProvenance() {
        val n = 1024
        val samples = DoubleArray(n) { i -> 0.65 * sin(2.0 * PI * 123 * i / n) }
        val result = RfAnalysisPipeline.analyzeSpectrum(
            observation(n, 8192.0), "run-spectrum", 1800000002000L, 1800000002100L, samples
        )
        assertNotNull(result.spectrumFrame)
        assertEquals(n / 2 + 1, result.spectrumFrame!!.amplitudeDbfs.size)
        assertEquals(AnalysisRunOutcome.SUCCESS, result.provenance.run.outcome)
        assertTrue(result.provenance.findings.isNotEmpty())
        assertTrue(result.provenance.findings.all { it.analysisRunId == "run-spectrum" })
        assertTrue(result.provenance.run.parameters["frequencyInterpretation"]!!.contains("complex-IQ"))
    }

    @Test fun flatInputIsRecordedAsNoFindings() {
        val result = RfAnalysisPipeline.analyzeSpectrum(
            observation(128, 2048.0), "run-empty", 1800000002000L, 1800000002100L, DoubleArray(128)
        )
        assertEquals(AnalysisRunOutcome.NO_FINDINGS, result.provenance.run.outcome)
        assertTrue(result.provenance.findings.isEmpty())
    }

    @Test fun burstPathDoesNotInventFrequency() {
        val samples = DoubleArray(1024)
        for (i in 512 until 640) samples[i] = 0.8
        val result = RfAnalysisPipeline.analyzeBursts(
            observation(1024, 1024.0), "run-burst", 1800000002000L, 1800000002100L,
            samples, BurstDetectionConfig(blockSize = 64)
        )
        assertNull(result.spectrumFrame)
        assertEquals(AnalysisRunOutcome.SUCCESS, result.provenance.run.outcome)
        assertNull(result.provenance.findings.single().frequencyHz)
        assertEquals("64", result.provenance.run.parameters["blockSize"])
    }

    @Test fun wrongSampleCountIsRejected() {
        try {
            RfAnalysisPipeline.analyzeSpectrum(
                observation(8, 1000.0), "run-bad", 1800000002000L, 1800000002100L, DoubleArray(16)
            )
            throw AssertionError("Expected sample-count validation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("sample count"))
        }
    }

    private fun observation(count: Int, rate: Double) = ObservationRecord(
        id = "obs-test", sourceType = ObservationSourceType.TEST_FIXTURE,
        capturedAtEpochMillis = 1800000001000L,
        unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
        state = ObservationRecordState.VALID,
        centerFrequencyHz = 100000000.0, sampleRateHz = rate, sampleCount = count,
        notes = listOf("Explicit test fixture; not live data")
    )
}
