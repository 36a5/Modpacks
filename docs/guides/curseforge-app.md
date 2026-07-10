# al Shabab — CurseForge App Setup

For players who already use the CurseForge (Overwolf) app and want to keep using it.

**Time needed:** about 20 minutes.
**Requires:** a real Minecraft account (the CurseForge app signs in through the official
launcher). No account? Use **[the TLauncher guide](tlauncher.md)**.

> **How this differs from other packs you've installed.** al Shabab isn't published to the
> CurseForge browse page (yet). You'll create an empty Forge profile and let the pack's own
> installer fill it. This is the same thing the "Import" button does, except it also keeps you
> auto-updatable and pulls a few mods that only exist on Modrinth.

---

## Step 1 — Install Java 17

The CurseForge app bundles a Java runtime for the game, but the pack installer script needs a
real Java 17 on your system.

1. **<https://adoptium.net/temurin/releases/?version=17>**
2. **Package Type:** JDK · **Version:** 17 · your OS.
3. Install. On Windows tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
4. **Reboot.**

Verify: `Win + R` → `cmd` → `java -version` → must say `openjdk version "17.0.x"`.

---

## Step 2 — Install the CurseForge App

1. Download from **<https://www.curseforge.com/download/app>** and install it.
2. Open it and choose **Minecraft** as your game.
3. It will ask where to keep instances — note that folder, you need it soon. The default is
   `C:\Users\<you>\curseforge\minecraft\Instances`.

---

## Step 3 — Create an empty Forge 1.20.1 profile

1. In the app: **Create Custom Profile** (top right).
2. Fill in:
   - **Profile Name:** `al Shabab`
   - **Minecraft version:** `1.20.1`
   - **Modloader:** **Forge**, version **47.4.x**
3. **Create**.
4. Click the profile → **Play once** and let it reach the main menu, then quit.
   This creates the folders the installer writes into. Don't skip it.

---

## Step 4 — Find the profile folder

1. Right-click the `al Shabab` profile → **Open Folder**.
2. A file explorer opens on something like:
   ```
   C:\Users\<you>\curseforge\minecraft\Instances\al Shabab
   ```
3. **Copy that full path.** You need it in the next step.

---

## Step 5 — Install the modpack

1. Download the installer script into that profile folder:
   **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.bat>**
   (Right-click → *Save link as…*, save it inside the folder from Step 4.)

2. In that folder, hold **Shift**, right-click on empty space, choose **"Open in Terminal"** or
   **"Open PowerShell window here"**, then run:
   ```
   .\update.bat "C:\Users\<you>\curseforge\minecraft\Instances\al Shabab"
   ```
   Use your real path, keep the quotes (there's a space in `al Shabab`).

3. It downloads ~207 mods, configs, and three shaderpacks — **3–10 minutes**. Wait for
   `[al-shabab] Done.`

---

## Step 6 — Give it memory

1. In the CurseForge app: **Settings** (gear, bottom left) → **Game Settings** → **Minecraft**.
2. Set **Allocated Memory** to **8 GB** (or 6 GB if your PC has 12 GB or less).
3. Optionally do it per-profile: right-click the profile → **Profile Options** → untick
   *Use System Memory Settings* → set the slider.

---

## Step 7 — Launch and join

1. Press **Play** on the `al Shabab` profile.
2. **First launch takes 3–8 minutes.** The window may say *Not Responding*. It's building
   caches, not frozen. Later launches take about a minute.
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

## Step 8 — Register your password (first join only)

The server runs offline-mode so that friends without Minecraft accounts can play. Everyone sets
a password on their first join, premium accounts included.

You'll spawn unable to move. Press `T`:
```
/trigger register set myPassword123
```

Every join after:
```
/trigger login set myPassword123
```

Change it later: `/trigger change_password set newPassword`

---

## Step 9 — Shaders

Already installed — **do not download any**. **Escape → Options → Video Settings → Shader
Packs**:

- **Complementary Unbound** — best balance, start here
- **Photon** — semi-realistic, heavier
- **Solas** — stylized fantasy, colored lighting

Choose **(none)** to switch them off.

> **Do not install OptiFine**, even though the CurseForge app makes it easy. It is incompatible
> with Valkyrien Skies (ships/airships/mechs) and conflicts with Create — the pack will crash.
> **Embeddium + Oculus** are already installed, run faster than OptiFine, and load these exact
> shaderpacks.

---

## Updating

Re-run `update.bat` with your profile path whenever the admin announces an update. Keep a
shortcut to it.

**Important:** don't use the CurseForge app's own *Update* button on this profile — it doesn't
know about this pack and will do nothing useful.

---

## Rules that keep you connected

- **Don't install mods through the CurseForge app's Mods tab.** The server checks your mod list
  on join and kicks anyone running cheat mods (xray, fullbright, freecam, killaura, baritone,
  ESP). Even innocent extra mods usually cause a crash on join.
- **Xray resource packs don't work here.** The server replaces ore blocks with fake stone before
  the data ever leaves it, so xray packs render plain stone.
- Want a mod added? Ask the admin — they add it to the pack for everyone.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| `ERROR: Java is not installed` | Install Java 17 (Step 1) and **reboot**. |
| `ERROR: "..." does not exist` | Wrong path. Copy it again from *Open Folder*, keep the quotes. |
| Script runs but the game has no mods | You pointed it at the wrong profile. Check that the profile folder now contains a `mods` folder with ~170 files. |
| Game closes right after Play | Allocate 6–8 GB (Step 6). |
| Crash on joining the server | Pack out of date — re-run `update.bat`. |
| Kicked: *"Please use the official modpack"* | Extra/cheat mod present. Delete it from the profile's `mods` folder and re-run the script. |
| `No access code entered` | Type the join code from `#server-info` in chat when you join. |
| Overwolf overlay causes stutter | CurseForge Settings → disable the in-game overlay. |
| Low FPS | Shaders off, render distance 8. |

Still stuck? Send the admin the profile's `logs\latest.log`.
