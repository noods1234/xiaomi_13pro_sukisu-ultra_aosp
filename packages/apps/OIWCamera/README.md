# OIWCamera

Standalone Android app (Path D — App-First Cinema Layer, see `/docs/ARCHITECTURE.md`). Not an AOSP system app.

## First-time setup

The `gradle-wrapper.jar` binary is intentionally not hand-authored in this change (binary wrapper jars shouldn't be
committed without building/verifying them). Before building:

```sh
cd packages/apps/OIWCamera
gradle wrapper --gradle-version 8.7   # generates gradle/wrapper/gradle-wrapper.jar + gradlew/gradlew.bat
./gradlew assembleDebug
```

Requires a local Gradle install (any recent version) just for this one bootstrap step; every build after that uses
the pinned wrapper.

## Module map

| Path | Responsibility |
|---|---|
| `camera/CameraController.kt` | Camera2 device/session lifecycle, capability-gated manual controls |
| `capture/CaptureProfile.kt`, `ProfileRepository.kt` | Profile data model + bundled/user-plugin loader |
| `record/Recorder.kt`, `RecordingService.kt` | MediaCodec/MediaMuxer pipeline, foreground service |
| `metadata/MetadataWriter.kt` | Atomic JSON sidecar writer |
| `storage/StorageManager.kt` | Preflight, benchmarking hook, folder layout, crash recovery scan |
| `thermal/ThermalMonitor.kt` | Thermal-mode decision state machine (pure, unit-tested) |
| `overlay/` | Histogram, zebra, focus-peaking (real); see `docs/CINEMA_FEATURES.md` for waveform/vectorscope status |
| `lut/CubeLutParser.kt` | `.cube` LUT parsing + trilinear sampling |
| `ui/CameraActivity.kt` | MVP capture screen wiring |

See `/docs/CAMERA_PIPELINE.md`, `/docs/CINEMA_FEATURES.md`, `/docs/STORAGE_MEDIA.md`, `/docs/THERMAL_POWER.md` for
the design rationale behind each module.
