# RLCraft 2.9.3 Coverage Ledger

Rule: every mod in RLCraft 2.9.3 (~169 mods) is classified into exactly one bucket.
This file is the source of truth for "did we cover RLCraft?". Filled progressively
during Phase 3; rows marked *(verify)* still need their 1.20.1 build confirmed.

Buckets:
- **A — Ported**: same mod (or official continuation) included on 1.20.1.
- **B — Analog**: 1.12-only mod replaced by a modern mod with the same feature.
- **C — Excluded by design**: cut on purpose (the "not frustrating" requirement).
- **D — Dropped**: 1.12-only, no equivalent worth including; reason logged.

## A — Ported

| RLCraft mod | 1.20.1 mod | Status |
|---|---|---|
| Lycanites Mobs | Lycanites Mobs (official 1.20.1 port) | alpha, but **content-complete**: jar contains 287 creature definitions, 6 dungeon schematics (Aberrant Station, Ashen Mausoleum, Demonic Temple, Desert Crypts, Lush Tomb, Shadow Labyrinth), `DeferredBossSpawner` (boss per dungeon floor), all 3 raid bosses (Rahovart/Asmodeus/Amalgalich), equipment forge tiers, soulstones + mounts. Dungeons `enabled = true` by default, 40-chunk spacing, 25% boss-tower chance. |
| Reskillable | **Reskillable Reimagined 4.6.6** — 8 skills + skill-gated items/blocks | confirmed, added (replaced PMMO) |
| No Tree Punching (flint knapping → shards → flint knife → plant fiber) | No Tree Punching 7.1.0 | confirmed, added — this is the exact mod RLCraft uses for that progression |
| ItemPhysic (items lie flat, right-click to pick up) | ItemPhysic Full 1.8.13 | confirmed, added |
| Mo' Bends (character/mob animations) | Mo' Bends [Unofficial Modern Port] 5.1.5, client-side only | confirmed, added |
| Spartan Weaponry | Spartan Weaponry 3.2.x | confirmed |
| Ice and Fire | Ice and Fire: Community Edition | confirmed |
| Battle Towers | Battle Towers (Modrinth remake — BrassAmber continuation is CF-API-excluded and would break auto-updates) | confirmed |
| RLCombat (Better Combat fork) | Better Combat + Player Animator | confirmed |
| Dynamic Trees | Dynamic Trees | confirmed |
| Waystones | Waystones | confirmed |
| Bountiful | Bountiful | confirmed |
| Corail Tombstone | Corail Tombstone | confirmed |
| Carry On | Carry On | confirmed |
| In Control! | In Control! | confirmed |
| Sound Filters | Sound Physics Remastered | confirmed |
| Enhanced Visuals | Enhanced Visuals | confirmed |
| CraftTweaker | CraftTweaker (+ KubeJS) | confirmed |
| Locks | Locks Reforged (maintained fork) | confirmed, added |
| Infernal Mobs | Champions-Unofficial (fork of Champions) | confirmed, added (B) |
| Antique Atlas | Antique Atlas port / Map Atlases | *(verify — Xaero's covers mapping meanwhile)* |
| Spartan Shields | Spartan Shields | confirmed, added |
| Sit | Sit | confirmed, added |

## B — Analog

| RLCraft mod | Replacement | Feature covered |
|---|---|---|
| ~~Reskillable + Level Up HP~~ → **now Bucket A** | Reskillable Reimagined 4.6.6 | see Ported table — the actual Reskillable lineage exists on 1.20.1 |
| Quality Tools | Apotheosis | equipment quality/affixes |
| Trinkets and Baubles | Curios + Artifacts + Relics | trinket slots + artifact items |
| Baubley Heart Canisters (1.12) | Baubley Heart Canisters (1.20.1) | heart progression |
| So Many Enchantments | Apotheosis enchanting + Enchantment Descriptions | enchant variety |
| Doomlike Dungeons / Roguelike Dungeons | When Dungeons Arise + YUNG's suite + Lootr | dungeon density + per-player loot |
| Recurrent Complex / Ruins | Structory, Towns & Towers, CTOV | worldgen structures |
| Simple Difficulty | Tough As Nails (tuned fair) | thirst + temperature |
| Fishing Made Better | Aquaculture 2 | fishing depth |

## C — Excluded by design

| RLCraft mod | Reason |
|---|---|
| First Aid | body-part health system — the #1 frustration source, per project brief |
| (tuning) one-shot mob damage configs | fair-combat requirement; vanilla damage model kept |

## D — Dropped

| RLCraft mod | Reason |
|---|---|
| Rustic | 1.12-only; decoration covered by building suite |
| Defiled Lands | 1.12-only, abandoned; corruption-biome niche unfilled but low value |
| Potion Core | 1.12-only internals; Apotheosis/Potion mods cover effect variety |

*(Remaining ~140 rows filled in during Phase 3 from the official RLCraft 2.9.3 manifest.)*

## Known mod-interaction notes

| Interaction | Resolution |
|---|---|
| Canary × Valkyrien Skies (`PoiManager` mixin clash, crashes world load) | `config/canary.properties` → `mixin.ai.poi=false` |
| Lycanites Mobs 1.20.1 | official port, **alpha** — stability verdict still pending on a long-running server; fallback mob set (Mowzie's, Alex's Mobs, Cataclysm, Born in Chaos) already in pack |
| CurseForge API-excluded mods | cannot be used at all — they break packwiz auto-updates for every player |
