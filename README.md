# Xiaomi_13Pro_SukiSU-Ultra

This is a repo for building kernel for Xiaomi 13 Pro (nuwa) with SukiSU, SUSFS and KPM. This is based on CRDROID Xiaomi SM8550 Kernel source, but **only works on HyperOS roms.**

---

## One Inch Wonder (OIW-ROM) — Cinema Camera Layer

This repository also hosts **One Inch Wonder (OIW-ROM)**: a cinema-camera-first software layer built on top of this
project's rooted GKI kernel (SukiSU Ultra) and the Xiaomi 13 Pro's (nuwa) stock HyperOS firmware and Sony IMX989
1"-type main sensor. OIW-ROM turns the phone into a purpose-built pocket cinema camera: manual capture app, camera-first
launcher, capture profiles, LUT/monitoring tools, thermal/storage safety services, and field tooling.

Because Xiaomi has not released an AOSP device tree, vendor tree, or full kernel source for `nuwa`, OIW-ROM is built as
a **Hybrid Vendor + App-First Cinema Layer** (see [`assumptions.md`](assumptions.md) and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full reasoning and the five candidate build paths). Start here:

| Doc | Purpose |
|---|---|
| [`assumptions.md`](assumptions.md) | Every assumption made, confidence, and what you must confirm on real hardware |
| [`implementation_plan.md`](implementation_plan.md) | Ordered milestones, dependency graph, acceptance criteria |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Full system architecture across all layers |
| [`docs/DEVICE_BRINGUP.md`](docs/DEVICE_BRINGUP.md) | Device identification, partitions, bring-up checklist |
| [`docs/CAMERA_PIPELINE.md`](docs/CAMERA_PIPELINE.md) | Sensor/HAL/Camera2/RAW/codec strategy |
| [`docs/CINEMA_FEATURES.md`](docs/CINEMA_FEATURES.md) | Every filmmaker-facing feature |
| [`docs/BUILD_SYSTEM.md`](docs/BUILD_SYSTEM.md) | How to build the kernel, apps, and package a release |
| [`docs/SECURITY_AND_RECOVERY.md`](docs/SECURITY_AND_RECOVERY.md) | SELinux/root posture, recovery, anti-brick |
| [`docs/THERMAL_POWER.md`](docs/THERMAL_POWER.md) | Thermal modes, governors, long-take stability |
| [`docs/STORAGE_MEDIA.md`](docs/STORAGE_MEDIA.md) | Internal/external storage, dropped-frame prevention |
| [`docs/AUDIO_TIMECODE.md`](docs/AUDIO_TIMECODE.md) | Audio sources, sync, timecode tiers |
| [`docs/UI_UX.md`](docs/UI_UX.md) | Launcher, camera UI, error UX |
| [`docs/API_PLUGIN_FRAMEWORK.md`](docs/API_PLUGIN_FRAMEWORK.md) | Profile/LUT/overlay/exporter plugin system |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Full test matrix |
| [`docs/RED_TEAM_AUDIT.md`](docs/RED_TEAM_AUDIT.md) | Failure modes and mitigations |

### OIW-ROM feature matrix (current status)

| Subsystem | Status | Path |
|---|---|---|
| Rooted GKI kernel (SukiSU Ultra / SUSFS / KPM) | **Working** (this repo, existing CI) | N/A |
| OIWCamera app (Camera2 manual control, recording, sidecars) | **Skeleton implemented** | D — App-first |
| OIWLauncher (camera-first launcher) | **Skeleton implemented** | D — App-first |
| Capture profiles / LUT index / button maps | **Implemented** (JSON) | D — App-first |
| Field tooling (capability dump, storage/thermal benchmarks) | **Implemented** (scripts) | D — App-first |
| OIW cinema kernel (flashable boot Image via CI) | **Verified & wired into CI** as the `nuwa-oiw` build variant — adds NTFS3/UDF footage-drive support + `-oiw-cinema` localversion; validated against real kernel source (`docs/BUILD_SYSTEM.md` §1). GKI limits mean the kernel can't change camera/ISP behavior. | B — Hybrid vendor (kernel) |
| Full custom ROM (LineageOS 23.2 + OIW layer) | **UNBLOCKED — buildable.** Verified community device/vendor/kernel chain (official LineageOS `nuwa` trees + TheMuppets blobs); manifest + end-to-end build script shipped (`manifests/oiw_nuwa.xml`, `tools/build_full_rom.sh`). First compile requires a real build host (~400 GB disk) — user action. | A — Full custom ROM (primary track) |
| RAW sensor video / CinemaDNG | **Research-possible under Path A** — kernel camera driver builds from source (`sm8550-modules` techpack); Lineage Camera2 typically exposes more than HyperOS. Verify with capability dump on the Lineage build. | A (research) |
| External monitor (HDMI/DP) | **Hardware limit confirmed:** USB 2.0 port, no DP alt mode. Workarounds shipped: UVC webcam-mode (AOSP 14 DeviceAsWebcam, Path A), scrcpy over USB, wireless cast. | See `docs/CINEMA_FEATURES.md` §7 |

