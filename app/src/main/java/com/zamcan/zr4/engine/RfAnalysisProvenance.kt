package com.zamcan.zr4.engine

/**
 * Converts detector outputs into traceable records without changing the
 * detector's evidence or claiming a probability of emitter identity.
 */
data class ProvenancedAnalysis(
    val run: AnalysisRunRecord,
    val findings: List<TraceableFinding>
)

object RfAnalysisProvenance {
    fun fromSpectrumResult(
        observationId: String,
        runId: String,
        algorithmVersion: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        result: AdaptiveSpectrumResult,
        parameters: Map<String, String> = emptyMap()
    ): ProvenancedAnalysis {
        require(result.inputBinCount >= 0) { "Input bin count must not be negative." }
        val run = AnalysisRunRecord(
            id = runId,
            observationId = observationId,
            algorithmName = "adaptive-spectrum-detector",
            algorithmVersion = algorithmVersion,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            outcome = if (result.findings.isEmpty()) AnalysisRunOutcome.NO_FINDINGS else AnalysisRunOutcome.SUCCESS,
            parameters = parameters + mapOf(
                "inputBinCount" to result.inputBinCount.toString(),
                "baselineDbfs" to (result.baselineDbfs?.toString() ?: "unavailable"),
                "robustSpreadDb" to (result.robustSpreadDb?.toString() ?: "unavailable"),
                "thresholdDbfs" to (result.thresholdDbfs?.toString() ?: "unavailable")
            )
        )
        val findings = result.findings.mapIndexed { index, finding ->
            TraceableFinding(
                id = "$runId-f${index + 1}",
                observationId = observationId,
                analysisRunId = runId,
                kind = finding.kind,
                createdAtEpochMillis = completedAtEpochMillis,
                frequencyHz = finding.frequencyHz,
                level = finding.levelDbfs,
                unit = ObservationDataUnit.DBFS,
                evidence = finding.evidence,
                limitations = finding.limitations
            )
        }
        return ProvenancedAnalysis(run, findings)
    }

    fun fromBurstResult(
        observationId: String,
        runId: String,
        algorithmVersion: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        result: BurstDetectionResult,
        parameters: Map<String, String> = emptyMap()
    ): ProvenancedAnalysis {
        val run = AnalysisRunRecord(
            id = runId,
            observationId = observationId,
            algorithmName = "sample-energy-burst-detector",
            algorithmVersion = algorithmVersion,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            outcome = if (result.findings.isEmpty()) AnalysisRunOutcome.NO_FINDINGS else AnalysisRunOutcome.SUCCESS,
            parameters = parameters + mapOf(
                "blockCount" to result.blockCount.toString(),
                "baselineRmsDbfs" to (result.baselineRmsDbfs?.toString() ?: "unavailable"),
                "thresholdRmsDbfs" to (result.thresholdRmsDbfs?.toString() ?: "unavailable")
            )
        )
        val findings = result.findings.mapIndexed { index, finding ->
            TraceableFinding(
                id = "$runId-f${index + 1}",
                observationId = observationId,
                analysisRunId = runId,
                kind = finding.kind,
                createdAtEpochMillis = completedAtEpochMillis,
                frequencyHz = finding.frequencyHz,
                level = finding.levelDbfs,
                unit = ObservationDataUnit.DBFS,
                evidence = finding.evidence,
                limitations = finding.limitations
            )
        }
        return ProvenancedAnalysis(run, findings)
    }
}
