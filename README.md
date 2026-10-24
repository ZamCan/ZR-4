# ZR-4 — ZamCan RF Observatory

**ZR-4** is the working project code for **ZamCan Radio Frequency Observatory**, an offline-first native Android application for observing and recording supported optical and wireless measurements.

## Project principles

- Real observations only: never present simulated values as live measurements.
- Clear measurement provenance: every observation identifies its source and limitations.
- Offline-first storage with local session history and CSV export.
- Modular architecture for Android Wi-Fi/Bluetooth observations, camera-based optical observations, USB host devices, and compatible external SDR receivers.
- Native Android/Kotlin interface designed for staged development and verification.
- No RF transmission, token generation, or bypass of device security.

## Measurement scope

1. **Optical observations:** camera-assisted observation of visible indicators, brightness changes, and blink timing within camera sampling limits. A camera does not detect RF.
2. **Built-in radios:** measurements actually exposed by Android Wi-Fi and Bluetooth APIs. These are discrete/API-limited observations, not a calibrated continuous spectrum.
3. **External SDR:** spectrum/IQ measurements only when a compatible receiver is connected and its supported range and capabilities are confirmed.
4. **Session synchronization:** consent-based exchange or correlation of recorded measurements; synchronization does not increase receiver sensitivity or frequency coverage.

## Development

- Android application: Kotlin
- UI: Jetpack Compose
- Local persistence: planned SQLite/Room-backed session storage
- Minimum Android API: 26 (initial target; validate device compatibility during implementation)
- Compile SDK: 35

Build with a compatible Gradle installation:

```bash
gradle assembleDebug
```

The debug APK, when the build succeeds, is normally produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Staged roadmap

- [ ] Stage 1: buildable Android foundation and measurement dashboard
- [ ] Stage 2: camera-based optical observation
- [ ] Stage 3: real Wi-Fi and Bluetooth observations with correct permissions
- [ ] Stage 4: measurement calculations, plots, and unit tests
- [ ] Stage 5: local session storage and CSV export
- [ ] Stage 6: USB host discovery and documented SDR adapter interface
- [ ] Stage 7: compatible SDR spectrum/IQ pipeline, only with verified hardware support
- [ ] Stage 8: multi-device session correlation and release verification

## Safety and accuracy

Use the app only with devices and radio environments you are authorized to observe. Respect local radio regulations and privacy. Frequency, power, dBm, bandwidth, and noise-floor values must not be claimed unless the actual data source supports them; calibration and uncertainty must be recorded where relevant.

**Status:** initial repository foundation. Features are not considered implemented until their code, tests, and build results are verified.
