package com.zamcan.zr4.engine

/**
 * Describes where an observation came from. These values describe acquisition
 * provenance; they do not imply that the source is currently available.
 */
enum class ObservationSourceType {
    ANDROID_WIFI,
    ANDROID_BLUETOOTH,
    CAMERA_VISIBLE_LIGHT,
    EXTERNAL_SDR,
    IMPORTED_FILE,
    TEST_FIXTURE,
    /** User-authored field note; not a sensor measurement. */
    MANUAL_NOTE
}

enum class ObservationDataUnit {
    NORMALIZED_AMPLITUDE,
    DBFS,
    DBM,
    DBM_PER_HZ,
    RSSI_DBM,
    HERTZ,
    LUX,
    PIXEL_INTENSITY,
    COUNT,
    UNKNOWN
}

enum class ObservationRecordState {
    VALID,
    INCOMPLETE,
    INVALID,
    UNCALIBRATED,
    STALE
}

enum class AnalysisRunOutcome {
    RUNNING,
    SUCCESS,
    NO_FINDINGS,
    REJECTED_INPUT,
    FAILED
}

enum class ObservationSessionState {
    OPEN,
    CLOSED
}

/**
 * Traceable metadata for one acquired or imported observation.
 *
 * This record deliberately stores metadata, not the raw sample payload.
 * A future storage layer can keep large samples separately and reference them.
 */
data class ObservationRecord(
    val id: String,
    val sourceType: ObservationSourceType,
    val capturedAtEpochMillis: Long,
    val unit: ObservationDataUnit,
    val state: ObservationRecordState,
    val frequencyStartHz: Double? = null,
    val frequencyEndHz: Double? = null,
    val centerFrequencyHz: Double? = null,
    val sampleRateHz: Double? = null,
    val sampleCount: Int? = null,
    val hardwareDescription: String? = null,
    val calibrationId: String? = null,
    val acquisitionConfig: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Observation id must not be blank." }
        require(capturedAtEpochMillis > 0L) { "Capture time must be a positive epoch timestamp." }
        require(frequencyStartHz == null || frequencyStartHz.isFinite() && frequencyStartHz >= 0.0) {
            "Start frequency must be finite and non-negative."
        }
        require(frequencyEndHz == null || frequencyEndHz.isFinite() && frequencyEndHz >= 0.0) {
            "End frequency must be finite and non-negative."
        }
        require(
            frequencyStartHz == null || frequencyEndHz == null || frequencyStartHz <= frequencyEndHz
        ) { "Frequency range start must not exceed its end." }
        require(centerFrequencyHz == null || centerFrequencyHz.isFinite() && centerFrequencyHz >= 0.0) {
            "Center frequency must be finite and non-negative."
        }
        require(sampleRateHz == null || sampleRateHz.isFinite() && sampleRateHz > 0.0) {
            "Sample rate must be finite and positive when provided."
        }
        require(sampleCount == null || sampleCount >= 0) { "Sample count must not be negative." }
        require(acquisitionConfig.keys.all { it.isNotBlank() }) {
            "Acquisition configuration keys must not be blank."
        }
    }
}

/** Records which deterministic algorithm processed which observation. */
data class AnalysisRunRecord(
    val id: String,
    val observationId: String,
    val algorithmName: String,
    val algorithmVersion: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val outcome: AnalysisRunOutcome = AnalysisRunOutcome.RUNNING,
    val parameters: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Analysis run id must not be blank." }
        require(observationId.isNotBlank()) { "Observation id must not be blank." }
        require(algorithmName.isNotBlank()) { "Algorithm name must not be blank." }
        require(algorithmVersion.isNotBlank()) { "Algorithm version must not be blank." }
        require(startedAtEpochMillis > 0L) { "Analysis start time must be a positive epoch timestamp." }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= startedAtEpochMillis) {
            "Analysis completion time must not precede its start time."
        }
        require(
            outcome == AnalysisRunOutcome.RUNNING || completedAtEpochMillis != null
        ) { "A completed analysis outcome requires a completion timestamp." }
        require(parameters.keys.all { it.isNotBlank() }) { "Parameter keys must not be blank." }
    }
}

/**
 * A finding with explicit links to its source observation and analysis run.
 * Level is optional because not every observation or finding is a power reading.
 */
