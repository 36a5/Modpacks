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
