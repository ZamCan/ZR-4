package com.zamcan.zr4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the SDR/import dialog input validation used to gate capture. */
class ReceiverInputTest {

    @Test
    fun tcpPortAcceptsValidRangeAndTrims() {
        assertEquals(1234, ReceiverInput.parseTcpPort("1234"))
        assertEquals(1234, ReceiverInput.parseTcpPort("  1234 "))
        assertEquals(1, ReceiverInput.parseTcpPort("1"))
        assertEquals(65535, ReceiverInput.parseTcpPort("65535"))
    }

    @Test
    fun tcpPortRejectsOutOfRangeAndMalformed() {
        assertNull(ReceiverInput.parseTcpPort("0"))
        assertNull(ReceiverInput.parseTcpPort("65536"))
        assertNull(ReceiverInput.parseTcpPort("-1"))
        assertNull(ReceiverInput.parseTcpPort("12.5"))
        assertNull(ReceiverInput.parseTcpPort("https"))
        assertNull(ReceiverInput.parseTcpPort(""))
    }

    @Test
    fun hostAcceptsNamesAndIpLiterals() {
        assertTrue(ReceiverInput.isValidHost("192.168.1.10"))
        assertTrue(ReceiverInput.isValidHost("rtl-server.local"))
        assertTrue(ReceiverInput.isValidHost("  localhost "))
    }

    @Test
    fun hostRejectsSchemesPortsPathsAndSpaces() {
        assertFalse(ReceiverInput.isValidHost("http://192.168.1.10"))
        assertFalse(ReceiverInput.isValidHost("192.168.1.10:1234"))
        assertFalse(ReceiverInput.isValidHost("server/path"))
        assertFalse(ReceiverInput.isValidHost("my server"))
        assertFalse(ReceiverInput.isValidHost(""))
        assertFalse(ReceiverInput.isValidHost("   "))
    }

    @Test
    fun positiveLongParsesHzValues() {
        assertEquals(2_048_000L, ReceiverInput.parsePositiveLong("2048000"))
        assertEquals(100_000_000L, ReceiverInput.parsePositiveLong("100000000"))
        assertNull(ReceiverInput.parsePositiveLong("0"))
        assertNull(ReceiverInput.parsePositiveLong("-5"))
        assertNull(ReceiverInput.parsePositiveLong("1e6"))
        assertNull(ReceiverInput.parsePositiveLong(""))
    }

    @Test
    fun powerOfTwoCountsMatchFftRequirement() {
        assertEquals(4096, ReceiverInput.parsePowerOfTwoCount("4096"))
        assertEquals(2, ReceiverInput.parsePowerOfTwoCount("2"))
        assertNull(ReceiverInput.parsePowerOfTwoCount("4095"))
        assertNull(ReceiverInput.parsePowerOfTwoCount("1"))
        assertNull(ReceiverInput.parsePowerOfTwoCount("0"))
        assertNull(ReceiverInput.parsePowerOfTwoCount("many"))
    }

    @Test
    fun captureReadyRequiresEveryField() {
        assertTrue(
            ReceiverInput.isSdrCaptureReady(
                host = "192.168.1.10", port = "1234",
                centerFrequencyHz = "100000000", sampleRateHz = "2048000", sampleCount = "4096",
            ),
        )
        assertFalse( // bad port
            ReceiverInput.isSdrCaptureReady("192.168.1.10", "99999", "100000000", "2048000", "4096"),
        )
        assertFalse( // scheme pasted into host
            ReceiverInput.isSdrCaptureReady("http://192.168.1.10", "1234", "100000000", "2048000", "4096"),
        )
        assertFalse( // non-power-of-two count
            ReceiverInput.isSdrCaptureReady("192.168.1.10", "1234", "100000000", "2048000", "4000"),
        )
        assertFalse( // blank host
            ReceiverInput.isSdrCaptureReady("", "1234", "100000000", "2048000", "4096"),
        )
    }
}
