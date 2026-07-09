# al Shabab

RPG sandbox modpack for the al Shabab server — Minecraft **1.20.1 / Forge 47.4.x**.
Three pillars: RLCraft's systems without its frustration, Better MC's world, and a full
Create + physics-vehicles tech stack.

## Layout

| Path | Purpose |
|---|---|
| `pack/` | packwiz root — single source of truth; every mod is a pinned `.pw.toml` |
| `server/` | self-bootstrapping dedicated-server scripts (auto-updates from pack URL) |
| `client-tlauncher/` | installer/updater for non-premium (TLauncher) players |
| `docs/` | RLCraft coverage ledger, changelog, design docs |
| `.github/workflows/` | CI: validate + exports on push, GitHub Release on tag, pack hosting on Pages |

## How distribution works

`pack/` is published to GitHub Pages. From that one URL: the server self-updates on every
restart (packwiz-installer), TLauncher players sync with `update.bat`, and tagged releases
produce a CurseForge zip + Modrinth `.mrpack` automatically.

## Development

```
cd pack
packwiz mr add <modrinth-slug>     # add a mod from Modrinth (preferred)
packwiz cf add <curseforge-slug>   # add a mod from CurseForge
packwiz refresh                    # regenerate index (CI enforces this)
```

Local test loop: `packwiz serve --port 8199` in `pack/`, then run the server script with
`PACK_URL=http://localhost:8199/pack.toml`. (Port 8080 is taken by a local Apache httpd on
the dev machine — packwiz silently loses the IPv4 bind and clients get 404s.)

Branches: `master` = releases, `dev` = integration, `feature/*` = per-phase work.
Design plan: see `docs/` and the phase gates defined there.
