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
- [ ] External control socket service (local-only, pairing-token gated) — see `docs/API_PLUGIN_FRAMEWORK.md`.

## Phase 5 — OIWCamera MVP

- [x] Preview + manual control panel skeleton.
- [x] Recording pipeline skeleton (Camera2 → MediaCodec → MediaMuxer).
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
- [x] LUT preview (`.cube` parser + 3D LUT sampling on preview, never baked into recorded file).
- [x] Anamorphic desqueeze preview toggle.
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

## Known unknowns blocking full completion (see `assumptions.md` for detail)

1. Whether `RAW_SENSOR` is exposed on this device (A-7, C-3) — blocks Phase 3's RAW acceptance test until dumped.
2. Whether USB-C DP Alt Mode / high-speed external SSD works on this exact unit (A-5, A-6) — blocks Phase 6/7
   external-monitor and external-SSD acceptance tests until measured.
3. Whether a `nuwa` AOSP device/vendor tree ever becomes available (A-3) — blocks re-evaluating Path A entirely.
