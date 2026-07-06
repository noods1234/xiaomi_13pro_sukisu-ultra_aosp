# device/oiw/nuwa — Path A scaffold (BLOCKED)

**Status: blocked, template-only.** This directory is a placeholder AOSP device-tree layout for Xiaomi 13 Pro
(`nuwa`), kept here so that Path A (Full Custom ROM, see `docs/ARCHITECTURE.md` §2) can be picked up quickly if a
real device/vendor tree or leaked/donated proprietary source ever becomes legitimately available. **Nothing in this
directory is currently buildable.** Do not attempt to fill it in with values guessed from binary vendor partitions —
that is a common brick vector (mismatched BoardConfig partition sizes, wrong A/B handling, wrong DTBO layout).

## Why this is blocked

Per `assumptions.md` A-3: Xiaomi has not published an AOSP-compatible device tree, vendor tree, or full kernel
source for `nuwa`. The only source basis this project has is the community GKI kernel source referenced in
`Kernel/configs/nuwa-13.config.json` (`crdroidandroid/android_kernel_xiaomi_sm8550`), which is sufficient to build a
bootable, rooted **kernel**, but is not sufficient to compile an AOSP framework/vendor image — that requires a real
`BoardConfig.mk`, partition layout, HAL manifests, and vendor blobs matched to this exact device's proprietary
build, none of which are legally available to this project.

## What real device bring-up would require, file by file

| File | Purpose | What's needed to fill it in for real |
|---|---|---|
| `AndroidProducts.mk` | Registers product makefiles with the build system | A real AOSP/LineageOS build tree to register against |
| `BoardConfig.mk` | Partition sizes, A/B config, kernel/DTB build flags | Exact partition table from `fastboot getvar all` / `/proc/partitions` on a real unit, plus confirmation of DTB/DTBO handling scheme |
| `device.mk` / `lineage_nuwa.mk` | Product package list, overlays | A working AOSP/LineageOS tree to build against |
| `vendorsetup.sh` | `lunch` target registration | Same |
| `init.oiw.*.rc` | Custom init triggers for camera/storage/thermal services | Only meaningful once there's a real init/vendor tree to hook into — the Path D equivalent is implemented as in-app logic (`docs/ARCHITECTURE.md` §4) |
| `fstab.oiw` | Mount table | Real partition names verified per `docs/DEVICE_BRINGUP.md` §3 |
| `ueventd.oiw.rc` | Device node permissions | Real device node list from a live unit (`adb shell ls -l /dev`) |
| `recovery.fstab` | Recovery mount table | Same as fstab, for recovery context — moot without a custom recovery (none planned, see `docs/ARCHITECTURE.md` §5) |
| `sepolicy/` | Custom domains | See `sepolicy/README.md` in this directory |
| `overlay/` | Framework resource overlays | Needs a real product/vendor overlay build target |

## Trigger to revisit

Re-run the path-selection matrix in `docs/ARCHITECTURE.md` §2 if any of the following becomes true:
1. Xiaomi (or a credible community reverse-engineering effort) publishes a `nuwa` device/vendor tree.
2. A `nuwa-*-oss` branch appears in `noods1234/xiaomi_kernel_opensource` (checked periodically, see
   `assumptions.md` A-3).
