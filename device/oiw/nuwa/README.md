# device/oiw/nuwa — Path A status: UNBLOCKED (use the real community trees)

**Status change (2026-07-06):** the original claim that no `nuwa` device/vendor tree exists was **wrong** and has
been corrected (`assumptions.md` A-3). A complete, verified, community-maintained chain exists:

| target_path | repository | branch |
|---|---|---|
| `device/xiaomi/nuwa` | `LineageOS/android_device_xiaomi_nuwa` (official LineageOS org) | `lineage-23.2` |
| `device/xiaomi/sm8550-common` | `LineageOS/android_device_xiaomi_sm8550-common` | `lineage-23.2` |
| `hardware/xiaomi` | `LineageOS/android_hardware_xiaomi` | `lineage-23.2` |
| `kernel/xiaomi/sm8550` | `LineageOS/android_kernel_xiaomi_sm8550` (Xiaomi OSS/CLO-derived) | `lineage-23.2` |
| `kernel/xiaomi/sm8550-devicetrees` | `LineageOS/android_kernel_xiaomi_sm8550-devicetrees` | `lineage-23.2` |
| `kernel/xiaomi/sm8550-modules` | `LineageOS/android_kernel_xiaomi_sm8550-modules` (incl. camera-kernel techpack **source**) | `lineage-23.2` |
| `vendor/xiaomi/nuwa` | `TheMuppets/proprietary_vendor_xiaomi_nuwa` (git-LFS blobs) | `lineage-23.2` |
| `vendor/xiaomi/sm8550-common` | `TheMuppets/proprietary_vendor_xiaomi_sm8550-common` (git-LFS) | `lineage-23.2` |

Each row was verified by fetching the repo and walking the `lineage.dependencies` chain
(`nuwa` → `sm8550-common` → the four hardware/kernel repos).

## Do not hand-write a device tree here

This directory intentionally contains **no** BoardConfig/device.mk scaffolding anymore: writing a parallel tree
when an actively-maintained official one exists would be pure liability. OIW-ROM consumes the real trees via
`manifests/oiw_nuwa.xml` and layers its cinema packages on top through `vendor/oiw/oiw.mk`
(`$(call inherit-product-if-exists, vendor/oiw-rom/vendor/oiw/oiw.mk)` in `lineage_nuwa.mk`, or keep the image
stock and sideload the apps).

## Build

See `tools/build_full_rom.sh` (host prep → `repo init -b lineage-23.2 --git-lfs` → local manifest → sync →
optional OIW app staging → `brunch nuwa`) and `docs/BUILD_SYSTEM.md` §Path A.

## sepolicy/

`sepolicy/README.md` still documents the OIW domains that would accompany promoting the OIW services from in-app
logic to real system services under Path A — that work is now *possible* (framework/sepolicy source is in the
tree) but remains a post-MVP item; the apps run fine in `untrusted_app` on a Lineage build.
