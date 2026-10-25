package com.zamcan.zr4.importer

import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedSampleObservationFactoryTest {
    @Test fun realImportIsTraceableAndNeverMarkedCalibratedByDefault() {
        val payload = NormalizedSampleCsvImporter.parse("sample\n0.1\n-0.1\n")
        val record = ImportedSampleObservationFactory.create(
            payload,
            ImportedSampleMetadata("import-1", 1800000001000L, 48000.0)
        )
        assertEquals(ObservationSourceType.IMPORTED_FILE, record.sourceType)
        assertEquals(ObservationRecordState.UNCALIBRATED, record.state)
        assertEquals(2, record.sampleCount)
        assertEquals("normalized-real-csv-v1", record.acquisitionConfig["format"])
        assertTrue(record.notes.any { it.contains("not a live acquisition") })
    }

    @Test fun complexImportRequiresCenteredFrequencyCoverage() {
        val payload = NormalizedSampleCsvImporter.parse("i,q\n0.1,0.0\n0.0,0.1\n")
        try {
            ImportedSampleObservationFactory.create(
                payload, ImportedSampleMetadata("import-iq", 1800000001000L, 48000.0)
            )
            throw AssertionError("Complex I/Q must require a center frequency.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("center frequency"))
        }
    }
}
