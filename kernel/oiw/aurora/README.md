# kernel/oiw/aurora — OIW cinema kernel for Xiaomi 14 Ultra (primary target)

**Key research finding (2026-07-06):** the paired `noods1234/xiaomi_kernel_opensource` mirror contains
**official Xiaomi kernel source for aurora** — branch `aurora-u-oss` (Linux 6.1 GKI, android14-6.1; also
`shennong-u-oss` for the 14 Pro). Unlike nuwa, aurora has a first-party kernel source drop.

## Verified facts (real `make ARCH=arm64 gki_defconfig` run on that source)

- `configs/oiw_cinema.config` (NTFS3 + UDF footage-drive support, `-oiw-cinema` localversion) **survives into
  the generated `.config`** — same verification method as nuwa (`docs/BUILD_SYSTEM.md` §1).
- `CONFIG_TYPEC_DP_ALTMODE=y` is already enabled in aurora's `gki_defconfig` — kernel-side DisplayPort Alt Mode
  support is present, consistent with the 14 Ultra's USB 3.2/DP hardware (`assumptions.md` E-1).
- Device-specific fragment `arch/arm64/configs/vendor/aurora_GKI.config` exists in the OSS drop (23 lines).
- **Known OSS-drop gap:** `drivers/misc/hwid/` is referenced by `drivers/misc/Kconfig` but missing from the
  drop — kconfig fails without a stub (`config HWID` bool). A full compile needs that stubbed or sourced from a
  community kernel tree (e.g. `Neoteric-OS/android_device_xiaomi_aurora-kernel`). Typical Xiaomi OSS omission;
  documented, not hidden.

## Build variant

`Kernel/configs/aurora-oiw.config.json.stop` is a ready CI matrix entry for the existing SukiSU workflow
(repo = this project's own mirror @ `aurora-u-oss`, `gki_defconfig`, SUSFS branch `gki-android14-6.1`,
`oiwCinemaFragment` → this directory). It ships **`.stop`-disabled** because (a) the hwid Kconfig gap needs a
patch step before the compile passes, and (b) a 6.1 build of this workflow hasn't been exercised yet — rename to
`.config.json` after adding the hwid stub step to enable it. Honest status: config-verified, compile-pending.
