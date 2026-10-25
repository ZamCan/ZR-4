package com.zamcan.zr4.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

class ComplexIqSpectrumEngineTest {
    @Test fun positiveComplexToneAppearsAboveCenterFrequency() {
        val n = 1024
        val sampleRate = 8192.0
        val center = 100_000_000.0
        val bin = 64
        val amplitude = 0.5
        val iSamples = DoubleArray(n) { i -> amplitude * cos(2.0 * PI * bin * i / n) }
        val qSamples = DoubleArray(n) { i -> amplitude * sin(2.0 * PI * bin * i / n) }

        val frame = ComplexIqSpectrumEngine.analyze(
            iSamples, qSamples, sampleRate, center, WindowFunction.HANN
        )

        assertEquals(n, frame.binFrequenciesHz.size)
        assertEquals(center - sampleRate / 2.0, frame.binFrequenciesHz.first(), 1e-9)
        assertEquals(center + sampleRate / 2.0 - sampleRate / n, frame.binFrequenciesHz.last(), 1e-6)
        val peak = frame.amplitudeDbfs.indices.maxBy { frame.amplitudeDbfs[it] }
        assertEquals(center + bin * sampleRate / n, frame.binFrequenciesHz[peak], 1e-6)
        assertEquals(-6.0206, frame.amplitudeDbfs[peak], 0.1)
    }

    @Test fun negativeComplexToneAppearsBelowCenterFrequency() {
        val n = 1024
        val sampleRate = 8192.0
        val center = 100_000_000.0
        val bin = 32
        val iSamples = DoubleArray(n) { i -> 0.4 * cos(2.0 * PI * bin * i / n) }
        val qSamples = DoubleArray(n) { i -> -0.4 * sin(2.0 * PI * bin * i / n) }

        val frame = ComplexIqSpectrumEngine.analyze(
            iSamples, qSamples, sampleRate, center, WindowFunction.RECTANGULAR
        )
        val peak = frame.amplitudeDbfs.indices.maxBy { frame.amplitudeDbfs[it] }
        assertEquals(center - bin * sampleRate / n, frame.binFrequenciesHz[peak], 1e-6)
    }

    @Test fun rejectsOutOfRangeNormalizedIqSamples() {
        try {
            ComplexIqSpectrumEngine.analyze(
                doubleArrayOf(0.0, 1.2), doubleArrayOf(0.0, 0.0), 1000.0, 10000.0,
                WindowFunction.RECTANGULAR
            )
            throw AssertionError("Out-of-range I/Q samples must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("normalized"))
        }
    }

    @Test fun rejectsMismatchedOrNonFiniteSamples() {
        try {
            ComplexIqSpectrumEngine.analyze(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0), 1000.0, 10000.0)
            throw AssertionError("Mismatched I/Q arrays should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("equal power-of-two"))
        }
        try {
            ComplexIqSpectrumEngine.analyze(
                doubleArrayOf(0.0, Double.NaN), doubleArrayOf(0.0, 1.0), 1000.0, 10000.0
            )
            throw AssertionError("Non-finite samples should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("NaN or infinity"))
        }
    }
}