### ⚠️ Flashing and modification warnings

- Everything in this project assumes an **unlocked bootloader**, a device you own, and acceptance of warranty/root
  risk from installing SukiSU Ultra as documented in the base project above.
- OIWCamera/OIWLauncher are ordinary Android apps and vendor/kernel config fragments — flashing the kernel carries the
  same risk as the base SukiSU Ultra kernel it is built from. Keep a stock boot image backup before flashing.
- Nothing in OIW-ROM disables verified boot or SELinux enforcement in release builds. See
  [`docs/SECURITY_AND_RECOVERY.md`](docs/SECURITY_AND_RECOVERY.md).

### Quick start

```
# 1. Flash/keep the SukiSU Ultra GKI kernel from this repo's existing CI (see Kernel/configs/nuwa-13.config.json)
# 2. Build and install the cinema apps
cd packages/apps/OIWCamera && ./gradlew assembleDebug
cd packages/apps/OIWLauncher && ./gradlew assembleDebug
adb install packages/apps/OIWCamera/app/build/outputs/apk/debug/app-debug.apk
adb install packages/apps/OIWLauncher/app/build/outputs/apk/debug/app-debug.apk

# 3. Dump camera capabilities on your actual unit before trusting any capture profile
python3 tools/camera_capability_dump.py --serial <adb-serial> --out camera_dump.json

# 4. Benchmark storage before enabling high-bitrate profiles
tools/storage_benchmark.sh /sdcard/OIW_MEDIA
```

> [!WARNING]
> GKI kernel source code used here is compatible for all the Xiaomi SM8550 devices, but this repo was created only for testing on devices that I have, the kernel built may not suitable for using on other devices.

---

### Proceed at your own risk!

---

# MUST HAVE Modules for Xiaomi 13 Pro (nuwa) with SukiSU Ultra
- **SukiSU Ultra**: Developed by [udochina](https://github.com/SukiSU-Ultra/SukiSU-Ultra).
- **SUSFS Module**: Developed by [sidex15](https://github.com/sidex15).
- **Fix Signal Oneplus 13 China with OSS**: Developed by [K58](https://github.com/K58/fix-signal-oneplus13/releases).
- **PlayIntegrityFix**: Developed by [chiteroman](https://github.com/chiteroman/PlayIntegrityFix/releases).
- **ZygiskNext**: Developed by [Dr-TSNG](https://github.com/Dr-TSNG/ZygiskNext/releases).
- **Zygisk-Assistant**: Developed by [snake-4](https://github.com/snake-4/Zygisk-Assistant).
- **magisk-module-wifi7**: Developed by [AndroPlus-org](https://github.com/AndroPlus-org/magisk-module-wifi7/releases).
- **liboemcryptodisabler**: Developed by [hzy132](https://github.com/hzy132/liboemcryptodisabler/releases).
---
# Features

- **KernelSU**: KernelSU is a root solution for Android GKI devices, it works in kernel mode and grants root permission to userspace applications directly in kernel space.
- **SUSFS**: An addon root hiding kernel patches and userspace module for KernelSU.

---

# Credits
- **SukiSU Ultra**: Developed by [udochina](https://github.com/SukiSU-Ultra/SukiSU-Ultra).
- **KernelSU**: Developed by [tiann](https://github.com/tiann/KernelSU).
- **KernelSU-Next**: Developed by [rifsxd](https://github.com/KernelSU-Next/KernelSU-Next).
- **Magic-KSU**: Developed by [5ec1cff](https://github.com/5ec1cff/KernelSU).  
- **SUSFS**: Developed by [simonpunk](https://gitlab.com/simonpunk/susfs4ksu.git).
- **SUSFS Module**: Developed by [sidex15](https://github.com/sidex15).
