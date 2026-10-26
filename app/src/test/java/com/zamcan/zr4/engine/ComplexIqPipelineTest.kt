package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ComplexIqPipelineTest {
    @Test fun pipelineKeepsCenteredRfFrequencyAndProvenance() {
        val n = 1024
        val rate = 8192.0
        val center = 100_000_000.0
        val bin = 64
        val i = DoubleArray(n) { index -> 0.5 * cos(2.0 * PI * bin * index / n) }
        val q = DoubleArray(n) { index -> 0.5 * sin(2.0 * PI * bin * index / n) }
        val observation = ObservationRecord(
            id = "obs-iq", sourceType = ObservationSourceType.TEST_FIXTURE,
            capturedAtEpochMillis = 1800000001000L,
            unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
            state = ObservationRecordState.VALID,
            centerFrequencyHz = center, sampleRateHz = rate, sampleCount = n,
            notes = listOf("Explicit test fixture; not live data")
        )

        val result = RfAnalysisPipeline.analyzeComplexIq(
            observation, "run-iq", 1800000002000L, 1800000002100L, i, q
        )
        val frame = result.spectrumFrame
        assertNotNull(frame)
        val peak = frame!!.amplitudeDbfs.indices.maxBy { frame.amplitudeDbfs[it] }
        assertEquals(center + bin * rate / n, frame.binFrequenciesHz[peak], 1e-6)
        assertEquals(AnalysisRunOutcome.SUCCESS, result.provenance.run.outcome)
        assertEquals("zr4-complex-iq-spectrum-pipeline-1", result.provenance.run.algorithmVersion)
    }
}
