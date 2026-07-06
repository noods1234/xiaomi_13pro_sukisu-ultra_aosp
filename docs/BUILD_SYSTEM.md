# OIW-ROM Build System

OIW-ROM has **three build surfaces** (revised 2026-07-06 after `assumptions.md` A-3 was corrected):

0. **Path A — full LineageOS-based ROM** (`brunch nuwa` with the OIW layer inherited), now unblocked;
1. the CI-built **OIW cinema kernel** for users staying on rooted HyperOS (Path B);
2. the **standalone Gradle apps** (Path D), which run identically on both.

## 0. Full ROM build (Path A — LineageOS 23.2 base)

Everything needed is in-repo and every upstream input was verified to exist (see `manifests/oiw_nuwa.xml` header
for the verification trail, and `device/oiw/nuwa/README.md` for the dependency table):

```sh
# On a real build host: x86_64 Linux, ~400 GB free disk, 32 GB RAM recommended, git-lfs installed
tools/build_full_rom.sh --tree ~/android/lineage --with-oiw-apps
```

The script performs: host preflight → `repo init -u LineageOS/android -b lineage-23.2 --git-lfs` → installs
`manifests/oiw_nuwa.xml` as a local manifest (device trees, Xiaomi-derived kernel + techpack module sources,
TheMuppets blobs) → `repo sync` → optionally Gradle-builds OIWCamera/OIWLauncher and stages them for
`vendor/oiw/Android.bp`'s `android_app_import` modules → `brunch nuwa`. To bake the OIW layer into the image, add
`$(call inherit-product-if-exists, vendor/oiw-rom/vendor/oiw/oiw.mk)` to `device/xiaomi/nuwa/lineage_nuwa.mk`
(deliberately a one-line explicit operator step so the same tree can also produce a stock Lineage build).

Git-LFS is mandatory — TheMuppets vendor repos store blobs in LFS; without it the vendor image is built from
pointer files and fails. The compile itself takes hours and cannot run in this authoring environment; the manifest,
build script, product makefiles, and app prebuilts wiring are the verified deliverables here.

## 1. Kernel build — the OIW cinema kernel (the one ROM-level artifact that IS rebuilt here)

GitHub Actions-driven (`.github/workflows/SukiSU_SUSFS.yml`), config-as-JSON under `Kernel/configs/*.config.json`.
The workflow reads **every** `*.config.json` as a build-matrix entry (via `jq -s '[.[][]]'`), clones
`crdroidandroid/android_kernel_xiaomi_sm8550@15.0`, applies SukiSU Ultra + SUSFS + KPM, builds a flashable
AnyKernel3 zip and boot `Image`. `.stop`-suffixed files are excluded by the `*.config.json` glob.

### The `nuwa-oiw` cinema kernel variant

`Kernel/configs/nuwa-oiw.config.json` adds a **dedicated OIW cinema kernel build** alongside the untouched stock
`nuwa-13.config.json`. It carries one extra field, `oiwCinemaFragment`, pointing at
`kernel/oiw/nuwa/configs/oiw_cinema.config`. A gated workflow step (`🎬 Inject OIW cinema kernel tuning`) appends
that fragment's `CONFIG_*` lines to `gki_defconfig` during the build — mirroring exactly how the workflow already
injects the SUSFS/KPM configs. The stock `nuwa-13` build has no `oiwCinemaFragment` field, so the step is skipped
for it and it is left byte-for-byte unchanged.

### What the fragment actually does (verified, and honest about GKI limits)

This is a **GKI (Generic Kernel Image)** build: camera/ISP/sensor drivers are proprietary vendor kernel modules
loaded from the stock vendor partition, **not** part of this Image. So the kernel cannot change camera/RAW/log/ISP
behavior — that is architecturally impossible at this layer (see `docs/ARCHITECTURE.md` §3). The genuinely useful,
GKI-legal, currently-missing delta is **broader external-footage-drive filesystem support**:

- `CONFIG_NTFS3_FS` (+ `NTFS3_LZX_XPRESS`, `NTFS3_FS_POSIX_ACL`) — read/write NTFS footage drives from
  Windows/macOS editors.
- `CONFIG_UDF_FS` — UDF-formatted media breadth.
- `CONFIG_LOCALVERSION="-oiw-cinema"` — makes the flashed cinema kernel identifiable in build metadata.

Everything else a camera project might reflexively add (USB_UAS/USB_STORAGE/EXFAT/F2FS/EXT4/DMABUF_HEAPS/THERMAL*/
TYPEC*/V4L_PLATFORM_DRIVERS) was **verified already enabled** in the stock `gki_defconfig` and is deliberately not
re-added.

