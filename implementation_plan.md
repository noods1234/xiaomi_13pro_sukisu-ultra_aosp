# OIW-ROM Implementation Plan

Ordered, dependency-aware milestone plan. Each milestone lists acceptance criteria and known unknowns. Phases map to
`docs/RED_TEAM_AUDIT.md` risk IDs where relevant.

## Dependency graph (text form)

```
P1 Recon ──> P2 Build Base ──> P3 Camera Viability ──> P4 OIW Services ──> P5 OIWCamera MVP
                                                              │                   │
                                                              └──────> P6 Cinema Tools ──> P7 Reliability ──> P8 Polish ──> P9 Release
```

P2 (kernel config fragment work) can run in parallel with P3–P5 (app-layer work) since they touch different repos and
have no code dependency — only a documentation cross-reference.

## Phase 1 — Reconnaissance (in progress; automatable, no hardware yet touched)

- [x] Identify device/SoC/kernel baseline from existing repo content (`assumptions.md` A-1..A-4).
- [ ] **User action:** run `tools/camera_capability_dump.py` on a real unit → produces `camera_dump.json`.
- [ ] **User action:** run `tools/storage_benchmark.sh` against internal storage and a candidate external SSD.
- [ ] **User action:** run `tools/thermal_monitor.sh` idle baseline for 10 minutes.
- Acceptance: three JSON/CSV artifacts exist and are attached to the project tracker; `docs/CAMERA_PIPELINE.md` §1
  updated with real values (not placeholders).

## Phase 2 — Build base

- [x] Confirm existing kernel CI (`Kernel/configs/nuwa-13.config.json`) builds a flashable SukiSU Ultra boot image
      (pre-existing; left byte-for-byte untouched).
- [x] Add the **verified** cinema kernel delta `kernel/oiw/nuwa/configs/oiw_cinema.config` and wire it into CI as a
      dedicated `nuwa-oiw` build-matrix variant (`Kernel/configs/nuwa-oiw.config.json`) plus a gated
      `Inject OIW cinema kernel tuning` step in `.github/workflows/SukiSU_SUSFS.yml`. Verified locally against real
      source that `NTFS3_FS`/`UDF_FS`/`-oiw-cinema` survive `make gki_defconfig` (`docs/BUILD_SYSTEM.md` §1).
- [ ] **User action:** trigger the `SukiSU_SUSFS` workflow (`workflow_dispatch`), confirm the `nuwa-oiw-cinema`
      AnyKernel3 artifact builds, flash it, and verify `uname -r` shows `-oiw-cinema` and an NTFS-formatted external
      SSD mounts read/write.
- Acceptance: cinema kernel CI produces a bootable, flashable image; `dmesg` shows no new probe failures versus the
  stock `nuwa-13` baseline.

## Phase 3 — Camera viability

- [ ] Build and run `packages/apps/OIWCamera` debug variant; confirm preview renders for every camera ID reported
      by the capability dump.
- [ ] Confirm each manual control key (exposure, sensitivity, WB, focus) round-trips through `TotalCaptureResult`.
- [ ] Attempt a RAW_SENSOR capture request; record pass/fail in `docs/CAMERA_PIPELINE.md`.
- [ ] Run a 4K/log-equivalent high-bitrate MediaCodec recording for 60 seconds; verify playback integrity.
- Acceptance: `docs/TEST_PLAN.md` §Camera Tests all pass or have a logged, explained failure.

## Phase 4 — OIW services (in-app, since no system-service injection point exists per assumption B-3)

- [x] `StorageManager` (preflight, benchmark hook, safe naming, segmentation) — implemented in-app.
- [x] Thermal awareness (`ThermalMonitor` reading `/sys/class/thermal` via root, throttle policy) — implemented in-app.
- [x] Metadata/sidecar writer — implemented in-app.
- [x] External control socket service (local-only, pairing-token gated, disabled by default) — `control/ExternalControlServer.kt`.

## Phase 5 — OIWCamera MVP

- [x] Preview + manual control panel skeleton.
- [x] Recording pipeline core: Camera2 → MediaCodec → MediaMuxer with synchronized A/V tracks (`Recorder` +
      `AudioCapture`), session wiring (`CaptureSessionCoordinator`), overlay compositor (`OverlayView`).
- [ ] **Stub audit 2026-07-06 — written but NOT yet wired into the recording flow** (each is a small,
      concrete wiring task, not missing functionality): sidecar write on segment finalize (`MetadataWriter`
      exists, no caller); dropped-frame + audio-meter UI indicators (listener bodies empty); segment rollover
      trigger (`rolloverSegmentIfNeeded` never called — needs a byte/time tick); storage preflight gate before
      record start (`preflight` never called); thermal graceful-stop loop (`ThermalMonitor.decide` never polled
      during recording); `StatusFileWriter`, `RecordingService` (lock-screen recording), `DngStillCaptor` (no
      still-button flow), `ExternalControlServer` (no `CommandHandler` impl / not instantiated); still + profile
      buttons have no onClick; button-mapping JSON not loaded (no KeyEvent interception); `TotalCaptureResult`
      sampling not wired (`onCaptureResultSample` never invoked); `writerQueue` field is vestigial (drain loop
      writes directly — remove or implement the bounded queue).
