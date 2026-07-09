# al Shabab — Client Installers

## `install.bat` — the one players should use

Download it, double-click it, done. It:

1. finds Java 17, or downloads a private portable copy (no admin rights, touches nothing else)
2. installs Forge 1.20.1 into the game folder
3. downloads the modpack — mods, configs, shaderpacks
4. creates an **al Shabab** launcher profile with RAM sized to the player's machine

Re-running it is the update path: it's idempotent, and it removes anything the pack no longer
ships. Works with TLauncher and the official Minecraft Launcher.

`install.bat` is a small wrapper that downloads and runs `install.ps1`, so players only ever
handle one file.

```
install.bat                          # installs into %APPDATA%\.minecraft
install.bat "D:\path\to\gamedir"     # any other game folder
```

## `update.bat` / `update.sh` — pack-only sync

For players who already have Java and Forge set up (Prism, CurseForge App, Modrinth App
instances). Syncs mods/configs/shaderpacks and nothing else.

```
update.bat "D:\path\to\gamedir"
./update.sh "/path/to/gamedir"       # macOS / Linux
```

`update.sh` is also the macOS/Linux equivalent of `install.bat` for the pack step — those
players install Java and Forge through their launcher.

---

**Players: don't follow this file.** Follow a guide:

| | |
|---|---|
| **[Quick Install](../docs/guides/quick-install.md)** ⭐ | one file, one double-click |
| [TLauncher](../docs/guides/tlauncher.md) | manual, no Minecraft account |
| [Minecraft Launcher](../docs/guides/minecraft-launcher.md) | manual, official launcher |
| [Prism Launcher](../docs/guides/prism-launcher.md) | auto-syncs every launch |
| [CurseForge App](../docs/guides/curseforge-app.md) | |
| [Modrinth App](../docs/guides/modrinth-app.md) | |
