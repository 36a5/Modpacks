# Adding and removing mods yourself

How to change the pack, test it on the server, and ship it — without help.

Everything here was run against this repo and works. Commands assume **PowerShell**.

---

## The one rule that will bite you first

**Never drop a `.jar` into `server/run/mods/` by hand. It will be deleted.**

`server/start.ps1` re-syncs the pack on *every* boot. `packwiz-installer` keeps a manifest at
`server/run/packwiz.json` listing every file it owns (331 entries today). On each start it makes
`run/mods/` match the pack exactly: files in the pack are downloaded, **files not in the pack are
removed**. Your hand-placed jar is "not in the pack."

`server/run/` is also in `.gitignore`. It is a build output, not source.

The pack lives in **`pack/`**. That is the only place you edit.

```
pack/
  pack.toml          <- game + loader versions, and a hash of index.toml
  index.toml         <- generated; lists every file. Never hand-edit.
  mods/*.pw.toml     <- one small text file per mod. THIS is a mod.
  config/            <- shipped config files
  shaderpacks/
```

---

## Setup (once)

`packwiz` is not on your PATH. Make an alias for the session:

```powershell
Set-Alias packwiz 'D:\Minecraft-dev-workspace\tools\packwiz.exe'
cd D:\Minecraft-dev-workspace\Modpacks\pack
```

Every `packwiz` command must run from inside `pack/`.

---

## The loop

```powershell
# 1. change the pack
packwiz mr add ferritecore          # add
packwiz remove some-mod             # remove
packwiz refresh                     # ALWAYS. rebuilds index.toml + the hash in pack.toml

# 2. serve the pack from your own machine (leave this terminal open)
packwiz serve --port 8099

# 3. in a SECOND terminal: boot the server against your local pack
cd D:\Minecraft-dev-workspace\Modpacks\server
$env:PACK_URL = "http://localhost:8099/pack.toml"
.\start.ps1

# 4. read the verdict (below), stop the server, go back to 1
```

`start.ps1` reads `$env:PACK_URL` and falls back to the live GitHub Pages URL when it is unset.
So `$env:PACK_URL` is what keeps your experiments off everyone else's game.

**Close that terminal when you're done**, or open a fresh one. `$env:PACK_URL` lives until the
window closes, and a stale value means your next "real" start syncs from a dead localhost.

---

## Adding a mod

Prefer the exact slug from the URL. `packwiz mr add jei` uses the slug from
`modrinth.com/mod/`**`jei`**.

```powershell
packwiz mr add ferritecore                    # Modrinth, by slug  (mr = modrinth)
packwiz mr add https://modrinth.com/mod/jei   # Modrinth, by URL
packwiz cf add jei                            # CurseForge         (cf = curseforge)
packwiz url add <name> <direct-download-url>  # anything else
```

If you give it a search term instead of a slug it shows a picker. Modrinth's search fuzzy-matches
badly — `"Charm"` returns *Charm of Undying*, `"First Aid"` returns *Medical Remedies*. **Always
check the slug it actually added.**

Add `-y` to accept every prompt without asking. Convenient and dangerous, for the reason above.

### packwiz adds dependencies behind your back

This is how a broken client-only mod got into the pack once. After any `add`, look at what
appeared:

```powershell
git status --short pack/mods/
```

If you added one mod and three files showed up, two are dependencies. That is usually correct and
occasionally a trap: a dependency may be Fabric-only, or a bridge mod that needs a library nobody
else in the pack provides.

---

## Removing a mod

```powershell
packwiz remove mcqoy      # the name of the .pw.toml, without the extension
packwiz refresh
```

Then clean up after it. `packwiz remove` deletes `pack/mods/mcqoy.pw.toml` and nothing else:

```powershell
git status --short pack/        # did a config/ folder get orphaned?
```

A leftover `pack/config/<modid>/` is harmless but ships to every player forever. Delete it.

---

## Which side does a mod load on?

Each `.pw.toml` has a `side` key. It decides who downloads the jar.

| `side` | Server gets it | Client gets it | Count today |
|---|---|---|---|
| `both` (default) | yes | yes | 188 |
| `server` | yes | no | 19 |
| `client` | no | yes | 17 |

So `run/mods/` holds 188 + 19 = **207 jars**, and a player's `mods/` holds 188 + 17 = **205**.
(Jar count ≠ mod count: Forge reports **226 mods** from 205 jars, because some jars contain several.)

Change it by editing the file directly:

```toml
name = "spark"
filename = "spark-1.10.53-forge.jar"
side = "both"       # <- change to "client" or "server"
```

Then `packwiz refresh`.

Getting this wrong is a classic: a client-only rendering mod marked `both` crashes the dedicated
server on boot with a `NoClassDefFoundError` about a client class.

---

## Reading the verdict

**Success.** The last line you want, roughly 40 s after boot:

```
Done (42.280s)! For help, type "help"
```

**Failure.** Search the log for these, in this order:

```powershell
cd D:\Minecraft-dev-workspace\Modpacks\server\run
Select-String -Path logs\latest.log -Pattern 'Done \(|Crash report|Missing or unsupported mandatory|Failed to create mod instance|NoClassDefFoundError|has failed to load correctly'
```

| What you see | What it means |
|---|---|
| `Missing or unsupported mandatory mods` | A dependency is absent or the wrong version. The log names it. |
| `NoClassDefFoundError` | A mod calls code from a mod that isn't there. Often a missing library. |
| `Failed to create mod instance` | The mod threw during construction. Read the stack trace above it. |
| `has failed to load correctly` | Same, but Forge caught it and kept going. |
| `FAILED TO BIND TO PORT` | A previous server is still alive. `Get-Process java \| Stop-Process`. |
| Nothing, but no `Done (` either | It is still generating chunks, or it hung. Wait, then read the tail. |

