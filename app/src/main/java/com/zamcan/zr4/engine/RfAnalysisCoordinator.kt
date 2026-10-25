package com.zamcan.zr4.engine

import com.zamcan.zr4.storage.LocalObservationDatabase

/**
 * Runs a deterministic analysis against an observation already stored in an
 * open local session, then saves the completed result in one database transaction.
 * It does not acquire hardware samples or store raw sample payloads.
 */
class RfAnalysisCoordinator(private val store: LocalObservationDatabase) {
    fun analyzeSpectrum(
        sessionId: String,
        observation: ObservationRecord,
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
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
        completedAtEpochMillis: Long,
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
        completedAtEpochMillis: Long,
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
