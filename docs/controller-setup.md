# Playing Shabab 2 with a PS5 controller (and running it on a laptop)

This covers two things that were set up together for a laptop player:

- **Controllable** — full gamepad support, including the PS5 DualSense.
- **Embeddium** — the renderer that was missing from the pack and is the single biggest
  frame-rate win on weak hardware.

Both are **client-side**. They ship in `pack-two/` and reach players through the normal
install/update flow — nobody has to download a jar by hand.

---

## What was actually added, and why

### Controllable (`mods/controllable.pw.toml`)

The pack already had **Controlling**, and that is easy to mistake for controller support — it
isn't. Controlling is a *keybind-conflict search box* for the Controls menu. It has nothing to do
with gamepads. There was no gamepad mod in the pack at all.

**Controllable** (by MrCrayfish, CurseForge project `317269`, version
`controllable-forge-1.20.1-0.21.9`) is the one that reads a physical controller. It detects the
DualSense over USB or Bluetooth, drives movement/look/menus from the sticks and buttons, draws an
on-screen cursor for inventories, and shows PlayStation button prompts in the UI.

### Embeddium (`mods/embeddium.pw.toml`)

This one is a fix as much as an addition. Shabab 2 already shipped **three Embeddium companion
mods** — Options API, Options Mod Compat, and Sodium/Embeddium Dynamic Lights — plus **Oculus**,
which *requires* Embeddium (or Rubidium) to run. But the base **Embeddium renderer itself was
never in the pack.** Those four mods had nothing to attach to.

Embeddium is the Forge port of Sodium: it rewrites Minecraft's chunk-rendering path and is the
biggest FPS gain you can get from a single mod, especially on integrated-graphics laptops. Adding
`embeddium-0.3.31+mc1.20.1` both delivers that gain and gives the existing companion mods (and
Oculus shaders) the dependency they were waiting on.

The rest of the laptop stack was already present and is left as-is: **BadOptimizations,
EntityCulling, FerriteCore, ImmediatelyFast, ModernFix, Krypton, smooth-chunk-save,
smooth-movement.** That is a strong set.

---

## Installing / updating

Nothing special. The player runs the normal Shabab 2 installer/updater (`client/install*.bat`
or the update script), which syncs from the pack. On next launch both mods are present.

If your friend is already installed, **re-running the updater** pulls the two new mods in.

---

## Connecting the PS5 controller (Windows)

**USB is the least-hassle option — use it if you can.** Plug the DualSense into the laptop with a
USB-C cable. Windows recognises it with no driver install. Done.

**Bluetooth**, if you want it wireless:

1. On the controller, hold **PS button + Create button** (the small button left of the touchpad)
   until the light bar **double-flashes** — that's pairing mode.
2. On the laptop: **Settings → Bluetooth & devices → Add device → Bluetooth**, pick
   *Wireless Controller* / *DualSense*.
3. Once paired, the light bar goes solid.

Either way, start the controller **before** launching Minecraft, or open Controllable's settings
after launch and select it (below).

---

## In-game setup (Controllable)

1. Launch the game with the controller connected.
2. **Main menu → Options → Controls → Controller Settings** (also reachable from the pause menu).
3. If more than one device shows, pick the DualSense under **Controller**.
4. Useful options in that screen:
   - **Auto Select** — grabs the controller automatically on startup.
   - **Radial Menu (default: assigned to a face/bumper combo)** — a quick-action wheel; handy for
     actions with no natural button.
   - **Cursor / dead zone / sensitivity** — tune stick look-speed and how far you push a stick
     before it registers.
   - **Rumble** — DualSense vibration; toggle to taste.

Controllable ships with a complete default PlayStation layout — left stick moves, right stick
looks, right trigger attacks/mines, left trigger uses/places, face buttons jump/sneak/etc. You can
**remap any of it** in that same screen. Movement + camera + menu navigation all work out of the
box; you don't have to configure anything to start playing.

Keyboard and controller stay live at the same time, so switching back and forth mid-session is
fine.

---

## If it doesn't work

| Symptom | Cause / fix |
|---|---|
| Controller does nothing in game | Not selected. **Options → Controls → Controller Settings**, pick the device. Enable **Auto Select** so it sticks. |
| Not detected at all | Connect it **before** launching, then restart the game. On Bluetooth, confirm it's paired and the light bar is solid in Windows. |
| Buttons mapped wrong / feels like an Xbox pad | **Steam Input** is remapping it. Either launch the game outside Steam, or in Steam → the game's Controller settings, set **"Disable Steam Input."** |
| Sticks drift / character walks on its own | Raise the **dead zone** in Controller Settings. |
| Bluetooth keeps dropping | Fall back to the **USB-C cable** — it's more stable and also charges the pad. |
| Some modded action has no button | Bind it to the **Radial Menu**, or remap a spare button in Controller Settings. |

---

## Files touched

- `pack-two/mods/embeddium.pw.toml` — new, Modrinth `sk9rgfiA`.
- `pack-two/mods/controllable.pw.toml` — new, CurseForge `317269`, forced `side = "client"`.
- `pack-two/index.toml` + `pack.toml` — refreshed by `packwiz refresh`.

Both are `side = "client"`, so the dedicated server never downloads them.
