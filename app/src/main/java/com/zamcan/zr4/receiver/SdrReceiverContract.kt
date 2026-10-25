package com.zamcan.zr4.receiver

/**
 * Hardware-neutral contract for future SDR drivers. This contract does not itself
 * discover USB devices or claim that a receiver is connected.
 */
enum class ReceiverTransport { USB, NETWORK, OTHER }

data class SdrReceiverDescriptor(
    val deviceId: String,
    val displayName: String,
    val transport: ReceiverTransport,
    val vendorId: Int? = null,
    val productId: Int? = null
) {
    init {
        require(deviceId.isNotBlank()) { "Receiver device id must not be blank." }
        require(displayName.isNotBlank()) { "Receiver display name must not be blank." }
        require(vendorId == null || vendorId in 0..65535) { "Vendor id must fit an unsigned 16-bit value." }
        require(productId == null || productId in 0..65535) { "Product id must fit an unsigned 16-bit value." }
    }
}

data class SdrReceiverCapabilities(
    val minimumFrequencyHz: Double,
    val maximumFrequencyHz: Double,
    val supportedSampleRatesHz: Set<Long>,
    val supportsComplexIq: Boolean,
    val verifiedCalibrationAvailable: Boolean = false
) {
    init {
        require(minimumFrequencyHz.isFinite() && minimumFrequencyHz >= 0.0) { "Minimum frequency must be finite and non-negative." }
        require(maximumFrequencyHz.isFinite() && maximumFrequencyHz >= minimumFrequencyHz) { "Maximum frequency must be finite and at least the minimum." }
        require(supportedSampleRatesHz.isNotEmpty() && supportedSampleRatesHz.all { it > 0L }) {
            "Declare at least one positive supported sample rate."
        }
    }

    fun validate(request: SdrCaptureRequest) {
        require(supportsComplexIq) { "This receiver does not declare complex I/Q support." }
        require(request.centerFrequencyHz in minimumFrequencyHz..maximumFrequencyHz) {
            "Requested center frequency is outside the receiver's declared range."
        }
        require(request.sampleRateHz in supportedSampleRatesHz) {
            "Requested sample rate is not in the receiver's declared supported-rate set."
        }
    }
}

data class SdrCaptureRequest(
    val centerFrequencyHz: Double,
    val sampleRateHz: Long,
    val sampleCount: Int,
    val gainDb: Double? = null,
    val bandwidthHz: Double? = null
) {
    init {
        require(centerFrequencyHz.isFinite() && centerFrequencyHz >= 0.0) { "Center frequency must be finite and non-negative." }
        require(sampleRateHz > 0L) { "Sample rate must be positive." }
        require(sampleCount >= 2 && sampleCount <= MAX_CAPTURE_SAMPLES && Integer.bitCount(sampleCount) == 1) {
            "Sample count must be a power of two between 2 and $MAX_CAPTURE_SAMPLES."
        }
        require(gainDb == null || gainDb.isFinite()) { "Gain must be finite when supplied." }
        require(bandwidthHz == null || bandwidthHz.isFinite() && bandwidthHz > 0.0) {
            "Bandwidth must be finite and positive when supplied."
        }
    }

    companion object { const val MAX_CAPTURE_SAMPLES = 262_144 }
}

/**
 * A normalized complex-I/Q capture returned by a real driver. Defensive copies
 * prevent callers from mutating the validated sample payload after construction.
 * Normalized amplitudes are not calibrated dBm.
 */
