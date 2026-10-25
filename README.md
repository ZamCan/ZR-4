# ZR 4 — ZamCan RF Observatory

**ZR 4** is the public app name. **ZamCan RF Observatory** is the formal product name.

ZR 4 is an offline-first native Android workspace for optical observations, Android-reported Wi-Fi/Bluetooth observations, and compatible external software-defined radio (SDR) receivers. The `core/premium-stable` branch consolidates the Observatory Glass interface, deterministic RF analysis, and local SQLite persistence foundations. Device verification is still required before calling persistence production-ready.

## Product principles

- **Real observations only:** never present simulated values as live measurements.
- **Traceable data:** retain source, timestamp, units, configuration, algorithm provenance, evidence, and limitations.
- **Offline-first:** core workflows require no cloud account or paid API.
- **Native Android:** Kotlin and Jetpack Compose; layouts adapt to compact and larger screens.
- **Receive-only:** no RF transmission, token generation, or bypass of device security.
- **Honest capability states:** distinguish implemented, unavailable, and hardware-required functions.

## Measurement scope

1. **Optical observation:** camera-assisted visible-indicator, brightness, and blink-timing analysis within camera sampling limits. A camera does not detect RF.
2. **Built-in radios:** only observations exposed by Android Wi-Fi and Bluetooth APIs; not a calibrated continuous spectrum.
3. **External SDR:** spectrum/IQ measurements only when a compatible receiver and validated acquisition pipeline are available.
4. **Measurement sessions:** local SQLite metadata persistence is implemented in this branch but still requires successful local compilation and Android-device/emulator test execution. CSV export is not implemented.
5. **Multi-device correlation:** future consent-based comparison of recorded sessions; it does not increase receiver sensitivity or frequency coverage.

## Backend foundation

- [x] Pure Kotlin radix-2 FFT and one-sided dBFS spectrum generation for normalized real samples
- [x] Deterministic spectral peak candidate and block-RMS energy burst detectors
- [x] Observation metadata, analysis-run records, and findings linked to source observations
- [x] In-memory session ledger with duplicate, provenance, validation, and closed-session guards
- [x] Adapter retaining evidence, limitations, algorithm version, parameters, and analysis outcome
- [x] SQLite database schema for sessions, observations, analysis runs, and findings
- [x] Transactional writes, foreign keys, session-state checks, and deterministic metadata serialization
- [x] Android instrumentation tests authored for database reopen, metadata serialization, provenance checks, closed sessions, and missing sessions
- [ ] Successful local compile and execution of Android instrumentation tests still required
- [ ] Persistent storage is not yet wired into the Compose interface
- [ ] Verified CSV export is not implemented
- [ ] Live Android radio acquisition and external SDR capture remain future integrations

Raw IQ/sample payloads are intentionally not stored in the metadata database. Unknown schema migrations fail explicitly rather than silently dropping data. Detector outputs are candidates, not proof of emitter identity. Spectrum levels are dBFS unless a calibrated acquisition path establishes another unit.

## Observatory Glass interface

- Premium glassmorphic panels with a restrained aurora background; no blur-library dependency
- Instrument gold for primary actions/identity, mint for ready/local states, amber for hardware-gated modules, and warm stone for records
- Custom Canvas line icons, branded PNG launcher assets, compact pinned header, floating bottom navigation, responsive source panels, and Overview / Sources / Sessions / About sections
- Empty spectrum states remain explicitly **NO DATA** until real samples exist
- Source selection and navigation are interface actions only; selecting a module does not start acquisition
- Sessions empty state accurately explains that SQLite exists in the backend but is not yet connected to the interface

See [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) and [docs/brand/](docs/brand/).

## Build and tests

Requirements: JDK 17 and Android SDK platform/build tools for API 35.

Desktop build:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
```

Termux / Debian proot build:

```bash
./tools/build.sh testDebugUnitTest assembleDebugAndroidTest
```

Run the Android instrumentation tests on a connected device/emulator:

```bash
./tools/build.sh connectedDebugAndroidTest
```

The debug APK is normally produced at `app/build/outputs/apk/debug/app-debug.apk`. A successful build or Android-test APK compilation does not mean instrumentation tests have executed. This branch's integration remains pending those checks.

## Staged roadmap

- [x] Buildable Android foundation and truthful empty-data states
- [x] Launcher identity, reusable build tooling, Observatory Glass UI
- [x] Deterministic DSP/detection foundation and traceable analysis records
- [x] SQLite persistence engine and Android instrumentation tests authored
- [ ] Verify integration build and execute device/emulator instrumentation tests
- [ ] Wire persistent sessions and findings into the UI
- [ ] Implement verified CSV export
- [ ] Implement camera-based optical observation with sampling limitations
- [ ] Implement real Wi-Fi/Bluetooth observations with permission and OS-version checks
- [ ] Implement USB host discovery and supported SDR adapter interface
- [ ] Integrate verified SDR spectrum/IQ acquisition
- [ ] Multi-device session correlation, accessibility/font-scaling checks, device testing, and release verification

## Accuracy, safety, and privacy

Use ZR 4 only with devices and radio environments you are authorized to observe. Respect local radio regulations and privacy. Do not claim frequency, power, dBm, bandwidth, noise-floor, or calibration values unless the actual source supports them. Record calibration and uncertainty where relevant.

**Status:** consolidated core branch under verification. A feature is not considered complete until its code, tests, and relevant device/hardware checks are verified.
