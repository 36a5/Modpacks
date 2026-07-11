# Shabab 2 — Systems Guide

Everything the 379-mod pack adds, what keys drive it, and how to get started with each.
All keys are rebindable in **Esc → Options → Controls**. The combat keys below are the
pack defaults (chosen so no two abilities or menus share a key).

---

## 🔐 Joining (first time)

The server runs in **offline mode** (so TLauncher / non-premium accounts can play), protected
by two layers:

1. **Password (Auth mod)** — you're frozen in spectator until you set one:
   - First join: `T` → `/trigger register set yourPassword`
   - Every join after: `T` → `/trigger login set yourPassword`
   - **Numbers only.** You get 3 tries, and 10 minutes — enough time for a slow client to
     finish loading before it asks you for anything.
2. **Join-code gate** — then you're held in adventure mode + blindness until you type the
   server's join code (posted at the top of the server channel):
   - `T` → `/trigger joincode set 1234` (with the real code)
   - 3 tries, 10 minutes, then you're kicked. Once you pass, you never see it again.

> ⚠️ **TLauncher players: your username is your account.** Offline mode means the server
> can't ask Mojang who you are — change your name and you lose your character, items and base.

---

## ⚔️ Epic Fight — action combat

Turns melee into a third-person, combo-based action game with dodges, guards and weapon skills.

| Key | Action |
|-----|--------|
| `K` | **Switch Battle / Mining mode** — draw or sheathe your weapon. You only get combos in Battle mode. |
| `N` | **Lock-on** to the nearest target |
| `Mouse 5` | **Dodge** |
| Right-click | **Guard** (with a weapon drawn) |
| `=` | **Weapon innate skill** (each weapon has a hidden special) |
| `` ` `` | **Skill editor** — assign learned skills to slots |

**How to use:** press `K` to enter Battle mode, then left-click for combos, hold right-click to
guard, `Mouse 5` to dodge. Different weapon types (sword, spear, greatsword, dual, etc.) have
their own movesets. **Weapons of Miracles** and **EpicFight: Resurrection** add extra weapons
and skills that plug into this same system — find/craft those weapons and their skills appear in
the skill editor (`` ` ``).

---

## 🌀 Solo Leveling — Gates, levels & jobs

The headline system. Kill monsters → gain XP → level up your "System" → unlock jobs and skills.

Solo Leveling has **first claim on the keyboard** — it gets the prime keys and every other mod
was moved out of its way. Nothing is on the numpad, so laptops are fine.

| Key | Action |
|-----|--------|
| `Z` `X` `C` `V` | **Job abilities 1–4** |
| `R` | **Use** the selected skill |
| `B` | **Cycle** to the next skill |
| `G` | Sword enhance |
| `Mouse 4` | Triple jump |
| `;` (semicolon) | **Open the System panel** — level, stats, skills, job |
| `'` (apostrophe) | Quest info |
| `Home` | Training |

Your health is the **Solo Leveling HP bar** — the vanilla hearts, hunger, armor and XP bars are
switched off for you automatically on first join. Want them back? `/ToggleCustomHUD`.

**Gates (the "portals"):** colored Gates (ranks **E → D → C → B → A → S**) spawn randomly in the
world (within ~200 blocks of players). Enter one to be sent into a **Gate dungeon dimension**
full of monsters and a boss.
- **Clear the boss before the timer** to close the Gate and get loot.
- **Fail and the timer runs out** → a **Dungeon Break** dumps those monsters into the Overworld.
- Higher rank = stronger monsters, better rewards.

**How to progress:** open the panel (`;`), spend stat points as you level, pick a job, and slot
skills. Kill things inside Gates to climb fast — those kills also feed the **weekly Gate-kills
leaderboard** (see below).

---

## 🏆 Weekly Gate-kills leaderboard

Every monster you kill **inside a Solo Leveling Gate dimension** is counted. The board **resets
every Sunday**.

