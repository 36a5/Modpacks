# Changelog

## [Unreleased]

### Shabab 2: Gates and the Punishment Zone return you to where you entered (slb-return datapack)

- **The mod always dropped you in the Overworld** when you left a Gate dungeon or the Punishment
  Zone — at its own saved/random point, never where you actually came from. Enter a Gate from the
  Twilight Forest, the Aether, the Nether, or just a base the mod had not recorded, and you came out
  somewhere you had never been. Two players clearing one Gate were both flung to the mod's single
  stored point.

- **Now you return to the exact block and the exact dimension you entered from.** A per-player
  invisible `marker` rides along through every safe dimension, recording the last spot you stood in;
  the instant you step into a Gate or the Punishment Zone it freezes there and its chunk is
  force-loaded so it survives while you are away. When the mod spits you back out, the datapack
  overrides its drop and sends you to the marker. Every player is tracked independently, so a whole
  party each land back on their own doorstep, in whatever dimension that was.

- Pure datapack (`config/paxi/datapacks/slb-return`), loaded by Paxi on the server. No mod, no jar
  change — the Solo Leveling mod is closed-source and this needed no patch to it. Covers all 11 Gate
  dungeon dimensions and the `system_void` Punishment Zone.

### Shabab 2: Damage numbers now show *other players'* hits on mobs (shababparty 1.22.0)

- The floating damage numbers already showed damage **you** dealt, and damage dealt **to you**. They
  said nothing about the person fighting the same boss next to you. **Now a fourth bucket — "Other
  players to mobs" (aqua by default) — shows every hit your allies land on a shared target**, so a
  party can read each other's damage in a group fight.

- Server-side, the hit is broadcast to every player within 48 blocks of the mob in the same
  dimension, at `EventPriority.LOWEST` like the existing buckets, so the number is the real
  post-everything figure. The dealer is skipped — they already see their own hit. **Mobs only**:
  player-versus-player damage stays private to the two duelists (still covered by "Players to you").

- New bucket has its own colour, on/off toggle and hex field in **Options → Controls → Shabab Party
  → Damage Numbers Settings**, exactly like the other three. Per-player, client-side config.

### Shabab 2: The Ender Dragon is a four-phase fight (slb-endgame datapack)

- Vanilla's dragon has one script. This stages it: **75%** a Shadow phantom wave and first-blood
  cry; **50%** enrage — a bigger wave plus a **Void barrage** raining fire on random fighters;
  **25%** a **second wind** where the dragon heals once and summons the **Herald of the Void**, a
  netherite mini-boss (permanent Resistance, hits hard, guaranteed sword drop). Thresholds are read
  from the dragon's own max health, so it works on the 150,000-HP boss-scaled dragon too.

- Pure datapack (`config/paxi/datapacks/slb-endgame`), Paxi-loaded, cheap (drives only a live
  dragon). No mod to install. Full-scale new dungeons/bosses remain the job of the pack's structure
  mods (Cataclysm, Bosses'Rise, Mowzie's) — this adds an endgame the whole server has to coordinate.

### Shabab 2: Floating damage numbers (shababparty 1.21.0)

- **Damage now shows up as numbers in the world**, in three separately-coloured buckets: damage
  **you deal** (yellow), damage **mobs deal to you** (red), and damage **other players deal to
  you** (purple). Each number reads `raw (final)` — what the weapon rolled, and what actually came
  off the health bar. The gap between the two is the target's armour, which is the whole point:
  with 391 mods installed, a weapon's tooltip damage says very little about what it does to an
  armoured Cataclysm boss.

- **NUMPAD 5** toggles every number on and off; **NUMPAD 6** opens the settings screen. Both are
  registered as real keybinds, so they appear under **Options → Controls → Shabab Party** and can
  be rebound there like any other key.

