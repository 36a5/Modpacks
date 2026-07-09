# al Shabab — Dedicated Server

Everything runtime lives in `server/run/` (gitignored). The scripts are self-bootstrapping:

1. Set the pack URL (once the GitHub repo + Pages are live) either by editing the
   `PACK_URL` line in the script or via environment variable.
2. Run `./start.sh` (Linux host) or `.\start.ps1` (Windows).
   - First run: downloads the Forge 1.20.1 server installer, installs it, downloads
     packwiz-installer-bootstrap, syncs all server-side mods/configs, writes JVM args, starts.
   - Every later run: packwiz-installer re-syncs the pack first, so restarting the
     server is also updating the server.
3. Copy `server.properties.template` into `run/server.properties` before first boot
   and review it. `online-mode=false` + whitelist is the project's resolved decision
   (TLauncher support). Add every player to the whitelist; install the server-side
   login mod (Phase 6) before opening the server beyond friends.

RAM: default 8G (`MEMORY` env var to override). Java 17 required.
