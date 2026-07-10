# al Shabab — Quick Install (start here)

**Three steps. About 15 minutes, almost all of it waiting.**

You don't need to install Java. You don't need to install Forge. You don't need to download a
single mod. The installer does all of it.

---

## 1. Download the installer

**→ [install.bat](https://raw.githubusercontent.com/36a5/Modpacks/master/client/install.bat) ←**
(right-click the link → *Save link as…* → save it to your Desktop)

## 2. Close your launcher, then double-click it

**Close CurseForge / Modrinth / the Minecraft Launcher first.** They each rewrite their own
profile list when they exit, which would erase the profile the installer is about to create.

A window opens, asks which launcher you use, and does everything:

- installs Java 17 if you don't have it (a private copy — it won't touch anything else)
- installs Forge 1.20.1
- creates a profile/instance called **al-shabab** in that launcher, with the right amount of RAM
  for your PC — or updates it in place if you already have one
- downloads all 224 mods, the configs, and the three shaderpacks

**It takes 5–15 minutes.** Leave the window open. When it says *"You are ready to play"*, close it.

> Windows may show a blue *"Windows protected your PC"* box, because the file isn't
> code-signed. Click **More info** → **Run anyway**. If you'd rather check first, the script is
> plain text — open it in Notepad, or read it
> [on GitHub](https://github.com/36a5/Modpacks/blob/master/client/install.ps1).

## 3. Play

1. Open your Minecraft launcher.
2. Pick the **al Shabab** profile (**al-shabab** in CurseForge and Modrinth).
   *TLauncher users:* pick the version **`1.20.1-forge-47.4.18`** from the dropdown, and set
   RAM to 6–8 GB in TLauncher's settings.
3. Press **Play**. **The first launch takes 3–8 minutes** and may look frozen. It isn't — Forge
   is building caches. Later launches take about a minute.
4. **Multiplayer → Add Server**, address: `SERVER_ADDRESS` *(ask the admin)*.
5. Join. You'll spawn unable to move — press `T` and type:
   ```
   /trigger register set yourPassword
   ```
   Every join after that: `/trigger login set yourPassword`

That's everything. You're in.

---

### The join code

The first time you join you will be **blinded and unable to break blocks** until you prove you
belong. Copy the **join code** from the Discord `#server-info` channel and type it into Minecraft
chat (press `T`):

```
Shabab-2026
```
*(that is an example â€” use the real one from Discord)*

You get **3 attempts and 90 seconds**, then you are kicked. Once accepted you are remembered
forever and never asked again.

Right after that, set your personal password so nobody can join as you:

```
/trigger register set yourPassword
```
Every later join: `/trigger login set yourPassword`

## Updating

**Double-click `install.bat` again.** It syncs you to exactly what the server runs and removes
anything that shouldn't be there. Keep it on your Desktop.

---

## The three things everyone asks

**Shaders** are already installed — don't download any.
`Escape → Options → Video Settings → Shader Packs`, then pick:
**Complementary Unbound** (start here), **Photon** (semi-realistic), or **Solas** (fantasy).
Choose *(none)* to turn them off if your framerate drops.

**Voice chat** is built in. Press **`V`**, pick your microphone. People hear you when you're near
them.

**Maps:** press **`M`** for the world map. The minimap is already in the corner.

---

## Don't install extra mods

The server checks your mod list when you connect. Cheat mods — xray, fullbright, freecam,
killaura, baritone, ESP — get you kicked instantly. Even a harmless mod the server doesn't have
will usually crash you on join.

Xray **resource packs** don't work either: the server hides ore blocks before the data ever
reaches your computer, so an xray pack just shows plain stone.

Want a mod added? Ask the admin — they add it to the pack and everyone gets it on next update.

---

## If something goes wrong

| What you see | Fix |
|---|---|
| *"Windows protected your PC"* | **More info** → **Run anyway**. |
| `Could not reach GitHub` | Check your internet, try again. |
| Installer closes instantly | Right-click → *Run as administrator*. |
| Game closes right after Play | Your PC has under 8 GB RAM. Ask the admin about lowering settings. |
| Crash on joining the server | You're out of date — run `install.bat` again. |
| Kicked: *"Please use the official modpack"* | You added a mod. Delete it, run `install.bat` again. |
| `No access code entered` | Type the join code from `#server-info` in chat when you join. |
| 5 FPS | Turn shaders off; set render distance to 8. |

Still stuck? Send the admin a screenshot **and** the file `logs\latest.log` from your game
folder — `%APPDATA%\al-shabab\logs\` on the Minecraft Launcher, `%APPDATA%\.minecraft\logs\` on
TLauncher, or the instance folder in CurseForge/Modrinth.

---

## TLauncher players: your username is permanent

The server runs in offline mode so friends without a Minecraft account can play. That means the
server can't check with Mojang who you are — **your username is your identity**. Change it and
you lose your character, items, and base.

Pick your name and never change it. Ask an admin for the join code before you connect.

---

## Want more control?

These longer guides do the same thing manually, and cover instance-based launchers:

[TLauncher](tlauncher.md) · [Minecraft Launcher](minecraft-launcher.md) ·
[Prism](prism-launcher.md) · [CurseForge App](curseforge-app.md) · [Modrinth App](modrinth-app.md)

**[Prism Launcher](prism-launcher.md)** is still worth the extra setup for one reason: it
re-syncs the pack automatically on every launch, so you can never forget to update.
