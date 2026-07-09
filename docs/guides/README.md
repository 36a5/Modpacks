# al Shabab — Player Guides

## → Just want to play? [**Quick Install**](quick-install.md) ←

**Download one file, double-click it, press Play.** The installer handles Java, Forge, all 162
mods, the configs, the shaderpacks, and your launcher profile. It works with TLauncher and the
official Minecraft launcher, and re-running it is how you update.

That's the whole thing. **Most players should stop here.**

---

## The manual guides

Use these if you want to understand each step, or if you use an instance-based launcher.

| Your situation | Guide |
|---|---|
| I don't own Minecraft (I use TLauncher) | [TLauncher](tlauncher.md) |
| I own Minecraft and use the normal launcher | [Minecraft Launcher](minecraft-launcher.md) |
| I want auto-updates on every launch | [Prism Launcher](prism-launcher.md) ⭐ |
| I already use the CurseForge App | [CurseForge App](curseforge-app.md) |
| I already use the Modrinth App | [Modrinth App](modrinth-app.md) |

Every path installs **exactly the same pack** — the same mods, configs, and shaderpacks the
server runs. None of them require you to download a mod by hand.

---

## What you need (the Quick Install handles all of this for you)

| Thing | Where | Notes |
|---|---|---|
| **Java 17** | <https://adoptium.net/temurin/releases/?version=17> | Pick *JDK 17*, `.msi` on Windows. Not Java 8, not Java 21. |
| **8 GB free RAM** | — | The pack needs 6–8 GB allocated. 16 GB total system RAM is comfortable. |
| **~6 GB free disk** | — | Mods, worlds, and shaders. |
| **Your username** | — | Ask the admin to whitelist it *before* you try to join. |
| **Server address** | Ask the admin | Referred to below as `SERVER_ADDRESS`. |

> **TLauncher players — read this once.** The server runs in offline mode, which means your
> **username is your identity**. There is no password protecting it at the Mojang level, so if
> you change your username you lose access to your character, your items, and your base. Pick
> the name you want, tell the admin, and never change it.

---

## After you're in: the things every player asks about

### Shaders
Three shaderpacks come preinstalled — you do not download them.
**Options → Video Settings → Shader Packs**, then pick one:

- **Complementary Unbound** — best balance of looks and framerate. Start here.
- **Photon** — semi-realistic, heavier.
- **Solas** — stylized fantasy with colored lighting.

Press `Escape → Options → Video Settings → Shader Packs → (none)` to turn shaders off if your
framerate drops.

There is **no OptiFine** in this pack, on purpose: it crashes with Valkyrien Skies (the ships
and airships mod) and conflicts with Create. The pack ships **Embeddium + Oculus** instead,
which run faster than OptiFine and load the exact same shaderpacks.

### Voice chat
Simple Voice Chat is built in. Press **`V`** to open its menu, pick your microphone, and talk —
other players hear you based on how close you are. Press **`V` then `M`** to mute.

### Logging in to the server
Because the server is offline-mode, it makes you set a password the first time you join.

| When | Command to type in chat |
|---|---|
| First join ever | `/trigger register set yourPassword` |
| Every join after | `/trigger login set yourPassword` |
| Changing it later | `/trigger change_password set newPassword` |

You can't move or break blocks until you log in. This is what stops someone joining under your
name and emptying your chests.

### Maps
Press **`M`** for the full world map, and the minimap sits in the corner. Waystones you find
while exploring let you teleport back to them later.

---

## Updating

Whenever the admin ships an update, you re-run the same installer you used to set up
(`update.bat` / `update.sh`), or just relaunch if you're on Prism. Your world, settings, and
keybinds are untouched — only mods change.

**Never** add or remove mods yourself. The server checks your mod list when you connect and
will kick you if it sees a cheat mod, and a mod the server doesn't have will make you crash on
join.

---

## When something breaks

| Symptom | Fix |
|---|---|
| `Java is not installed` | Install Java 17 (link above), reboot, try again. |
| Game crashes instantly on launch | You allocated too little RAM. Set 6–8 GB. |
| Crash on joining the server | Your pack is out of date. Re-run the installer. |
| Kicked: "Please use the official modpack" | You have an extra mod installed. Delete it and re-run the installer. |
| "You are not whitelisted" | Ask the admin to add your exact username. |
| Only 5 FPS | Turn shaders off, lower render distance to 8. |
| Voice chat not working | Press `V`, choose the right microphone in the menu. |

Still broken? Screenshot the error and send it to the admin along with `latest.log` from your
game folder's `logs/` directory.
