# al Shabab — Setup Guide

Pack URL (the one address everything reads from):
`https://36a5.github.io/Modpacks/pack.toml`

---

## 1. Enable GitHub Pages (once, do this first)

Nothing works until the pack is published.

1. Push the repo: `git push -u origin master` (and `git push origin dev`).
2. On GitHub → **Settings** → **Pages** → *Build and deployment* → *Source* = **GitHub Actions**.
3. **Allow `master` to deploy.** Settings → **Environments** → `github-pages` →
   *Deployment branches and tags* → **Add deployment branch rule** → `master`.

   This step is not optional. Enabling Pages auto-creates a `github-pages` environment whose
   protection rules **only allow the repo's default branch to deploy**. This repo's default is
   `dev`, so a deploy from `master` is rejected with:

   > `Branch "master" is not allowed to deploy to github-pages due to environment protection rules.`

   *(Equivalent alternative: make `master` the default branch instead.)*

4. Actions tab → confirm **Deploy pack to GitHub Pages** ran green (re-run it if it failed
   before you changed the setting).
5. Verify in a browser: <https://36a5.github.io/Modpacks/pack.toml> must show text, not a 404.
   **Nothing — server or player — works until this returns text.**

Pages publishes from `master`, so merging `dev` → `master` is how you ship an update to
players. `dev` is where you test.

---

## 2. Running the server (you)

First run installs Forge and downloads every mod automatically. Every later run re-syncs the
pack first, so **restarting the server is how the server updates itself**.

**Windows:**
```powershell
cd D:\Minecraft-dev-workspace\Modpacks\server
.\start.ps1
```

**Linux host:**
```bash
cd /path/to/Modpacks/server
chmod +x start.sh
./start.sh
```

Options (both platforms):
```powershell
$env:MEMORY = "10G"      # default is 8G
.\start.ps1
```

Before the very first boot, copy the server config template and review it:
```powershell
Copy-Item server.properties.template run\server.properties
```
It sets `online-mode=false` (required for TLauncher friends), `white-list=true`, and
`enforce-whitelist=true`.

If the pack sync fails, the script **refuses to start** rather than booting a modless server.
That is deliberate — a modless server would corrupt player inventories on next join.

### Server admin commands
| Task | Command (in server console) |
|---|---|
| Whitelist a friend | `whitelist add <username>` |
| Reload whitelist | `whitelist reload` |
| Check performance | `forge tps` |
| Pre-generate world (less lag) | `chunky radius 3000` then `chunky start` |
| Stop safely | `stop` |

---

## 3. How players install the pack

**Send players to [docs/guides/quick-install.md](guides/quick-install.md).** They download
`client/install.bat`, double-click it, and press Play — it installs Java (if missing), Forge,
the whole pack, and a launcher profile with correct RAM. Re-running it updates them.

Per-launcher manual guides live in [docs/guides/](guides/README.md) for players who want them.

**The universal method — works on every launcher, premium or not.** `client/update.bat`
(and `client/update.sh` for macOS/Linux) points at any launcher's game folder and installs the
exact pack the server runs, downloading each mod straight from CurseForge and Modrinth. Nobody
redistributes anything, and re-running it is how players update.

This is the recommended path for everyone until the release artifacts are cleaned up (see the
note at the bottom of this section).

### TLauncher / official Minecraft launcher (any account)
1. Install Java 17: <https://adoptium.net>
2. In the launcher, pick **Forge 1.20.1** (47.4.x), install it, launch once, close the game.
3. Download `client/update.bat` from the repo and run it.
   Custom game folder? Run: `update.bat "C:\path\to\.minecraft"`
4. Set launcher memory to 6–8 GB.
5. TLauncher players: pick a username and **never change it** — on an offline-mode server the
   name *is* your identity, and changing it loses your items. Ask the admin to whitelist it.

### Prism Launcher (best experience for premium players)
1. Create a **Forge 1.20.1** instance.
2. Instance → **Edit** → **Settings** → **Custom commands** → *Pre-launch command*:
   ```
   "$INST_JAVA" -jar packwiz-installer-bootstrap.jar -g -s client https://36a5.github.io/Modpacks/pack.toml
   ```
   Drop `packwiz-installer-bootstrap.jar` in the instance's `.minecraft` folder first
   ([download](https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest)).
