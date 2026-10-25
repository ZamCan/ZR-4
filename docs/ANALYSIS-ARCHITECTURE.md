# ZR 4 — analysis and acquisition architecture

## Implemented analysis path

The current deterministic path is:

1. A caller supplies an existing observation record and a sample payload.
2. RfAnalysisPipeline validates the source type, observation state, sample count, units, timestamps and sample rate.
3. RfDspEngine computes a one-sided spectrum from normalized real-valued samples.
4. RfDetectionEngine produces spectral-peak or energy-burst candidates.
5. RfAnalysisProvenance records the algorithm version, parameters, outcome, evidence and limitations.
6. LocalObservationDatabase.saveAnalysis writes a completed run and its findings in one SQLite transaction.
7. RfAnalysisCoordinator checks that the observation exists in the selected open session and connects the pipeline to persistence.

A failure while inserting any finding rolls back the analysis run and all findings from that transaction. Raw samples are not stored in the metadata database.

## Source trust boundaries

The real-sample pipeline accepts only EXTERNAL_SDR, IMPORTED_FILE, or explicitly labelled TEST_FIXTURE observations whose unit is NORMALIZED_AMPLITUDE. Manual notes, Android radio summaries, and camera observations must not be passed to this sample-processing path as though they were raw RF samples.

A test fixture is test data, never a live measurement. A source adapter must create an observation only after it has actually acquired or imported data and recorded its source metadata.

## Frequency and amplitude interpretation

Two paths are kept distinct:

- The real-sample FFT returns a one-sided spectrum from zero to the Nyquist frequency. Its bins are baseband offsets; a tuned center frequency is retained as metadata but is not added to these bins.
- The complex-I/Q FFT returns a two-sided centered spectrum from center frequency minus half the sample rate (inclusive) to center frequency plus half the sample rate (exclusive). It requires equal-length finite I/Q arrays and a center frequency that keeps the represented RF bins non-negative.

Both paths report relative dBFS amplitudes, not calibrated dBm or dBm/Hz. Absolute power requires a documented, validated receiver calibration path. The complex-I/Q engine accepts sample arrays but does not discover or acquire SDR hardware by itself.

## Imported normalized sample files

The pure Kotlin CSV importer accepts only two explicit headers: `sample` for normalized real samples, or `i,q` for normalized complex-I/Q pairs. Values must be finite decimal numbers within [-1, 1]. It rejects malformed rows and limits imports to 8 MiB and 262,144 samples to keep memory use bounded on phones. It does not infer sample rate, center frequency, hardware identity, calibration, or whether the data genuinely came from a receiver.

`ImportedSampleObservationFactory` creates metadata marked as imported and uncalibrated. A supplied calibration identifier is recorded as a user-provided label, not proof of verified calibration. The sample payload remains separate from the metadata database. The current importer is a parsing/factory layer; a Storage Access Framework file-picker flow and user-facing metadata form are still required for end-to-end import in the Android UI.

## Not yet implemented

- Live optical camera sampling and blink-timing acquisition.
- Android Wi-Fi and Bluetooth observations using the actual permissions and operating-system APIs.
- External SDR discovery, driver support, sample acquisition, and hardware validation.
- Device/emulator execution of the Android SQLite instrumentation tests.
- A complete UI workflow that acquires supported source data and invokes the coordinator.

Do not display synthetic fixtures as live data. Do not infer transmitter identity or protocol from a spectral peak. Source selection in the UI is not itself acquisition.
