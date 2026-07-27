# vendor/oiw/prebuilt

Staging directory for the Gradle-built OIW app APKs consumed by `../Android.bp`:

- `OIWCamera.apk`
- `OIWLauncher.apk`

`tools/build_full_rom.sh --with-oiw-apps` builds and copies them here automatically. APK binaries are
not committed to git — build them from `packages/apps/*` so the source in this repo is always the
source of truth.
