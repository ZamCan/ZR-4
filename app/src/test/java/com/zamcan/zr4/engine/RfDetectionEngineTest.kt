package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RfDetectionEngineTest {
    @Test
    fun adaptiveSpectrumDetectorFindsPeakAboveBaseline() {
        val powers = DoubleArray(33) { -82.0 }
        powers[15] = -20.0
        val frequencies = DoubleArray(powers.size) { it * 1000.0 }
        val frame = SpectrumFrame(
            sampleRateHz = 32000.0,
            centerFrequencyHz = 100000000.0,
            binFrequenciesHz = frequencies,
            amplitudeDbfs = powers,
            window = WindowFunction.HANN,
            sampleCount = 64
        )

        val result = RfDetectionEngine.analyzeSpectrum(frame)

        assertEquals(-82.0, result.baselineDbfs!!, 1e-9)
        assertNotNull(result.thresholdDbfs)
        assertEquals(1, result.findings.size)
        assertEquals(15000.0, result.findings[0].frequencyHz!!, 1e-9)
        assertTrue(result.findings[0].limitations.any { it.contains("not a confirmed transmitter") })
    }

    @Test
    fun flatSpectrumDoesNotProduceCandidateFindings() {
        val values = DoubleArray(64) { -75.0 }
        val frame = SpectrumFrame(
            sampleRateHz = 64000.0,
            centerFrequencyHz = null,
            binFrequenciesHz = DoubleArray(64) { it.toDouble() },
            amplitudeDbfs = values,
            window = WindowFunction.RECTANGULAR,
            sampleCount = 128
        )

        val result = RfDetectionEngine.analyzeSpectrum(frame)

        assertTrue(result.findings.isEmpty())
        assertNotNull(result.baselineDbfs)
    }

    @Test
    fun energyBurstDetectorFindsInjectedHighEnergyRegion() {
        val sampleRate = 1024.0
        val samples = DoubleArray(1024)
        for (i in 512 until 640) samples[i] = 0.8

        val result = RfDetectionEngine.detectBursts(
            samples = samples,
            sampleRateHz = sampleRate,
            config = BurstDetectionConfig(blockSize = 64)
        )

        assertEquals(16, result.blockCount)
        assertEquals(1, result.findings.size)
        assertEquals(0.5, result.findings[0].startOffsetSeconds!!, 1e-9)
        assertEquals(0.125, result.findings[0].durationSeconds!!, 1e-9)
        assertEquals("ENERGY_BURST_CANDIDATE", result.findings[0].kind)
    }

    @Test
    fun allSilenceDoesNotProduceBurstFinding() {
        val result = RfDetectionEngine.detectBursts(DoubleArray(1024), 1000.0)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun nonFiniteBurstSamplesAreRejected() {
        try {
            RfDetectionEngine.detectBursts(doubleArrayOf(0.0, Double.POSITIVE_INFINITY), 1000.0)
            throw AssertionError("Infinite samples should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.isNullOrBlank())
        }
    }
}
