# ZR 4 — ZamCan RF Observatory

**ZR 4** is the public app name. **ZamCan RF Observatory** is the formal product name.

ZR 4 is an offline-first native Android workspace being engineered for optical observations, Android-reported Wi-Fi/Bluetooth observations, and supported external software-defined radio (SDR) receivers.

## Product principles

- **Real observations only:** never present simulated values as live measurements.
- **Traceable data:** identify the source, timestamp, units, configuration, and limitations of observations.
- **Offline-first:** core workflows should not require a cloud account or paid API.
- **Native Android:** Kotlin and Jetpack Compose, with layouts designed to adapt to compact and larger screens.
- **Responsible RF use:** receive-only observation; no RF transmission, token generation, or bypass of device security.
- **Honest capability states:** distinguish implemented, unavailable, and hardware-required functions.

## Measurement scope

1. **Optical observation:** camera-assisted observation of visible indicators, brightness changes, and blink timing within camera sampling limits. A camera does not detect RF.
2. **Built-in radios:** observations actually exposed by Android Wi-Fi and Bluetooth APIs. These are OS/API-limited observations, not a calibrated continuous spectrum.
3. **External SDR:** spectrum/IQ measurements only when a compatible receiver is connected and its supported range, data format, and capabilities are verified.
4. **Measurement sessions:** local records with source metadata. CSV export is not implemented yet.
5. **Multi-device correlation:** a future consent-based way to compare recorded sessions. It does not increase receiver sensitivity or frequency coverage.

## Current backend foundation

- [x] Pure Kotlin radix-2 FFT and one-sided dBFS spectrum generation for normalized real samples
- [x] Deterministic spectral peak candidate and block-RMS energy burst detectors
- [x] Observation metadata, analysis-run records, and findings linked to their source observation
- [x] In-memory session ledger with duplicate, provenance, validation, and closed-session guards
- [x] Adapter that preserves detector evidence, limitations, algorithm version, parameters, and result outcome
- [x] Android SQLite storage layer for sessions, observations, analysis runs, and findings
- [x] Foreign-key constraints, transactional writes, session-state checks, and deterministic metadata serialization
- [x] Android instrumentation tests for persistence across database reopen, provenance validation, closed-session protection, and missing sessions
- [ ] Persistence layer still needs a successful local compile and Android-device/emulator test run
- [ ] UI is not yet wired to the persistent storage API
- [ ] Verified CSV export is not implemented
- [ ] Live Android radio acquisition and external SDR capture remain separate future integrations

The in-memory ledger remains a domain layer. `LocalObservationDatabase` is a separate SQLite persistence layer; raw IQ/sample payloads are deliberately not stored in this metadata database. The database schema has an explicit version and does not silently drop data on an unknown migration. Detector outputs are candidates, not proof of emitter identity. Spectrum levels are dBFS unless a calibrated acquisition path explicitly establishes another unit.

## Current interface foundation

- Public launcher name: **ZR 4**
- Formal product identity: **ZamCan RF Observatory**
- Native dark instrument styling with deep green surfaces and restrained warm-gold accents
- Overview, Sources, Sessions, and About sections
- Source status and limitations shown without implying unfinished hardware or acquisition engines are available
- Empty-data states must remain explicit until real observations are supplied

## Build

Requirements: a compatible JDK 17 installation and Android SDK with platform/build tools for API 35.

Use the committed Gradle wrapper:

```bash
./gradlew assembleDebug
```

For Termux on Android, use the helper script. It uses Termux's compatible AAPT2 binary when available, without writing a machine-specific path into tracked Gradle configuration:

```bash
./tools/build.sh
```

Run local unit tests:

```bash
./tools/build.sh testDebugUnitTest
```

Compile Android instrumentation tests:

```bash
./tools/build.sh assembleDebugAndroidTest
```

The debug APK is normally produced at `app/build/outputs/apk/debug/app-debug.apk`. A successful build confirms compilation and packaging only. SQLite persistence tests require an Android device or emulator and are not covered by the local JVM unit-test task.

## Staged engineering roadmap

- [x] Stage 1A: buildable Android foundation, navigable interface, and truthful empty-data states
- [x] Stage 1B: launcher identity and reusable build tooling for Termux/desktop workflows
- [x] Stage 4A: deterministic DSP/detection foundation and traceable analysis records
- [x] Stage 5A: SQLite persistence engine and Android instrumentation tests authored
- [ ] Stage 2: camera-based optical observation with sampling limitations documented
- [ ] Stage 3: real Wi-Fi and Bluetooth observations with Android permissions and OS-version checks
- [ ] Stage 4B: measurement data model integration, units, real-sample plots, and device tests
- [ ] Stage 5B: connect app workflows to persistent sessions and implement verified CSV export
- [ ] Stage 6: USB host discovery and documented SDR adapter interface
- [ ] Stage 7: compatible SDR spectrum/IQ pipeline, only with verified hardware support
- [ ] Stage 8: multi-device session correlation, accessibility checks, device testing, and release verification

## Accuracy, safety, and privacy

Use ZR 4 only with devices and radio environments you are authorized to observe. Respect local radio regulations and privacy. Frequency, power, dBm, bandwidth, noise-floor, and calibration claims must not be made unless the actual source supports them. Record calibration and uncertainty where relevant.

**Status:** backend implementation is staged and under verification. A feature is not considered complete until its code, tests, and relevant device/hardware checks are verified.
