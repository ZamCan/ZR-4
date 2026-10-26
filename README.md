# ZR 4 — ZamCan RF Observatory

**ZR 4** is the public app name. **ZamCan RF Observatory** is the formal product name.

ZR 4 is an offline-first native Android workspace for Android-reported Wi-Fi/Bluetooth metadata, imported normalized sample files, and compatible external SDR receivers. The engineering branch adds a user-configured RTL-TCP network path for real complex I/Q capture, local deterministic analysis, and a computed spectrum plot. Direct USB SDR and physical receiver/handset validation remain outstanding.

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
3. **External SDR:** a user-configured RTL-TCP endpoint can stream I/Q from a compatible remote RTL-SDR into local analysis. This is not automatic network discovery; direct USB acquisition and physical receiver validation remain pending.
4. **Measurement sessions:** create, inspect, and close local SQLite sessions; add explicitly labelled user field notes (never represented as measurements); inspect stored observations and findings; export session metadata through Android's document picker as CSV. The database stores metadata, not raw IQ/sample payloads.
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
- [x] GitHub Actions CI assembles the debug APK and runs JVM unit tests; emulator instrumentation is a separate manual workflow, and physical handset validation remains required
- [x] Compose session workspace wired to SQLite for create/list/inspect/close flows and non-measurement field notes
- [x] CSV metadata exporter and document-picker save flow, with JVM tests authored
- [x] Android Wi-Fi scan-result and bounded Bluetooth LE metadata capture adapters are wired to local sessions; physical-device validation remains required
- [x] Hardware-neutral SDR registry, RTL-TCP network capture, capture validation, complex-I/Q analysis and computed spectrum plot are implemented; remote hardware validation remains outstanding

Raw IQ/sample payloads are intentionally not stored in the metadata database. Unknown schema migrations fail explicitly rather than silently dropping data. Detector outputs are candidates, not proof of emitter identity. Spectrum levels are dBFS unless a calibrated acquisition path establishes another unit.

## Observatory Glass interface

- Premium glassmorphic panels with a restrained aurora background; no blur-library dependency
- Instrument gold for primary actions/identity, mint for ready/local states, amber for hardware-gated modules, and warm stone for records
- Custom Canvas line icons, branded PNG launcher assets, compact pinned header, floating bottom navigation, responsive source panels, and Overview / Sources / Sessions / Settings / About sections
- Complete light + dark themes from one centralized token system (`ZrTheme.kt`): System / Light / Dark appearance in Settings, persisted on-device; the spectrum plot keeps a dark instrument screen in both themes
- Native light/dark splash (API 31+ splash resources, themed window background on earlier versions) and a true single-colour monochrome launcher icon for Android themed icons
- System Back returns to Overview; the last-used RTL-TCP endpoint is remembered as the SDR capture default
- Empty spectrum states remain explicitly **NO DATA** until real samples exist; imported or network-captured I/Q produces a computed dBFS spectrum frame (not a live continuous waterfall)
- Source selection and navigation are interface actions only; selecting a module does not start acquisition
- Session workspace uses SQLite for persisted session workflows and offers CSV export; no placeholder measurements are shown

See [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) and [docs/brand/](docs/brand/). See [docs/ANALYSIS-ARCHITECTURE.md](docs/ANALYSIS-ARCHITECTURE.md) for the analysis-to-storage pipeline and acquisition boundaries.

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

The debug APK is normally produced at `app/build/outputs/apk/debug/app-debug.apk`. A successful build or Android-test APK compilation does not mean instrumentation tests have executed. Recent fast CI run [#40278617251](https://github.com/ZamCan/ZR-4/actions/runs/40278617251) passed APK assembly, JVM unit tests, and debug APK artifact upload on the related instrumentation-fix branch. This PR's exact head still needs its own fast CI result. Emulator instrumentation is intentionally manual because prior runs were excessively long; physical handset and RTL-SDR validation remain separate.

## Live device log capture

- Android Developer Options > Select debug app > ZR 4 only configures debugger attachment; it does not stream logs to GitHub or grant Termux privileged log access. Keep **Wait for debugger** off for normal testing.
- If ADB is already authorized and connected, start a capture before reproducing a problem: `adb logcat -v time > zr4-logcat.txt`. Stop with Ctrl+C after the issue appears. For a focused live view use `adb logcat -v time | grep -iE 'AndroidRuntime|FATAL EXCEPTION|com.zamcan.zr4|ZR4'`.
- On the same Android phone, Termux's `adb` client does not automatically get access to the phone's own system ADB service. If `adb devices` shows no device or `adb logcat` returns a permission error, do not uninstall the app or change signing; use Android's **Take bug report** option to capture a system log, or connect to an authorized ADB host later.
- GitHub Actions logs show build/test results from GitHub's runner, not live logs from the physical phone. Upload only the relevant log excerpt and remove Wi-Fi names, addresses, tokens, and other private data first.

## Staged roadmap

- [x] Buildable Android foundation and truthful empty-data states
- [x] Launcher identity, reusable build tooling, Observatory Glass UI
- [x] Deterministic DSP/detection foundation and traceable analysis records
- [x] SQLite persistence engine and Android instrumentation tests authored
- [x] GitHub Actions build, JVM tests and APK artifact upload passed for the combined RTL-TCP branch (run #89); emulator instrumentation remains a separate manual run
- [ ] Validate Wi-Fi/BLE permission prompts and actual radio behavior on physical handsets
- [x] Wire persistent session workflows and findings into the UI
- [x] Implement CSV metadata export and exporter unit tests
- [ ] Implement camera-based optical observation with sampling limitations
- [x] Implement Android Wi-Fi scan snapshots and bounded BLE metadata observations with permission and OS-version checks; physical-device verification remains pending
- [x] Implement hardware-neutral SDR adapter contract and RTL-TCP network I/Q capture path
- [x] Connect validated captures to deterministic complex-I/Q analysis and computed spectrum plotting
- [ ] Implement direct USB host discovery and supported USB SDR adapters
- [ ] Multi-device session correlation, accessibility/font-scaling checks, device testing, and release verification

## Accuracy, safety, and privacy

Use ZR 4 only with devices and radio environments you are authorized to observe. Respect local radio regulations and privacy. Do not claim frequency, power, dBm, bandwidth, noise-floor, or calibration values unless the actual source supports them. Record calibration and uncertainty where relevant.

**Status:** RTL-TCP analysis integration is implemented online. The latest successful fast run is linked above, but this PR's exact head needs fresh fast CI. Emulator instrumentation and physical handset/receiver checks remain outstanding. A successful build does not prove real hardware acquisition.


## Local device validation and safe in-place updates

- Build the debug APK locally in the same Android/Termux environment that produced the installed debug app. Android debug builds use that environment's `~/.android/debug.keystore`; preserve it to keep the signing certificate stable.
- Do not install the APK downloaded from GitHub Actions over a locally signed debug app unless its signer certificate is verified to match. GitHub-hosted runners normally use a different debug keystore.
- Use `adb install -r app/build/outputs/apk/debug/app-debug.apk` for an in-place update only after verifying the application ID and signer. The application ID is `com.zamcan.zr4`.
- Capture device logs with `adb logcat`; selecting ZR 4 as the Developer Options debug app is optional and is only needed for wait-for-debugger workflows. Do not enable “Wait for debugger” for ordinary log capture.
- Current implementation does not yet have direct USB SDR acquisition, calibrated dBm measurements, or a live continuous waterfall. A configured RTL-TCP endpoint must be reachable and attached to a real receiver for physical testing.
