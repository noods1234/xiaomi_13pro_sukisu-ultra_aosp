# kernel/oiw/nuwa — OIW kernel integration plan

This directory documents (and stages) the kernel-level changes OIW-ROM wants layered onto the existing SukiSU Ultra
GKI build defined by `Kernel/configs/nuwa-13.config.json`. **Nothing here is applied automatically by that build's
CI today** — see `docs/BUILD_SYSTEM.md` §1 for how to apply it manually, and `implementation_plan.md` Phase 2 for
the tracked follow-up to wire it into CI.

## Why config fragments, not patches, for most of this

The upstream kernel source (`crdroidandroid/android_kernel_xiaomi_sm8550`, branch `15.0`) is not checked out in this
repo — this repo only holds the *build recipe* (JSON) that tells the external CI where to fetch it. We therefore
cannot safely hand-author line-level source patches against code we can't see and diff against. What we *can* do
responsibly is provide a `CONFIG_*` fragment (a standard `merge_config.sh`-compatible file) — these are additive,
order-independent, and safe to review without the full source tree, since they only turn on/off/parameterize
existing Kconfig options that a modern SM8550 GKI kernel is expected to already define.

## Contents

- `configs/oiw_camera_media.config` — camera/media/USB-storage/thermal-adjacent Kconfig fragment (see file for the
  full option list and rationale per option).
- `patch-queue/0000-README.md` — layout convention for any future source-level patches, once/if this repo starts
  vendoring the kernel source directly instead of fetching it in CI.

## Scope boundaries (explicit)

- This fragment does **not** touch the camera sensor driver itself (proprietary, vendor-module-loaded — see
  `assumptions.md` A-3) — it only affects the generic media/V4L2/DMA-BUF/USB-storage/thermal subsystems that a GKI
  kernel controls independently of the vendor camera module.
- This fragment does **not** change default CPU/GPU governors (see `docs/THERMAL_POWER.md` §2 — that's handled at
  the app layer intentionally, to avoid shipping an unreviewed systemwide governor change).