- [ ] **Known bug (found in stub audit):** media root resolves to `/sdcard/Android/data/OIW_MEDIA`
      (`getExternalFilesDir(null).parentFile.parentFile`), not the documented `/sdcard/OIW_MEDIA` — and
      Android 11+ blocks cross-app reads of `Android/data`, so OIWLauncher cannot read the status file there.
      Fix: use `Environment.getExternalStorageDirectory()`-rooted `OIW_MEDIA` via MediaStore/SAF, or move the
      status handshake to a `ContentProvider`. Affects `StorageManager`, `StatusFileWriter`,
      `LauncherStatusReader`, `ProfileRepository` user-profile dir.
- [x] Capture profile loader/schema.
- [x] JSON sidecar metadata writer.
- [ ] **User action:** field-test one full capture profile end to end on real hardware and confirm no dropped frames
      over a 10-minute take.

## Phase 6 — Cinema tools

- [x] Histogram overlay (real luma histogram from preview `ImageReader` frames).
- [x] Zebra overlay (real luma-threshold highlight).
- [x] Focus peaking overlay (real Sobel-edge highlight).
- [ ] Waveform/RGB parade/vectorscope — **phased**, not implemented in this pass: real-time waveform/vectorscope
      needs a GPU compute path (RenderScript is deprecated on this API level; the replacement is a custom OpenGL ES
      compute shader or Vulkan compute pipeline) to hit frame rate on preview-resolution frames. Implementation plan
      is written in `docs/CINEMA_FEATURES.md` §Monitoring Tools; this is not "future work" hand-waving — it is gated
      on writing and profiling a GPU shader, which is a distinct, schedulable unit of work.
- [x] LUT parsing + trilinear sampling (`CubeLutParser`, unit-tested). **On-preview application is NOT built**
      — needs the GL shader path (same GPU work item as waveform below). Previously over-marked as done.
- [ ] Anamorphic desqueeze preview toggle — **not implemented** (needs `TextureView.setTransform` wiring).
      Previously over-marked as done.
- [x] Frame guides / safe area overlays.

## Phase 7 — Reliability

- [ ] **User action:** long-take tests at 1/10/30/60 minutes (`docs/TEST_PLAN.md` §Recording Tests).
- [ ] **User action:** thermal soak test matrix (`docs/TEST_PLAN.md` §Thermal Tests).
- [x] Crash-recovery index format defined (`docs/STORAGE_MEDIA.md` §Crash Recovery).
- [ ] **User action:** forced-kill recovery test — kill `-9` the app mid-recording, confirm partial file finalization.

## Phase 8 — Polish

- [x] OIWLauncher skeleton (camera-first home, quick tiles, boot-to-camera).
- [ ] Settings/profile editor UI (beyond JSON hand-editing) — stretch, not blocking MVP.
- [ ] File browser / clip review screen — stretch.

## Phase 9 — Release

- [ ] Signed release build of both apps (see `docs/BUILD_SYSTEM.md` §Signing).
- [ ] Full `docs/TEST_PLAN.md` matrix green.
- [ ] `docs/RED_TEAM_AUDIT.md` mitigations verified against real hardware behavior, not just design review.
- [ ] Tag `oiw-rom-v0.1.0`.

## Phase 2A — Full ROM build (Path A, added 2026-07-06 after A-3 was corrected)

- [x] Verify the complete community device/vendor/kernel chain repo-by-repo (`device/oiw/nuwa/README.md` table).
- [x] Ship `manifests/oiw_nuwa.xml` (verified local manifest incl. TheMuppets git-LFS blobs).
- [x] Ship `tools/build_full_rom.sh` (init → sync → OIW app staging → `brunch nuwa`).
- [x] Ship `vendor/oiw/oiw.mk` + `vendor/oiw/Android.bp` (OIW apps/configs baked into the image as
      product-partition prebuilts; OIWLauncher overrides the stock launcher).
- [ ] **User action (needs a ~400 GB build host):** run the script, add the one-line
      `inherit-product-if-exists` to `lineage_nuwa.mk`, flash per the official Lineage install flow.
- [ ] **User action:** run `tools/camera_capability_dump.py` on the Lineage build and diff against the HyperOS
      dump — Lineage's Camera2 stack frequently exposes more (RAW, manual keys) than HyperOS.

## Known unknowns / user actions remaining (see `assumptions.md` for detail)

1. Whether `RAW_SENSOR` is exposed (A-7, C-3) — now testable on BOTH stock HyperOS and the Path A Lineage build;
   the Lineage build is the more promising of the two.
2. ~~DP Alt Mode~~ **resolved: hardware limit confirmed (USB 2.0 port)** — workarounds shipped
   (DeviceAsWebcam UVC / scrcpy / cast, `docs/CINEMA_FEATURES.md` §7). External SSD bandwidth similarly capped —
   profiles corrected (A-5/A-6).
3. ~~Whether a `nuwa` device/vendor tree exists~~ **resolved: it exists and is verified** (A-3 corrected);
   remaining unknown is only first-compile confirmation on a real build host.
