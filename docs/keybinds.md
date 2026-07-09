# al Shabab — Keybind Layout

The pack ships `config/defaultoptions/keybindings.txt`, applied by the **Default Options** mod.
It sets these keys on a **fresh install only** — once a player rebinds something, their choice
sticks and updates never clobber it.

The layout was not hand-written. Every keybind was extracted from the 196 client mod jars
(`assets/*/lang/en_us.json`, keys named `key.*`), giving **178 registered keybinds across 41
mods**. The bindings below are generated and machine-checked for three kinds of conflict:

1. mod against mod,
2. mod against **vanilla** (`W A S D E Q F T Tab Esc Space Shift Ctrl L P 1-9 F1-F11`),
3. mod against **Xaero's Minimap / World Map**, which register their keys at runtime and cannot
   be rebound from a file — so `M` and `Y` are treated as reserved and routed around.

## Progression menus — one key each

| Key | Opens |
|---|---|
| `K` | **Skill Tree** (Pufferfish's Skills) |
| `N` | **Skills** (Reskillable Reimagined) |
| `H` | **Solo Leveling** main menu |
| `J` | **Beastiary** (Lycanites) |
| `O` | **Pets** (Lycanites) |
| `I` | **Minions** (Lycanites) |
| `;` | Difficulty meter (Scaling Health) |
| `'` | Mo' Bends settings |
| `.` | Jade config |

## Abilities

| Key | Action |
|---|---|
| **Mouse 4** | **Special ability** (Alex's Caves — mounts & armor sets) |
| **Mouse 5** | Cataclysm ability |
| `R` | Solo Leveling — use skill |
| `X` | Solo Leveling — select skill |
| `G` | Solo Leveling — target lock |
| `C` | Dodge roll (Elenai Dodge 2) |
| `Z` | Summoning (Lycanites) |
| `]` | Gravitite jump (Aether) |
| `B` | Carry On |

## Voice chat

Bound explicitly, because Simple Voice Chat's default `V` would otherwise silently collide
with whatever else claimed it.

| Key | Action |
|---|---|
| `V` | Voice chat menu |
| `Caps Lock` | Push to talk |
| `\` | Mute microphone |
| `Left Alt` | Whisper |

Settings, volumes and group management are **unbound** — all three are reachable from inside
the `V` menu.

## Mounts

| Key | Action |
|---|---|
| `[` | Mount ability |
| `Page Down` | Mount descend |
| `U` | Mount inventory |
| `,` | Unperch pet |

`Ctrl` is deliberately avoided here — it is vanilla sprint.

## Storage / tools

| Key | Opens |
|---|---|
| `` ` `` | Backpack (Sophisticated Backpacks) |
| `/` | Tool belt |
| `-` | Storage terminal (Tom's) |

## Deliberately unbound

Every one of these is reachable another way, so the key is freed rather than wasted:

| Was | Why it's unbound |
|---|---|
| Curios inventory | click the Curios tab inside the normal inventory |
| Aether accessories | same inventory tab |
| Relics ability HUD | a display toggle, not worth a key |
| Beastiary Index | lives inside the Beastiary (`J`) |
| Fancy Block Particles (7 keys) | cosmetic mod — configure it in its config file |
| Jade narrate / toggle fluid | niche accessibility toggles |
| Voice chat settings / volumes / groups | inside the `V` menu |
| Voice chat hide-icons / disable / record | niche |

## Regenerating

The generator lives in the repo history and validates before writing; it refuses to emit a file
with a duplicate key, a vanilla collision, or an ID that no installed mod actually registers.

## Known gap: Origins

There is **no Origins mod in this pack** — Origins and Origins: Classes were removed as
non-RLCraft progression systems. So there is no "Origins special ability" to bind.

**Mouse 4** is instead bound to **Alex's Caves' Special Ability**, which is the pack's
general-purpose "activate my mount/armor power" key. If Origins is ever re-added, its primary
ability key is `key.origins.primary_active` and Mouse 4 is free to take it.