data class TraceableFinding(
    val id: String,
    val observationId: String,
    val analysisRunId: String,
    val kind: String,
    val createdAtEpochMillis: Long,
    val frequencyHz: Double? = null,
    val level: Double? = null,
    val unit: ObservationDataUnit = ObservationDataUnit.UNKNOWN,
    val evidence: List<String> = emptyList(),
    val limitations: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Finding id must not be blank." }
        require(observationId.isNotBlank()) { "Finding observation id must not be blank." }
        require(analysisRunId.isNotBlank()) { "Finding analysis run id must not be blank." }
        require(kind.isNotBlank()) { "Finding kind must not be blank." }
        require(createdAtEpochMillis > 0L) { "Finding time must be a positive epoch timestamp." }
        require(frequencyHz == null || frequencyHz.isFinite() && frequencyHz >= 0.0) {
            "Finding frequency must be finite and non-negative."
        }
        require(level == null || level.isFinite()) { "Finding level must be finite." }
    }
}

data class ObservationSession(
    val id: String,
    val title: String,
    val startedAtEpochMillis: Long,
    val state: ObservationSessionState = ObservationSessionState.OPEN,
    val closedAtEpochMillis: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Session id must not be blank." }
        require(title.isNotBlank()) { "Session title must not be blank." }
        require(startedAtEpochMillis > 0L) { "Session start time must be a positive epoch timestamp." }
        require(closedAtEpochMillis == null || closedAtEpochMillis >= startedAtEpochMillis) {
            "Session close time must not precede its start time."
        }
        require(
            (state == ObservationSessionState.OPEN && closedAtEpochMillis == null) ||
                (state == ObservationSessionState.CLOSED && closedAtEpochMillis != null)
        ) { "Session state and close timestamp must agree." }
    }
}

data class ObservationSessionSnapshot(
    val session: ObservationSession,
    val observations: List<ObservationRecord>,
    val analysisRuns: List<AnalysisRunRecord>,
    val findings: List<TraceableFinding>
)

/**
 * In-memory domain ledger. It enforces traceability and session boundaries but
 * is not persistent storage; persistence will be implemented as a separate layer.
 */
class ObservationSessionLedger(session: ObservationSession) {
    private var currentSession: ObservationSession = session
    private val observations = linkedMapOf<String, ObservationRecord>()
    private val analysisRuns = linkedMapOf<String, AnalysisRunRecord>()
    private val findings = linkedMapOf<String, TraceableFinding>()

    @Synchronized
    fun addObservation(record: ObservationRecord) {
        ensureOpen()
        require(!observations.containsKey(record.id)) { "Duplicate observation id: ${record.id}" }
        observations[record.id] = record.copy(
            acquisitionConfig = record.acquisitionConfig.toMap(),
            notes = record.notes.toList()
        )
    }

    @Synchronized
    fun addAnalysisRun(record: AnalysisRunRecord) {
        ensureOpen()
        require(observations.containsKey(record.observationId)) {
            "Analysis run references an observation not in this session: ${record.observationId}"
        }
        require(!analysisRuns.containsKey(record.id)) { "Duplicate analysis run id: ${record.id}" }
        analysisRuns[record.id] = record.copy(
            parameters = record.parameters.toMap(),
            notes = record.notes.toList()
        )
    }

    @Synchronized
    fun addFinding(finding: TraceableFinding) {
        ensureOpen()
        require(!findings.containsKey(finding.id)) { "Duplicate finding id: ${finding.id}" }
        val run = analysisRuns[finding.analysisRunId]
            ?: throw IllegalArgumentException("Finding references an analysis run not in this session.")
        require(run.observationId == finding.observationId) {
            "Finding observation id must match its analysis run's observation."
        }
        require(run.outcome == AnalysisRunOutcome.SUCCESS) {
            "Findings can only be attached to a successful analysis run."
        }
        findings[finding.id] = finding.copy(
            evidence = finding.evidence.toList(),
            limitations = finding.limitations.toList()
        )
    }

    @Synchronized
    fun close(closedAtEpochMillis: Long): ObservationSessionSnapshot {
        ensureOpen()
        currentSession = currentSession.copy(
            state = ObservationSessionState.CLOSED,
            closedAtEpochMillis = closedAtEpochMillis
        )
        return snapshot()
    }

    @Synchronized
    fun snapshot(): ObservationSessionSnapshot = ObservationSessionSnapshot(
        session = currentSession,
        observations = observations.values.map { it.copy(
            acquisitionConfig = it.acquisitionConfig.toMap(),
            notes = it.notes.toList()
        ) },
        analysisRuns = analysisRuns.values.map { it.copy(
            parameters = it.parameters.toMap(),
            notes = it.notes.toList()
        ) },
        findings = findings.values.map { it.copy(
            evidence = it.evidence.toList(),
            limitations = it.limitations.toList()
        ) }
    )

    private fun ensureOpen() {
        check(currentSession.state == ObservationSessionState.OPEN) {
            "Cannot modify a closed observation session."
        }
    }
}
