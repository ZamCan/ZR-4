package com.zamcan.zr4.importer

import com.zamcan.zr4.engine.ObservationDataUnit
import com.zamcan.zr4.engine.ObservationRecord
import com.zamcan.zr4.engine.ObservationRecordState
import com.zamcan.zr4.engine.ObservationSourceType

/** Metadata explicitly supplied by the user/import workflow; never inferred from CSV values. */
data class ImportedSampleMetadata(
    val observationId: String,
    val capturedAtEpochMillis: Long,
    val sampleRateHz: Double,
    val centerFrequencyHz: Double? = null,
    val hardwareDescription: String? = null,
    val calibrationId: String? = null
) {
    init {
        require(observationId.isNotBlank())
        require(capturedAtEpochMillis > 0L)
        require(sampleRateHz.isFinite() && sampleRateHz > 0.0)
        require(centerFrequencyHz == null || centerFrequencyHz.isFinite() && centerFrequencyHz >= 0.0)
    }
}

/**
 * Converts a successfully parsed normalized sample payload into traceable metadata.
 * The returned record does not contain the samples; callers must keep the payload
 * alongside the analysis request and must not call it a live hardware capture.
 */
object ImportedSampleObservationFactory {
    fun create(
        payload: NormalizedSamplePayload,
        metadata: ImportedSampleMetadata
    ): ObservationRecord {
        require(payload.sampleCount > 0)
        if (payload is NormalizedSamplePayload.ComplexIq) {
            require(metadata.centerFrequencyHz != null && metadata.centerFrequencyHz >= metadata.sampleRateHz / 2.0) {
                "Complex-I/Q imports require a center frequency at least half the sample rate."
            }
        }
        return ObservationRecord(
            id = metadata.observationId,
            sourceType = ObservationSourceType.IMPORTED_FILE,
            capturedAtEpochMillis = metadata.capturedAtEpochMillis,
            unit = ObservationDataUnit.NORMALIZED_AMPLITUDE,
            state = if (metadata.calibrationId.isNullOrBlank()) {
                ObservationRecordState.UNCALIBRATED
            } else {
                ObservationRecordState.VALID
            },
            centerFrequencyHz = metadata.centerFrequencyHz,
            sampleRateHz = metadata.sampleRateHz,
            sampleCount = payload.sampleCount,
            hardwareDescription = metadata.hardwareDescription,
            calibrationId = metadata.calibrationId,
            acquisitionConfig = mapOf(
                "format" to when (payload) {
                    is NormalizedSamplePayload.Real -> "normalized-real-csv-v1"
                    is NormalizedSamplePayload.ComplexIq -> "normalized-complex-iq-csv-v1"
                },
                "sampleCount" to payload.sampleCount.toString(),
                "sampleRateHz" to metadata.sampleRateHz.toString(),
                "centerFrequencyHz" to (metadata.centerFrequencyHz?.toString() ?: "not provided")
            ),
            notes = listOf(
                "Imported sample file; not a live acquisition.",
                "CSV values are declared normalized amplitudes in [-1, 1].",
                if (metadata.calibrationId.isNullOrBlank()) {
                    "No receiver calibration identifier provided; levels are relative dBFS."
                } else {
                    "Calibration identifier supplied by user: ${metadata.calibrationId}; calibration validity is not independently verified."
                }
            )
        )
    }
}
