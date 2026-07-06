# OIW-ROM Architecture

## 1. Purpose

One Inch Wonder (OIW-ROM) converts a rooted Xiaomi 13 Pro (`nuwa`) running stock HyperOS + the SukiSU Ultra GKI
kernel (this repo) into a purpose-built cinema camera. This document describes the full layered architecture: what
runs where, why, and which of the five candidate build paths each layer uses.

## 2. Build path selection matrix

| Path | Description | Feasibility here | Risk | Required files | Effort | Feature ceiling | Recovery path | Test plan |
|---|---|---|---|---|---|---|---|---|
| **A — Full custom ROM** (AOSP/Lineage device tree + vendor + kernel + apps) | Compile a full AOSP-derived ROM against a real `nuwa` device/vendor tree. | **Blocked.** No `nuwa` device/vendor tree is published by Xiaomi; the paired `xiaomi_kernel_opensource` repo has no `nuwa-*-oss` branch (assumption A-3). | Highest — full framework patching, easiest to brick, largest surface area. | `device/oiw/nuwa/*`, `vendor/oiw/nuwa/*`, full AOSP manifest | Very high (months, requires leaked/donated proprietary sources) | Highest (full HAL/framework control) | Standard AOSP recovery/fastboot | `docs/TEST_PLAN.md` full matrix |
| **B — Hybrid vendor ROM modification** | Debloat stock HyperOS via root; ship overlays/Magisk modules/custom launcher+camera app on top of stock vendor image. | **Partially used.** Root (SukiSU Ultra) is already required and present; OIW uses it for thermal-zone reads, systemless overlays, and future kernel config fragments. | Medium — root-level changes are reversible via Magisk/module removal. | Magisk/KernelSU modules, `kernel/oiw/nuwa/*` | Medium | Medium-high (root-gated OS tweaks, no camera HAL rewrite) | Uninstall module / re-flash stock boot image | `docs/TEST_PLAN.md` §Boot Tests |
| **C — GSI-based development** | Treble GSI + stock vendor partition, for early UI/storage/thermal testing. | **Not used.** `nuwa` HyperOS Treble compliance is unconfirmed and GSI camera support on GSIs is historically poor (stock camera HAL usually is not GSI-compatible for anything beyond basic YUV preview). | Medium | GSI image, vendor partition intact | Low-medium | Low (camera HAL usually degrades hard on GSI) | Re-flash stock system image | N/A — excluded |
| **D — App-first cinema layer** | Robust standalone camera app (`OIWCamera`) + launcher (`OIWLauncher`) on stock firmware, using public Camera2/MediaCodec APIs. | **Primary path used in this repo.** No device/vendor tree needed; works on any properly-rooted or even unrooted `nuwa` unit for the app layer itself (root only needed for thermal-zone reads and future kernel work). | Low — ordinary APK install/uninstall, fully reversible. | `packages/apps/OIWCamera/*`, `packages/apps/OIWLauncher/*` | Medium | Medium (bounded by what Camera2 + vendor exposes; no raw ISP/HAL rewrite) | `adb uninstall`, no system partition touched | `docs/TEST_PLAN.md` full app-layer matrix |
| **E — Experimental HAL shim** | Wrapper/shim around the existing camera HAL to expose more manual control/metadata without redistributing proprietary internals. | **Documented as a research track only**, not implemented. Requires disassembling/hooking a running proprietary HAL process (e.g., via `LD_PRELOAD`/Frida-style interposition against `android.hardware.camera.provider@2.x`/AIDL service), which is high-risk, easy to destabilize camera service, and blurs into HAL redistribution/tamper concerns we've been asked to avoid. | High — can crash `cameraserver`, hard to debug, brittle across HyperOS updates. | N/A (research spike only) | Very high | Potentially highest short of Path A | Force-stop/restart `cameraserver`, reboot | Manual exploratory testing only |

**Chosen strategy:** Path D as the primary, shippable layer; Path B for root-enabled system tweaks (thermal reads,
future kernel config); Path A and E are documented and gated behind explicit future unknowns (see `assumptions.md`
A-3). Path C is excluded as net-negative for a camera-first product.