3. Prism now re-syncs the pack on **every launch** — players can never drift from the server.

### Modrinth App / CurseForge App
Import the `.mrpack` or CurseForge `.zip` from the repo's **Releases** page (once you tag a
release — see §6), then set memory to 6–8 GB.

> **Before you publish a release:** 29 of the pack's mods are sourced from CurseForge, and
> packwiz cannot cross-match metadata between sites, so both exports **embed those jars**
> rather than linking them (CF zip: 645 MB, `.mrpack`: 242 MB). Publicly distributing embedded
> mod jars requires each mod's license to permit it. Re-adding mods with `packwiz mr add`
> where a Modrinth listing exists shrinks the embedded set. The `update.bat` and Prism paths
> above have no such problem — they only ever link.

**Players update by re-running `update.bat`, or just relaunching (Prism/Modrinth App).
Nobody ever downloads mods by hand.**

---

## 4. Shaders

The pack ships three shaderpacks and the loader that runs them:

| Shaderpack | Style |
|---|---|
| Complementary Shaders — Unbound | balanced, best all-round performance |
| Photon | semi-realistic, gameplay-focused |
| Solas | stylized fantasy, colored lighting |

In game: **Options → Video Settings → Shader Packs** → pick one. They arrive already
installed; no downloading.

### Why there is no OptiFine
OptiFine **crashes this pack**. It is incompatible with Valkyrien Skies (the ships/airships/
mechs mod), and conflicts with Create — both core pillars of al Shabab. There is no config
that fixes it.

Instead the pack ships **Embeddium + Oculus**, which together do everything players want
OptiFine for: they are faster than OptiFine on most hardware, and Oculus loads the same
`.zip` shaderpacks OptiFine does. Anyone asking for OptiFine already has its benefits.

---

## 5. Anti-cheat: what is enforced, and what cannot be

Three layers, weakest to strongest:

### Layer 1 — Whitelist (`white-list=true`)
Strangers cannot join at all. On an offline-mode server this is the single most important
setting; without it, anyone can connect using any username, including yours.

### Layer 2 — Auth mod (password login)
Because the server runs `online-mode=false`, usernames are not verified by Mojang. The
**Auth** mod makes each player register a password and log in before they can move or
interact. This stops someone joining as your friend's username and emptying their base.

Player commands: `/trigger register set <password>` once, then `/trigger login set <password>`
on each join.

### Layer 3 — Mod Whitelist (blocks cheat mods)
Checks each client's mod list on connect and kicks anyone running a blacklisted mod — xray
mods, fullbright, killaura, freecam, and similar. Configured in `config/modwhitelist.json`
(shipped with the pack, so it applies to everyone automatically).

### The xray texture-pack problem — and the only real fix
**A mod whitelist cannot stop xray resource packs.** Neither can forcing a server resource
pack: the client can still load its own pack on top, and there is no way to detect it. Any
guide claiming otherwise is wrong.

The only fix that actually works is to **never send the ore locations to the client**. That is
what **AntiXray** does: the server replaces hidden ore blocks with fake stone in the chunk
packets it sends. An xray pack renders exactly what the server sent — plain stone. The cheat
sees nothing because the data was never there.

Honest limits, so you are not surprised later:
- AntiXray hides **ores**. It does not hide caves, bases, or chests.
- Determined cheaters on any server can still use freecam-style exploits that Mod Whitelist
  may miss if the mod is renamed. Layers 1 and 2 are what keep a friends server safe.
- Nothing here weakens performance for honest players; they never notice AntiXray.

---

## 6. Shipping an update to players

```powershell
cd D:\Minecraft-dev-workspace\Modpacks\pack
D:\Minecraft-dev-workspace\tools\packwiz.exe update --all   # bump mod versions
D:\Minecraft-dev-workspace\tools\packwiz.exe refresh
cd ..
git add -A; git commit -m "Update mods"; git push origin dev
```
Test on the server first (`.\server\start.ps1`). When happy:
```powershell
git checkout master; git merge dev; git push origin master
git tag v1.0.0; git push origin v1.0.0     # builds CurseForge zip + .mrpack Release
```
Players get it on their next launch. The server gets it on its next restart.
