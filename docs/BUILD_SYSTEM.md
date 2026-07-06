# OIW-ROM Build System

OIW-ROM has **two independent build surfaces** — there is no unified AOSP `lunch`/`m` build graph because there is
no AOSP device/vendor tree for `nuwa` (see `assumptions.md` A-3). This is intentional, not an oversight.

## 1. Kernel build (existing, this repo)

Unchanged from the base project: GitHub Actions-driven, config-as-JSON under `Kernel/configs/*.config.json`.
`nuwa-13.config.json` is the active config; `gki.config.json.stop`/`vermeer.config.json.stop` are disabled variants
(the `.stop` suffix appears to gate them out of the active build matrix — do not rename without confirming the
workflow's file-glob behavior first).

**OIW addition**: `kernel/oiw/nuwa/configs/oiw_camera_media.config` is a `CONFIG_*` fragment intended to be merged
into the `gki_defconfig` build step. To apply it manually against a local checkout of the upstream kernel source
(`crdroidandroid/android_kernel_xiaomi_sm8550`, branch `15.0`):

```sh
git clone --branch 15.0 https://github.com/crdroidandroid/android_kernel_xiaomi_sm8550.git
cd android_kernel_xiaomi_sm8550
ARCH=arm64 scripts/kconfig/merge_config.sh arch/arm64/configs/gki_defconfig \
    /path/to/kernel/oiw/nuwa/configs/oiw_camera_media.config
```
Then build per that kernel's own instructions. This has **not** been run/verified in this change (no kernel source
checked out here) — treat it as an unverified patch until a build is attempted (`implementation_plan.md` Phase 2).

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
