package com.zamcan.zr4.receiver

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtlTcpNetworkDriverTest {
    @Test
    fun rtlTcpHandshakeCommandsAndUnsignedIqAreValidated() {
        val server = ServerSocket(0)
        val failure = AtomicReference<Throwable?>()
        val commandsSeen = AtomicReference<ByteArray?>()
        val worker = Thread {
            try {
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    val out = DataOutputStream(client.getOutputStream())
                    out.write(byteArrayOf('R'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), '0'.code.toByte()))
                    out.writeInt(5)
                    out.writeInt(0)
                    out.flush()
                    val commands = ByteArray(15)
                    DataInputStream(client.getInputStream()).readFully(commands)
                    commandsSeen.set(commands)
                    Thread.sleep(100)
                    out.write(byteArrayOf(0, -1, 0, -1, 0, -1, 0, -1))
                    out.flush()
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                try { server.close() } catch (_: Throwable) { }
            }
        }
        worker.start()
        val driver = RtlTcpNetworkDriver("127.0.0.1", server.localPort, 1_000, 5_000)
        val registry = SdrReceiverRegistry()
        registry.register(driver)
        val device = registry.discoverDevices().single()
        assertEquals(ReceiverTransport.NETWORK, device.descriptor.transport)
        val connection = registry.open(driver.driverId, device.descriptor.deviceId)
        val capture = connection.capture(SdrCaptureRequest(100_000_000.0, 1_024_000L, 4))
        connection.close()
        worker.join(5_000)
        server.close()

        assertTrue(!worker.isAlive)
        assertEquals(null, failure.get())
        val commands = commandsSeen.get()
        assertNotNull(commands)
        assertEquals(0x02, commands!![0].toInt() and 0xff)
        assertEquals(1_024_000, ((commands[1].toInt() and 0xff) shl 24) or
            ((commands[2].toInt() and 0xff) shl 16) or
            ((commands[3].toInt() and 0xff) shl 8) or (commands[4].toInt() and 0xff))
        assertEquals(0x01, commands[5].toInt() and 0xff)
        assertEquals(0x03, commands[10].toInt() and 0xff)
        assertEquals(-1.0, capture.copyISamples()[0], 0.0)
        assertEquals(1.0, capture.copyQSamples()[0], 0.0)
        assertEquals(4, capture.sampleCount)
        assertTrue(capture.hardwareDescription.contains("sample timestamps unavailable"))
    }

    @Test
    fun rejectsWrongHandshakeInsteadOfClaimingAReceiver() {
        ServerSocket(0).use { server ->
            val worker = Thread {
                server.accept().use { client ->
                    client.getOutputStream().write(ByteArray(12))
                    client.getOutputStream().flush()
                }
            }
            worker.start()
            val driver = RtlTcpNetworkDriver("127.0.0.1", server.localPort, 1_000, 2_000)
            val device = driver.discoverDevices().single()
            val error = try {
                driver.open(device.deviceId)
                null
            } catch (expected: IllegalArgumentException) {
                expected
            }
            assertNotNull(error)
            worker.join(3_000)
        }
    }
}
