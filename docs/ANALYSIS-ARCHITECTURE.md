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

The current real-sample FFT returns a one-sided spectrum from zero to the Nyquist frequency. Its bins are baseband offsets; a tuned center frequency is retained as metadata but is not added to these bins. This prevents a one-sided real-sample spectrum from being mislabelled as a centered complex-I/Q SDR spectrum.

Levels are dBFS relative to normalized full scale. They are not calibrated dBm or dBm/Hz. Absolute power requires a documented, validated receiver calibration path.

## Not yet implemented

- Live optical camera sampling and blink-timing acquisition.
- Android Wi-Fi and Bluetooth observations using the actual permissions and operating-system APIs.
- External SDR discovery, driver support, sample acquisition, and hardware validation.
- A dedicated centered complex-I/Q FFT path for SDR captures.
- Device/emulator execution of the Android SQLite instrumentation tests.
- A complete UI workflow that acquires supported source data and invokes the coordinator.

Do not display synthetic fixtures as live data. Do not infer transmitter identity or protocol from a spectral peak. Source selection in the UI is not itself acquisition.
