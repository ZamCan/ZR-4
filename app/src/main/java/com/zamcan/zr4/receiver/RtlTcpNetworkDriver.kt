package com.zamcan.zr4.receiver

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

/**
 * Client for a user-configured rtl_tcp server. This is endpoint configuration,
 * not automatic network discovery or proof of physical receiver availability.
 */
class RtlTcpNetworkDriver(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
) : SdrReceiverDriver {
    init {
        require(host.isNotBlank())
        require(port in 1..65535)
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
    }

    override val driverId = "rtl-tcp:$host:$port"
    private val deviceId = "rtl-tcp-endpoint:$host:$port"
    private val endpoint = SdrReceiverDescriptor(
        deviceId, "RTL-TCP at $host:$port", ReceiverTransport.NETWORK
    )

    override fun discoverDevices(): List<SdrReceiverDescriptor> = listOf(endpoint)

    override fun open(deviceId: String): SdrReceiverConnection {
        require(deviceId == this.deviceId) { "Device does not match the configured rtl_tcp endpoint." }
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.soTimeout = readTimeoutMillis
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val header = ByteArray(HEADER_SIZE)
            input.readFully(header)
            require(header.copyOfRange(0, 4).contentEquals(MAGIC)) {
                "Remote endpoint did not return the rtl_tcp RTL0 handshake."
            }
            val tunerType = unsignedInt(header, 4)
            val gainCount = unsignedInt(header, 8)
            require(gainCount <= MAX_GAIN_COUNT) { "rtl_tcp server reported an unreasonable gain count." }
            val output = DataOutputStream(socket.getOutputStream())
            val caps = SdrReceiverCapabilities(
                minimumFrequencyHz = MIN_FREQUENCY_HZ.toDouble(),
                maximumFrequencyHz = MAX_FREQUENCY_HZ.toDouble(),
                supportedSampleRatesHz = SUPPORTED_SAMPLE_RATES_HZ,
                supportsComplexIq = true,
                verifiedCalibrationAvailable = false
            )
            val hardware = "RTL-TCP stream; tuner type $tunerType; gain entries $gainCount; sample timestamps unavailable"
            return object : SdrReceiverConnection {
                override val descriptor = endpoint
                override val capabilities = caps

                override fun capture(request: SdrCaptureRequest): NormalizedComplexIqCapture {
                    capabilities.validate(request)
                    require(request.bandwidthHz == null) {
                        "Basic rtl_tcp has no portable bandwidth-setting command."
                    }
                    require(request.centerFrequencyHz == kotlin.math.round(request.centerFrequencyHz) &&
                        request.centerFrequencyHz <= MAX_FREQUENCY_HZ.toDouble()) {
                        "rtl_tcp frequency must be an integer number of hertz in the supported range."
                    }
                    val gain = request.gainDb
                    require(gain == null || gain in -10.0..100.0) {
                        "rtl_tcp gain must be between -10 and 100 dB."
                    }

                    command(output, CMD_SAMPLE_RATE, request.sampleRateHz.toInt())
                    command(output, CMD_FREQUENCY, request.centerFrequencyHz.toInt())
                    command(output, CMD_GAIN_MODE, if (gain == null) 0 else 1)
                    if (gain != null) command(output, CMD_GAIN, (gain * 10.0).roundToInt())

                    // Basic rtl_tcp has no tune acknowledgement or per-sample timestamp.
                    // Drain already-buffered bytes without changing I/Q byte parity.
                    drainBufferedIq(input)
                    val capturedAt = System.currentTimeMillis()
                    val raw = ByteArray(Math.multiplyExact(request.sampleCount, 2))
                    input.readFully(raw)
                    val i = DoubleArray(request.sampleCount)
                    val q = DoubleArray(request.sampleCount)
                    for (index in 0 until request.sampleCount) {
                        i[index] = ((raw[index * 2].toInt() and 0xff) - 127.5) / 127.5
                        q[index] = ((raw[index * 2 + 1].toInt() and 0xff) - 127.5) / 127.5
                    }
                    val completedAt = System.currentTimeMillis()
                    return NormalizedComplexIqCapture(
                        i, q, request.centerFrequencyHz, request.sampleRateHz,
                        capturedAt, maxOf(capturedAt, completedAt), hardware, null
                    )
                }

                override fun close() = socket.close()
            }
        } catch (failure: Throwable) {
            try { socket.close() } catch (_: Throwable) { }
            throw failure
        }
    }

    private fun command(output: DataOutputStream, id: Int, value: Int) {
        output.writeByte(id)
        output.writeInt(value) // rtl_tcp uses network byte order (big-endian).
        output.flush()
    }

    private fun drainBufferedIq(input: DataInputStream) {
        val buffer = ByteArray(8192)
        var discarded = 0
        while (discarded < MAX_DRAIN_BYTES) {
            val available = input.available()
            if (available < 2) return
            val amount = minOf(available, buffer.size, MAX_DRAIN_BYTES - discarded) and -2
            if (amount < 2) return
            var remaining = amount
            while (remaining > 0) {
                val read = input.read(buffer, amount - remaining, remaining)
                if (read < 0) throw EOFException("rtl_tcp stream ended while discarding buffered samples.")
                remaining -= read
            }
            discarded += amount
        }
    }

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)

    companion object {
        const val DEFAULT_PORT = 1234
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 3_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
        private const val HEADER_SIZE = 12
        private const val MAX_GAIN_COUNT = 1024L
        private const val MAX_DRAIN_BYTES = 1_048_576
        private const val MIN_FREQUENCY_HZ = 24_000_000L
        private const val MAX_FREQUENCY_HZ = 1_766_000_000L
        private val MAGIC = byteArrayOf('R'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), '0'.code.toByte())
        private const val CMD_FREQUENCY = 0x01
        private const val CMD_SAMPLE_RATE = 0x02
        private const val CMD_GAIN_MODE = 0x03
        private const val CMD_GAIN = 0x04
        val SUPPORTED_SAMPLE_RATES_HZ = setOf(1_024_000L, 1_800_000L, 2_048_000L, 2_400_000L, 2_880_000L)
    }
}
