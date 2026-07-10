# al Shabab — Modrinth App Setup

For players who already use the Modrinth App. It's clean, fast, open source, and handles
per-instance Java and memory well.

**Time needed:** about 20 minutes.
**Requires:** a real Minecraft account (the Modrinth App signs in with Microsoft). No account?
Use **[the TLauncher guide](tlauncher.md)**.

---

## Step 1 — Install the Modrinth App

1. Download from **<https://modrinth.com/app>** and install.
2. Open it → **Sign In** (top right) → sign in with your Microsoft account.
3. **Close the Modrinth App completely.** It keeps its instance list in a database it holds open
   while running, and the installer needs to write to it.

You do **not** need to install Java, and you do **not** need to create an instance by hand — the
installer does both.

---

## Step 2 — Run the installer (Windows)

1. Download **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/install.bat>**
   (right-click → *Save link as…*). Anywhere is fine — your Desktop works.
2. Double-click it.
3. When it asks which launcher you use, type **`2`** for the Modrinth App.

It then:

- finds Java 17, or downloads a private copy if you don't have one
- creates a Modrinth instance called **`al-shabab`** on Minecraft 1.20.1 + Forge 47.4.18
- downloads 224 mods, all configs, and three shaderpacks

**3–10 minutes.** Wait for `Done. You are ready to play.`

> Already have an `al-shabab` instance? The installer updates it in place and leaves your saves
> alone. Re-running the installer is how you update.

Then open the Modrinth App, right-click the instance → **Options** → **Java**, untick
*Use global settings* and set **Memory allocated** to **8 GB** (6 GB if your machine has 12 GB
or less). The Modrinth App owns that setting; the installer can't set it for you.

---

## Step 2b — macOS / Linux

`install.bat` is Windows-only. On macOS and Linux, create the instance by hand:

1. **+ Create new instance** → **Name:** `al-shabab` · **Loader:** Forge · **Game version:**
   `1.20.1` · **Loader version:** `47.4.18`. Press **Play** once, reach the main menu, quit.
2. Right-click the instance → **Open folder** and copy the path:
   - macOS: `~/Library/Application Support/ModrinthApp/profiles/al-shabab`
   - Linux: `~/.config/ModrinthApp/profiles/al-shabab`
3. Save **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.sh>** anywhere,
   then in a terminal:
   ```bash
   chmod +x update.sh
   ./update.sh "$HOME/Library/Application Support/ModrinthApp/profiles/al-shabab"
   ```
4. Set memory: right-click the instance → **Options** → **Java** → **8 GB**.

---

## Step 3 — Launch and join

1. Open the Modrinth App and press **Play** on the `al-shabab` instance.
2. **The first launch takes 3–8 minutes.** It may appear frozen while Forge builds caches. It
   isn't. Later launches are about a minute.
3. **Multiplayer** → **Add Server**:
   - **Server Name:** `al Shabab`
   - **Server Address:** `SERVER_ADDRESS` ← *ask the admin*
4. **Done** → double-click to join.

---

### The join code

The first time you join you are **blinded and cannot break blocks** until you prove you belong.
Copy the **join code** from the Discord `#server-info` channel and type it into Minecraft chat (press `T`).

You get **3 attempts and 90 seconds**, then you are kicked. Once accepted you are remembered
forever and never asked again.

## Step 4 — Register your password (first join only)

The server is offline-mode so that friends without accounts can join too, so every player sets
a password on first join.

You'll spawn unable to move. Press `T` and type:
```
/trigger register set myPassword123
```

Every join after that:
```
/trigger login set myPassword123
```

Change it later with `/trigger change_password set newPassword`.

---

## Step 5 — Shaders

Preinstalled. **Escape → Options → Video Settings → Shader Packs**:

- **Complementary Unbound** — best all-rounder, start here
- **Photon** — semi-realistic, demanding
- **Solas** — stylized fantasy, colored lighting

Select **(none)** to disable.

> **Don't add OptiFine.** It's incompatible with Valkyrien Skies and conflicts with Create — the
> two mods this pack is built on. It will crash the game. **Embeddium + Oculus** ship with the
> pack, run faster, and load the same shaderpacks.

---

## Updating

Close the Modrinth App and re-run `install.bat` (or `update.sh` on macOS/Linux) when the admin
announces an update. Your saves and settings survive.

**Don't** use the Modrinth App's *Update* button on this instance — the app doesn't manage this
pack and it won't do anything useful.

> Want zero-effort updates? **[Prism Launcher](prism-launcher.md)** can run the updater
> automatically before every launch. It's the one thing the Modrinth App can't do here.

---

## Rules that keep you connected

- **Don't install mods from Modrinth into this instance.** The server checks your mod list at
  join and kicks anyone running xray, fullbright, freecam, killaura, baritone, or ESP mods.
  Harmless extra mods usually just crash you on join.
- **Xray resource packs are pointless here.** The server hides ore blocks inside the chunk data
  it sends, so an xray pack draws plain stone.
- Want a mod added for everybody? Ask the admin.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| `The Modrinth App is running` | Close it fully, then re-run `install.bat`. |
| `The Modrinth App is not installed` | Install it, open it once, close it, re-run `install.bat`. |
| `app.db has an unfamiliar ... table` | The Modrinth App changed its database format. Create the instance by hand (Step 2b) and tell the admin. |
| Script finished but no mods in game | The instance folder should contain `mods` with ~224 files. |
| Game closes right after Play | Allocate 6–8 GB (Options → Java). |
| `UnsupportedClassVersionError` | Instance is on the wrong Java. Set Java 17 in Options → Java. |
| Crash on joining the server | Pack out of date — re-run the script. |
| Kicked: *"Please use the official modpack"* | Extra/cheat mod. Delete it from `mods`, re-run the script. |
| `No access code entered` | Type the join code from `#server-info` in chat when you join. |
| Low FPS | Shaders off, render distance 8. |

Still stuck? Send the admin `logs/latest.log` from the instance folder.