### Verification performed in this change (not a claim — actually run)

Against a real shallow clone of `crdroidandroid/android_kernel_xiaomi_sm8550@15.0`, the fragment was appended to
`gki_defconfig` and the real kernel kconfig was generated:

```sh
git clone --depth=1 --branch 15.0 https://github.com/crdroidandroid/android_kernel_xiaomi_sm8550.git
cd android_kernel_xiaomi_sm8550
# (CI's SukiSU step creates drivers/kernelsu; stub it for a local config-only check)
cat /path/to/kernel/oiw/nuwa/configs/oiw_cinema.config | grep '^CONFIG_' >> arch/arm64/configs/gki_defconfig
make ARCH=arm64 O=out gki_defconfig
grep -E 'NTFS3_FS|UDF_FS|LOCALVERSION="-oiw' out/.config   # confirmed present
```

`NTFS3_FS=y`, `NTFS3_LZX_XPRESS=y`, `NTFS3_FS_POSIX_ACL=y`, `UDF_FS=y`, and `LOCALVERSION="-oiw-cinema"` were all
confirmed to survive into the generated `.config`. The full compile (ARM Clang `r536225`, thin-LTO) is what the CI
does on `workflow_dispatch`; it is not run in this environment, but the config wiring it consumes is verified.

## 2. Host dependencies (app build)

- JDK 17
- Android SDK (`compileSdk 34`, `minSdk 29`, `targetSdk 34` — Android 10+ covers all realistic HyperOS baselines for
  this device; Camera2 manual-control keys used here are all API 21+, dynamic-range-profile keys are gated behind
  API-level checks at runtime)
- Android NDK (side-by-side, r26+) — only needed if/when a native (C++) monitoring-overlay shader path replaces the
  Kotlin/GL prototype (see `implementation_plan.md` Phase 6 waveform/vectorscope plan)
- `adb`/`fastboot` (platform-tools)
- Python 3.10+ (for `tools/*.py`)

## 3. Repo sync / manifests

No `repo` manifest is used — this is not a multi-project AOSP checkout. Two independent git repositories make up the
whole project:

| Repo | Role |
|---|---|
| `xiaomi_13pro_sukisu-ultra_aosp` (this repo) | Kernel build config + OIW-ROM docs/apps/tools/configs |
| `xiaomi_kernel_opensource` | Upstream multi-device kernel source mirror (no `nuwa` branch present); hosts `oiw/nuwa/` integration docs and config fragments as a staging area for when/if a real kernel source lands there |

## 4. Local manifests / lunch targets

Not applicable (no AOSP build). App build "targets" are ordinary Gradle build variants (below).

## 5. Build variants

| Variant | App logging | Debug panel | Signing | Intended use |
|---|---|---|---|---|
| `debug` | Verbose | Enabled | Debug keystore | Local development, field debugging |
| `release` | Warn/Error only | Hidden behind 7-tap Settings unlock | Release keystore (user-supplied, not committed) | Field production use |

Gradle flavor dimension `channel` is **not** used (single build target per variant — no OTA channel split, since
there is no OTA distribution mechanism for a sideloaded app; see `docs/SECURITY_AND_RECOVERY.md` §OTA).

## 6. Signing

```sh
keytool -genkeypair -v -keystore oiw-release.jks -alias oiw -keyalg RSA -keysize 4096 -validity 10000
```
Store the keystore **outside** this repo (e.g., a password manager / offline drive). Reference it via
`gradle.properties` (gitignored) using standard `signingConfigs` — never commit a keystore or its password.

## 7. Reproducibility

- Gradle wrapper is pinned (`gradle/wrapper/gradle-wrapper.properties`) to a fixed Gradle version.
- Dependency versions are pinned exactly (no `+`/dynamic versions) in each `build.gradle.kts`.
- Kernel build reproducibility is inherited from the base project's pinned Clang toolchain archive URL in
  `Kernel/configs/nuwa-13.config.json`.

## 8. CI plan

- **Kernel CI**: existing GitHub Actions workflow (external to this doc's authorship, referenced by
  `Kernel/configs/*.config.json`) — unchanged by OIW-ROM except for the proposed config-fragment merge (Phase 2,
  not yet wired in).
- **App CI (proposed, not yet added)**: a GitHub Actions workflow running `./gradlew testDebugUnitTest
  assembleDebug` for both `OIWCamera` and `OIWLauncher` on push/PR. Not added in this change to avoid introducing a
  CI config without a place to run it (no Actions runner access confirmed in this environment) — tracked as a
  follow-up task in `implementation_plan.md` Phase 9.
