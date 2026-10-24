package com.zamcan.zr4.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline, deterministic signal-analysis primitives.
 *
 * Power values are deliberately expressed as dBFS (relative to a normalized
 * full-scale sinusoid), not dBm. Absolute RF power requires receiver calibration.
 */
enum class WindowFunction { RECTANGULAR, HANN }

enum class ObservationQuality { VALID, STALE, INCOMPLETE, INVALID }

data class MeasurementProvenance(
    val sourceId: String,
    val capturedAtEpochMillis: Long,
    val sampleRateHz: Double? = null,
    val centerFrequencyHz: Double? = null,
    val hardwareDescription: String? = null,
    val calibrationId: String? = null,
    val quality: ObservationQuality = ObservationQuality.VALID
)

data class SpectrumFrame(
    val sampleRateHz: Double,
    val centerFrequencyHz: Double?,
    val binFrequenciesHz: DoubleArray,
    /** Peak amplitude spectrum in dBFS; not calibrated dBm or dBm/Hz. */
    val amplitudeDbfs: DoubleArray,
    val window: WindowFunction,
    val sampleCount: Int,
    val provenance: MeasurementProvenance? = null
) {
    init {
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0) { "Sample rate must be finite and positive." }
        require(binFrequenciesHz.size == amplitudeDbfs.size) { "Frequency and amplitude arrays must have equal lengths." }
        require(sampleCount > 0) { "Sample count must be positive." }
    }
}

data class DetectionConfig(
    val minimumProminenceDb: Double = 8.0,
    val minimumBinSeparation: Int = 2,
    val maximumPeaks: Int = 32
) {
    init {
        require(minimumProminenceDb.isFinite() && minimumProminenceDb >= 0.0)
        require(minimumBinSeparation >= 1)
        require(maximumPeaks >= 1)
    }
}

data class SpectralPeak(
    val frequencyHz: Double,
    val amplitudeDbfs: Double,
    val prominenceDb: Double,
    val binIndex: Int
)

data class DetectionResult(
    val peaks: List<SpectralPeak>,
    val estimatedNoiseFloorDbfs: Double?,
    val inputBinCount: Int,
    val detector: String = "median-baseline-local-maximum-v1"
)

object RfDspEngine {
    private const val MIN_AMPLITUDE = 1e-12

    /**
     * Calculates a one-sided peak-amplitude spectrum from real-valued samples.
     * Input length must be a power of two. Samples should be normalized to [-1, 1].
     * Frequencies are baseband offsets unless centerFrequencyHz is supplied.
     */
    fun analyzeRealSamples(
        samples: DoubleArray,
        sampleRateHz: Double,
        centerFrequencyHz: Double? = null,
        window: WindowFunction = WindowFunction.HANN,
        provenance: MeasurementProvenance? = null
    ): SpectrumFrame {
        require(samples.size >= 2 && samples.size and (samples.size - 1) == 0) {
            "Sample count must be a power of two and at least 2."
        }
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0) { "Sample rate must be finite and positive." }
        require(centerFrequencyHz == null || centerFrequencyHz.isFinite()) { "Center frequency must be finite." }
        require(samples.all { it.isFinite() }) { "Samples must not contain NaN or infinity." }

        val n = samples.size
        val real = samples.copyOf()
        val imag = DoubleArray(n)
        var windowSum = 0.0

        for (i in 0 until n) {
            val coefficient = when (window) {
                WindowFunction.RECTANGULAR -> 1.0
                WindowFunction.HANN -> 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            }
            real[i] *= coefficient
            windowSum += coefficient
        }
        require(windowSum > 0.0) { "Window has zero coherent gain." }

        fftInPlace(real, imag)
        val binCount = n / 2 + 1
        val frequencies = DoubleArray(binCount)
        val amplitudes = DoubleArray(binCount)

        for (k in 0 until binCount) {
            frequencies[k] = (centerFrequencyHz ?: 0.0) + k * sampleRateHz / n
            val magnitude = sqrt(real[k] * real[k] + imag[k] * imag[k])
            val oneSidedFactor = if (k == 0 || k == n / 2) 1.0 else 2.0
            val normalizedAmplitude = oneSidedFactor * magnitude / windowSum
            amplitudes[k] = 20.0 * log10(max(normalizedAmplitude, MIN_AMPLITUDE))
        }

        return SpectrumFrame(
            sampleRateHz = sampleRateHz,
            centerFrequencyHz = centerFrequencyHz,
            binFrequenciesHz = frequencies,
            amplitudeDbfs = amplitudes,
            window = window,
            sampleCount = n,
            provenance = provenance
        )
    }

    /**
     * Iterative radix-2 Cooley-Tukey FFT. Arrays are transformed in place.
     */
    internal fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        require(real.size == imag.size && real.size >= 2 && real.size and (real.size - 1) == 0) {
            "FFT arrays must have equal power-of-two lengths >= 2."
        }
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wLenReal = cos(angle)
            val wLenImag = sin(angle)
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImag = 0.0
                val half = length / 2
                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half
                    val oddReal = real[odd] * wReal - imag[odd] * wImag
                    val oddImag = real[odd] * wImag + imag[odd] * wReal
                    val evenReal = real[even]
                    val evenImag = imag[even]
                    real[even] = evenReal + oddReal
                    imag[even] = evenImag + oddImag
                    real[odd] = evenReal - oddReal
                    imag[odd] = evenImag - oddImag
                    val nextWReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWReal
                }
                start += length
            }
            if (length == n) break
            length = length shl 1
        }
    }

    /**
     * Detects local maxima against a median spectral baseline. The returned
     * prominence is a heuristic feature, not a probability or transmitter identity.
     */
    fun detectPeaks(frame: SpectrumFrame, config: DetectionConfig = DetectionConfig()): DetectionResult {
        val values = frame.amplitudeDbfs
        if (values.size < 3) {
            return DetectionResult(emptyList(), values.takeIf { it.isNotEmpty() }?.average(), values.size)
        }

        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else sorted[sorted.size / 2]

        val candidates = mutableListOf<SpectralPeak>()
        for (i in 1 until values.lastIndex) {
            if (values[i] >= values[i - 1] && values[i] > values[i + 1]) {
                val prominence = values[i] - median
                if (prominence >= config.minimumProminenceDb) {
                    candidates += SpectralPeak(
                        frequencyHz = frame.binFrequenciesHz[i],
                        amplitudeDbfs = values[i],
                        prominenceDb = prominence,
                        binIndex = i
                    )
                }
            }
        }

        val selected = mutableListOf<SpectralPeak>()
        for (candidate in candidates.sortedByDescending { it.prominenceDb }) {
            if (selected.none { abs(it.binIndex - candidate.binIndex) < config.minimumBinSeparation }) {
                selected += candidate
                if (selected.size >= config.maximumPeaks) break
            }
        }
        return DetectionResult(
            peaks = selected.sortedBy { it.frequencyHz },
            estimatedNoiseFloorDbfs = median,
            inputBinCount = values.size
        )
    }

    /** Median baseline absolute deviation helper, useful for robust noise estimates. */
    fun medianAbsoluteDeviation(values: DoubleArray): Double? {
        if (values.isEmpty() || values.any { !it.isFinite() }) return null
        val median = median(values)
        return median(values.map { abs(it - median) }.toDoubleArray())
    }

    private fun median(values: DoubleArray): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
