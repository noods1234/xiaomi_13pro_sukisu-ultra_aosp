# OIW-ROM Storage and Media

## 1. Internal storage strategy

Default target `/sdcard/OIW_MEDIA/` (app-scoped via Storage Access Framework where feasible; falls back to
`MANAGE_EXTERNAL_STORAGE` only if SAF write throughput proves insufficient for high-bitrate segment writing —
measure with `tools/storage_benchmark.sh`, don't assume). Internal storage is UFS-class on this SoC tier; expected
sustained sequential write well above any bitrate profile shipped here, but **always** benchmark the specific unit
rather than trusting the SoC-tier assumption for a real shoot.

## 2. USB-C external SSD

Detected via `StorageManager.getStorageVolumes()` (public API, reports removable USB storage once mounted by the
OS) — OIW-ROM does not implement its own USB mass-storage driver logic. If a volume isn't auto-mounted (exFAT
support depends on the stock kernel/vendor build), the app surfaces "External storage not recognized — check
filesystem (exFAT/ext4) and cable" rather than failing silently.

## 3. exFAT/F2FS/ext4 strategy

- Internal `/data` partition: whatever HyperOS ships (typically F2FS) — not modified by OIW-ROM.
- External SSD: **exFAT recommended** for cross-platform edit-bay compatibility (macOS/Windows/Linux all read exFAT
  natively); ext4 is higher-performance on some controllers but requires a workaround (a rooted mount helper or a
  desktop-side ext4 reader) for editors on macOS/Windows, so it's a documented, non-default option under Settings →
  Storage → "Advanced: ext4 (Linux/rooted-only workflows)".
- Filesystem is verified before recording (`StatFs` + a `blkid`-equivalent capability check where root is available)
  and a write-speed benchmark clip is required before enabling any profile whose bitrate exceeds the last-measured
  sustained write speed by more than a safety margin (20%).

## 4. Write-speed testing

`tools/storage_benchmark.sh <path>`: writes a sequence of large (256MB) files with `dd`/`fio` (whichever is
available on the host or via `adb shell` on-device — see script), measures sustained sequential write MB/s (not
burst/cache-inflated numbers — uses `oflag=direct` where supported, falls back to `sync` + drop-cache-equivalent
timing on-device where direct I/O isn't available), and writes a JSON result consumed by the in-app storage preflight
check.

## 5. File segmentation

Long recordings are segmented (default: every 4GB or 10 minutes, whichever comes first — configurable) using
`MediaMuxer` restart with a new output file, not because of a legacy FAT32 4GB limit (exFAT/ext4 don't have that
limit) but to bound worst-case data loss on a crash/power event to one segment instead of an entire take.

## 6. Dropped-frame prevention

- Preflight: refuse to start a profile whose bitrate exceeds the benchmarked sustained write speed (with margin).
- Runtime: `MediaCodec` output buffers are drained on a dedicated writer thread with a bounded in-memory queue;
  if the queue backs up beyond a threshold (writer falling behind encoder), the dropped-frame indicator increments
  and — for `long_take_safe`/thermal-constrained profiles — the app can optionally auto-downshift bitrate one step
  rather than silently dropping frames indefinitely (opt-in, not default, since silently changing quality
  mid-recording without a very visible on-screen indicator would itself be a "hidden automatic" behavior we want to
  avoid; when enabled, the sidecar logs the exact timestamp and new bitrate of every downshift).

## 7. Metadata sidecar strategy

Every clip gets a JSON sidecar (schema: `configs/schema/clip_metadata.schema.json`) written **atomically** (write to
`<clip>.json.tmp`, `fsync`, rename) to avoid a half-written sidecar if the app dies mid-write. Fields per §Metadata in
`docs/CINEMA_FEATURES.md`/root brief §8.7. Folder layout:

```
/OIW_MEDIA/
  <PROJECT_NAME>/
    <YYYY-MM-DD>/
      <CAM_LABEL>/
        <CLIP_0001>/
          CLIP_0001.mp4
          CLIP_0001.json
          CLIP_0001_lut.cube        (only if a LUT was active for that clip)
          CLIP_0001_thumb.jpg
```

## 8. Media integrity verification

Optional (off by default, adds write overhead): SHA-256 hash of the finalized file, stored in the sidecar's
`integrity.sha256` field, checkable later with `tools/metadata_validate.py --verify-hash`.

## 9. Crash recovery

On app start, `StorageManager` scans the current project's clip folders for any `.mp4` file **without** a
corresponding finalized (non-`.tmp`) sidecar, or any `<clip>.mp4.part` left by an interrupted `MediaMuxer` session,
and surfaces a "Recover clips?" prompt. Recovery re-indexes the container (using `MediaExtractor` to probe how much
of the file is valid) and writes a best-effort sidecar marked `"recovered": true, "recovery_notes": "..."` — it never
silently drops or hides a partial file.

## 10. No silent failure

Every storage-path failure (write error, benchmark failure, mount loss mid-recording) triggers an explicit,
specific on-screen message per `docs/UI_UX.md` §Error UX — never a generic toast.
