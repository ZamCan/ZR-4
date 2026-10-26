package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class RfDspEngineTest {
    @Test
    fun binCenteredToneHasExpectedFrequencyAndAmplitude() {
        val n = 1024
        val fs = 1024.0
        val targetBin = 64
        val amplitude = 0.5
        val samples = DoubleArray(n) { i -> amplitude * sin(2.0 * PI * targetBin * i / n) }

        val frame = RfDspEngine.analyzeRealSamples(
            samples = samples,
            sampleRateHz = fs,
            window = WindowFunction.HANN
        )

        assertEquals(n / 2 + 1, frame.amplitudeDbfs.size)
        assertEquals(targetBin * fs / n, frame.binFrequenciesHz[targetBin], 1e-9)
        assertEquals(-6.0206, frame.amplitudeDbfs[targetBin], 0.08)
    }

    @Test
    fun peakDetectorFindsToneAndReportsFrequency() {
        val n = 1024
        val fs = 8192.0
        val targetBin = 123
        val samples = DoubleArray(n) { i ->
            0.65 * sin(2.0 * PI * targetBin * i / n)
        }
        val frame = RfDspEngine.analyzeRealSamples(samples, fs)
        val result = RfDspEngine.detectPeaks(frame)

        assertTrue("Expected at least one detected peak", result.peaks.isNotEmpty())
        assertEquals(targetBin * fs / n, result.peaks.minBy { abs(it.frequencyHz - targetBin * fs / n) }.frequencyHz, 1e-6)
        assertTrue(result.estimatedNoiseFloorDbfs != null)
    }

    @Test
    fun invalidSampleCountsAreRejected() {
        try {
            RfDspEngine.analyzeRealSamples(doubleArrayOf(0.0, 1.0, 0.0), 1000.0)
            throw AssertionError("Non-power-of-two input should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("power of two"))
        }
    }

    @Test
    fun nonFiniteSamplesAreRejected() {
        try {
            RfDspEngine.analyzeRealSamples(doubleArrayOf(0.0, Double.NaN, 0.0, 1.0), 1000.0)
            throw AssertionError("NaN input should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("NaN"))
        }
    }

    @Test
    fun outOfRangeNormalizedSamplesAreRejected() {
        try {
            RfDspEngine.analyzeRealSamples(doubleArrayOf(0.0, 0.2, 1.01, 0.0), 1000.0)
            throw AssertionError("Out-of-range samples must be rejected for dBFS analysis")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("normalized"))
        }
    }

    @Test
    fun emptyPeakInputReturnsNoDetections() {
        val frame = SpectrumFrame(
            sampleRateHz = 1000.0,
            centerFrequencyHz = null,
            binFrequenciesHz = doubleArrayOf(),
            amplitudeDbfs = doubleArrayOf(),
            window = WindowFunction.RECTANGULAR,
            sampleCount = 2
        )
        val result = RfDspEngine.detectPeaks(frame)
        assertTrue(result.peaks.isEmpty())
        assertEquals(0, result.inputBinCount)
    }
}