**A clean server boot does not mean the pack is good.** It only tests `side = both` and
`side = server`. The 17 client-only mods are never loaded by a dedicated server — one of them
crashed the client while the server ran happily for days.

---

## Testing the client too

Point your own game folder at the same local pack. This is exactly what `install.bat` does
internally, just with a different URL:

```powershell
cd $env:APPDATA\al-shabab      # or your CurseForge / Modrinth instance folder
java -jar packwiz-installer-bootstrap.jar -g -s client http://localhost:8099/pack.toml
```

Then launch normally. Two things to know:

- The **Mod Whitelist** mod on the server compares your mod list against its own and kicks
  mismatches. While experimenting, keep client and server on the *same* local pack.
- When you're done testing, re-run the real `install.bat` to put your client back on the live pack.

### When a crash says `NoClassDefFoundError`

Mod jars declare their dependencies in `META-INF/mods.toml`. **Some lie** — they declare nothing
and still call another mod's classes. Forge loads them happily, then they die at runtime. That is
exactly how `Barebones McQoy` crashed the client while the server ran fine for days:

```
mcqoy has failed to load correctly
java.lang.NoClassDefFoundError: dev/isxander/yacl3/api/controller/ControllerBuilder
```

Copy the class name out of the error and ask which jar provides it:

```powershell
python D:\Minecraft-dev-workspace\Modpacks\tools\whoprovides.py `
  "$env:APPDATA\al-shabab\mods" dev/isxander/yacl3/api/controller/ControllerBuilder
```

```
scanned 205 jars in C:\Users\you\AppData\Roaming\al-shabab\mods
looking for: dev/isxander/yacl3/api/controller/ControllerBuilder.class

  NOTHING in this pack provides it.
  The mod that referenced it is missing a dependency. Add the library mod,
  or remove the mod that wants it.
```

Nothing provides it → either add the library (here, YetAnotherConfigLib) or remove the mod that
wants it. Point it at `server\run\mods` to check the server side instead. Dotted names
(`dev.isxander.yacl3...`) and a trailing `.class` both work.

Cheaper in practice: **add mods a few at a time**, and launch the client after each batch. When it
breaks you know which batch did it.

---

## Rolling back

Everything in `pack/` is git-tracked, so a bad experiment costs nothing:

```powershell
cd D:\Minecraft-dev-workspace\Modpacks
git checkout -- pack/            # throw away all uncommitted pack changes
git status --short               # confirm it's clean
```

Commit a combination the moment you like it, so the next experiment has a floor:

```powershell
git add pack/
git commit -m "pack: drop X, add Y"
```

---

## Two rules that break every player's install

**1. `pack/` must use LF line endings.** `.gitattributes` enforces it. CI recomputes the index
hash on Linux; a CRLF file hashes differently and the check fails.

**2. Never leave a stray file in `pack/`.** `packwiz refresh` indexes *everything* under `pack/`,
ignoring `.gitignore`. A file that is indexed but not committed becomes a 404 on GitHub Pages, and
`packwiz-installer` aborts the whole install — for every player, not just you. That is what
`pack/.packwizignore` is for (`*.log`, `*.zip`, `*.jar`, `*.mrpack`). If you download a jar into
`pack/`, delete it before refreshing.

Sanity check before you ship:

```powershell
cd D:\Minecraft-dev-workspace\Modpacks
git status --short pack/     # must be empty of untracked files
packwiz refresh              # (from pack/) must produce no new untracked files
```

---

## Shipping it to players

Players never see your local server. The pack reaches them through GitHub Pages, which deploys
from **`master`**, not `dev`.

```powershell
cd D:\Minecraft-dev-workspace\Modpacks
git add pack/
git commit -m "pack: <what changed>"
git push origin dev

# then merge dev -> master (that is the release)
git checkout master
git merge dev
git push origin master
git checkout dev
```

Wait for the Pages deploy, then confirm the live pack really changed:

```powershell
(Invoke-WebRequest https://36a5.github.io/Modpacks/pack.toml -UseBasicParsing).Content
```

The `[index] hash` in that output must match your local `pack/pack.toml`. If it doesn't, Pages
hasn't finished — players who run `install.bat` now get the old pack.

Then: `$env:PACK_URL` unset, restart the real server, and tell players to re-run `install.bat`.
The Discord pack-watcher notices the index hash changed and pings `@Minecraft` on its own.

---

## Other useful commands

```powershell
packwiz list                      # every indexed file (227 = 224 mods + 3 shaderpacks)
packwiz update ferritecore        # update one mod to its newest version
packwiz update --all              # update everything (then boot-test!)
packwiz pin jei                   # freeze a mod so --all skips it
packwiz unpin jei
```

---

## Performance testing, not just "does it boot"

A pack that boots can still run at 8 TPS. Type these **into the server console window** (the
terminal running `start.ps1`), with no leading slash:

```
forge tps                  # mean tick time, per dimension
forge entity list          # entity census of the current dimension
spark profiler start --timeout 30
```

50 ms/tick = 20 TPS. Anything higher and the dimension it names is where the cost is. A dimension
sitting at 20.000 TPS is not your problem, however many mods it has.

> **Close your Minecraft client before you trust any TPS number.**
> The client and the server share this 6-core CPU. Measured on this machine: with the client open
> the overworld read **98–117 ms/tick (~8 TPS)** with *zero players online*; with the client closed,
> the same server on the same world read a flat **12 ms/tick, 20.000 TPS**. Nothing about the pack
> changed. If you profile with your game open you will chase a mod that was never the problem.

`spark` is in the pack, but **its output does not come back over RCON** (you get an empty string).
Run `spark` from the server console window. `forge tps` and `forge entity list` do work over RCON,
which is how the Discord bot could read them.
