# kernel/oiw/nuwa — OIW cinema kernel integration

This is the kernel-level layer of OIW-ROM, and it is the **one ROM-level artifact this project actually
rebuilds**: a flashable boot `Image` produced by this repo's existing GitHub Actions CI. Everything else in
OIW-ROM (OIWCamera/OIWLauncher) is userspace running on the stock system image, which cannot be rebuilt without a
vendor tree (see `docs/ARCHITECTURE.md` §2).

## What ships here

- `configs/oiw_cinema.config` — a **verified** GKI defconfig delta. It adds NTFS3/UDF external-footage-drive
  filesystem support and a `-oiw-cinema` localversion, and nothing else, because on a GKI build that is the only
  genuinely useful, currently-missing, kernel-legal cinema change (full reasoning in the file header and
  `docs/BUILD_SYSTEM.md` §1).

## How it's wired into the build (not a stub anymore)

`Kernel/configs/nuwa-oiw.config.json` declares a dedicated `nuwa-oiw-cinema` build-matrix entry with an
`oiwCinemaFragment` field pointing at `configs/oiw_cinema.config`. The `🎬 Inject OIW cinema kernel tuning` step in
`.github/workflows/SukiSU_SUSFS.yml` appends the fragment to `gki_defconfig` during that build (and only that
build — the stock `nuwa-13` build has no such field and is untouched). Trigger it via the workflow's
`workflow_dispatch`; the artifact is `AnyKernel3-nuwa-oiw-cinema-...zip`, flashable like any AnyKernel3 zip.

## Verification status

**Verified**, not aspirational. The fragment was appended to a real clone of
`crdroidandroid/android_kernel_xiaomi_sm8550@15.0` and `make ARCH=arm64 gki_defconfig` was run; `NTFS3_FS`,
`NTFS3_LZX_XPRESS`, `NTFS3_FS_POSIX_ACL`, `UDF_FS`, and the `-oiw-cinema` localversion were all confirmed present in
the generated `.config` (`docs/BUILD_SYSTEM.md` §1 has the exact commands). The final ARM compile happens in CI.

## Honest scope boundary

- Does **not** touch the camera sensor driver, ISP, or any camera/RAW/log behavior — those are proprietary vendor
  kernel modules loaded from the stock vendor partition, not part of this GKI Image (`assumptions.md` A-3).
- Does **not** change CPU/GPU governors — that stays at the app layer intentionally (`docs/THERMAL_POWER.md` §2).

## patch-queue/

`patch-queue/0000-README.md` — layout convention for future source-level patches, unused today because the kernel
source is fetched by CI rather than vendored in this repo.
