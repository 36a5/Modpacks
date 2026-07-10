# al Shabab — Prism Launcher Setup ⭐ recommended

Prism is free, open source, and does one thing no other launcher does here: it can **re-sync
the modpack every single time you press Play**. You can never be out of date, never crash from
a version mismatch, never have to remember to run an updater.

**Time needed:** about 20 minutes.
**Works with:** a real Minecraft account. (Prism requires a legitimate Microsoft/Mojang login.
If you don't own the game, use **[the TLauncher guide](tlauncher.md)**.)

---

## Step 1 — Install Java 17

1. Go to **<https://adoptium.net/temurin/releases/?version=17>**
2. **Package Type:** JDK · **Version:** 17 · your OS.
3. Install it. On Windows tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
4. Reboot.

Verify in a terminal: `java -version` → `openjdk version "17.0.x"`.

---

## Step 2 — Install Prism Launcher

1. Download from **<https://prismlauncher.org/download/>** and install.
2. On first run it walks you through a setup wizard:
   - It will **auto-detect your Java installations** — pick the **17** one.
   - Set **Maximum memory allocation** to **8192 MB** (or 6144 MB if you have ≤12 GB RAM).
3. Click the **Accounts** button (top right) → **Add Microsoft account** → sign in.

---

## Step 3 — Create the instance

1. Click **Add Instance** (top left).
2. Left panel: **Custom**. Then:
   - **Name:** `al Shabab`
   - **Minecraft version:** `1.20.1`
   - **Mod loader:** click **Forge**, choose version **47.4.x**
3. **OK**. Prism creates the instance.
4. **Right-click the instance → Edit → Settings → Java** and confirm:
   - **Java installation:** your Java **17**
   - **Maximum memory allocation:** 6144–8192 MB

---

## Step 4 — Add the auto-updater (this is the magic step)

1. Right-click the instance → **Folder**. A file explorer window opens on the instance folder.
   Go into the **`.minecraft`** subfolder.
2. Download **packwiz-installer-bootstrap.jar** into that `.minecraft` folder:
   **<https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar>**
3. Back in Prism: right-click the instance → **Edit** → **Settings** → tick
   **Custom commands**.
4. In the **Pre-launch command** box, paste this exactly:

   ```
   "$INST_JAVA" -jar packwiz-installer-bootstrap.jar -g -s client https://36a5.github.io/Modpacks/pack.toml
   ```

5. Close the settings window.

That's it. From now on, **every time you press Play**, Prism downloads whatever the server
changed before the game starts. You will never manually update again.

---

## Step 5 — First launch

1. Select the instance, press **Launch**.
2. A console window shows the pack downloading — ~207 mods, configs, three shaderpacks.
   **3–10 minutes the first time.**
3. Then Minecraft starts. **First start takes 3–8 minutes** while Forge builds its caches. It
   may look frozen. It isn't. Later launches take about a minute.

---

## Step 6 — Join the server

1. Main menu → **Multiplayer** → **Add Server**:
   - **Server Name:** `al Shabab`
   - **Server Address:** `SERVER_ADDRESS` ← *ask the admin*
2. **Done** → double-click the server entry.

---

### The join code

The first time you join you are **blinded and cannot break blocks** until you prove you belong.
Copy the **join code** from the Discord `#server-info` channel and type it into Minecraft chat (press `T`).

You get **3 attempts and 90 seconds**, then you are kicked. Once accepted you are remembered
forever and never asked again.

## Step 7 — Register your password (first join only)

The server runs offline-mode so friends without accounts can join, which means everyone —
including premium players — sets a password on first join.

You'll spawn frozen in place. Press `T`, then type:
```
/trigger register set myPassword123
```

Each later join:
```
/trigger login set myPassword123
```

Change it with `/trigger change_password set newPassword`.

---

## Step 8 — Shaders

Preinstalled. **Escape → Options → Video Settings → Shader Packs**:

| Shaderpack | Character | Cost |
|---|---|---|
| **Complementary Unbound** | balanced, gorgeous — start here | moderate |
| **Photon** | semi-realistic | heavy |
| **Solas** | stylized fantasy, colored lighting | moderate |

Pick **(none)** to disable.

> **Don't install OptiFine.** It's incompatible with Valkyrien Skies and conflicts with Create,
> the two mods this pack is built around — it will crash you. The pack already includes
> **Embeddium + Oculus**, which are faster than OptiFine and read the same shaderpacks.

---

## Updating

**Nothing to do.** Press Launch; the pre-launch command syncs you to the server's exact
version. That's the entire reason to use Prism.

If you ever want to check what changed, the console window at launch lists every mod it
downloaded.

---

## Rules that keep you connected

- **Don't drop mods into the instance's `mods` folder.** The pre-launch sync will delete
  anything the pack doesn't know about — and if it somehow survives, the server kicks you for
  running an unapproved mod (xray, fullbright, freecam, killaura, baritone, ESP).
- **Xray resource packs achieve nothing.** The server obfuscates ore blocks before sending
  chunk data, so an xray pack shows you plain stone.
- Want a mod added for everyone? Ask the admin — they add it to the pack and everyone gets it
  on next launch.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| Pre-launch command fails, `Unable to access jarfile` | `packwiz-installer-bootstrap.jar` isn't in the instance's `.minecraft` folder. Move it there. |
| Console: `Failed to download pack.toml … 404` | The admin hasn't published the pack yet, or you mistyped the URL. |
| Game closes right after launch | Raise memory to 6144–8192 MB in instance Settings → Java. |
| `UnsupportedClassVersionError` | Wrong Java. Point the instance at Java 17. |
| Crash on joining server | Rare on Prism. Force a resync: delete the instance's `mods` folder, press Launch. |
| `No access code entered` | Type the join code from `#server-info` in chat when you join. |
| Low FPS | Shaders off, render distance 8. |

Still stuck? Right-click the instance → **Folder** → `.minecraft/logs/latest.log` — send that to
the admin.
