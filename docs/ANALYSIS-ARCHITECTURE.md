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

Both paths report relative dBFS amplitudes, not calibrated dBm or dBm/Hz. Absolute power requires a documented, validated receiver calibration path. The complex-I/Q engine accepts sample arrays but does not discover hardware by itself. The coordinator now accepts an explicitly opened SDR connection, validates the returned capture against the request, creates an `EXTERNAL_SDR` observation, and sends the in-memory I/Q arrays through the same deterministic analysis and transactional persistence path. It does not bundle a physical USB/network driver, persist raw samples, or claim verified calibration. The caller retains responsibility for closing the connection.

## Imported normalized sample files

The pure Kotlin CSV importer accepts only two explicit headers: `sample` for normalized real samples, or `i,q` for normalized complex-I/Q pairs. Values must be finite decimal numbers within [-1, 1]. It rejects malformed rows and limits imports to 8 MiB and 262,144 samples to keep memory use bounded on phones. It does not infer sample rate, center frequency, hardware identity, calibration, or whether the data genuinely came from a receiver.

`ImportedSampleObservationFactory` creates metadata marked as imported and uncalibrated. A supplied calibration identifier is recorded as a user-provided label, not proof of verified calibration. The sample payload remains separate from the metadata database. The Sessions screen now provides a Storage Access Framework file picker, bounded UTF-8 reading, explicit sample-rate and (for I/Q) center-frequency entry, and a deterministic import-to-analysis path into an open local session. The workflow records the import time—not a claimed original capture time—and explains this limitation. Raw sample arrays remain in memory for the current analysis operation and are not persisted by the metadata database.

## Android Wi-Fi scan snapshots

The Sessions screen can record the handset's currently available Android Wi-Fi scan results after the Android location permission is granted (required by the platform scan-results API even though this feature does not read or store the handset location). The app does not force a fresh scan; Android may throttle scanning or return empty/stale results due to OS policy, location settings, radio state, or device restrictions. The record stores aggregate result count, reported strongest RSSI, observed frequencies, result freshness, and limitations. SSIDs and BSSIDs are not persisted. These discrete scan summaries are not continuous RF spectrum, raw IQ, calibrated receiver power, or inputs to the normalized-sample analysis pipeline.

## Bluetooth Low Energy scan observations

The Sessions screen can run a bounded five-second Android BLE scan after the required Bluetooth runtime permissions are granted. Records include the number of scan-result callbacks and strongest handset-reported RSSI, when available. The callback count is not a count of unique physical devices. Device addresses, advertised names, and payloads are not retained. The manifest marks BLE scanning as never-for-location; Android may filter some beacon types. BLE observations are not raw IQ or continuous RF spectrum. Physical-device behavior remains to be validated.

## External SDR driver contract

The pure Kotlin receiver contract defines explicit driver registration, device discovery, capability declarations, bounded capture requests, normalized complex-I/Q payload validation, and capture metadata checks. A registry with no registered driver returns no devices; it never synthesizes receiver data. Each driver must declare its supported frequency range and discrete sample rates, and capture metadata must match the requested center frequency, sample rate, and sample count. The capture object defensively copies sample arrays and validates finite normalized values in [-1, 1]. A calibration label is descriptive metadata, not proof of calibration.

The registry is hardware-neutral and does not infer hardware from USB vendor/product IDs. The RTL-TCP network driver is implemented for a user-configured remote endpoint: it verifies the RTL0 handshake, sends frequency/sample-rate/gain commands, converts unsigned interleaved 8-bit I/Q to normalized complex samples, and returns validated captures to the analysis coordinator. A local protocol-server test covers the network protocol but is not a physical receiver test. Direct USB acquisition and physical receiver validation remain outstanding.

## Verification boundary

GitHub Actions has successfully assembled the debug APK and run JVM tests for the RTL-TCP engineering branch. Emulator instrumentation is now a separate manual workflow because the full emulator job took more than 30 minutes and previously exposed an overly strict test expectation; the test was corrected to assert the actual detector outcome. Emulator and JVM tests do not validate a physical RTL-SDR, phone-specific radio behavior, Wi-Fi scan throttling, location services, BLE availability, or permission prompts.

## Not yet implemented

- Live optical camera sampling and blink-timing acquisition.
- Direct USB SDR driver implementation and physical hardware validation.
- Physical handset validation of Android Wi-Fi/BLE permission behavior, scan throttling, and hardware availability.
- Direct USB SDR discovery/acquisition. RTL-TCP network I/Q capture and computed spectrum plotting are implemented but still require testing with a physical receiver.

Do not display synthetic fixtures as live data. Do not infer transmitter identity or protocol from a spectral peak. Source selection in the UI is not itself acquisition. Android Wi-Fi scan metadata is not passed to the normalized-sample analysis pipeline.


## RTL-TCP network acquisition

The optional network driver connects to a user-configured rtl_tcp endpoint (default port 1234), verifies the 12-byte `RTL0` handshake, issues big-endian sample-rate/frequency/gain commands, and reads unsigned 8-bit interleaved I/Q bytes. It converts each channel to normalized [-1, 1] values and passes the capture through the SDR analysis coordinator. A local protocol-server test verifies handshake, commands, conversion, and error handling without claiming that a physical receiver was tested.

The basic rtl_tcp stream has no per-sample timestamps and no portable command acknowledgement. Recorded capture times are handset-side read times, and requested tuner settings are not independently confirmed by this protocol. The configured endpoint is not automatic device discovery. Network access requires the Android INTERNET permission. Direct USB acquisition and physical RTL-SDR validation remain outstanding.
