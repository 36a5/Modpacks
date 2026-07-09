# al Shabab — Official Minecraft Launcher Setup

For players who **own Minecraft: Java Edition** and want to use the launcher that came with it.

**Time needed:** about 20 minutes, mostly downloads.
**Result:** you're in the world on the al Shabab server, 180 mods and shaders running.

> **Prism Launcher is genuinely better for this**, because it re-syncs the pack automatically
> every time you launch, so you can never fall out of step with the server. If you're open to
> it, use **[the Prism guide](prism-launcher.md)** instead. If you'd rather stay on the
> official launcher, this guide works fine.

---

## Step 1 — Install Java 17

The vanilla launcher ships its own Java, but the modpack installer script needs a real Java 17
on your system.

1. Go to **<https://adoptium.net/temurin/releases/?version=17>**
2. **Operating System** = yours, **Package Type** = **JDK**, **Version** = **17**.
3. Download and run the **`.msi`** (Windows) / **`.pkg`** (macOS) installer.
4. On Windows, enable **"Set JAVA_HOME variable"** and **"Add to PATH"** during install.
5. **Reboot.**

**Verify:** open Command Prompt (`Win + R` → `cmd`) and run `java -version`.
It must print `openjdk version "17.0.x"`.

---

## Step 2 — Install Forge 1.20.1

1. Go to **<https://files.minecraftforge.net/net/minecraftforge/index_1.20.1.html>**
2. Download the **Installer** under *Latest* (version **47.4.x**).
   The site shows an ad page with a *Skip* button in the top-right — wait 5 seconds, click Skip.
   You want the file named `forge-1.20.1-47.4.x-installer.jar`.
3. **Double-click the downloaded `.jar`.** If Windows doesn't know how to open it, right-click →
   *Open with* → *Java(TM) Platform SE binary*.
4. In the window that appears, choose **Install client** and press **OK**.
5. Wait for *"Successfully installed client profile Forge"*.

---

## Step 3 — Create a separate installation (do not skip this)

If you install the pack into your normal `.minecraft` folder, it mixes with your vanilla worlds
and any other mods you have. Give it its own folder.

1. Open the **Minecraft Launcher** → **Installations** tab → **New installation**.
2. Fill it in:
   - **Name:** `al Shabab`
   - **Version:** `release 1.20.1-forge-47.4.x`
   - **Game directory:** click the folder icon and pick a **new empty folder**, for example
     `C:\Games\alShabab`
   - **Resolution:** leave alone
3. Click **More Options** and set **JVM Arguments**. Replace the `-Xmx2G` at the start with:
   ```
   -Xmx8G -XX:+UseG1GC
   ```
   Use `-Xmx6G` instead if your computer has 8–12 GB of RAM total.
4. **Create**.
5. **Play it once** and let it reach the main menu, then quit. This creates the folders the
   installer needs.

Write down that game directory path — you need it in the next step.

---

## Step 4 — Install the modpack

1. Download the installer script:
   **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.bat>**
   (Right-click → *Save link as…*. On macOS/Linux use
   **[update.sh](https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.sh)**.)

2. Save it **inside the game directory you chose** (e.g. `C:\Games\alShabab`).

3. **Double-click `update.bat`.**

   Because your game directory isn't the default `.minecraft`, the script needs to be told
   where to install. The simplest way: hold **Shift**, right-click inside the folder, choose
   **"Open PowerShell window here"** or **"Open in Terminal"**, and run:
   ```
   .\update.bat "C:\Games\alShabab"
   ```
   (Use your actual path.)

   macOS / Linux:
   ```bash
   chmod +x update.sh
   ./update.sh "/path/to/your/game/folder"
   ```

4. It downloads ~180 mods, all configs, and three shaderpacks. **3–10 minutes.** Wait for
   `[al-shabab] Done.`

---

## Step 5 — Launch and join

1. Minecraft Launcher → **Play** with the **al Shabab** installation selected.
2. **First launch takes 3–8 minutes.** The window may show *Not Responding* — it is working.
   Don't close it. Later launches are about a minute.
3. Main menu → **Multiplayer** → **Add Server**:
   - **Server Name:** `al Shabab`
   - **Server Address:** `SERVER_ADDRESS` ← *ask the admin*
4. **Done** → double-click the server.

---

## Step 6 — Register your password (first join only)

The server runs in offline mode so that friends without a Minecraft account can play too. That
means it asks everyone — including you — to set a password.

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

## Step 7 — Shaders

Already installed, nothing to download. **Escape → Options → Video Settings → Shader Packs**:

- **Complementary Unbound** — best looks-per-frame. Start here.
- **Photon** — semi-realistic, demanding.
- **Solas** — stylized fantasy with colored lighting.

Pick **(none)** on that screen to turn them off.

> **Why there's no OptiFine.** It's incompatible with Valkyrien Skies (ships, airships, mechs)
> and conflicts with Create. The pack ships **Embeddium + Oculus**, which are faster than
> OptiFine and load the identical shaderpack files. Installing OptiFine here will crash you.

---

## Updating

Re-run `update.bat` (with your game directory path) whenever the admin announces an update.
Worlds and settings are preserved; only mods change.

---

## Rules that keep you connected

- **Don't add mods.** The server inspects your mod list on join and kicks anyone running xray,
  fullbright, freecam, killaura, baritone, or ESP mods. A non-cheat mod the server lacks will
  usually crash you instead.
- **Xray resource packs don't work.** The server strips ore locations out of the data it sends
  you, so an xray pack renders plain stone. Nothing to gain.
- Want a mod added for everyone? Ask the admin.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| Forge installer won't open | Right-click → *Open with* → *Java(TM) Platform SE binary*. Java 17 must be installed. |
| Forge version missing in launcher | You installed the *server*, not the *client*. Re-run, pick **Install client**. |
| `ERROR: ... not found` from the script | Wrong path. Pass your real game directory in quotes. |
| Game closes right after Play | Raise `-Xmx` to 6G–8G in the installation's JVM Arguments. |
| Crash on joining server | Pack out of date. Re-run `update.bat`. |
| Kicked: *"Please use the official modpack"* | Extra/cheat mod present. Delete it from the `mods` folder, re-run the script. |
| `You are not whitelisted` | Ask the admin to whitelist your username. |
| Low FPS | Shaders off, render distance 8, allocate 8G. |

Still stuck? Send the admin `logs\latest.log` from your game directory.
