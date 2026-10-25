package com.zamcan.zr4.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zamcan.zr4.engine.*
import com.zamcan.zr4.importer.ImportedSampleMetadata
import com.zamcan.zr4.importer.ImportedSampleObservationFactory
import com.zamcan.zr4.importer.NormalizedSampleCsvImporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class RfAnalysisCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: LocalObservationDatabase

    @Before fun setUp() {
        context.deleteDatabase(LocalObservationDatabase.DATABASE_NAME)
        store = LocalObservationDatabase(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(LocalObservationDatabase.DATABASE_NAME)
    }

    @Test fun importedComplexIqFlowsThroughAnalysisAndPersistence() {
        val session = ObservationSession("session-import-iq", "Imported I/Q test", 1800000000000L)
        val n = 1024
        val sampleRate = 8192.0
        val centerFrequency = 100_000_000.0
        val toneBin = 64
        val csv = buildString {
            append("i,q\n")
            repeat(n) { index ->
                val phase = 2.0 * PI * toneBin * index / n
                append(0.5 * kotlin.math.cos(phase)).append(',').append(0.5 * kotlin.math.sin(phase)).append('\n')
            }
        }
        val payload = NormalizedSampleCsvImporter.parse(csv)
        val observation = ImportedSampleObservationFactory.create(
            payload,
            ImportedSampleMetadata(
                observationId = "observation-import-iq",
                capturedAtEpochMillis = 1800000001000L,
                sampleRateHz = sampleRate,
                centerFrequencyHz = centerFrequency
            )
        )
        store.createSession(session)
        store.addObservation(session.id, observation)

        val result = RfAnalysisCoordinator(store).analyzeImportedSpectrum(
            sessionId = session.id,
            observation = observation,
            runId = "run-import-iq",
            startedAtEpochMillis = 1800000002000L,
            completedAtEpochMillis = 1800000002100L,
            payload = payload
        )

        val saved = store.loadSession(session.id)!!
        assertEquals(AnalysisRunOutcome.SUCCESS, saved.analysisRuns.single().outcome)
        assertEquals("zr4-complex-iq-spectrum-pipeline-1", saved.analysisRuns.single().algorithmVersion)
        assertEquals(observation.id, saved.analysisRuns.single().observationId)
        assertEquals(result.provenance.findings.size, saved.findings.size)
        assertTrue(saved.findings.all { it.analysisRunId == "run-import-iq" })
        assertTrue(saved.analysisRuns.single().parameters["frequencyInterpretation"]!!.contains("centered complex-I/Q"))
    }

    @Test fun failedFindingInsertRollsBackTheEntireAnalysisTransaction() {
        val session = ObservationSession("session-rollback", "Rollback test", 1800000000000L)
        val observation = ObservationRecord(
            id = "observation-rollback",
            sourceType = ObservationSourceType.TEST_FIXTURE,
            capturedAtEpochMillis = 1800000001000L,
            unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
            state = ObservationRecordState.VALID,
            sampleRateHz = 8192.0,
            sampleCount = 1024,
            notes = listOf("Test fixture only; not live data")
        )
        store.createSession(session)
        store.addObservation(session.id, observation)
        val samples = DoubleArray(1024) { i -> 0.7 * sin(2.0 * PI * 123 * i / 1024) }
        val analysis = RfAnalysisPipeline.analyzeSpectrum(
            observation, "run-original", 1800000002000L, 1800000002100L, samples
        )
        val rollbackRunId = "run-rollback"
        val findings = analysis.provenance.findings.map { it.copy(analysisRunId = rollbackRunId) }
        val deliberatelyInvalid = analysis.provenance.copy(
            run = analysis.provenance.run.copy(id = rollbackRunId),
            findings = findings + findings.first()
        )

        try {
            store.saveAnalysis(session.id, deliberatelyInvalid)
            throw AssertionError("Expected duplicate finding ID to abort the transaction")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // The run and any findings inserted before the duplicate must roll back.
        }

        val saved = store.loadSession(session.id)!!
        assertTrue(saved.analysisRuns.isEmpty())
        assertTrue(saved.findings.isEmpty())
    }

    @Test fun spectrumRunIsPersistedWithLinkedFindings() {
        val session = ObservationSession("session-coordinator", "Coordinator test", 1800000000000L)
        val observation = ObservationRecord(
            id = "observation-coordinator",
            sourceType = ObservationSourceType.TEST_FIXTURE,
            capturedAtEpochMillis = 1800000001000L,
            unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
            state = ObservationRecordState.VALID,
            sampleRateHz = 8192.0,
            sampleCount = 1024,
            notes = listOf("Test fixture only; not live data")
        )
        store.createSession(session)
        store.addObservation(session.id, observation)
        val samples = DoubleArray(1024) { i -> 0.7 * sin(2.0 * PI * 123 * i / 1024) }

        val result = RfAnalysisCoordinator(store).analyzeSpectrum(
            sessionId = session.id,
            observation = observation,
            runId = "run-coordinator",
            startedAtEpochMillis = 1800000002000L,
            completedAtEpochMillis = 1800000002100L,
            samples = samples
        )

        val saved = store.loadSession(session.id)!!
        assertEquals(result.provenance.run, saved.analysisRuns.single())
        assertEquals(result.provenance.findings.size, saved.findings.size)
        assertTrue(saved.findings.all { it.observationId == observation.id })
        assertTrue(saved.findings.all { it.analysisRunId == "run-coordinator" })
    }
}
