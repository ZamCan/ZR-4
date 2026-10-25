package com.zamcan.zr4.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SdrReceiverContractTest {
    @Test
    fun captureRequestRequiresPowerOfTwoSampleCountAndValidMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            SdrCaptureRequest(centerFrequencyHz = 100_000_000.0, sampleRateHz = 2_000_000, sampleCount = 3000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SdrCaptureRequest(centerFrequencyHz = Double.NaN, sampleRateHz = 2_000_000, sampleCount = 4096)
        }
    }

    @Test
    fun normalizedCaptureValidatesIqShapeRangeAndTimeOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedComplexIqCapture(doubleArrayOf(0.0, 1.1), doubleArrayOf(0.0, 0.0),
                100_000_000.0, 2_000_000, 1000, 1100, "Test receiver")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedComplexIqCapture(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0),
                100_000_000.0, 2_000_000, 1000, 1100, "Test receiver")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedComplexIqCapture(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0),
                100_000_000.0, 2_000_000, 1200, 1100, "Test receiver")
        }
    }

    @Test
    fun captureSamplesAreDefensivelyCopied() {
        val i = doubleArrayOf(0.25, -0.25)
        val q = doubleArrayOf(0.5, -0.5)
        val capture = NormalizedComplexIqCapture(i, q, 100_000_000.0, 2_000_000, 1000, 1100, "Test receiver")
        i[0] = 1.0
        val exported = capture.copyISamples()
        exported[0] = -1.0
        assertEquals(0.25, capture.copyISamples()[0], 0.0)
    }

    @Test
    fun capabilitiesRejectUnsupportedFrequencyAndRate() {
        val caps = capabilities()
        assertThrows(IllegalArgumentException::class.java) {
            caps.validate(SdrCaptureRequest(500_000_000.0, 2_000_000, 4096))
        }
        assertThrows(IllegalArgumentException::class.java) {
            caps.validate(SdrCaptureRequest(100_000_000.0, 1_000_000, 4096))
        }
    }

    @Test
    fun emptyRegistryReportsNoDriversAndDoesNotInventDevices() {
        val registry = SdrReceiverRegistry()
        assertTrue(registry.registeredDriverIds().isEmpty())
        assertTrue(registry.discoverDevices().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { registry.open("rtl-sdr", "usb-1") }
    }

    @Test
    fun registryDiscoversOnlyExplicitDriverDevicesAndValidatesCapture() {
        val registry = SdrReceiverRegistry()
        val driver = FakeDriver()
        registry.register(driver)
        assertEquals(listOf("fixture-driver"), registry.registeredDriverIds())
        val found = registry.discoverDevices()
        assertEquals(1, found.size)
        assertEquals("test-receiver-1", found.single().descriptor.deviceId)
        val connection = registry.open("fixture-driver", "test-receiver-1")
        val request = SdrCaptureRequest(100_000_000.0, 2_000_000, 4)
        val capture = connection.capture(request)
        SdrCaptureValidator.validate(request, connection.capabilities, capture)
        assertEquals(4, capture.sampleCount)
        connection.close()
    }

    @Test
    fun registryRejectsDuplicateDriverIds() {
        val registry = SdrReceiverRegistry()
        registry.register(FakeDriver())
        assertThrows(IllegalArgumentException::class.java) { registry.register(FakeDriver()) }
    }

    @Test
    fun captureValidatorRejectsDriverMetadataMismatch() {
        val request = SdrCaptureRequest(100_000_000.0, 2_000_000, 4)
        val capture = NormalizedComplexIqCapture(
            doubleArrayOf(0.0, 0.1, 0.0, -0.1), doubleArrayOf(0.1, 0.0, -0.1, 0.0),
            101_000_000.0, 2_000_000, 1000, 1100, "Test receiver"
        )
        assertThrows(IllegalArgumentException::class.java) {
            SdrCaptureValidator.validate(request, capabilities(), capture)
        }
    }

    private fun capabilities() = SdrReceiverCapabilities(
        minimumFrequencyHz = 24_000_000.0,
        maximumFrequencyHz = 1_700_000_000.0,
        supportedSampleRatesHz = setOf(2_000_000L, 2_400_000L),
        supportsComplexIq = true
    )

    private class FakeDriver : SdrReceiverDriver {
        override val driverId = "fixture-driver"
        private val descriptor = SdrReceiverDescriptor("test-receiver-1", "Test receiver", ReceiverTransport.USB, 0x1234, 0x5678)
        override fun discoverDevices() = listOf(descriptor)
        override fun open(deviceId: String): SdrReceiverConnection {
            require(deviceId == descriptor.deviceId)
            return object : SdrReceiverConnection {
                override val descriptor = this@FakeDriver.descriptor
                override val capabilities = capabilities()
                override fun capture(request: SdrCaptureRequest): NormalizedComplexIqCapture {
                    capabilities.validate(request)
                    return NormalizedComplexIqCapture(
                        DoubleArray(request.sampleCount) { if (it == 1) 0.25 else 0.0 },
                        DoubleArray(request.sampleCount) { if (it == 2) -0.25 else 0.0 },
                        request.centerFrequencyHz, request.sampleRateHz,
                        1000, 1100, descriptor.displayName
                    )
                }
                override fun close() = Unit
            }
        }
    }
}
