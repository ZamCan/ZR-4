package com.zamcan.zr4.engine

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AdaptiveDetectionConfig(
    val minimumPeakExcessDb: Double = 8.0,
    val madMultiplier: Double = 3.0,
    val minimumBinSeparation: Int = 2,
    val maximumFindings: Int = 32
) {
    init {
        require(minimumPeakExcessDb.isFinite() && minimumPeakExcessDb >= 0.0)
        require(madMultiplier.isFinite() && madMultiplier >= 0.0)
        require(minimumBinSeparation >= 1)
        require(maximumFindings >= 1)
    }
}

data class SignalFinding(
    val id: String,
    val kind: String,
    val startOffsetSeconds: Double?,
    val durationSeconds: Double?,
    val frequencyHz: Double?,
    val levelDbfs: Double?,
    val baselineDbfs: Double?,
    val thresholdDbfs: Double?,
    val prominenceDb: Double?,
    val evidence: List<String>,
    val limitations: List<String>,
    val algorithmVersion: String = "zr4-detection-1"
)

data class AdaptiveSpectrumResult(
    val baselineDbfs: Double?,
    val robustSpreadDb: Double?,
    val thresholdDbfs: Double?,
    val findings: List<SignalFinding>,
    val inputBinCount: Int
)

data class BurstDetectionConfig(
    val blockSize: Int = 256,
    val minimumExcessDb: Double = 10.0,
    val minimumAbsoluteRmsDbfs: Double = -80.0
) {
    init {
        require(blockSize >= 8)
        require(minimumExcessDb.isFinite() && minimumExcessDb >= 0.0)
        require(minimumAbsoluteRmsDbfs.isFinite())
    }
}

data class BurstDetectionResult(
    val baselineRmsDbfs: Double?,
    val thresholdRmsDbfs: Double?,
    val findings: List<SignalFinding>,
    val blockCount: Int
)

/**
 * Deterministic detectors for already-acquired samples/spectrum frames.
 * These algorithms do not acquire RF and do not identify a transmitter or protocol.
 */
object RfDetectionEngine {
    private const val FLOOR_AMPLITUDE = 1e-12
    private const val MAD_TO_SIGMA = 1.4826

    fun analyzeSpectrum(
        frame: SpectrumFrame,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): AdaptiveSpectrumResult {
        val values = frame.amplitudeDbfs
        if (values.size < 3 || values.any { !it.isFinite() } ||
            frame.binFrequenciesHz.any { !it.isFinite() }
        ) {
            return AdaptiveSpectrumResult(null, null, null, emptyList(), values.size)
        }

        val baseline = median(values)
        val mad = median(values.map { abs(it - baseline) }.toDoubleArray())
        val robustSpread = mad * MAD_TO_SIGMA
        val requiredExcess = max(config.minimumPeakExcessDb, config.madMultiplier * robustSpread)
        val threshold = baseline + requiredExcess

        val candidates = mutableListOf<SignalFinding>()
        for (i in 1 until values.lastIndex) {
            val level = values[i]
            if (level >= values[i - 1] && level > values[i + 1] && level >= threshold) {
                candidates += SignalFinding(
                    id = "spectrum-bin-$i",
                    kind = "SPECTRAL_PEAK_CANDIDATE",
                    startOffsetSeconds = null,
                    durationSeconds = null,
                    frequencyHz = frame.binFrequenciesHz[i],
                    levelDbfs = level,
                    baselineDbfs = baseline,
                    thresholdDbfs = threshold,
                    prominenceDb = level - baseline,
                    evidence = listOf(
                        "Local maximum at FFT bin $i",
                        "Level exceeds robust baseline by %.2f dB".format(java.util.Locale.ROOT, level - baseline),
                        "Detector threshold is baseline plus %.2f dB".format(java.util.Locale.ROOT, requiredExcess)
                    ),
                    limitations = listOf(
                        "Candidate feature only; not a confirmed transmitter or protocol",
                        "Level is dBFS, not calibrated dBm",
                        "Result depends on acquisition settings, window and receiver characteristics"
                    )
                )
            }
        }

        val selected = mutableListOf<SignalFinding>()
        for (candidate in candidates.sortedByDescending { it.prominenceDb ?: Double.NEGATIVE_INFINITY }) {
            val bin = candidate.id.removePrefix("spectrum-bin-").toInt()
            val tooClose = selected.any {
                abs(it.id.removePrefix("spectrum-bin-").toInt() - bin) < config.minimumBinSeparation
            }
            if (!tooClose) selected += candidate
            if (selected.size >= config.maximumFindings) break
        }

        return AdaptiveSpectrumResult(
            baselineDbfs = baseline,
            robustSpreadDb = robustSpread,
            thresholdDbfs = threshold,
            findings = selected.sortedBy { it.frequencyHz ?: Double.NEGATIVE_INFINITY },
            inputBinCount = values.size
        )
    }

    /**
     * Finds high-energy blocks in normalized real samples. This is a coarse
     * energy-burst detector; it does not recover modulation or identify sources.
     */
    fun detectBursts(
        samples: DoubleArray,
        sampleRateHz: Double,
        config: BurstDetectionConfig = BurstDetectionConfig()
    ): BurstDetectionResult {
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0) { "Sample rate must be finite and positive." }
        require(samples.all { it.isFinite() }) { "Samples must not contain NaN or infinity." }
        if (samples.isEmpty()) return BurstDetectionResult(null, null, emptyList(), 0)

        val blockCount = (samples.size + config.blockSize - 1) / config.blockSize
        val levels = DoubleArray(blockCount)
        for (block in 0 until blockCount) {
            val start = block * config.blockSize
            val end = minOf(start + config.blockSize, samples.size)
            var sumSquares = 0.0
            for (i in start until end) sumSquares += samples[i] * samples[i]
            val rms = sqrt(sumSquares / (end - start))
            levels[block] = 20.0 * log10(max(rms, FLOOR_AMPLITUDE))
        }

        val baseline = median(levels)
        val threshold = max(config.minimumAbsoluteRmsDbfs, baseline + config.minimumExcessDb)
        val findings = mutableListOf<SignalFinding>()
        var block = 0
        while (block < blockCount) {
            if (levels[block] < threshold) {
                block++
                continue
            }
            val startBlock = block
            var maxLevel = levels[block]
            while (block < blockCount && levels[block] >= threshold) {
                maxLevel = max(maxLevel, levels[block])
                block++
            }
            val startSample = startBlock * config.blockSize
            val endSample = minOf(block * config.blockSize, samples.size)
            findings += SignalFinding(
                id = "burst-$startBlock",
                kind = "ENERGY_BURST_CANDIDATE",
                startOffsetSeconds = startSample / sampleRateHz,
                durationSeconds = (endSample - startSample) / sampleRateHz,
                frequencyHz = null,
                levelDbfs = maxLevel,
                baselineDbfs = baseline,
                thresholdDbfs = threshold,
                prominenceDb = maxLevel - baseline,
                evidence = listOf(
                    "Block RMS exceeded the adaptive energy threshold",
                    "Detected block range: $startBlock through " + (block - 1)
                ),
                limitations = listOf(
                    "Energy burst only; frequency and modulation are not identified",
                    "Input must be real acquired samples; synthetic test samples are not live observations",
                    "Amplitude is relative dBFS unless acquisition is calibrated"
                )
            )
        }
        return BurstDetectionResult(baseline, threshold, findings, blockCount)
    }

    private fun median(values: DoubleArray): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
