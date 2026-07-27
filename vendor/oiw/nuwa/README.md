# vendor/oiw/nuwa — blob inventory (reference only, Path A prerequisite)

## Licensing warning

**Do not commit extracted vendor blobs to this or any repository.** Everything under this directory is tooling to
extract blobs *locally, from a device you own*, for your own local Path A experimentation. Xiaomi/Qualcomm
proprietary blobs (camera HAL, ISP firmware, display/graphics, audio DSP images, sensor HAL, modem) are not licensed
for redistribution. `extract-files.sh` writes its output to a gitignored local directory only.

## Status (updated 2026-07-06)

**Largely superseded for Path A:** the community already maintains extracted, build-ready vendor blobs at
`TheMuppets/proprietary_vendor_xiaomi_nuwa` + `proprietary_vendor_xiaomi_sm8550-common` (`lineage-23.2`, git-LFS),
which `manifests/oiw_nuwa.xml` pulls directly — no local extraction needed for a normal full-ROM build.

`extract-files.sh` remains useful for two narrower cases: (a) capturing blobs from **your unit's exact HyperOS
version** when it diverges from TheMuppets' capture, and (b) auditing/diffing blob versions between firmware
updates. Path D needs none of this — the apps run on public APIs against the stock vendor partition.

## Blob classes (for future Path A reference)

| Class | Subsystem | Redistributable |
|---|---|---|
| `camera.*`, `libcamx*`, `libchi*` | Camera HAL (camx/chi-cdk) | No |
| ISP firmware images | Camera ISP | No |
| `libdisplay*`, `libgralloc*` | Display/graphics | No |
| ADSP/audio DSP firmware | Audio | No |
| Sensor HAL | Sensors | No |
| Thermal/power HAL | Thermal | No |
| USB gadget/host HAL | USB | No |
| Modem/radio images | Radio (optional, not needed for camera work) | No |

## Usage

```sh
./extract-files.sh --serial <adb-serial> --out /path/outside/this/repo/blobs
```
