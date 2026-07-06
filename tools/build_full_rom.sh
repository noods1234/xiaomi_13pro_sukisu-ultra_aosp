#!/usr/bin/env bash
# End-to-end Path A build: LineageOS 23.2 for Xiaomi 13 Pro (nuwa) with the OIW cinema layer.
#
# Every repo this pulls was verified to exist (manifests/oiw_nuwa.xml header has the trail).
# Host requirements (LineageOS 23.x era): x86_64 Linux, ~400 GB free disk, 32 GB RAM recommended
# (16 GB + zram/swap works but is slow), Python 3, git, git-lfs, repo tool.
#
# Usage:
#   ./build_full_rom.sh --tree ~/android/lineage [--sync-only] [--with-oiw-apps]
#
# Stages: init -> local manifest -> sync (with LFS) -> optional OIW app gradle build -> brunch nuwa
set -euo pipefail

TREE=""
SYNC_ONLY=false
WITH_OIW_APPS=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tree) TREE="$2"; shift 2 ;;
        --sync-only) SYNC_ONLY=true; shift ;;
        --with-oiw-apps) WITH_OIW_APPS=true; shift ;;
        *) echo "Unknown arg: $1" >&2; exit 1 ;;
    esac
done

[[ -z "$TREE" ]] && { echo "Usage: $0 --tree <lineage-tree-dir> [--sync-only] [--with-oiw-apps]" >&2; exit 1; }

# ---- Host preflight -------------------------------------------------------------------------
for tool in git git-lfs curl python3; do
    command -v "$tool" >/dev/null || { echo "ERROR: '$tool' not installed."; exit 1; }
done
if ! command -v repo >/dev/null; then
    echo "Installing 'repo' tool to ~/.local/bin ..."
    mkdir -p ~/.local/bin
    curl -sSf https://storage.googleapis.com/git-repo-downloads/repo > ~/.local/bin/repo
    chmod +x ~/.local/bin/repo
    export PATH="$HOME/.local/bin:$PATH"
fi
git lfs install --skip-repo   # TheMuppets vendor repos store blobs in LFS — without this, sync
                              # produces pointer files and the build fails at vendor-image time.

free_gb=$(df -BG --output=avail "$(dirname "$TREE")" 2>/dev/null | tail -1 | tr -dc '0-9' || echo 0)
if [[ "${free_gb:-0}" -lt 350 ]]; then
    echo "WARNING: <350 GB free at $(dirname "$TREE") — a full LineageOS build typically needs ~400 GB." >&2
fi

# ---- Init + local manifest ------------------------------------------------------------------
mkdir -p "$TREE"
cd "$TREE"
if [[ ! -d .repo ]]; then
    repo init -u https://github.com/LineageOS/android.git -b lineage-23.2 --git-lfs
fi
mkdir -p .repo/local_manifests
cp "$REPO_ROOT/manifests/oiw_nuwa.xml" .repo/local_manifests/oiw_nuwa.xml
echo "Local manifest installed: .repo/local_manifests/oiw_nuwa.xml"

# ---- Sync -----------------------------------------------------------------------------------
repo sync -j"$(nproc)" --retry-fetches=3 --force-sync
$SYNC_ONLY && { echo "Sync complete (--sync-only)."; exit 0; }

# ---- Optional: build OIW apps and stage them as prebuilts ------------------------------------
if $WITH_OIW_APPS; then
    for app in OIWCamera OIWLauncher; do
        echo "Building $app via Gradle ..."
        ( cd "$REPO_ROOT/packages/apps/$app" && ./gradlew --no-daemon assembleRelease )
        apk=$(find "$REPO_ROOT/packages/apps/$app" -name "*-release*.apk" | head -1)
        [[ -z "$apk" ]] && { echo "ERROR: no release APK produced for $app (signing configured?)"; exit 1; }
        cp "$apk" "$TREE/vendor/oiw-rom/vendor/oiw/prebuilt/$app.apk"
        echo "Staged $app -> vendor/oiw-rom/vendor/oiw/prebuilt/$app.apk"
    done
fi

# ---- Build ----------------------------------------------------------------------------------
set +u
source build/envsetup.sh
set -u
# To bake the OIW layer into the image, add to device/xiaomi/nuwa/lineage_nuwa.mk:
#   $(call inherit-product-if-exists, vendor/oiw-rom/vendor/oiw/oiw.mk)
# (left as an explicit operator step so a stock Lineage build remains possible from the same tree)
breakfast nuwa
brunch nuwa

echo
echo "Build complete. Flashables in: \$OUT (out/target/product/nuwa/)"
echo "  - lineage-*-nuwa.zip  (sideload via Lineage recovery: adb sideload)"
echo "  - boot.img / recovery artifacts per the official install flow"
echo "Reminder: install the matching HyperOS firmware baseline required by the device tree before flashing."
