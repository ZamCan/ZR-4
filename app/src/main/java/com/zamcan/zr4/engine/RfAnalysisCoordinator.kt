package com.zamcan.zr4.engine

import com.zamcan.zr4.storage.LocalObservationDatabase
import com.zamcan.zr4.importer.NormalizedSamplePayload
import com.zamcan.zr4.receiver.NormalizedComplexIqCapture
import com.zamcan.zr4.receiver.SdrCaptureRequest
import com.zamcan.zr4.receiver.SdrCaptureValidator
import com.zamcan.zr4.receiver.SdrReceiverConnection

/**
 * Runs a deterministic analysis against an observation already stored in an
 * open local session, then saves the completed result in one database transaction.
 * It does not acquire hardware samples or store raw sample payloads.
 */
/**
 * Result of a real driver capture processed by the deterministic analysis path.
 * The raw I/Q arrays are consumed in memory and are not returned or persisted.
 */
data class SdrCaptureAnalysisResult(
    val observation: ObservationRecord,
    val analysis: RfAnalysisPipelineResult
)

class RfAnalysisCoordinator(private val store: LocalObservationDatabase) {
    fun analyzeSpectrum(
        sessionId: String,
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long? = null,
        samples: DoubleArray,
        window: WindowFunction = WindowFunction.HANN,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): RfAnalysisPipelineResult {
        requireStoredObservation(sessionId, observation)
        val result = RfAnalysisPipeline.analyzeSpectrum(
            observation, runId, startedAtEpochMillis, completedAtEpochMillis, samples, window, config
        )
        store.saveAnalysis(sessionId, result.provenance)
        return result
    }

    fun analyzeComplexIq(
        sessionId: String,
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long? = null,
        iSamples: DoubleArray,
        qSamples: DoubleArray,
        window: WindowFunction = WindowFunction.HANN,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): RfAnalysisPipelineResult {
        requireStoredObservation(sessionId, observation)
        val result = RfAnalysisPipeline.analyzeComplexIq(
            observation, runId, startedAtEpochMillis, completedAtEpochMillis,
            iSamples, qSamples, window, config
        )
        store.saveAnalysis(sessionId, result.provenance)
        return result
    }

    fun analyzeBursts(
        sessionId: String,
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long? = null,
        samples: DoubleArray,
        config: BurstDetectionConfig = BurstDetectionConfig()
    ): RfAnalysisPipelineResult {
        requireStoredObservation(sessionId, observation)
        val result = RfAnalysisPipeline.analyzeBursts(
            observation, runId, startedAtEpochMillis, completedAtEpochMillis, samples, config
        )
        store.saveAnalysis(sessionId, result.provenance)
        return result
    }