## 3. Layer diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ UI / App layer                                                          │
│  OIWLauncher (HOME) ──launches──> OIWCamera (capture, monitoring, LUTs) │
└───────────────┬───────────────────────────────┬─────────────────────────┘
                │ Camera2 / MediaCodec / MediaMuxer│ Storage Access Framework /
                │ (public Android APIs)            │ MediaStore / raw File (root)
┌───────────────▼───────────────────────────────▼─────────────────────────┐
│ Android framework (stock HyperOS, unmodified — Path A blocked)          │
│  CameraService · MediaCodec/OMX-IL/Codec2 · SurfaceFlinger · AudioFlinger│
└───────────────┬───────────────────────────────┬─────────────────────────┘
                │ HIDL/AIDL camera provider        │ V4L2 / ALSA / codec2 HAL
┌───────────────▼───────────────────────────────▼─────────────────────────┐
│ Vendor layer (stock, proprietary, unmodified — no vendor tree available)│
│  Qualcomm camx/chi-cdk camera HAL · ISP firmware · vendor tags          │
└───────────────┬───────────────────────────────────────────────────────── ┘
                │
┌───────────────▼───────────────────────────────────────────────────────── ┐
│ Kernel layer (THIS REPO'S ACTUAL BUILD SURFACE)                         │
│  SukiSU Ultra GKI kernel (crdroid SM8550 source) + SUSFS + KPM           │
│  + OIW config fragment (documented, kernel/oiw/nuwa/) for thermal/media/│
│    USB storage tuning                                                    │
└───────────────┬───────────────────────────────────────────────────────── ┘
                │
┌───────────────▼───────────────────────────────────────────────────────── ┐
│ Hardware                                                                 │
│  Snapdragon 8 Gen 2 (SM8550) · Sony IMX989 1"-type sensor · USB-C ·      │
│  UFS storage · thermal sensors                                          │
└───────────────────────────────────────────────────────────────────────── ┘
```

The critical architectural fact: **everything above the kernel line except the app layer is stock, proprietary, and
unmodifiable without sources we don't have.** OIW-ROM's entire feature ceiling is bounded by what stock HyperOS
exposes through Camera2, MediaCodec, and root-readable `/sys` and `/proc` nodes.

## 4. Subsystem architecture

### 4.1 Camera pipeline
See `docs/CAMERA_PIPELINE.md`. Camera2 → `ImageReader` (preview analysis for overlays) + `Surface` (encoder input) →
`MediaCodec` (hardware AVC/HEVC encode) → `MediaMuxer` (MP4 container) → segmented file writer → JSON sidecar.

### 4.2 Media/storage pipeline
See `docs/STORAGE_MEDIA.md`. Preflight benchmark → capacity/time estimate → write → atomic sidecar → crash-recovery
index entry → optional hash verification.

### 4.3 Thermal/power pipeline
See `docs/THERMAL_POWER.md`. In-app `ThermalMonitor` polls `/sys/class/thermal/thermal_zone*/temp` (root read via
SukiSU Ultra `su`), maps to one of 7 OIW thermal modes, and drives Android's public thermal-aware knobs (screen
brightness cap, foreground-only operation, network radios off) plus a "graceful stop" state machine.

### 4.4 UI/app layer
See `docs/UI_UX.md`. OIWLauncher is the `HOME`/`DEFAULT` launcher; boots directly to a camera-ready state.
OIWCamera owns the record/monitor/profile experience.

### 4.5 Debug/recovery architecture
See `docs/SECURITY_AND_RECOVERY.md`. Recovery is bounded to what the base SukiSU Ultra project already provides
(stock recovery, fastboot, boot image backup/restore) since Path A recovery (custom recovery partition, A/B slot
management rewrite) is out of scope without a device tree.

## 5. Non-goals (explicit)

- Rewriting `cameraserver`, `camx`, or ISP firmware.
- Building or signing a full HyperOS system image.
- Providing a custom recovery (TWRP-equivalent) — none exists for this device in the open-source ecosystem consulted
  here; stock recovery + fastboot is the supported recovery path.
