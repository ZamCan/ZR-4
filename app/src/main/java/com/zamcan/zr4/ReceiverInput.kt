package com.zamcan.zr4

/**
 * Pure validation for receiver/import numeric fields, shared by the SDR and
 * import dialogs so the UI can flag bad input before any engine call.
 * Display/gating aid only — `RtlTcpNetworkDriver`, the analysis pipeline,
 * and the importer keep their own authoritative validation.
 */
internal object ReceiverInput {

    /** TCP port in 1..65535, or null when the text is not a valid port. */
    fun parseTcpPort(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /**
     * A bare host name or IP literal with no scheme, path, port, or spaces
     * (the port has its own field). Deliberately shallow: reachability is a
     * runtime question only the connection attempt can answer.
     */
    fun isValidHost(text: String): Boolean {
        val host = text.trim()
        return host.isNotEmpty() &&
            host.length <= 253 &&
            host.none { it.isWhitespace() } &&
            !host.contains("://") &&
            !host.contains("/") &&
            !host.contains(":")
    }

    /** Strictly positive whole number (sample rates, frequencies in Hz). */
    fun parsePositiveLong(text: String): Long? =
        text.trim().toLongOrNull()?.takeIf { it > 0L }

    /** Power-of-two sample count >= 2, as the radix-2 FFT requires. */
    fun parsePowerOfTwoCount(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it >= 2 && (it and (it - 1)) == 0 }

    /** True when every RTL-TCP capture field is well-formed. */
    fun isSdrCaptureReady(
        host: String,
        port: String,
        centerFrequencyHz: String,
        sampleRateHz: String,
        sampleCount: String,
    ): Boolean =
        isValidHost(host) &&
            parseTcpPort(port) != null &&
            parsePositiveLong(centerFrequencyHz) != null &&
            parsePositiveLong(sampleRateHz) != null &&
            parsePowerOfTwoCount(sampleCount) != null
}
