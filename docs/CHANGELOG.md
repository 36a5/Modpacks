# Changelog

## [Unreleased]

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
