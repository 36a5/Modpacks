# RLCraft Parity Report

Source of truth: the RLCraft wiki's own mod table (v2.9.1c, **169 mods**). 163 mod names were
parsed from it and each was checked against Modrinth's 1.20.1 + Forge index automatically.

## Result

| Bucket | Count | Meaning |
|---|---|---|
| Already in al Shabab | ~30 | Lycanites, Ice and Fire CE, Spartan Weaponry/Shields, Waystones, Carry On, CraftTweaker, JEI, Dynamic Trees, Locks, Aquaculture, Bountiful, ItemPhysic, No Tree Punching, Mo' Bends, Corail Tombstone, In Control!, EnhancedVisuals, Enchantment Descriptions, YUNG's suite, Macaw's, Sit, Better Combat … |
| Added in this pass | 24 | see below |
| Covered by a modern analog | ~15 | Reskillable→Reskillable Reimagined, Quality Tools→Apotheosis, So Many Enchantments→Apotheosis, Infernal Mobs→Champions, Simple Difficulty→Tough As Nails, Corpse Complex→Corail Tombstone, Fishing Made Better/Advanced Fishing→Aquaculture, Baubles→Curios, First Aid→**excluded by design** |
| Dead 1.12-only libraries | ~35 | LLibrary, LibrarianLib, Shadowfacts' Forgelin, MixinBootstrap, FoamFix, Phosphor, BNBGamingLib, BASE, AutoRegLib, Carrots Lib, Fantastic Lib, TschippLib, Reborn Core, OreLib, IvToolkit, MtLib, AlcatrazCore, Silent Lib … — these exist only to support 1.12 mods. Nothing to port. |
| No 1.20.1 equivalent | ~30 | Better Questing suite, Dynamic Surroundings, Defiled Lands, Traverse, Scape and Run Parasites, Varied Commodities, Classy Hats, Switch-Bow, Potion Core, Trumpet Skeleton, Familiar Fauna, RLTweaker, LootTweaker … |

## Added in this pass (all verified on 1.20.1 Forge)

**Progression / combat**
- Scaling Health — RLCraft's heart-crystal system *(replaces Baubley Heart Canisters)*
- Elenai Dodge 2 — the dodge roll
- Set Bonus (Armor Set Bonuses), ShieldBreak (Shield Breaking)
- Target Dummy (MmmMmmMmmMmm)

**Exploration / worldgen**
- Doomlike Dungeons, Roguelike Dungeons (Wesley's), Bloodmoon (Rebrushed)
- Grappling Hook Mod (Advanced Hook Launchers analog)

**Survival flavor**
- Serene Seasons, Snow! Real Magic!, Comforts (sleeping bags/hammocks)

**Travel / utility**
- AstikorCarts Redux, Craftable Horse Armour, Wolf Armor & Storage, Traveler's Tool Belt,
  XP Tome, Multi Mine, Neat (health bars)

**Client cosmetics**
- Better Foliage Renewed, Fancy Block Particles (FBP Renewed), Chunk Animator,
  Inventory Tweaks Refoxed

## Deliberately NOT added

| Mod | Reason |
|---|---|
| **First Aid** | body-part health — the original "no frustrating mechanics" rule |
| **Rough Tweaks** | no natural regeneration; healing needs bandages. User-excluded. |
| **Realistic Torches** | torches burn out. User-excluded. |
| **More Player Models / Customizable Player Models** | **hard conflict with Mo' Bends.** Mo' Bends' own tracker: MPM breaks armor rendering and crashes (issues #6, #188); CPM makes the player invisible (#317). Mo' Bends was chosen. RLCraft itself has no character creator. |
| **Origins + Origins: Classes** | a race/class progression system RLCraft does not have. Removed per "remove progression systems that are not from RLCraft". |
| **The Lost Cities, Recurrent Complex, Quark** | all three exist on 1.20.1, but each injects large amounts of worldgen. Holding them back until structure spacing is proven stable; adding them now would worsen the overlap this pass is fixing. |
| **Antique Atlas 4** | its 1.20.1 build depends on `surveyor`, a **Fabric-only** library that needs Sinytra Connector to run on Forge. packwiz resolved the dependency anyway and the server refused to boot (`Mod surveyor requires connectormod`). Removed. Xaero's Minimap + World Map already cover mapping. |

## Worldgen: structures no longer generate inside each other

The pack ships **156 structure sets** across 30+ namespaces. Scanning every mod jar showed the
overworld surface was heavily contested — Towns & Towers, CTOV, Structory, When Dungeons Arise,
Battle Towers, Cataclysm, Ice and Fire, Doomlike Dungeons, Born in Chaos, Mowzie's, Apotheosis
and the YUNG suite all placing structures on the same chunks at 15–35 chunk spacing.

Rather than delete mods (user chose "keep everything, tune spacing"), al Shabab ships a
**global datapack** — `pack/config/openloader/data/al-shabab-spacing/`, loaded by **Open Loader** —
that overrides the `structure_set` placement of **90 sets across 19 overworld namespaces**,
multiplying `spacing` and `separation` by **1.6×**. Each override is generated from the mod's own
JSON, so only the placement numbers change; the structure lists, biome tags and salts are
untouched.

Examples: `dungeons_arise:major_structures` 60→96, `towns_and_towers:other` 32→51,
`ctov:pillager_outposts` 32→51, `cataclysm:abandoned_structures` 30→48, `battle_towers:overworld`
24→38.

Left alone deliberately:
- **Dimension-exclusive sets** (Aether, Blue Skies, Undergarden, Bumblezone, Nullscape,
  Incendium, Deeper and Darker) — they never compete with overworld structures.
- **Vanilla `minecraft:` sets** — so villages keep their expected rhythm.
- **Dense cave sets** (spacing < 14, e.g. Better Mineshafts at 1) — those are meant to be
  everywhere and don't visually collide.

**Lycanites dungeons** get extra room via their own config: `distance` 40 → 64 chunks.

Every generated file is validated: parses as JSON, keeps `separation < spacing` (Minecraft throws
during chunk generation otherwise), and retains its `structures` list.

## Resource packs: there is nothing to copy

**RLCraft does not ship a resource pack.** Its CurseForge page recommends *Chroma Hills* as an
optional external download ("which I used for building this modpack") and points players at
OptiFine for shaders. There is no bundled texture pack in the modpack.

Separately, **RLCraft is licensed All Rights Reserved.** Even if it did bundle assets, copying
them into al Shabab and redistributing them would not be permitted.

What al Shabab ships instead: three shaderpacks (Complementary Unbound, Photon, Solas) loaded
by Oculus, plus Better Foliage and Fancy Block Particles for the visual feel. Players who want
Chroma Hills can install it themselves from its own page — it's a resource pack, so it does not
need to match the server.

## Character creation

No MMO-style body/face creator can coexist with Mo' Bends (see table above). Player identity in
al Shabab comes from:

- **skins** — CustomSkinLoader is installed, so non-premium/TLauncher players get skins too
- **Mo' Bends** animations
- gear, trinkets (Curios), and Scaling Health heart progression

If a character creator ever matters more than animations, the swap is one line: drop Mo' Bends,
add More Player Models.
