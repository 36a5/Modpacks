# al Shabab — Modrinth App Setup

For players who already use the Modrinth App. It's clean, fast, open source, and handles
per-instance Java and memory well.

**Time needed:** about 20 minutes.
**Requires:** a real Minecraft account (the Modrinth App signs in with Microsoft). No account?
Use **[the TLauncher guide](tlauncher.md)**.

---

## Step 1 — Install Java 17

The Modrinth App can download Java for the game itself, but the pack installer script needs a
system-wide Java 17.

1. **<https://adoptium.net/temurin/releases/?version=17>**
2. **Package Type:** JDK · **Version:** 17 · your OS.
3. Install it. Windows: tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
4. **Reboot.**

Verify in a terminal: `java -version` → `openjdk version "17.0.x"`.

---

## Step 2 — Install the Modrinth App

1. Download from **<https://modrinth.com/app>** and install.
2. Open it → **Sign In** (top right) → sign in with your Microsoft account.

---

## Step 3 — Create an empty Forge 1.20.1 instance

1. Click **+ Create new instance** (or the **+** in the Instances view).
2. Set:
   - **Name:** `al Shabab`
   - **Loader:** **Forge**
   - **Game version:** `1.20.1`
   - **Loader version:** `47.4.x` (newest)
3. **Create instance**.
4. Press **Play once**, let it reach the main menu, quit. This creates the folders the installer
   writes into. Don't skip it.

---

## Step 4 — Open the instance folder

1. Right-click the `al Shabab` instance → **Open folder** (or ⋯ → *Open folder*).
2. The path looks like:
   - Windows: `C:\Users\<you>\AppData\Roaming\ModrinthApp\profiles\al Shabab`
   - macOS: `~/Library/Application Support/ModrinthApp/profiles/al Shabab`
   - Linux: `~/.config/ModrinthApp/profiles/al Shabab`
3. **Copy the full path.**

---

## Step 5 — Install the modpack

**Windows**

1. Save the installer into that folder:
   **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.bat>**
2. Shift + right-click inside the folder → **Open in Terminal** → run:
   ```
   .\update.bat "C:\Users\<you>\AppData\Roaming\ModrinthApp\profiles\al Shabab"
   ```
   (Quotes matter — there's a space in `al Shabab`.)

**macOS / Linux**

1. Save **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.sh>** anywhere.
2. In a terminal:
   ```bash
   chmod +x update.sh
   ./update.sh "$HOME/Library/Application Support/ModrinthApp/profiles/al Shabab"
   ```
   (Linux users: swap in the `~/.config/ModrinthApp/...` path.)

It downloads ~180 mods, all configs, and three shaderpacks — **3–10 minutes**. Wait for
`[al-shabab] Done.`

---

## Step 6 — Memory

1. Right-click the instance → **Options** → **Java**.
2. Untick *Use global settings*, set **Memory allocated** to **8 GB** (6 GB if your machine has
   12 GB or less).
3. Confirm the **Java version** is 17.

---

## Step 7 — Launch and join

1. Press **Play**.
2. **The first launch takes 3–8 minutes.** It may appear frozen while Forge builds caches. It
   isn't. Later launches are about a minute.
3. **Multiplayer** → **Add Server**:
   - **Server Name:** `al Shabab`
   - **Server Address:** `SERVER_ADDRESS` ← *ask the admin*
4. **Done** → double-click to join.

---

## Step 8 — Register your password (first join only)

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

## Step 9 — Shaders

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

Re-run the script (`update.bat` / `update.sh`) with your instance path when the admin announces
an update.

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
| `ERROR: Java is not installed` | Install Java 17 system-wide (Step 1), reboot. |
| `ERROR: "..." does not exist` | Wrong path. Re-copy it from *Open folder*; keep the quotes. |
| Script finished but no mods in game | You pointed it at the wrong instance. The instance folder should now contain `mods` with ~170 files. |
| Game closes right after Play | Allocate 6–8 GB (Step 6). |
| `UnsupportedClassVersionError` | Instance is on the wrong Java. Set Java 17 in Options → Java. |
| Crash on joining the server | Pack out of date — re-run the script. |
| Kicked: *"Please use the official modpack"* | Extra/cheat mod. Delete it from `mods`, re-run the script. |
| `You are not whitelisted` | Ask the admin to whitelist your exact username. |
| Low FPS | Shaders off, render distance 8. |

Still stuck? Send the admin `logs/latest.log` from the instance folder.