    /**
     * Captures from an explicitly opened driver connection, records truthful
     * capture metadata, then analyses the in-memory I/Q samples. The caller owns
     * and must close the connection. If analysis fails after capture, the capture
     * observation remains stored without a completed analysis run.
     */
    fun captureAndAnalyzeSdr(
        sessionId: String,
        connection: SdrReceiverConnection,
        request: SdrCaptureRequest,
        observationId: String,
        runId: String,
        window: WindowFunction = WindowFunction.HANN,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): SdrCaptureAnalysisResult {
        require(observationId.isNotBlank()) { "Observation id must not be blank." }
        require(runId.isNotBlank()) { "Analysis run id must not be blank." }
        val beforeCapture = store.loadSession(sessionId)
            ?: throw IllegalArgumentException("Session does not exist.")
        check(beforeCapture.session.state == ObservationSessionState.OPEN) {
            "Cannot capture into a closed session."
        }

        val capture = connection.capture(request)
        SdrCaptureValidator.validate(request, connection.capabilities, capture)
        val now = System.currentTimeMillis()
        require(capture.completedAtEpochMillis <= now) {
            "Receiver capture completion timestamp is in the future relative to the handset clock."
        }
        val observation = ObservationRecord(
            id = observationId,
            sourceType = ObservationSourceType.EXTERNAL_SDR,
            capturedAtEpochMillis = capture.capturedAtEpochMillis,
            unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
            state = ObservationRecordState.UNCALIBRATED,
            frequencyStartHz = capture.centerFrequencyHz - capture.sampleRateHz / 2.0,
            frequencyEndHz = capture.centerFrequencyHz + capture.sampleRateHz / 2.0,
            centerFrequencyHz = capture.centerFrequencyHz,
            sampleRateHz = capture.sampleRateHz.toDouble(),
            sampleCount = capture.sampleCount,
            hardwareDescription = capture.hardwareDescription,
            calibrationId = capture.calibrationLabel,
            acquisitionConfig = mapOf(
                "transport" to connection.descriptor.transport.name,
                "receiverDisplayName" to connection.descriptor.displayName,
                "centerFrequencyHz" to capture.centerFrequencyHz.toString(),
                "sampleRateHz" to capture.sampleRateHz.toString(),
                "sampleCount" to capture.sampleCount.toString(),
                "gainDb" to (request.gainDb?.toString() ?: "driver default"),
                "bandwidthHz" to (request.bandwidthHz?.toString() ?: "driver default")
            ),
            notes = listOf(
                "Captured through an explicitly registered external SDR driver.",
                "Normalized I/Q samples were analysed in memory and are not stored in the metadata database.",
                "Levels are relative dBFS; no absolute receiver-power calibration is asserted.",
                if (capture.calibrationLabel.isNullOrBlank()) {
                    "No calibration label was supplied by the driver."
                } else {
                    "Driver supplied calibration label: ${capture.calibrationLabel}; verification is not implied."
                }
            )
        )
        store.addObservation(sessionId, observation)
        val startedAt = maxOf(System.currentTimeMillis(), capture.completedAtEpochMillis)
        val completedAt = maxOf(System.currentTimeMillis(), startedAt)
        val result = RfAnalysisPipeline.analyzeComplexIq(
            observation = observation,
            runId = runId,
            startedAtEpochMillis = startedAt,
            completedAtEpochMillis = completedAt,
            iSamples = capture.copyISamples(),
            qSamples = capture.copyQSamples(),
            window = window,
            config = config
        )
        store.saveAnalysis(sessionId, result.provenance)
        return SdrCaptureAnalysisResult(observation, result)
    }

    /**
     * Convenience entry point for a parsed normalized CSV payload.
     * The observation must already be saved in the selected open session.
     */
    fun analyzeImportedSpectrum(
        sessionId: String,
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long? = null,
        payload: NormalizedSamplePayload,
        window: WindowFunction = WindowFunction.HANN,
        config: AdaptiveDetectionConfig = AdaptiveDetectionConfig()
    ): RfAnalysisPipelineResult {
        require(observation.sourceType == ObservationSourceType.IMPORTED_FILE) {
            "Imported payloads require an observation marked IMPORTED_FILE."
        }
        require(observation.sampleCount == payload.sampleCount) {
            "Imported payload count does not match the stored observation metadata."
        }
        return when (payload) {
            is NormalizedSamplePayload.Real -> analyzeSpectrum(
                sessionId, observation, runId, startedAtEpochMillis, completedAtEpochMillis,
                payload.samples, window, config
            )
            is NormalizedSamplePayload.ComplexIq -> analyzeComplexIq(
                sessionId, observation, runId, startedAtEpochMillis, completedAtEpochMillis,
                payload.iSamples, payload.qSamples, window, config
            )
        }
    }

    private fun requireStoredObservation(sessionId: String, observation: ObservationRecord) {
        val snapshot = store.loadSession(sessionId)
            ?: throw IllegalArgumentException("Session does not exist.")
        check(snapshot.session.state == ObservationSessionState.OPEN) {
            "Cannot analyse into a closed session."
        }
        val stored = snapshot.observations.singleOrNull { it.id == observation.id }
            ?: throw IllegalArgumentException("Observation is not in this session.")
        require(stored == observation) { "Observation metadata does not match the stored record." }
    }
}