- **Settings screen** covers per-bucket colour (five presets from Minecraft's chat palette, or any
  hex value you type), per-bucket hide, whether to show raw and final, text size, how long numbers
  linger, and a cap on how many can be on screen at once. Also reachable from the Mods list.
  Settings are per-player and live in `config/shababparty-client.toml` — the server does not
  dictate them.

- **The numbers are the real ones.** They are read at `EventPriority.LOWEST`, after every damage
  modifier in the pack has run — weapon base damage, enchantments, Epic Fight's combat maths, the
  Solo Leveling Strength multiplier from `PlayerPower`, boss scaling and damage relief. Nothing is
  recomputed from weapon stats or player level, so what is displayed cannot drift from what was
  dealt.

### Shabab 2: Op ghost mode (Vanishmod) and the h4mod1 un-deopable OP fix

- **Ops can now go full ghost with one command.** Added **Vanishmod 1.1.21** (`side = "server"` —
  clients download nothing) and a **`ghost-commands`** datapack. `/function shabab:vanish` runs the
  mod's `/vanish` (a fake yellow `multiplayer.player.left` message — pixel-identical to a real
  leave — plus removal from the tab list, entity untracking so Xaero's radar/minimap drops you, and
  sound/particle suppression) and then puts you in spectator for noclip. `/function shabab:unvanish`
  reverses it via creative (not survival, so leaving spectator mid-air can't kill you). Voice chat
  still works while vanished: the server already had `spectator_interaction=true` and
  `spectator_player_possession=true`, so a ghosted op can talk to nearby players and to whoever
  they're spectating.

- **Fixed h4mod1's un-deopable OP.** `ops.json` held his old **online-mode (v4) UUID**
  `9a218f21-…`, but on this offline-mode server he resolves to the v3 UUID `4ed7106f-…` — so
  `/deop h4mod1` looked up the offline UUID, found no op entry, and reported "Nothing changed."
  Any hand-edit of `ops.json` while the server ran was silently rewritten from memory on shutdown,
  which is why the entry "kept coming back." Removed the stale entry from `ops.json` and purged the
  dead v4 records from `usercache.json` / `usernamecache.json` — done with the server stopped, so
  it sticks.

### Shabab 2: Gate teleport buttons, rolled jobs, and an all-time leaderboard

- **`colonyspeed` 1.1.0 — a worker's inventory now grows with his hut level.** The speed half of this
  mod saturates early (the per-block delay bottoms out at the 1-tick floor by hut level 3–4), so
  upgrading a Builder past that bought nothing. The reason a Builder *feels* the same at level 5 is
  that he isn't placing-speed bound, he is **walking bound**: `AbstractEntityAIStructure` sends him
  home to dump the moment `InventoryCitizen.hasSpace()` goes false, and stock citizen inventory is a
  flat **27 slots** — set by research alone, with no config anywhere and no reference to the hut level.

  New `InventoryCitizenMixin` scales it: **27 / 36 / 54 / 63 / 81** slots for hut levels 1–5.

  **81 is a hard ceiling and `maxSlots` is range-limited to it.** `WindowCitizenInventory` sizes
  itself as `114 + min(9, rows) * 18` and does not scroll, so slots past the ninth row would render on
  top of the player's own inventory. It is not a real limit in practice: slots hold *stacks*, so 81
  slots is 81 distinct block types at 64 each, and no MineColonies blueprint asks for anywhere near
  that many. A level 5 Builder effectively stops going home for materials.

  Injected at `InventoryCitizen.write`, the one place the mod recomputes its own size
  (`resizeInventory(size, 27 + researchEffect)`), so the new size lands on the next citizen save —
  the same latency MineColonies' own research bonus has. The scaler only ever grows the inventory,
  never shrinks it, so a downgraded or destroyed hut cannot drop items out of slots that vanish.

- **Click a Gate open notification to teleport to it.** Solo Leveling announced a new Gate as flat
  text — you read the coordinates and walked. New **`slb-gates`** datapack replaces that line with
  one carrying the same coordinates plus a **`[ TELEPORT ]`** button, and turns the mod's own
  message off (`soloGateNotification false`) so there is only one line per Gate.

  The button runs **`/trigger`**, not `/tp`: only two players are OP and `/tp` needs level 2, so a
  `/tp` button would have worked for nobody. The datapack does the teleport on the player's behalf.
  Gates take slot numbers 1–9, recycled — the tenth Gate takes slot 1 back and the oldest button
  goes dead rather than sending someone to the wrong place.

- **The Job Change Quest now rolls a random job, and can be re-rolled.** Vanilla Solo Leveling
  always ended the quest in **Shadow Monarch**, and handed out exactly one Job Change Quest Key per
  player per life (at level 40). New **`slb-jobs`** datapack:

  - finishing the quest awards a random job — **Shadow Monarch / Grand Mage / Frost Monarch**
  - finishing it again **re-rolls**, and never returns the job you walked in with, so a re-roll
    always changes something
  - a **bonus Job Key at level 100, 150, 200, …**, on top of the mod's own level-40 key. Anyone
    already past 100 is paid retroactively.
  - **Monarch of White Flames is sealed.** It was never obtainable in-game anyway — nothing but
    `/slr <player> job MonarchOfWhiteFlames` writes `JOB=4` — but the datapack also strips it from
    anyone holding it.

  This needs **`function-permission-level=4`** (now `[enforce]`d in `server.properties.template`).
  The datapack awards jobs through the mod's own `/slr`, which is permission level 3; at the
  default of 2 those commands fail **silently** and every quest quietly ends in Shadow Monarch
  again.

- **Fixed the Gate-kills counter double-crediting whenever two players were online.** `gate_kills`
  snapshotted with `scoreboard players operation @a pk_prev = @a pk_kills`, which reads as if it
  pairs each player with themselves. It does not: the command applies every target against every
  source in a **nested loop**, so everyone's `pk_prev` ended up holding whichever player was
  iterated last. Anyone above that player was then credited the difference *again on every tick*
  they stood in a Gate. Now snapshotted per player. **Existing `pk_total` values are inflated — run
  `/function gate_kills:reset` once.**

- **The leaderboards are all-time and refresh daily.** Both boards ranked by what a player gained
  since a Sunday snapshot, and wiped themselves every week. They now show **lifetime totals**, the
  bot re-renders them **once a day**, and nothing resets — the standing message is edited in place
  and simply climbs. The weekly baseline is gone from `.state.json`.

- **`reset-player-deaths.ps1` → `set-player-deaths.ps1`**, which takes a count:
  `.\set-player-deaths.ps1 Abdulrhman-S 5`. It no longer has to zero a leaderboard baseline,
  because there is no baseline any more.

- **New docs:** [admin-commands.md](admin-commands.md) (Epic Fight skill-book `/give` commands and
  drop rates, the full `/slr` tree, the Gate and job datapacks) and
  [guides/minecolonies-schematics.md](guides/minecolonies-schematics.md) (styles, the Build Tool,
  the Scan Tool, substitution blocks, custom structure packs).

### Shabab 2: parties that actually work, shared XP, and Epic Fight on the keyboard

- **Fixed FTB Teams on the offline-mode server.** The team screen showed *"Team data has not been
  received from the server! … ensure your server is in online mode"* and parties were unusable.
  The server was fine — `world/ftbteams/` had the data and the logs had no errors. FTB Teams asks
  the **launcher** who you are (`Minecraft.getUser().getGameProfile().getId()`), but an offline-mode
  server keys its player map by the offline UUID it derives from your name. TLauncher hands the
  client a v1 time-based UUID, so the two never matched:

  | | UUID |
  |---|---|
  | Server thinks `Abdulrhman-S` is | `28ea2e15-7778-3876-…` (v3, `nameUUIDFromBytes("OfflinePlayer:"+name)`) |
  | TLauncher tells the client it is | `0e2dc0f4-7cb9-11f1-…` (v1, TLauncher's own) |

  The launcher's UUID never crosses the wire, so no server-side setting can fix this. New
  client-side mixin in **`shababparty`** falls back to the offline UUID when the launcher's isn't in
  the synced player map. Online-mode behaviour is untouched.

- **Party members share Solo Leveling XP and job advancement points.** Solo Leveling paid both only
  to whoever landed the killing blow. `shababparty` now pays every party member within 64 blocks
  (configurable; same dimension only) by calling Solo Leveling's *own* routines once per member — so
  the mob reward table, each member's personal multiplier and the XP gamerules all still apply, and
  it stays correct if the mod changes its numbers. The killer is paid by Solo Leveling directly and
  is not double-paid. This is the mod's hunter-levelling track (`Xp` → `Level` → rank), not vanilla
  experience orbs, which are untouched.

- **Loot needed no change.** Solo Leveling drops its mob loot (mana crystals and so on) as item
  entities on the ground at the corpse, so a party can already all pick it up — it was never
  assigned to the killer. It stays first-come-first-served rather than a copy each, deliberately:
  duplicating drops per member would multiply crystals by party size. Worth knowing:
  `GoldGainProcedure` is a **dead stub** (its body is a bare `return`), so Solo Leveling awards no
  gold on kill to anyone, and there is nothing there to share.

- **Party members can't hit each other.** Solo Leveling already cancels attacks between two players
  whose `party` field matches, but it had never heard of FTB Teams. `shababparty` copies each
  player's FTB party into that field once a second, so friendly fire stops as a consequence.
  **FTB Teams is now the single source of truth for parties** — use its GUI, not Solo Leveling's
  `/Party` command, which this overrides.

- **XP outside dungeon portals** needs one command, because it is a world gamerule rather than a
  config: `/gamerule soloDungeonProgressionOnly false`. While it is `true`, Solo Leveling pays XP
  *only* for its own dungeon mobs; with it off, every other mob pays a base of 1 (× your multiplier
  × the `soloLevelingXPMultiplier` gamerule).

- **Epic Fight's abilities moved onto the main keyboard.** They were stranded on `=` and `0`, and
  Guard was not bound at all. The keyboard was completely full, so three keys were reclaimed from
  binds this pack does not need: advancements (FTB Quests replaces it), social interactions (dead on
  an offline server) and Corpse's death history (`/back` covers it).

  | Key | Was | Now |
  |---|---|---|
  | `L` | Advancements | **Weapon Innate Skill** |
  | `P` | Social Interactions | **Mobility Skill** |
  | `U` | Corpse death history | **Guard** |
  | `Mouse 5` | Dodge Skill | unchanged |

- **The two `options.txt` had drifted apart.** `configureddefaults/options.txt` (fresh installs) and
  the packwiz-synced root `options.txt` disagreed, and the former bound JEI's edit mode to `N` — the
  same key as Epic Fight's lock-on. Both are now consistent and the collision is gone.

### Shabab 2: the join gate, the timeout kick, and the keyboard

- **Fixed the mid-load kick.** `h4mod1` was joining, sitting on a loading screen, and being
  dropped ~88s later with `lost connection: Timed out`. Nothing kicked him on purpose — his
  client stops answering keepalives while it loads 380 mods' worth of terrain, and the server
  gave up. Connectivity's `disconnectTimeout` 60s → **300s** and `logintimeout` 120s → **600s**
  (`config/connectivity.json`, mirrored in `defaultconfigs/`).
- **Auth's 30-second kick is gone.** The Auth mod's datapack kicks a player who hasn't typed
  their password within 600 ticks — the second thing punishing a slow client. `shabab_gate`
  disables Auth's timer (`kick_time` = 0) and runs its own at **12000 ticks (10 minutes)**.
  Wrong-password kicks after 3 tries are untouched.
- **Join-code gate, actually implemented.** `docs/guides/systems.md` has described one since
  launch; it never existed. New Paxi datapack `config/paxi/datapacks/shabab-gate/`: once a
  player is past the Auth password, they're held in adventure + blindness until they type
  `/trigger joincode set <code>`. 3 tries, 10 minutes, then kicked. Admins skip it with
  `/tag <player> add shabab.bypass`.
  **The code ships as `0`, which means the gate is OFF** — the real code must never sit in a
  public repo. Arm it once, on the server: `/scoreboard players set code shabab.gate 4821`.
  It lives in the world and survives restarts.
- **Vanilla hearts removed.** Solo Leveling already ships `DisableHealthbar` / `DisableHungerBar`
  / `DisableArmorBar` / `DisableLevelBar`, all gated behind a per-player `CustomHUD` flag, and a
  `/ToggleCustomHUD` command to flip it — but nothing ever called it, so players got both HUDs.
  `shabab_gate` now runs it once per player on first join. Its own HP/MP bar is the only one
  left. A player who wants the hearts back just runs `/ToggleCustomHUD`.
- **Keyboard rebuilt around Solo Leveling.** Nothing gameplay-related is on the numpad any more.
  Solo Leveling takes the prime keys and everything else moved out of its way:
  `Z X C V` job abilities, `R` use skill, `B` cycle skill, `G` sword enhance, `Mouse 4` triple
  jump, `'` quest info, `Home` training, `;` panel.
  Displaced: Epic Fight battle mode `G`→`K`, innate skill `Numpad 5`→`=`, skill editor
  `Numpad 0`→`` ` ``; Cataclysm's four armor abilities off `X`/`V`/`C`/`Y` onto `[` `]` `,` `.`;
  Just Zoom → `Left Alt`; Vein Mining → `Caps Lock`; Ping Wheel → `Right Shift`; Inmis backpack
  → `H`; Xaero's instant waypoint off `Numpad +` → `Insert`.
- **Voice chat moved off `V`** (Solo Leveling owns it): menu → `\`, mute → `Right Alt`, and
  **group management → `-`**, which is a free key and is not `G`.
- Applied to **both** `options.txt` (force-synced to every player on update) and
  `configureddefaults/options.txt` (fresh installs), so existing players get the layout too.
  0 conflicts between any two world-context actions; the 6 that remain are JEI keys, which only
  fire inside JEI's own GUI.

### Shabab 2: skins, corpses, /back

- **CustomSkinLoader 15.0.1** (client). The server is `online-mode=false`, so Mojang hands it no
  skin data and every player renders as Steve. CSL looks skins up **by username** instead, from
  `configureddefaults/CustomSkinLoader/CustomSkinLoader.json`: Ely.by → TLauncher → LittleSkin →
  Mojang → a local `.png`. First host with a hit wins. There is no server-side alternative on
  Forge (SkinsRestorer is Bukkit-only), and it doesn't need one — the pack ships to every client.
  Player-facing steps: `docs/guides/skins.md`.
- **Corpse 1.0.23** replaces **You're in Grave Danger**. Two death-container mods cannot coexist —
  they race for the same drops — so YIGD and its config are gone. Corpse leaves a body wearing the
  dead player's skin and gear; `defaultconfigs/corpse-server.toml` makes it owner-only, never
  despawning while it holds items, and lootable by anyone after the 1-hour skeleton stage (the
  same grave-robbing window YIGD had).
- **Corpse x Curios API Compat 4.0.1**, so Curios slots go into the corpse instead of vanishing.
  Its config was already sitting orphaned in `defaultconfigs/`.
- **FTB Essentials 2001.2.4** for one command: **`/back`**, 60s cooldown, `only_on_death: true` —
  it returns a player to their corpse and cannot be used as a general warp. Corpse itself only
  *prints* a `/tp` command, which needs op. Every other FTB Essentials feature (`/home`, `/warp`,
  `/rtp`, `/tpa`, portable `/enderchest` `/crafting` `/anvil` `/smithing`, `/nick`) is disabled in
  `defaultconfigs/ftbessentials-server.snbt` — they would undercut Waystones and survival.
- **`docs/keybinds-shabab2.md`** — all 256 keybinds across the 39 mods that register any,
  extracted from the jars by `tools/gen-keybinds-doc.ps1` and cross-referenced against
  `configureddefaults/options.txt`. It also lists the 7 keys that are double-bound (Cataclysm's
  ability keys collide with Xaero's, Just Zoom, Deeper and Darker, and the Aether).

### Added
- Phase 0: repository skeleton — packwiz root moved to `pack/`, CI (validate/release/pages),
  self-bootstrapping server scripts, TLauncher client updater, RLCraft coverage ledger.
- Phase 1: performance + QoL core (Embeddium/Oculus/ModernFix/Canary stack, Simple Voice
  Chat, Xaero's Minimap + World Map, CustomSkinLoader for offline-mode skins).
- Phase 2: worldgen (Terralith, Tectonic, BOP, Regions Unexplored, Alex's Caves, YUNG suite),
  structures (CTOV, Towns & Towers, Structory, When Dungeons Arise), six dimensions
  (Twilight Forest, Aether + Deep Aether, Blue Skies, Bumblezone, Undergarden, Deeper and Darker).
- Phase 3: RLCraft content (Lycanites Mobs 1.20.1 alpha, Ice and Fire CE, Spartan
  Weaponry/Shields, BrassAmber BattleTowers, Mowzie's, Alex's Mobs, Cataclysm, Born in Chaos,
  Locks Reforged, Enhanced Visuals, Dynamic Trees, Corail Tombstone, In Control!).
- Phase 4: RPG + identity (PMMO, Apotheosis, Curios/Artifacts/Relics + compats,
  Champions-Unofficial, Better Combat, Tough As Nails, Baubley Heart Canisters, Origins Forge
  + Classes, More Player Models, horror set: From The Fog / The Man From The Fog / Cave Dweller).
- Phase 5: Create 6.0.8 + 13 addons, Valkyrien Skies 2.4.11 + Eureka + Clockwork 0.5.6 +
  Trackwork + Immersive Aircraft, Macaw's suite + building mods, Chunky/LuckPerms/FTB Backups.

### Phase 6c: Solo Leveling

- **Cromta's Solo Leveling 1.0.10** + **Cromta's Solo Leveling Addon 1.0.4** +
  **Kleider's Custom Renderer API 7.4.1** (an undeclared hard dependency packwiz does not resolve).
- The only genuine addon that exists; every other "Solo Leveling" project on Modrinth is a
  competing standalone mod or a modpack.
- Adds no `structure_set` entries, so the structure-spacing datapack is unaffected.
- **Open risk:** Kleider's Custom Renderer replaces player models and may conflict with Mo' Bends.
  Untestable from a dedicated server — verify in game.
- **Open balance issue:** the pack now runs three progression systems (Solo Leveling levels,
  Reskillable gates, Scaling Health scaling). Use `/gamerule SoloLevelingXPGain` to pace them.

### Phase 6b: RLCraft progression pass (in-game feedback)

The 1.12-vs-1.20.1 question was re-opened and settled with evidence: **1.20.1 keeps everything**.
The Lycanites 1.20.1 jar was inspected directly rather than trusted — it ships 287 creature
definitions, 6 dungeon schematics, a `DeferredBossSpawner` that puts a boss on each dungeon
floor, all three raid bosses, the equipment forge tiers, soulstones and mounts. Dungeons are on
by default. No rebuild on 1.12 required.

- **Progression: PMMO → Reskillable Reimagined 4.6.6.** The actual Reskillable lineage has a
  1.20.1 build, with skill-gated items/blocks — the RLCraft mechanic, not an approximation.
- **Primitive start: No Tree Punching 7.1.0.** Verified this is the exact mod RLCraft uses for
  knapping flint into shards, crafting a flint knife, and cutting grass for plant fiber.
- **Animations: Mo' Bends [Unofficial Modern Port] 5.1.5.** Removed Not Enough Animations and
  First-person Model, which overlapped with it. Client-side only, so it costs the server nothing.
- **ItemPhysic Full 1.8.13** — items lie flat on the ground and are picked up with right-click.
- **Dynamic Trees actually fells trees now.** It was installed but inert:
  `replaceVanillaSapling = false` meant player-planted trees stayed static vanilla trees. Shipped
  config turns it on, and added **Dynamic Trees Plus** + **Dynamic Trees – Biomes O' Plenty** so
  the pack's BOP trees are dynamic too, not just vanilla species.

### Phase 6a: shaders + server security
- Shaderpacks shipped preinstalled: Complementary Unbound, Photon, Solas (loaded by Oculus).
- **No OptiFine, by necessity**: it is incompatible with Valkyrien Skies and conflicts with
  Create. Embeddium + Oculus already provide better performance and load the same shaderpacks.
- **AntiXray** (server-side ore obfuscation) — the only thing that defeats xray *resource
  packs*, which no client-side mod check can catch. Shipped config sets the global default to
  `enabled = true`; the mod's own default of `false` would have left every modded dimension
  (Twilight Forest, Aether, Blue Skies, Undergarden, Bumblezone, Deeper and Darker) unprotected.
- **Mod Whitelist** — kicks clients running blacklisted cheat mods (freecam, fullbright,
  killaura, baritone, radar/ESP). Shipped with `strict: false`; the mod's default of
  `strict: true` combined with its empty allow-lists would have kicked every player, admin
  included. Upgrade path to strict mode documented in the config and in `SETUP.md`.
- **Auth** (server-side password login) — required because `online-mode=false` lets anyone
  connect under any username. Chosen over Simple Login because Auth is server-side only:
  players install nothing, which matters most for the TLauncher crowd.
- Pack URL wired to `https://36a5.github.io/Modpacks/pack.toml` in both server scripts and the
  TLauncher updater.

### Compatibility fixes (found by the server boot gate)
- **Canary × Valkyrien Skies**: Canary merges `PoiManager#getInRange`, so VS2's
  `feature.poi.MixinPOIManager` fails to inject and the server hard-crashes at world load
  (upstream ValkyrienSkies/Valkyrien-Skies-2#1901). Shipped `config/canary.properties` with
  `mixin.ai.poi=false` — disables one mixin package, keeps every other Canary optimization.
- Server start scripts now abort when packwiz-installer fails, instead of silently booting a
  modless vanilla server.

### Known before public CurseForge upload
- packwiz cannot cross-match metadata between sites, so Modrinth-sourced mods get **bundled
  into the CurseForge zip** (645 MB vs 242 MB for the `.mrpack`). CurseForge requires bundled
  mods to be on its Approved Non-CurseForge Mods list. Before a public CF release, re-add as
  many mods as possible with `packwiz cf add` so they are referenced, not embedded. Modrinth
  distribution and the server/TLauncher pipeline are unaffected.

### Substitutions (upstream availability)
- BrassAmber BattleTowers: **excluded from the CurseForge API** (cannot be fetched
  programmatically → would permanently break auto-updates) → replaced by Battle Towers
  (Modrinth remake).
- Oh The Biomes You'll Go: **no 1.20.1 release exists** (team skipped to 1.21) → replaced by
  Regions Unexplored.
- Locks: original ends at 1.19 → Locks Reforged (maintained 1.20.1 fork).
- Champions: original ends before 1.20.1 → Champions-Unofficial (maintained fork).
