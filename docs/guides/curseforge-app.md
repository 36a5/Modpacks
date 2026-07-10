# al Shabab — CurseForge App Setup

For players who already use the CurseForge (Overwolf) app and want to keep using it.

**Time needed:** about 20 minutes.
**Requires:** a real Minecraft account (the CurseForge app signs in through the official
launcher). No account? Use **[the TLauncher guide](tlauncher.md)**.

> **How this differs from other packs you've installed.** al Shabab isn't published to the
> CurseForge browse page (yet). The pack's own installer creates the instance for you and keeps
> it updatable, and it pulls a few mods that only exist on Modrinth.

---

## Step 1 — Install the CurseForge App

1. Download from **<https://www.curseforge.com/download/app>** and install it.
2. Open it once and choose **Minecraft** as your game.
3. **Close the CurseForge App completely.** It rewrites its own instance list when it exits,
   which would erase the instance the installer is about to create.

You do **not** need to install Java, and you do **not** need to create a profile by hand — the
installer does both.

---

## Step 2 — Run the installer

1. Download **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/install.bat>**
   (right-click → *Save link as…*). Anywhere is fine — your Desktop works.
2. Double-click it.
3. When it asks which launcher you use, type **`1`** for the CurseForge App.

It then:

- finds Java 17, or downloads a private copy if you don't have one
- creates a CurseForge instance called **`al-shabab`** on Minecraft 1.20.1 + Forge 47.4.18,
  already set to 8 GB of RAM (6 GB if your PC has 12 GB or less)
- downloads 224 mods, all configs, and three shaderpacks

**3–10 minutes.** Wait for `Done. You are ready to play.`

> Already have an `al-shabab` instance? The installer updates it in place and leaves your saves,
> screenshots and play time alone. Re-running the installer is how you update.

---

## Step 3 — Launch and join

1. Open the CurseForge App and press **Play** on the `al-shabab` instance.
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

## Step 4 — Register your password (first join only)

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

## Step 5 — Shaders

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

Close the CurseForge App and re-run `install.bat` whenever the admin announces an update. Keep a
shortcut to it. Your saves and settings survive.

**Important:** don't use the CurseForge app's own *Update* button on this instance — it doesn't
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
| `The CurseForge App is running` | Close it fully (check the system tray), then re-run `install.bat`. |
| `The CurseForge App is not installed` | Install it, open it once, close it, re-run `install.bat`. |
| The `al-shabab` instance isn't listed | Restart the CurseForge App — it rescans its Instances folder on start. |
| Script runs but the game has no mods | Check the instance folder contains a `mods` folder with ~224 files. |
| Game closes right after Play | Right-click the instance → *Profile Options* → give it 6–8 GB. |
| Crash on joining the server | Pack out of date — re-run `install.bat`. |
| Kicked: *"Please use the official modpack"* | Extra/cheat mod present. Delete it from the instance's `mods` folder and re-run the installer. |
| `No access code entered` | Type the join code from `#server-info` in chat when you join. |
| Overwolf overlay causes stutter | CurseForge Settings → disable the in-game overlay. |
| Low FPS | Shaders off, render distance 8. |

Still stuck? Send the admin the profile's `logs\latest.log`.
