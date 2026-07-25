# OIW-ROM Critical Audit — 2026-07-06

Adversarial re-read of the OIWCamera code (beyond the earlier stub audit), looking for real defects.
Severity: **High** (blocks or corrupts core function), **Medium** (wrong behavior / leak), **Low** (latent / hardening).

## Fixed in this pass

| # | Sev | Finding | Fix |
|---|---|---|---|
| A | High | **A/V sync drift.** Audio AAC PTS used `System.nanoTime()` sampled *at each read*, folding the audio-buffer latency into a jittery, uneven timeline while video PTS came from the camera surface clock. | `AudioCapture` now derives PTS from a monotonic **sample counter anchored at the first buffer** — exactly-spaced, drift-free. Remaining device-dependent caveat (nanoTime vs camera `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME`) is now called out as a bring-up check, not silently assumed. |
| B | High | **Frame rate never locked.** `buildManualRequest` set exposure/ISO/AWB-off but never `SENSOR_FRAME_DURATION`. With AE off the sensor cadence is undefined — a 24p profile could capture at ~30fps (wrong motion cadence). | Sets `SENSOR_FRAME_DURATION = 1e9/fps`, clamped ≥ exposure and ≤ `SENSOR_INFO_MAX_FRAME_DURATION`, with an explicit warning if adjusted. |
| C | High | **Recording could never start in the default flow.** `storage_benchmark.sh` prints JSON to *stdout*; the app read `OIW_MEDIA/benchmarks/<target>.json`, which nothing ever created → `preflight` always refused. | Added a real in-app `StorageManager.runAndStoreBenchmark()` (buffered write + `fsync`, writes the file the app reads). `CameraActivity` benchmarks on first record if none exists, then preflights. `preflight` stays pure/testable (refuses on truly-absent data; caller supplies it). |
| D | Medium | **Anamorphic desqueeze pivot wrong.** `applyDesqueeze` used `textureView.width` at wire time (0 before layout), offsetting the stretch instead of centering it. | Deferred to `textureView.post { … }` so the pivot uses the laid-out width. |
| G | Medium | **Thermal-poll leak.** The 5-s `thermalTick` Runnable was never cancelled in `onDestroy` — leaked the Activity and kept firing after teardown. | `removeCallbacks(thermalTick)` + `coordinator.release()` + `analysisThread.quitSafely()` in `onDestroy`. |
| H | Medium | **Profile-switch resource leak.** `cycleProfile` → `wireSessionWhenSurfaceReady` created a new `HandlerThread` and added another `OverlayView` to the container every switch, never releasing the old ones (stacked overlays, thread leak). | Re-wire now `quitSafely()`s the prior analysis thread and `removeAllViews()` on the overlay container first. |

## Found, NOT yet fixed (flagged honestly — tracked, not hidden)

| # | Sev | Finding | Plan |
|---|---|---|---|
| I | Medium | The on-first-use benchmark (fix C) runs on the **UI thread** in `toggleRecording` (~1 s stall on a 64 MB write). Functionally correct, bad UX. | Move to a coroutine on `Dispatchers.IO`; show a spinner. |
| ~~J~~ | Medium | **FIXED.** `LtcEncoder` rewritten with phase-accurate fractional bit timing (anchored to a global bit counter, drift-free); encoder/decoder `frameRate` is now `Double`. Round-trip tests pass at 44100/25, 44100/30, and 48000/29.97 drop-frame. |
| ~~K~~ | Low | **FIXED.** `open()` guards all three StateCallback paths with an `AtomicBoolean` (also fixes a latent hang: `onDisconnected` during open never resumed the coroutine before). |
| ~~L~~ | Low | **FIXED.** Pairing token compared with `MessageDigest.isEqual` (constant-time). Verified compiles in the pure-logic harness. |
| M | Low | `StatusProvider` is `exported=true` with no permission — any app can read storage/thermal/battery/profile summary. Non-sensitive by design, but it is world-readable. | Acceptable for v1; documented. Could gate behind a signature permission shared with OIWLauncher. |
| N | Low | `kelvinToGains` is a coarse approximation (documented as such), not colorimetrically validated; green channel handling is simplistic. | Validate against a gray card on real hardware (Phase 1 recon); consider a measured per-sensor matrix. |

## Method / status

Pure-logic classes (11 files) compiled with Kotlin 1.9.24 and the 20-test suite re-run on JDK 21 after
these edits — still green (the fixes above are in Android-framework-coupled files that compile at
`./gradlew assembleDebug`, the documented next gate; the audit changes were reviewed, not device-run).
This document is the standing defect register — new findings append here rather than being silently fixed.