class NormalizedComplexIqCapture(
    iSamples: DoubleArray,
    qSamples: DoubleArray,
    val centerFrequencyHz: Double,
    val sampleRateHz: Long,
    val capturedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val hardwareDescription: String,
    val calibrationLabel: String? = null
) {
    private val iBuffer = iSamples.copyOf()
    private val qBuffer = qSamples.copyOf()

    val sampleCount: Int get() = iBuffer.size
    fun copyISamples(): DoubleArray = iBuffer.copyOf()
    fun copyQSamples(): DoubleArray = qBuffer.copyOf()

    init {
        require(iBuffer.size == qBuffer.size) { "I and Q arrays must have equal lengths." }
        require(iBuffer.size >= 2 && iBuffer.size <= SdrCaptureRequest.MAX_CAPTURE_SAMPLES && Integer.bitCount(iBuffer.size) == 1) {
            "I/Q sample count must be a supported power of two."
        }
        require(iBuffer.all { it.isFinite() && it in -1.0..1.0 } && qBuffer.all { it.isFinite() && it in -1.0..1.0 }) {
            "Normalized I/Q values must be finite and within [-1, 1]."
        }
        require(centerFrequencyHz.isFinite() && centerFrequencyHz >= 0.0) { "Center frequency must be finite and non-negative." }
        require(sampleRateHz > 0L) { "Sample rate must be positive." }
        require(capturedAtEpochMillis > 0L && completedAtEpochMillis >= capturedAtEpochMillis) {
            "Capture timestamps must be positive and ordered."
        }
        require(hardwareDescription.isNotBlank()) { "Hardware description must not be blank." }
        require(calibrationLabel == null || calibrationLabel.isNotBlank()) { "Calibration label must not be blank when supplied." }
    }
}

data class DiscoveredSdrReceiver(
    val driverId: String,
    val descriptor: SdrReceiverDescriptor
) {
    init {
        require(driverId.isNotBlank()) { "Driver id must not be blank." }
    }
}

/** Implementations must report actual hardware and must not synthesize capture data. */
interface SdrReceiverDriver {
    val driverId: String
    fun discoverDevices(): List<SdrReceiverDescriptor>
    fun open(deviceId: String): SdrReceiverConnection
}

interface SdrReceiverConnection : AutoCloseable {
    val descriptor: SdrReceiverDescriptor
    val capabilities: SdrReceiverCapabilities
    fun capture(request: SdrCaptureRequest): NormalizedComplexIqCapture
}

/**
 * Registry for explicitly installed drivers. Empty registry means no SDR driver
 * is available; it never substitutes fixture data or guesses device capabilities.
 */
class SdrReceiverRegistry {
    private val drivers = linkedMapOf<String, SdrReceiverDriver>()

    @Synchronized
    fun register(driver: SdrReceiverDriver) {
        require(driver.driverId.isNotBlank()) { "Driver id must not be blank." }
        require(!drivers.containsKey(driver.driverId)) { "Duplicate SDR driver id: ${driver.driverId}" }
        drivers[driver.driverId] = driver
    }

    @Synchronized
    fun registeredDriverIds(): List<String> = drivers.keys.toList()

    @Synchronized
    fun discoverDevices(): List<DiscoveredSdrReceiver> = drivers.values.flatMap { driver ->
        driver.discoverDevices().map { DiscoveredSdrReceiver(driver.driverId, it) }
    }

    @Synchronized
    fun open(driverId: String, deviceId: String): SdrReceiverConnection {
        val driver = drivers[driverId] ?: throw IllegalArgumentException("No SDR driver registered with id: $driverId")
        require(driver.discoverDevices().any { it.deviceId == deviceId }) {
            "The requested receiver is not currently reported by driver $driverId."
        }
        val connection = driver.open(deviceId)
        require(connection.descriptor.deviceId == deviceId) { "Driver opened a different device than requested." }
        return connection
    }
}

object SdrCaptureValidator {
    fun validate(request: SdrCaptureRequest, capabilities: SdrReceiverCapabilities, capture: NormalizedComplexIqCapture) {
        capabilities.validate(request)
        require(capture.sampleCount == request.sampleCount) { "Driver returned a different sample count than requested." }
        require(capture.centerFrequencyHz == request.centerFrequencyHz) { "Driver capture center frequency does not match the request." }
        require(capture.sampleRateHz == request.sampleRateHz) { "Driver capture sample rate does not match the request." }
    }
}
