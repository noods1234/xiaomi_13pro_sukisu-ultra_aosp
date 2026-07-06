# OIW-ROM Device Bring-Up

## 1. Device identification process

Run these before doing anything else and save the output — every other doc references this data.

```sh
adb shell getprop ro.product.device        # expect: nuwa
adb shell getprop ro.product.model         # expect: 2210132C (or regional variant)
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.mi.os.version.code     # HyperOS version marker
adb shell getprop ro.boot.hardware
adb shell getprop ro.boot.flash.locked      # 0 = unlocked
adb shell cat /proc/version
adb shell cat /proc/cpuinfo | head -20
```

Save all output to `bringup/device_ident_<date>.txt` (create a local, untracked `bringup/` folder — do not commit
raw device dumps containing IMEI/serial to this repo).

## 2. Bootloader state

- Confirm unlock: `fastboot flashing get_unlock_ability` and `fastboot oem device-info` (reboot to bootloader first:
  `adb reboot bootloader`).
- **Do not** run any command that re-locks the bootloader once SukiSU Ultra is installed — this will trip verified
  boot against a patched boot image and can hard-brick without a matching stock boot image on hand.
- Keep a copy of the **original stock `boot.img`/`init_boot.img`** pulled before first patch, per the base SukiSU
  Ultra project's own instructions. This is your unbrick path (see `docs/SECURITY_AND_RECOVERY.md`).

## 3. Partition map (SM8550 GKI Android 13/14 generation — verify against your unit)

| Partition | Purpose | OIW-relevant |
|---|---|---|
| `boot_a`/`boot_b` | Kernel + ramdisk (pre `init_boot` split) or just kernel post-split | Target of SukiSU Ultra patch |
| `init_boot_a`/`init_boot_b` | Generic ramdisk (Android 13+ GKI split) | Target of SukiSU Ultra patch (verify which of boot/init_boot per your build) |
| `vendor_boot_a`/`vendor_boot_b` | Vendor ramdisk, DTB/DTBO refs | Not modified by OIW |
| `dtbo_a`/`dtbo_b` | Device tree overlays | Not modified by OIW |
| `system_a`/`system_b` | Android framework | Not modified (no vendor tree to rebuild against) |
| `vendor_a`/`vendor_b` | Proprietary vendor + camera HAL | Not modified, read-only reference for blob inventory (`vendor/oiw/nuwa/`) |
| `product_a`/`product_b` | OEM apps/config | Not modified |
| `userdata` | `/data`, holds `/sdcard/OIW_MEDIA` | OIW writes here |
| `metadata` | FBE metadata | Not touched |
| `persist` | Calibration data (camera included) | **Never touch** — camera calibration loss is often unrecoverable without OEM re-calibration tools |

**Action:** run `adb shell cat /proc/partitions` and `fastboot getvar all` on your unit and replace this table with
verified partition names/sizes in your local bring-up notes before doing any flashing beyond the documented SukiSU
Ultra kernel flash.

## 4. Kernel source requirements

Already solved by this repo: `Kernel/configs/nuwa-13.config.json` points at
`crdroidandroid/android_kernel_xiaomi_sm8550` branch `15.0`, `gki_defconfig`, built with a pinned Clang toolchain via
the existing CI. OIW-ROM's kernel-level contribution is the config fragment at `kernel/oiw/nuwa/configs/` — see
`docs/BUILD_SYSTEM.md` §Kernel for how to merge it into that build.

## 5. Vendor blob inventory

See `vendor/oiw/nuwa/README.md` and `vendor/oiw/nuwa/extract-files.sh`. Summary of blob classes needed **only if**
Path A is ever revisited (not needed for Path D/B as implemented today):

| Class | Needed for | Status |
|---|---|---|
| Camera (`camx`, `chi-cdk`, ISP firmware) | Any camera HAL rebuild | Not extracted — Path D doesn't touch HAL |
| Display/graphics | Framework rebuild | Not extracted |
| Audio (ADSP images) | Framework rebuild | Not extracted |
| Sensors/thermal | Framework rebuild | Not extracted |
| Modem/radio | Optional, Path A only | Not extracted, not needed |

## 6. Device tree bring-up

Blocked — see `assumptions.md` A-3 and `device/oiw/nuwa/README.md`. Scaffolding exists as a template for when/if a
`nuwa` device/vendor tree becomes available; do not attempt to synthesize one from binary vendor partitions, as an
incorrect BoardConfig is a common brick vector (mismatched partition sizes, wrong A/B config, wrong DTBO handling).

## 7. Recovery bring-up

No custom recovery is used. Recovery path is: stock recovery (accessible via hardware key combo, typically Volume Up
+ Power from powered-off state) + `fastboot` for boot image restore. Document your unit's exact key combo in local
bring-up notes (varies slightly by region/firmware).

## 8. First boot checklist (after flashing SukiSU Ultra kernel)

1. Boot fully to HyperOS home screen at least once before installing OIWCamera/OIWLauncher — confirms base kernel
   flash is healthy.
2. Confirm root: `adb shell su -c id` → should show `uid=0`.
3. Confirm SUSFS status per base project instructions.
4. Run `tools/camera_capability_dump.py` before installing OIWLauncher (so you have an unmodified-launcher baseline
   camera dump to compare against later).
5. Install `OIWCamera`, confirm preview on all reported camera IDs.
6. Install `OIWLauncher` **last**, and confirm you can still reach system Settings (`adb shell am start -a
   android.settings.SETTINGS`) in case the new launcher has an issue — do not set it as default until this is
   confirmed.

## 9. Radio/Wi-Fi/Bluetooth decision matrix

| Radio | Default cinema-mode state | Rationale |
|---|---|---|
| Wi-Fi | Off during recording unless external-control/monitoring feature explicitly enabled | Reduces thermal load, background sync risk (`docs/THERMAL_POWER.md`) |
| Bluetooth | Off during recording unless a paired HID/BLE remote trigger is explicitly enabled | Same; audio-over-BT explicitly excluded (`assumptions.md` C-4) |
| Cellular/modem | Airplane-mode-eligible ("Airplane Cinema Mode" quick tile) | Eliminates call/SMS interrupts during a take |
| GPS | Off unless explicitly enabled per `assumptions.md`/metadata privacy note | Privacy default; field metadata should not silently geotag |
