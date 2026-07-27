# sepolicy scaffold (aspirational — Path A only)

No sepolicy changes are required or shipped by OIW-ROM as implemented (Path D). This file documents what domains
**would** need to be defined if Path A ever becomes tractable (see `device/oiw/nuwa/README.md`).

Aspirational domains (not defined, not compiled — templates for future authors):

- `oiw_camera_app` — would apply to `OIWCamera` if repackaged as a privileged/system app under Path A.
- `oiw_launcher` — would apply to `OIWLauncher` under Path A.
- `oiw_service` — a hypothetical native `init` service for the currently-in-app thermal/storage monitors.
- `oiw_storage_monitor` — same.
- `oiw_thermal_monitor` — same.
- `oiw_external_control` — the local-control socket service (`docs/API_PLUGIN_FRAMEWORK.md` §4), if ever moved
  out-of-process.

Policy rules for each would be authored using the standard minimal-allow-rule + `audit2allow`-on-real-denials
workflow, never a blanket permissive domain, per `docs/SECURITY_AND_RECOVERY.md` §1.
