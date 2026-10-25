package com.zamcan.zr4.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zamcan.zr4.engine.AnalysisRunOutcome
import com.zamcan.zr4.engine.AnalysisRunRecord
import com.zamcan.zr4.engine.ObservationDataUnit
import com.zamcan.zr4.engine.ObservationRecord
import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSession
import com.zamcan.zr4.engine.ObservationSessionSnapshot
import com.zamcan.zr4.engine.ObservationSessionState
import com.zamcan.zr4.engine.ProvenancedAnalysis
import com.zamcan.zr4.engine.ObservationSourceType
import com.zamcan.zr4.engine.TraceableFinding
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * On-device SQLite persistence for ZR 4 session metadata and traceable findings.
 *
 * Raw IQ/sample payloads are intentionally not stored here. Writes are
 * transactional, relationships are guarded by foreign keys and domain checks,
 * and all data remains on-device.
 */
class LocalObservationDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                started_at INTEGER NOT NULL CHECK(started_at > 0),
                state TEXT NOT NULL CHECK(state IN ('OPEN','CLOSED')),
                closed_at INTEGER,
                CHECK((state = 'OPEN' AND closed_at IS NULL) OR
                      (state = 'CLOSED' AND closed_at IS NOT NULL AND closed_at >= started_at))
            )"""
        )
        db.execSQL(
            """CREATE TABLE observations (
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                captured_at INTEGER NOT NULL CHECK(captured_at > 0),
                unit TEXT NOT NULL,
                state TEXT NOT NULL,
                frequency_start REAL,
                frequency_end REAL,
                center_frequency REAL,
                sample_rate REAL,
                sample_count INTEGER,
                hardware TEXT,
                calibration_id TEXT,
                acquisition_config TEXT NOT NULL,
                notes TEXT NOT NULL,
                UNIQUE(id, session_id),
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE,
                CHECK(frequency_start IS NULL OR frequency_start >= 0),
                CHECK(frequency_end IS NULL OR frequency_end >= 0),
                CHECK(frequency_start IS NULL OR frequency_end IS NULL OR frequency_start <= frequency_end),
                CHECK(center_frequency IS NULL OR center_frequency >= 0),
                CHECK(sample_rate IS NULL OR sample_rate > 0),
                CHECK(sample_count IS NULL OR sample_count >= 0)
            )"""
        )
        db.execSQL(
            """CREATE TABLE analysis_runs (
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                observation_id TEXT NOT NULL,
                algorithm_name TEXT NOT NULL,
                algorithm_version TEXT NOT NULL,
                started_at INTEGER NOT NULL CHECK(started_at > 0),
                completed_at INTEGER,
                outcome TEXT NOT NULL CHECK(outcome IN ('RUNNING','SUCCESS','NO_FINDINGS','REJECTED_INPUT','FAILED')),
                parameters TEXT NOT NULL,
                notes TEXT NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(observation_id, session_id) REFERENCES observations(id, session_id) ON DELETE CASCADE,
                CHECK(completed_at IS NULL OR completed_at >= started_at),
                CHECK(outcome = 'RUNNING' OR completed_at IS NOT NULL)
            )"""
        )
        db.execSQL(
            """CREATE TABLE findings (
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                observation_id TEXT NOT NULL,
                analysis_run_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                created_at INTEGER NOT NULL CHECK(created_at > 0),
                frequency REAL,
                level REAL,
                unit TEXT NOT NULL,
                evidence TEXT NOT NULL,
                limitations TEXT NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(observation_id, session_id) REFERENCES observations(id, session_id) ON DELETE CASCADE,
                FOREIGN KEY(analysis_run_id) REFERENCES analysis_runs(id) ON DELETE CASCADE,
                CHECK(frequency IS NULL OR frequency >= 0)
            )"""
        )
        db.execSQL("CREATE INDEX idx_observations_session_time ON observations(session_id, captured_at)")
        db.execSQL("CREATE INDEX idx_runs_observation_time ON analysis_runs(observation_id, started_at)")
        db.execSQL("CREATE INDEX idx_findings_session_time ON findings(session_id, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No destructive migrations are defined. Future schema changes must add
        // an explicit versioned migration instead of dropping user observations.
        throw IllegalStateException("No migration path from database version $oldVersion to $newVersion.")
    }

    @Synchronized
    fun createSession(session: ObservationSession) {
        require(session.state == ObservationSessionState.OPEN && session.closedAtEpochMillis == null) {
            "A new session must begin open."
        }
        writableDatabase.insertOrThrow("sessions", null, session.toValues())
    }

    @Synchronized
    fun addObservation(sessionId: String, observation: ObservationRecord) {
        writableDatabase.inTransaction {
            requireSessionOpen(this, sessionId)
            insertOrThrow("observations", null, observation.toValues(sessionId))
        }
    }

    @Synchronized
    fun addAnalysisRun(sessionId: String, run: AnalysisRunRecord) {
        writableDatabase.inTransaction {
            requireSessionOpen(this, sessionId)
            requireObservationBelongsToSession(this, run.observationId, sessionId)
            insertOrThrow("analysis_runs", null, run.toValues(sessionId))
        }
    }

    @Synchronized
    fun addFinding(sessionId: String, finding: TraceableFinding) {
        writableDatabase.inTransaction {
            requireSessionOpen(this, sessionId)
            requireObservationBelongsToSession(this, finding.observationId, sessionId)
            val run = queryRun(this, finding.analysisRunId)
                ?: throw IllegalArgumentException("Finding references an analysis run that does not exist.")
            require(run.observationId == finding.observationId && run.sessionId == sessionId) {
                "Finding observation and session must match its analysis run."
            }
            require(run.outcome == AnalysisRunOutcome.SUCCESS) {
                "Findings can only be attached to a successful analysis run."
            }
            insertOrThrow("findings", null, finding.toValues(sessionId))
        }
    }

    /**
     * Atomically persists a completed analysis run and all its findings.
     * If any relationship or insert fails, SQLite rolls back the entire write.
     * Raw sample payloads remain outside this metadata database.
     */
    @Synchronized
    fun saveAnalysis(sessionId: String, analysis: ProvenancedAnalysis) {
        val run = analysis.run
        require(run.outcome != AnalysisRunOutcome.RUNNING) {
            "Only completed analysis runs can be persisted as final results."
        }
        require(run.completedAtEpochMillis != null) {
            "A persisted analysis run must have a completion timestamp."
        }
        require(analysis.findings.all {
            it.observationId == run.observationId && it.analysisRunId == run.id
        }) {
            "Every finding must reference the analysis run and observation being saved."
        }
        require(run.outcome != AnalysisRunOutcome.NO_FINDINGS || analysis.findings.isEmpty()) {
            "A NO_FINDINGS run cannot contain findings."
        }
        require(run.outcome != AnalysisRunOutcome.SUCCESS || analysis.findings.isNotEmpty()) {
            "A SUCCESS run must contain at least one finding; use NO_FINDINGS otherwise."
        }

        writableDatabase.inTransaction {
            requireSessionOpen(this, sessionId)
            requireObservationBelongsToSession(this, run.observationId, sessionId)
            insertOrThrow("analysis_runs", null, run.toValues(sessionId))
            if (run.outcome == AnalysisRunOutcome.SUCCESS) {
                analysis.findings.forEach { finding ->
                    insertOrThrow("findings", null, finding.toValues(sessionId))
                }
            }
        }
    }

    @Synchronized
    fun closeSession(sessionId: String, closedAtEpochMillis: Long): ObservationSessionSnapshot {
        val snapshot = writableDatabase.inTransaction {
            requireSessionOpen(this, sessionId)
            val session = querySession(this, sessionId)
                ?: throw IllegalArgumentException("Session does not exist: $sessionId")
            require(closedAtEpochMillis >= session.startedAtEpochMillis) {
                "Session close time must not precede its start time."
            }
            val values = ContentValues().apply {
                put("state", ObservationSessionState.CLOSED.name)
                put("closed_at", closedAtEpochMillis)
            }
            update("sessions", values, "id = ? AND state = 'OPEN'", arrayOf(sessionId))
            loadSnapshot(this, sessionId)
                ?: throw IllegalStateException("Closed session could not be read back.")
        }
        return snapshot
    }

    @Synchronized
    fun loadSession(sessionId: String): ObservationSessionSnapshot? =
        readableDatabase.useDatabase { loadSnapshot(this, sessionId) }

    @Synchronized
    fun listSessions(): List<ObservationSession> =
        readableDatabase.useDatabase {
            val result = mutableListOf<ObservationSession>()
            rawQuery(
                "SELECT id, title, started_at, state, closed_at FROM sessions ORDER BY started_at DESC",
                emptyArray()
            ).use { cursor ->
                while (cursor.moveToNext()) result += cursor.toSession()
            }
            result
        }

    private data class RunLink(
        val sessionId: String,
        val observationId: String,
        val outcome: AnalysisRunOutcome
    )

    private fun queryRun(db: SQLiteDatabase, id: String): RunLink? =
        db.rawQuery(
            "SELECT session_id, observation_id, outcome FROM analysis_runs WHERE id = ?",
            arrayOf(id)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else RunLink(
                sessionId = cursor.getString(0),
                observationId = cursor.getString(1),
                outcome = AnalysisRunOutcome.valueOf(cursor.getString(2))
            )
        }

    private fun requireSessionOpen(db: SQLiteDatabase, sessionId: String) {
        val session = querySession(db, sessionId)
            ?: throw IllegalArgumentException("Session does not exist: $sessionId")
        check(session.state == ObservationSessionState.OPEN) {
            "Cannot modify a closed observation session."
        }
    }

    private fun requireObservationBelongsToSession(
        db: SQLiteDatabase,
        observationId: String,
        sessionId: String
    ) {
        db.rawQuery(
            "SELECT 1 FROM observations WHERE id = ? AND session_id = ?",
            arrayOf(observationId, sessionId)
        ).use { cursor ->
            require(cursor.moveToFirst()) {
                "Observation $observationId does not belong to session $sessionId."
            }
        }
    }

    private fun querySession(db: SQLiteDatabase, id: String): ObservationSession? =
        db.rawQuery(
            "SELECT id, title, started_at, state, closed_at FROM sessions WHERE id = ?",
            arrayOf(id)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toSession() else null }

    private fun loadSnapshot(db: SQLiteDatabase, sessionId: String): ObservationSessionSnapshot? {
        val session = querySession(db, sessionId) ?: return null
        val observations = mutableListOf<ObservationRecord>()
        db.rawQuery(
            """SELECT id, source_type, captured_at, unit, state, frequency_start, frequency_end,
               center_frequency, sample_rate, sample_count, hardware, calibration_id,
               acquisition_config, notes FROM observations WHERE session_id = ? ORDER BY captured_at, id""",
            arrayOf(sessionId)
        ).use { c -> while (c.moveToNext()) observations += c.toObservation() }

        val runs = mutableListOf<AnalysisRunRecord>()
        db.rawQuery(
            """SELECT id, observation_id, algorithm_name, algorithm_version, started_at,
               completed_at, outcome, parameters, notes FROM analysis_runs
               WHERE session_id = ? ORDER BY started_at, id""",
            arrayOf(sessionId)
        ).use { c -> while (c.moveToNext()) runs += c.toAnalysisRun() }

        val findings = mutableListOf<TraceableFinding>()
        db.rawQuery(
            """SELECT id, observation_id, analysis_run_id, kind, created_at, frequency,
               level, unit, evidence, limitations FROM findings
               WHERE session_id = ? ORDER BY created_at, id""",
            arrayOf(sessionId)
        ).use { c -> while (c.moveToNext()) findings += c.toFinding() }

        return ObservationSessionSnapshot(session, observations, runs, findings)
    }

    private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            val result = block()
            setTransactionSuccessful()
            return result
        } finally {
            endTransaction()
        }
    }

    private inline fun <T> SQLiteDatabase.useDatabase(block: SQLiteDatabase.() -> T): T =
        block(this)

    private fun ObservationSession.toValues() = ContentValues().apply {
        put("id", id); put("title", title); put("started_at", startedAtEpochMillis)
        put("state", state.name)
        if (closedAtEpochMillis == null) putNull("closed_at") else put("closed_at", closedAtEpochMillis)
    }

    private fun ObservationRecord.toValues(sessionId: String) = ContentValues().apply {
        put("id", id); put("session_id", sessionId); put("source_type", sourceType.name)
        put("captured_at", capturedAtEpochMillis); put("unit", unit.name); put("state", state.name)
        putNullable("frequency_start", frequencyStartHz); putNullable("frequency_end", frequencyEndHz)
        putNullable("center_frequency", centerFrequencyHz); putNullable("sample_rate", sampleRateHz)
        if (sampleCount == null) putNull("sample_count") else put("sample_count", sampleCount)
        putNullable("hardware", hardwareDescription); putNullable("calibration_id", calibrationId)
        put("acquisition_config", encodeMap(acquisitionConfig)); put("notes", encodeList(notes))
    }

    private fun AnalysisRunRecord.toValues(sessionId: String) = ContentValues().apply {
        put("id", id); put("session_id", sessionId); put("observation_id", observationId)
        put("algorithm_name", algorithmName); put("algorithm_version", algorithmVersion)
        put("started_at", startedAtEpochMillis)
        if (completedAtEpochMillis == null) putNull("completed_at") else put("completed_at", completedAtEpochMillis)
        put("outcome", outcome.name); put("parameters", encodeMap(parameters)); put("notes", encodeList(notes))
    }

    private fun TraceableFinding.toValues(sessionId: String) = ContentValues().apply {
        put("id", id); put("session_id", sessionId); put("observation_id", observationId)
        put("analysis_run_id", analysisRunId); put("kind", kind); put("created_at", createdAtEpochMillis)
        putNullable("frequency", frequencyHz); putNullable("level", level); put("unit", unit.name)
        put("evidence", encodeList(evidence)); put("limitations", encodeList(limitations))
    }

    private fun ContentValues.putNullable(key: String, value: Double?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun Cursor.toSession() = ObservationSession(
        id = getString(0), title = getString(1), startedAtEpochMillis = getLong(2),
        state = ObservationSessionState.valueOf(getString(3)),
        closedAtEpochMillis = if (isNull(4)) null else getLong(4)
    )

    private fun Cursor.toObservation() = ObservationRecord(
        id = getString(0), sourceType = ObservationSourceType.valueOf(getString(1)),
        capturedAtEpochMillis = getLong(2), unit = ObservationDataUnit.valueOf(getString(3)),
        state = ObservationRecordState.valueOf(getString(4)),
        frequencyStartHz = doubleOrNull(5), frequencyEndHz = doubleOrNull(6),
        centerFrequencyHz = doubleOrNull(7), sampleRateHz = doubleOrNull(8),
        sampleCount = if (isNull(9)) null else getInt(9),
        hardwareDescription = stringOrNull(10), calibrationId = stringOrNull(11),
        acquisitionConfig = decodeMap(getString(12)), notes = decodeList(getString(13))
    )

    private fun Cursor.toAnalysisRun() = AnalysisRunRecord(
        id = getString(0), observationId = getString(1), algorithmName = getString(2),
        algorithmVersion = getString(3), startedAtEpochMillis = getLong(4),
        completedAtEpochMillis = if (isNull(5)) null else getLong(5),
        outcome = AnalysisRunOutcome.valueOf(getString(6)),
        parameters = decodeMap(getString(7)), notes = decodeList(getString(8))
    )

    private fun Cursor.toFinding() = TraceableFinding(
        id = getString(0), observationId = getString(1), analysisRunId = getString(2),
        kind = getString(3), createdAtEpochMillis = getLong(4),
        frequencyHz = doubleOrNull(5), level = doubleOrNull(6),
        unit = ObservationDataUnit.valueOf(getString(7)),
        evidence = decodeList(getString(8)), limitations = decodeList(getString(9))
    )

    private fun Cursor.doubleOrNull(index: Int): Double? = if (isNull(index)) null else getDouble(index)
    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun encodeMap(map: Map<String, String>): String =
        map.entries.sortedBy { it.key }.joinToString(".") { "${encode(it.key)}:${encode(it.value)}" }

    private fun decodeMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        return value.split('.').associate { entry ->
            val separator = entry.indexOf(':')
            require(separator >= 0) { "Stored map entry is malformed." }
            decode(entry.substring(0, separator)) to decode(entry.substring(separator + 1))
        }
    }

    private fun encodeList(values: List<String>): String = values.joinToString(".") { encode(it) }
    private fun decodeList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split('.').map(::decode)

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = String(
        Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8
    )

    companion object {
        const val DATABASE_NAME = "zr4_observations.db"
        const val DATABASE_VERSION = 1
    }
}
