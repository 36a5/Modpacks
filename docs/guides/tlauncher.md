# al Shabab — TLauncher Setup (players without a Minecraft account)

**Time needed:** about 20 minutes, most of it waiting for downloads.
**Result:** you're standing in the world on the al Shabab server with 180 mods and shaders.

Nothing here asks you to download a mod by hand. One script does all of it.

---

## Step 1 — Install Java 17

The pack will not start without it, and TLauncher's bundled Java is usually the wrong version.

1. Go to **<https://adoptium.net/temurin/releases/?version=17>**
2. Set **Operating System** to your OS and **Package Type** to **JDK**.
3. Download the **`.msi`** (Windows) / **`.pkg`** (macOS) installer for version **17**.
4. Run it. On the installer screen, make sure **"Set JAVA_HOME variable"** and
   **"Add to PATH"** are enabled (click the ✕ next to them and choose *Will be installed*).
5. Reboot your computer. This matters — the PATH change doesn't apply until you do.

**Check it worked.** Press `Win + R`, type `cmd`, press Enter, then type:
```
java -version
```
You should see `openjdk version "17.0.x"`. If you see 1.8 or 21, Java 17 isn't first on your
PATH — uninstall the other version, or ask the admin for help.

---

## Step 2 — Install TLauncher

1. Go to **<https://tlauncher.org/en/>** and download the installer.
2. Install it and open it.
3. Create your account name in the top-right. **Choose carefully.**

> ### Your username is permanent
> The server runs in **offline mode**, the only way non-premium accounts can join. That means
> the server has no way to check with Mojang who you are — **your username *is* your identity**.
> If you change it, you lose your character, inventory, base, and skills. The server treats you
> as a brand-new player.
>
> Pick your name now. Tell it to the admin. Never change it.

---

## Step 3 — Install Forge 1.20.1

1. In TLauncher's version dropdown (next to the big Play button), click it and find
   **Forge 1.20.1**. Pick the newest **47.4.x** build.
2. Press **Install**, wait for it to finish.
3. Press **Play once** and let the game reach the main menu, then **close it**.
   This step creates the game folder the next step needs. Don't skip it.

---

## Step 4 — Give the game enough memory

1. TLauncher → **Settings** (gear icon, top right) → **Minecraft settings**.
2. Set **Memory (RAM)** to **6144 MB** (6 GB) if you have 8–12 GB of system RAM, or
   **8192 MB** (8 GB) if you have 16 GB or more.
3. Never allocate more than about half your total RAM — the rest is needed by Windows.

---

## Step 5 — Install the modpack

1. Download the installer script:
   **<https://raw.githubusercontent.com/36a5/Modpacks/master/client/update.bat>**
   (Right-click the link → *Save link as…* → save it somewhere you'll find again, like your
   Desktop. If your browser warns about a `.bat` file, keep it — it's a plain text script and
   you can open it in Notepad to read exactly what it does.)

2. **Double-click `update.bat`.**

   A black window opens and starts downloading ~180 mods, the configs, and the three
   shaderpacks. **This takes 3–10 minutes** depending on your internet. Leave it alone.

3. When you see `[al-shabab] Done.`, press any key to close it.

**If TLauncher uses a custom game folder** (you'd know — it's in Settings → *Directory*), then
instead of double-clicking, open Command Prompt in the folder where you saved the script and
run:
```
update.bat "D:\your\custom\.minecraft"
```

**If you see `ERROR: Java is not installed`** — go back to Step 1, and remember to reboot.

---

## Step 6 — Launch and join

1. Open TLauncher, make sure the version still says **Forge 1.20.1**, press **Play**.
2. **The first launch takes 3–8 minutes.** The window may say *Not Responding*. It is not
   frozen. Do not close it. Later launches take about a minute.
3. At the main menu, click **Multiplayer** → **Add Server**.
   - **Server Name:** `al Shabab`
   - **Server Address:** `SERVER_ADDRESS` ← *ask the admin for this*
4. Click **Done**, then double-click the server to join.

---

## Step 7 — Register your password (first join only)

The moment you spawn you won't be able to move. That's intentional. Type this in chat
(press `T` to open chat):

```
/trigger register set myPassword123
```

Replace `myPassword123` with a password you'll remember. You can move now.

**Every time you join after this**, type:
```
/trigger login set myPassword123
```

To change it later: `/trigger change_password set newPassword`

This exists because offline mode means anyone could connect using your username. The password
is what stops them.

---

## Step 8 — Turn on shaders (optional, looks incredible)

Shaders are already installed. **Escape → Options → Video Settings → Shader Packs**, then
click one:

- **Complementary Unbound** — start here. Beautiful and the fastest of the three.
- **Photon** — semi-realistic, needs a stronger graphics card.
- **Solas** — stylized fantasy, colored lighting.

If your framerate tanks, come back to this screen and pick **(none)**.

> There is no OptiFine in this pack, and there can't be: it crashes with Valkyrien Skies, the
> mod that makes ships and airships work. The pack uses **Embeddium + Oculus** instead — faster
> than OptiFine, and it loads the same shaderpacks. You're not missing anything.

---

## Updating, forever

When the admin ships an update, **double-click `update.bat` again**. That's the whole process.
Your world, keybinds, and settings are untouched — only mods change.

Keep the script on your Desktop. You'll use it a lot.

---

## Don't install extra mods

The server checks your mod list when you connect. Cheat mods (xray, fullbright, freecam,
killaura, baritone) get you **kicked immediately**. Even a harmless mod the server doesn't have
will usually crash you on join.

If you want a mod added, ask the admin — they can add it to the pack so everyone gets it.

Also, xray **resource packs** don't work here either. The server hides ore locations before
they ever reach your computer, so an xray pack just shows you plain stone.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| `ERROR: Java is not installed` | Install Java 17 (Step 1). **Reboot.** |
| `ERROR: %APPDATA%\.minecraft not found` | You skipped Step 3's "Play once". Do it, then re-run the script. |
| Game closes right after clicking Play | Not enough RAM allocated. Set 6 GB (Step 4). |
| Crash on launch, log mentions `mixin` | Your pack is half-installed. Re-run `update.bat`. |
| Crash when joining the server | You're on an old pack version. Re-run `update.bat`. |
| `You are not whitelisted on this server` | Ask the admin to whitelist your exact username. |
| Kicked: *"Please use the official modpack"* | You have an extra/cheat mod. Delete it from `.minecraft\mods`, re-run `update.bat`. |
| `Incorrect password` | Passwords are case-sensitive. Ask the admin to reset yours. |
| 5 FPS | Turn shaders off, set render distance to 8. |
| Voice chat silent | Press `V`, select the correct microphone. |

Still stuck? Send the admin a screenshot **and** the file
`%APPDATA%\.minecraft\logs\latest.log`.
