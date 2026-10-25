package com.zamcan.zr4.engine

/**
 * Coordinates deterministic analysis of already-acquired, normalized real samples.
 *
 * This class does not acquire hardware data, generate substitute measurements,
 * persist records, or infer transmitter identity. Callers must create an
 * ObservationRecord from a real/imported source and separately persist the
 * returned provenance transactionally.
 *
 * IMPORTANT: this path is for real-valued samples, not complex SDR I/Q. The
 * spectrum is one-sided and its frequency axis is expressed as baseband offsets
 * from zero. A tuned center frequency is retained in observation metadata but
 * is not added to these bins; doing so would incorrectly imply a centered-I/Q
 * spectrum. A dedicated complex-I/Q FFT path is required for centered SDR plots.
 */
data class RfAnalysisPipelineResult(
    val spectrumFrame: SpectrumFrame?,
    val detectorSummary: String,
    val provenance: ProvenancedAnalysis
)

object RfAnalysisPipeline {
    const val SPECTRUM_ALGORITHM_VERSION = "zr4-real-spectrum-pipeline-1"
    const val BURST_ALGORITHM_VERSION = "zr4-real-burst-pipeline-1"

    fun analyzeSpectrum(
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        samples: DoubleArray,
        window: WindowFunction = WindowFunction.HANN,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): RfAnalysisPipelineResult {
        validateInput(observation, runId, startedAtEpochMillis, completedAtEpochMillis, samples)
        val sampleRate = observation.sampleRateHz
            ?: throw IllegalArgumentException("Spectrum analysis requires a recorded sample rate.")
        val frame = RfDspEngine.analyzeRealSamples(
            samples = samples,
            sampleRateHz = sampleRate,
            centerFrequencyHz = null,
            window = window,
            provenance = MeasurementProvenance(
                sourceId = observation.id,
                capturedAtEpochMillis = observation.capturedAtEpochMillis,
                sampleRateHz = sampleRate,
                centerFrequencyHz = observation.centerFrequencyHz,
                hardwareDescription = observation.hardwareDescription,
                calibrationId = observation.calibrationId,
                quality = if (observation.state == ObservationRecordState.UNCALIBRATED) {
                    ObservationQuality.INCOMPLETE
                } else {
                    ObservationQuality.VALID
                }
            )
        )
        val detected = RfDetectionEngine.analyzeSpectrum(frame, config)
        val trace = RfAnalysisProvenance.fromSpectrumResult(
            observationId = observation.id,
            runId = runId,
            algorithmVersion = SPECTRUM_ALGORITHM_VERSION,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            result = detected,
            parameters = mapOf(
                "window" to window.name,
                "minimumPeakExcessDb" to config.minimumPeakExcessDb.toString(),
                "madMultiplier" to config.madMultiplier.toString(),
                "minimumBinSeparation" to config.minimumBinSeparation.toString(),
                "maximumFindings" to config.maximumFindings.toString(),
                "sampleRateHz" to sampleRate.toString(),
                "sampleCount" to samples.size.toString(),
                "frequencyInterpretation" to "one-sided real-sample baseband offsets; not complex-IQ centered RF",
                "calibration" to (observation.calibrationId ?: "not provided")
            )
        )
        return RfAnalysisPipelineResult(
            spectrumFrame = frame,
            detectorSummary = "Adaptive spectrum detector; candidates are not transmitter or protocol identification.",
            provenance = trace
        )
    }

    fun analyzeBursts(
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        samples: DoubleArray,
        config: BurstDetectionConfig = BurstDetectionConfig()
    ): RfAnalysisPipelineResult {
        validateInput(observation, runId, startedAtEpochMillis, completedAtEpochMillis, samples)
        val sampleRate = observation.sampleRateHz
            ?: throw IllegalArgumentException("Burst analysis requires a recorded sample rate.")
        val detected = RfDetectionEngine.detectBursts(samples, sampleRate, config)
        val trace = RfAnalysisProvenance.fromBurstResult(
            observationId = observation.id,
            runId = runId,
            algorithmVersion = BURST_ALGORITHM_VERSION,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            result = detected,
            parameters = mapOf(
                "blockSize" to config.blockSize.toString(),
                "minimumExcessDb" to config.minimumExcessDb.toString(),
                "minimumAbsoluteRmsDbfs" to config.minimumAbsoluteRmsDbfs.toString(),
                "sampleRateHz" to sampleRate.toString(),
                "sampleCount" to samples.size.toString(),
                "calibration" to (observation.calibrationId ?: "not provided")
            )
        )
        return RfAnalysisPipelineResult(
            spectrumFrame = null,
            detectorSummary = "Real-sample block-energy detector; frequency and modulation are not identified.",
            provenance = trace
        )
    }

    private fun validateInput(
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        samples: DoubleArray
    ) {
        require(runId.isNotBlank()) { "Analysis run id must not be blank." }
        require(startedAtEpochMillis > 0L) { "Analysis start time must be positive." }
        require(completedAtEpochMillis >= startedAtEpochMillis) {
            "Analysis completion time must not precede its start time."
        }
        require(observation.sourceType in setOf(
            ObservationSourceType.EXTERNAL_SDR,
            ObservationSourceType.IMPORTED_FILE,
            ObservationSourceType.TEST_FIXTURE
        )) {
            "This sample-analysis path accepts only SDR, imported samples, or explicitly labelled test fixtures."
        }
        require(observation.unit == ObservationDataUnit.NORMALIZED_AMPLITUDE) {
            "Sample-analysis input must be recorded as normalized amplitude."
        }
        require(observation.state == ObservationRecordState.VALID ||
            observation.state == ObservationRecordState.UNCALIBRATED) {
            "Invalid, stale, or incomplete observations cannot be analysed."
        }
        require(samples.isNotEmpty()) { "At least one acquired sample is required." }
        require(observation.sampleCount == null || observation.sampleCount == samples.size) {
            "Observation sample count does not match the supplied sample payload."
        }
        require(observation.sampleRateHz != null && observation.sampleRateHz.isFinite() &&
            observation.sampleRateHz > 0.0) {
            "A finite positive sample rate must be recorded before analysis."
        }
        require(samples.all { it.isFinite() }) { "Samples must not contain NaN or infinity." }
    }
}
