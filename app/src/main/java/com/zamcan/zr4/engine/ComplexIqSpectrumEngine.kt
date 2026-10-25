package com.zamcan.zr4.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A two-sided, frequency-centred spectrum computed from normalized complex I/Q.
 * Bins span center - sampleRate/2 inclusive to center + sampleRate/2 exclusive.
 * Levels are relative dBFS, not calibrated dBm or dBm/Hz.
 */
data class ComplexIqSpectrumFrame(
    val sampleRateHz: Double,
    val centerFrequencyHz: Double,
    val binFrequenciesHz: DoubleArray,
    val amplitudeDbfs: DoubleArray,
    val window: WindowFunction,
    val sampleCount: Int,
    val provenance: MeasurementProvenance? = null
) {
    init {
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0)
        require(centerFrequencyHz.isFinite() && centerFrequencyHz >= sampleRateHz / 2.0)
        require(binFrequenciesHz.size == sampleCount && amplitudeDbfs.size == sampleCount)
        require(sampleCount >= 2)
    }

    fun asDetectorFrame(): SpectrumFrame = SpectrumFrame(
        sampleRateHz = sampleRateHz,
        centerFrequencyHz = centerFrequencyHz,
        binFrequenciesHz = binFrequenciesHz,
        amplitudeDbfs = amplitudeDbfs,
        window = window,
        sampleCount = sampleCount,
        provenance = provenance
    )
}

/**
 * Complex-I/Q FFT adapter. It reuses the tested radix-2 FFT but keeps the
 * two-sided centered frequency axis distinct from the one-sided real-sample path.
 */
object ComplexIqSpectrumEngine {
    private const val MIN_AMPLITUDE = 1e-12

    fun analyze(
        iSamples: DoubleArray,
        qSamples: DoubleArray,
        sampleRateHz: Double,
        centerFrequencyHz: Double,
        window: WindowFunction = WindowFunction.HANN,
        provenance: MeasurementProvenance? = null
    ): ComplexIqSpectrumFrame {
        require(iSamples.size == qSamples.size && iSamples.size >= 2 &&
            (iSamples.size and (iSamples.size - 1)) == 0) {
            "I and Q arrays must have equal power-of-two lengths of at least 2."
        }
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0)
        require(centerFrequencyHz.isFinite() && centerFrequencyHz >= sampleRateHz / 2.0) {
            "Center frequency must be at least half the sample rate for a non-negative RF frequency axis."
        }
        require(iSamples.all { it.isFinite() } && qSamples.all { it.isFinite() }) {
            "I/Q samples must not contain NaN or infinity."
        }

        val n = iSamples.size
        val real = iSamples.copyOf()
        val imag = qSamples.copyOf()
        var windowSum = 0.0
        for (i in 0 until n) {
            val weight = when (window) {
                WindowFunction.RECTANGULAR -> 1.0
                WindowFunction.HANN -> 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            }
            real[i] *= weight
            imag[i] *= weight
            windowSum += weight
        }
        require(windowSum > 0.0)
        RfDspEngine.fftInPlace(real, imag)

        val frequencies = DoubleArray(n)
        val amplitudes = DoubleArray(n)
        for (displayIndex in 0 until n) {
            val fftIndex = (displayIndex + n / 2) % n
            frequencies[displayIndex] = centerFrequencyHz +
                (displayIndex - n / 2) * sampleRateHz / n
            val magnitude = sqrt(real[fftIndex] * real[fftIndex] + imag[fftIndex] * imag[fftIndex])
            amplitudes[displayIndex] = 20.0 * log10(max(magnitude / windowSum, MIN_AMPLITUDE))
        }
        return ComplexIqSpectrumFrame(
            sampleRateHz, centerFrequencyHz, frequencies, amplitudes, window, n, provenance
        )
    }
}
