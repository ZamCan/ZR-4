package com.zamcan.zr4.export

import com.zamcan.zr4.engine.ObservationSessionSnapshot
import java.time.Instant

/**
 * Exports persisted metadata as RFC-4180-style CSV. Raw sample payloads are not
 * present in the local database and are therefore never implied by this export.
 */
object SessionCsvExporter {
    private val columns = listOf(
        "record_type", "session_id", "session_title", "session_state",
        "session_started_at_utc", "session_closed_at_utc",
        "observation_id", "source_type", "captured_at_utc", "observation_state", "unit",
        "frequency_start_hz", "frequency_end_hz", "center_frequency_hz", "sample_rate_hz",
        "sample_count", "hardware", "calibration_id", "acquisition_config", "observation_notes",
        "analysis_run_id", "algorithm_name", "algorithm_version", "analysis_started_at_utc",
        "analysis_completed_at_utc", "analysis_outcome", "analysis_parameters", "analysis_notes",
        "finding_id", "finding_kind", "finding_created_at_utc", "finding_frequency_hz",
        "finding_level", "finding_unit", "evidence", "limitations"
    )

    fun export(snapshot: ObservationSessionSnapshot): String = buildString {
        append(columns.joinToString(",") { cell(it) }).append("\r\n")
        val session = snapshot.session
        appendRow(
            "SESSION", session.id, session.title, session.state.name,
            utc(session.startedAtEpochMillis), session.closedAtEpochMillis?.let(::utc)
        )
        snapshot.observations.forEach { observation ->
            appendRow(
                "OBSERVATION", session.id, session.title, session.state.name,
                utc(session.startedAtEpochMillis), session.closedAtEpochMillis?.let(::utc),
                observation.id, observation.sourceType.name, utc(observation.capturedAtEpochMillis),
                observation.state.name, observation.unit.name, observation.frequencyStartHz,
                observation.frequencyEndHz, observation.centerFrequencyHz, observation.sampleRateHz,
                observation.sampleCount, observation.hardwareDescription, observation.calibrationId,
                mapText(observation.acquisitionConfig), observation.notes.joinToString(" | ")
            )
        }
        snapshot.analysisRuns.forEach { run ->
            appendRow(
                "ANALYSIS_RUN", session.id, session.title, session.state.name,
                utc(session.startedAtEpochMillis), session.closedAtEpochMillis?.let(::utc),
                observationId = run.observationId,
                analysisRunId = run.id,
                algorithmName = run.algorithmName,
                algorithmVersion = run.algorithmVersion,
                analysisStartedAtUtc = utc(run.startedAtEpochMillis),
                analysisCompletedAtUtc = run.completedAtEpochMillis?.let(::utc),
                analysisOutcome = run.outcome.name,
                analysisParameters = mapText(run.parameters),
                analysisNotes = run.notes.joinToString(" | ")
            )
        }
        snapshot.findings.forEach { finding ->
            appendRow(
                "FINDING", session.id, session.title, session.state.name,
                utc(session.startedAtEpochMillis), session.closedAtEpochMillis?.let(::utc),
                observationId = finding.observationId,
                findingId = finding.id,
                findingKind = finding.kind,
                findingCreatedAtUtc = utc(finding.createdAtEpochMillis),
                findingFrequencyHz = finding.frequencyHz,
                findingLevel = finding.level,
                findingUnit = finding.unit.name,
                evidence = finding.evidence.joinToString(" | "),
                limitations = finding.limitations.joinToString(" | ")
            )
        }
    }

    private fun StringBuilder.appendRow(
        recordType: String,
        sessionId: String,
        sessionTitle: String,
        sessionState: String,
        sessionStartedAtUtc: String,
        sessionClosedAtUtc: String? = null,
        observationId: String? = null,
        sourceType: String? = null,
        capturedAtUtc: String? = null,
        observationState: String? = null,
        unit: String? = null,
        frequencyStartHz: Any? = null,
        frequencyEndHz: Any? = null,
        centerFrequencyHz: Any? = null,
        sampleRateHz: Any? = null,
        sampleCount: Any? = null,
        hardware: String? = null,
        calibrationId: String? = null,
        acquisitionConfig: String? = null,
        observationNotes: String? = null,
        analysisRunId: String? = null,
        algorithmName: String? = null,
        algorithmVersion: String? = null,
        analysisStartedAtUtc: String? = null,
        analysisCompletedAtUtc: String? = null,
        analysisOutcome: String? = null,
        analysisParameters: String? = null,
        analysisNotes: String? = null,
        findingId: String? = null,
        findingKind: String? = null,
        findingCreatedAtUtc: String? = null,
        findingFrequencyHz: Any? = null,
        findingLevel: Any? = null,
        findingUnit: String? = null,
        evidence: String? = null,
        limitations: String? = null
    ) {
        val values = listOf(
            recordType, sessionId, sessionTitle, sessionState, sessionStartedAtUtc,
            sessionClosedAtUtc, observationId, sourceType, capturedAtUtc, observationState, unit,
            frequencyStartHz, frequencyEndHz, centerFrequencyHz, sampleRateHz, sampleCount,
            hardware, calibrationId, acquisitionConfig, observationNotes, analysisRunId,
            algorithmName, algorithmVersion, analysisStartedAtUtc, analysisCompletedAtUtc,
            analysisOutcome, analysisParameters, analysisNotes, findingId, findingKind,
            findingCreatedAtUtc, findingFrequencyHz, findingLevel, findingUnit, evidence, limitations
        )
        append(values.joinToString(",") { cell(it) }).append("\r\n")
    }

    private fun mapText(values: Map<String, String>): String =
        values.toSortedMap().entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun utc(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun cell(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else text
    }
}
