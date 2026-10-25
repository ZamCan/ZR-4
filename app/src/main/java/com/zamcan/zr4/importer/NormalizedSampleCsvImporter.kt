package com.zamcan.zr4.importer

/**
 * Strict parser for explicitly normalized sample CSV files.
 *
 * Supported formats:
 *   sample
 *   0.1
 *   -0.2
 *
 * or:
 *   i,q
 *   0.1,0.0
 *   0.0,0.1
 *
 * This parser never infers sample rate, center frequency, hardware, calibration,
 * or source authenticity. The caller must supply and validate that metadata.
 */
sealed interface NormalizedSamplePayload {
    val sampleCount: Int

    data class Real(val samples: DoubleArray) : NormalizedSamplePayload {
        override val sampleCount: Int get() = samples.size
    }

    data class ComplexIq(val iSamples: DoubleArray, val qSamples: DoubleArray) : NormalizedSamplePayload {
        override val sampleCount: Int get() = iSamples.size
    }
}

object NormalizedSampleCsvImporter {
    const val MAX_SAMPLE_COUNT = 262_144
    const val MAX_CSV_CHARACTERS = 8 * 1024 * 1024

    fun parse(csv: String): NormalizedSamplePayload {
        require(csv.length <= MAX_CSV_CHARACTERS) {
            "CSV file exceeds the supported 8 MiB import limit."
        }
        require(csv.isNotBlank()) { "CSV file is empty." }
        val lines = csv.lineSequence()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { (_, line) -> line.isNotEmpty() }
            .toList()
        require(lines.isNotEmpty()) { "CSV file contains no rows." }

        return when (lines.first().second.lowercase()) {
            "sample" -> parseReal(lines.drop(1))
            "i,q" -> parseComplex(lines.drop(1))
            else -> throw IllegalArgumentException("Expected exactly the header 'sample' or 'i,q'.")
        }
    }

    private fun parseReal(rows: List<Pair<Int, String>>): NormalizedSamplePayload.Real {
        require(rows.isNotEmpty()) { "CSV contains a header but no sample rows." }
        require(rows.size <= MAX_SAMPLE_COUNT) { "CSV exceeds the supported sample limit." }
        val samples = DoubleArray(rows.size)
        rows.forEachIndexed { index, (lineNumber, row) ->
            require(!row.contains(',')) { "Line $lineNumber must contain one sample value." }
            val value = row.toDoubleOrNull()
                ?: throw IllegalArgumentException("Line $lineNumber is not a valid decimal sample.")
            require(value.isFinite()) { "Line $lineNumber contains NaN or infinity." }
            require(value in -1.0..1.0) { "Line $lineNumber is outside normalized range [-1, 1]." }
            samples[index] = value
        }
        return NormalizedSamplePayload.Real(samples)
    }

    private fun parseComplex(rows: List<Pair<Int, String>>): NormalizedSamplePayload.ComplexIq {
        require(rows.isNotEmpty()) { "CSV contains a header but no sample rows." }
        require(rows.size <= MAX_SAMPLE_COUNT) { "CSV exceeds the supported sample limit." }
        val iSamples = DoubleArray(rows.size)
        val qSamples = DoubleArray(rows.size)
        rows.forEachIndexed { index, (lineNumber, row) ->
            val values = row.split(',')
            require(values.size == 2) { "Line $lineNumber must contain exactly two comma-separated values." }
            val i = values[0].trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("Line $lineNumber has an invalid I sample.")
            val q = values[1].trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("Line $lineNumber has an invalid Q sample.")
            require(i.isFinite() && q.isFinite()) { "Line $lineNumber contains NaN or infinity." }
            require(i in -1.0..1.0 && q in -1.0..1.0) {
                "Line $lineNumber is outside normalized range [-1, 1]."
            }
            iSamples[index] = i
            qSamples[index] = q
        }
        return NormalizedSamplePayload.ComplexIq(iSamples, qSamples)
    }
}