- **In Discord:** type `!gates` for the current week's ranking.
- **Under the hood:** a datapack tracks a `pk_total` scoreboard; the bot reads it over RCON and
  ranks players by kills gained since Sunday. Admins can hard-reset with `/function gate_kills:reset`.

---

## 🏘️ MineColonies — build a town with NPC workers

No keybind — it's block-and-GUI based.

1. Craft a **Supply Camp** (or Supply Ship) and place it per the preview.
2. That gives you a **Town Hall** and a **Builder's Hut**.
3. Place the Builder's Hut, hire a builder, then use the **Build Tool** to place more huts
   (farmer, miner, guard, etc.). Builders construct them from your resources over time.
4. Manage citizens, jobs and requests from the Town Hall GUI.

Great for a co-op base — split roles across players.

---

## 🚗 Vehicles — cars, planes & more (Immersive Vehicles)

Build and drive real vehicles.

1. Craft the **Immersive Vehicles crafting bench** (from the mod's recipes / JEI).
2. Craft vehicle **parts** and the vehicle item, then place/assemble it.
3. Add wheels, engine, fuel; right-click to enter, standard movement keys to drive/fly.
4. The **Official Content Pack** adds ready-made cars, planes, helicopters, tanks and trucks.

Look mods up in **JEI** (item list, right side) — hover an item and press the recipe key to see
how to make it.

---

## 🧭 Getting around — teleports & map

| Command / Key | What |
|---------------|------|
| `/tpa <player>` | Ask to teleport to someone (**TPA++**) |
| `/tpahere <player>` | Ask them to teleport to you |
| `/tpaccept` / `/tpdeny` | Answer a request |
| `/back` | **Teleport back** to where you last died (**Better /back**) |
| `M` | Open the **world map** (Xaero) — minimap is top-right |
| Xaero waypoints | Set waypoints on the map to navigate |

---

## 🎙️ Voice chat (Simple Voice Chat)

| Key | What |
|-----|------|
| `\` (backslash) | Voice chat menu |
| `-` (minus) | **Group management** — create/join a voice group |
| `Right Alt` | Mute mic |
| — | Push-to-talk is off; voice activates when you speak |

Proximity voice — you hear players near you. Configure devices in the `\` menu.

---

## 🗺️ Extra worlds, bosses & dungeons

The BMC4 base + our additions pack in a huge amount of exploration content:
- **Dimensions:** the Aether, Twilight Forest, Blue Skies, Deeper & Darker, **Lost Cities**
  (ruined-city world), **The Undergarden**, plus the Solo Leveling Gate dimensions.
- **Bosses:** L_Ender's Cataclysm, Mowzie's Mobs, **Bosses'Rise** (Souls-like), **Born in Chaos**,
  **Mutant Monsters**.
- **Dungeons/structures:** When Dungeons Arise, YUNG's suite, **Dungeons & Taverns**,
  **CTOV** villages, AdoraBuild, Structory + Towers.

Most bosses drop unique gear; many armor pieces have an **ability** on a key — check the item
tooltip. Cataclysm's armor abilities moved off `X`/`V`/`C`/`Y` (Solo Leveling owns those now)
onto four keys in a row: `[` = ability, `]` = helmet, `,` = chestplate, `.` = boots.

---

## ✨ Shaders

Pre-installed. `Esc → Options → Video Settings → Shader Packs`:
- **Complementary Unbound** (start here) · **Complementary Reimagined** · **Photon** · **Solas**
- For weaker PCs: open a shader's settings and pick the **Potato / Low** profile, or select
  **(none)**.

---

## 🔄 Updating

When the bot pings **@Minecraft** that the pack updated, run **install-shabab2.bat** again.
Your world, settings and keybinds are kept.

## 🧰 Full keybind reference

Every combat/menu key set by the pack is in
[`pack-two/options.txt`](../../pack-two/options.txt) (search `epicfight` / `sololeveling`).
Rebind anything in-game via **Esc → Options → Controls**.
