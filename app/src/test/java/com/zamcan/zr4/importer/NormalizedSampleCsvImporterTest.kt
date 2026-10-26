package com.zamcan.zr4.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedSampleCsvImporterTest {
    @Test fun importsRealSamplesWithExplicitHeader() {
        val parsed = NormalizedSampleCsvImporter.parse("sample\n0.25\n-0.5\n1.0\n")
        assertTrue(parsed is NormalizedSamplePayload.Real)
        assertArrayEquals(doubleArrayOf(0.25, -0.5, 1.0), (parsed as NormalizedSamplePayload.Real).samples, 0.0)
    }

    @Test fun importsComplexIqSamplesWithoutInventingMetadata() {
        val parsed = NormalizedSampleCsvImporter.parse("i,q\n0.5,0.0\n0.0,-0.5\n")
        assertTrue(parsed is NormalizedSamplePayload.ComplexIq)
        val iq = parsed as NormalizedSamplePayload.ComplexIq
        assertArrayEquals(doubleArrayOf(0.5, 0.0), iq.iSamples, 0.0)
        assertArrayEquals(doubleArrayOf(0.0, -0.5), iq.qSamples, 0.0)
        assertEquals(2, iq.sampleCount)
    }

    @Test fun rejectsMissingHeaderMalformedRowsAndOutOfRangeValues() {
        assertRejected("0.1\n0.2")
        assertRejected("sample\n")
        assertRejected("sample\n1.2")
        assertRejected("sample\nNaN")
        assertRejected("i,q\n0.1")
        assertRejected("i,q\n0.1,Infinity")
    }

    private fun assertRejected(csv: String) {
        try {
            NormalizedSampleCsvImporter.parse(csv)
            throw AssertionError("Invalid CSV should be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.isNotBlank())
        }
    }
}
