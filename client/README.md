# al Shabab — Client Installer

`update.bat` (Windows) and `update.sh` (macOS/Linux) install the modpack and update it, for
**every launcher** — TLauncher, the official Minecraft Launcher, Prism, CurseForge App, and the
Modrinth App. They download each mod straight from CurseForge and Modrinth, so nothing is
redistributed and you always land on exactly the version the server runs.

**Players: don't follow this file. Follow the step-by-step guide for your launcher:**

| Launcher | Guide |
|---|---|
| TLauncher (no Minecraft account) | [docs/guides/tlauncher.md](../docs/guides/tlauncher.md) |
| Official Minecraft Launcher | [docs/guides/minecraft-launcher.md](../docs/guides/minecraft-launcher.md) |
| Prism Launcher ⭐ | [docs/guides/prism-launcher.md](../docs/guides/prism-launcher.md) |
| CurseForge App | [docs/guides/curseforge-app.md](../docs/guides/curseforge-app.md) |
| Modrinth App | [docs/guides/modrinth-app.md](../docs/guides/modrinth-app.md) |

## Usage

```
update.bat                          # installs into %APPDATA%\.minecraft
update.bat "D:\path\to\gamedir"     # any other game folder
```
```bash
./update.sh                         # default .minecraft for your OS
./update.sh "/path/to/gamedir"      # any other game folder
```

Requires Java 17 and a Forge 1.20.1 profile already created once in your launcher.
