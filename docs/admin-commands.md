# Admin command reference — Shabab 2

Everything here needs OP (level 3+) or the server console. Player-facing commands live in
[guides/systems.md](guides/systems.md).

---

## Epic Fight — skill books

### How players are *supposed* to get them

A skill book is a random drop from **any mob a player kills**, at a base **2.5% chance**
(`SkillBookLootModifier`, condition `killed_by_player` — so a mob that dies to fall damage,
a Create machine, or another mob drops nothing). The book that drops is a weighted random
pick from the learnable pool, so common skills like `berserker`, `roll` and `guard` show up
far more often than the rare weapon-innate ones.

Two knobs in `config/epicfight-common.toml` tune this:

```toml
[loot]
    # Range: -100 ~ 100 — a percentage modifier on the base chance, not the chance itself.
    # 0 means "leave the mod's 2.5% alone". Both are currently 0.
    skill_book_mob_drop_chance_modifier = 0
    skill_book_chest_drop_chance_modifier = 0
```

Raise `skill_book_mob_drop_chance_modifier` if books feel too rare. It is a **common** config,
so it has to change on the server *and* ship to clients in the pack.

To **use** a book: hold it and right-click to learn the skill, then press `` ` `` to open the
Skill Editor and slot it. `keepSkills = true` in the same config, so a death never costs a
learned skill. Swapping a slotted skill has a `skillReplaceCooldown` of 6000 ticks (5 minutes).

### Handing one out with a command

The item is `epicfight:skillbook` and the skill it teaches lives in an NBT string called
`skill`. An `epicfight:skillbook` with no NBT is a blank book and teaches nothing.

```
/give <player> epicfight:skillbook{skill:"epicfight:sweeping_edge"} 1
```

Give yourself one of every skill in the pack:

```
/give @s epicfight:skillbook{skill:"epicfight:berserker"} 1
/give @s epicfight:skillbook{skill:"epicfight:swordmaster"} 1
/give @s epicfight:skillbook{skill:"epicfight:the_guillotine"} 1
```

**The skill ids.** (Epic Fight 20.14.17. `EpicFight: Resurrection` and `Weapons of Miracles`
add their own weapons but no new skill ids in this pack.)

| Kind | Slot it fills | Skill ids |
|---|---|---|
| **Passive** | Passive 1–3 | `adaptive_skin` `adrenaline_fiend` `berserker` `bonebreaker` `catharsis` `death_harvest` `emergency_escape` `endurance` `forbidden_strength` `hypervitality` `stamina_pillager` `swordmaster` `technician` `vengeance` |
| **Weapon innate** — only fires with a weapon of the matching type, on `=` | Weapon Innate | `battojutsu` `blade_rush` `dancing_edge` `demolition_leap` `eviscerate` `everlasting_allegiance` `grasping_spire` `heartpiercer` `liechtenauer` `meteor_slam` `phantom_ascent` `relentless_combo` `revelation` `rushing_tempo` `sharp_stab` `steel_whirlwind` `sweeping_edge` `the_guillotine` `tsunami` `wrathful_lighting` |
| **Dodge / Guard** — usually already known | Dodge, Guard | `roll` `step` `guard` `parrying` `impact_guard` |
| **Weapon passive** | Weapon Passive | `battojutsu_passive` |

Weapon-innate skills are the interesting ones and the ones worth handing out as a reward: a
`the_guillotine` book is useless to someone who only ever swings a sword, because it needs an
axe. If you want to *see* every book in-game, they all have a JEI entry — search `Skill Book`.

---

## Solo Leveling — `/slr`

`/slr` is the mod's own command. It requires **permission level 3**, and its player argument
is a real entity selector, so `@s`, `@p` and `@a` all work.

```
/slr <player> level <amount>                 # set System level (max 500)
/slr <player> Rank <E|D|C|B|A|S|OP>          # set Hunter rank
/slr <player> class <Assassin|Mage|Fighter|Tanker|Healer|Ranger|Random|reset>
/slr <player> job <Reset|ShadowMonarch|GrandMage|FrostMonarch|MonarchOfWhiteFlames>
/slr <player> gold <Set|Add> <amount>
/slr <player> Stats <skillpoints|strength|vitality|agility|sense|intelligence> <Set|Add> <amount>
/slr <player> DungeonBreak                   # force a dungeon break
/slr <player> TriggerPenaltyZone
/slr <player> FinishDaily
/slr <player> Debug <Dimension|ClearedGates|CurrentGatesStatus>
```

> **`job MonarchOfWhiteFlames` does not stick — unless the player is on the allowlist.** The
> `slb-jobs` datapack re-rolls anyone holding JOB=4 into one of the three allowed jobs within a
> tick. The exception is the allowlist in `slb_jobs:tick`, which currently holds **Abdulrhman-S**:
>
> ```mcfunction
> execute as @a[name=Abdulrhman-S] run tag @s add slb_wf_ok
> execute as @a[tag=!slb_wf_ok] if score @s sl_job matches 4 run function slb_jobs:strip_white_flames
> ```
>
> To let someone else keep it, add a second `name=` line. To take it back, remove theirs — the
> strip fires on the next tick. Note that the Job Change Quest still never *awards* White Flames to
> anyone, allowlisted or not: an allowlisted player has to be given it with `/slr`, and if they run
> the quest again they will be re-rolled off it like everyone else.

### The job rules on this server (`slb-jobs` datapack)

Vanilla Solo Leveling always ends the Job Change Quest in **Shadow Monarch**, and gives a
player exactly **one** Job Change Quest Key in their life (at the `soloLevelingJobChangeLevel`
gamerule's level, default 40). Here instead:

- Finishing the quest awards a **random** job: Shadow Monarch, Grand Mage, or Frost Monarch.
- Finishing it **again re-rolls**, and never returns the job you walked in with — so a re-roll
  always changes something.
- **Monarch of White Flames is sealed.** Nothing in the game grants it anyway (it is
  `/slr`-only), but the datapack also strips it from anyone who has it.
- A **bonus Job Change Quest Key at level 100, 150, 200, …** on top of the mod's own key. A
  player already past 100 when this first loads is paid their keys retroactively.

This all leans on `function-permission-level=4` in `server.properties`: the datapack awards
jobs by running the mod's own `/slr`, which is level 3. **If that value ever goes back to 2,
every Job Change Quest silently ends in Shadow Monarch again** and nothing logs an error.
It is in the `[enforce]` block of `server.properties.template` for exactly that reason.

Useful gamerules:

| Gamerule | What it does |
|---|---|
| `soloLevelingJobChangeLevel` | Level at which the mod grants its one Job Key (default 40) |
| `soloLevelingMonarchLimit` | How many players may hold the *same* Monarch at once |
| `soloGateSpawning` | Turn Gate spawning on/off |
| `soloGateDelay` | Minimum seconds between Gate spawns |
| `soloGateNotification` | The mod's own Gate chat message. **Set to `false` by `slb-gates`** — our announcement carries the same coordinates plus a teleport button, and two lines per Gate is noise. |
| `soloDungeonBreak` | Toggle dungeon breaks |
| `soloLevelingXPMultiplier` | `10` = 1×, `15` = 1.5× |

---

## Gates — the teleport button (`slb-gates` datapack)

When a Gate opens, everyone gets **one line** with the Gate's name, its coordinates and a `[TP]`
button. Clicking it runs `/trigger slb_gate set <n>` — **not** `/tp`, because only two players are
OP and `/tp` needs level 2. The datapack does the teleport on the player's behalf.

```
⚡ Gate - Ant Nest   X -31901 Y 76 Z 4571   [TP]
```

The Gate's name is its own — the announce function runs *as the Gate entity*, so `{"selector":"@s"}`
prints whatever the mod calls it, and that is how Solo Leveling distinguishes what is inside:

| Entity | Name shown |
|---|---|
| `portal` | D Rank Gate |
| `portal_1` | Gate (random dungeon) |
| `portal_s` | Gate (Kamish — S rank) |
| `portal_sewers` | Gate - Goblin Sewers |
| `portal_beru` | Gate - Ant Nest |
| `portal_lush` | Gate - Lush Cave |
| `portal_lab` | Gate - Abandoned Lab |
| `portal_ancient_golem` | Gate - Ancient Golem Cave |
| `portal_kargalgans_throne_room` | Gate - Throne Room |
| `random_cave_large` | Gate - Giant Spider |
| `red_gate` | Gate (Red Gate) |

`portal_job_change` is excluded — it is personal to whoever used a Job Key. **`portal_12` is
excluded too**: it is the *Dungeon Exit Portal* that stands inside a dungeon, not a Gate that
appears in the world. The entity-type tag is otherwise exactly the set of entities whose spawn code
calls `PortalSpawnProcedure`.

Gates are given slot numbers 1–9, recycled. A tenth Gate takes slot 1 back, and the oldest
Gate's button stops working rather than sending someone to the wrong place.

| Task | Command |
|---|---|
| See the live Gates the mod knows about | `/slr @s Debug CurrentGatesStatus` |
| Turn the buttons off | `/datapack disable "file/slb-gates"` — then set `soloGateNotification` back to `true`, or nobody hears about Gates at all |

---

## Gate kills & the leaderboard

Gate kills are counted by the `gate_kills` datapack into a `pk_total` scoreboard, and only
while the player is inside a Solo Leveling dungeon dimension.

| Task | Command |
|---|---|
| Read a player's Gate kills | `/scoreboard players get <player> pk_total` |
| Wipe everyone's Gate kills | `/function gate_kills:reset` |
| Post/refresh the Discord board | `!board` in Discord |
| Show the board on demand | `!leaderboard`, `!gates` in Discord |

The Discord board is **all-time** and re-renders itself once a day. Nothing resets. The numbers
come from `world/stats/<uuid>.json`, which the server writes itself, so they cannot be faked
from chat.

### Editing a player's death count

There is no command for it — deaths live in `world/stats/<uuid>.json`, and the server rewrites
that file from memory on logout and on its periodic flush, so editing it on a live server does
nothing. **Stop the server first.**

```powershell
cd server
.\set-player-deaths.ps1 Abdulrhman-S 5      # set to 5
.\set-player-deaths.ps1 Abdulrhman-S        # set to 0
```

It keeps a `.bak` next to the file it edits and refuses to run while the server is up. The
board picks the new number up on its next refresh (`!board` to force it).


# 🟢 DODGE & MOBILITY SKILLS

## Roll (Epic Fight Base Mod)
#### Drop Source: Zombies, Skeletons
#### Structure Loot: Common Dungeon Chests, Village Blacksmiths
```/give @s epicfight:skillbook{skill:"epicfight:roll"} 1```

## Step (Epic Fight Base Mod)
#### Drop Source: Skeletons, Spiders
#### Structure Loot: Abandoned Mineshafts, Desert Temples
```/give @s epicfight:skillbook{skill:"epicfight:step"} 1```

## Phantom Ascent (Epic Fight Base Mod)
#### Drop Source: Endermen, Phantoms
#### Structure Loot: End Cities, Shipwreck Supply Chests
```/give @s epicfight:skillbook{skill:"epicfight:phantom_ascent"} 1```

## Demolition Leap (Epic Fight Base Mod)
#### Drop Source: Creepers, Charged Creepers
#### Structure Loot: Desert Temples, Mineshaft Chests
```/give @s epicfight:skillbook{skill:"epicfight:demolition_leap"} 1```

## Meteor Slam (Epic Fight Base Mod)
#### Drop Source: Wither Skeletons, Magma Cubes
#### Structure Loot: Nether Fortresses, Ancient Cities
```/give @s epicfight:skillbook{skill:"epicfight:meteor_slam"} 1```

## Shadow Step (Weapons of Miracles Addon)
#### Drop Source: Vindicators, Pillagers, Wither Skeletons
#### Structure Loot: Pillager Outposts, Ancient Cities, Nether Fortresses
```/give @s epicfight:skillbook{skill:"wom:shadow_step"} 1```

## Ender Step (Weapons of Miracles Addon)
#### Drop Source: Endermen
#### Structure Loot: End Cities, Stronghold Libraries
```/give @s epicfight:skillbook{skill:"wom:ender_step"} 1```

## Ender Obscuris (Weapons of Miracles Addon)
#### Drop Source: Ender Dragon, Shulkers, Endermen
#### Structure Loot: End Cities, Stronghold Altars
```/give @s epicfight:skillbook{skill:"wom:ender_obscuris"} 1```

## Precise Roll (Weapons of Miracles Addon)
#### Drop Source: Spiders, Pillagers, Vindicators
#### Structure Loot: Desert Temples, Pillager Outposts, Shipwrecks
```/give @s epicfight:skillbook{skill:"wom:precise_roll"} 1```

## Spider Technique (Weapons of Miracles Addon)
#### Drop Source: Cave Spiders, Spiders
#### Structure Loot: Abandoned Mineshafts, Woodland Mansions
```/give @s epicfight:skillbook{skill:"wom:spider_technique"} 1```

## Alco Maneuver (Weapons of Miracles Addon)
#### Drop Source: Drowned
#### Structure Loot: Ocean Ruins, Shipwrecks
```/give @s epicfight:skillbook{skill:"wom:alco_maneuver"} 1```

## Better Step (Epic Fight - Resurrection Addon)
## Drop Source: Strays
## Structure Loot: Igloo Basements, Trial Chambers
```/give @s epicfight:skillbook{skill:"resurrection:better_step"} 1```

## Wolf God Leap (Epic Fight - Resurrection Addon)
## Drop Source: Cave Spiders, Spiders
## Structure Loot: Abandoned Mineshafts
```/give @s epicfight:skillbook{skill:"resurrection:wolf_god_leap"} 1```


# 🛡️ GUARD SKILLS

## Guard (Epic Fight Base Mod)
## Drop Source: Armored Zombies, Shield Skeletons
## Structure Loot: Common Dungeon Chests, Stronghold Chests, Village Armorer Chests
```/give @s epicfight:skillbook{skill:"epicfight:guard"} 1```

## Parrying (Epic Fight Base Mod)
## Drop Source: Wither Skeletons, Pillager Captains
## Structure Loot: Nether Fortresses, Pillager Outposts, Woodland Mansions
```/give @s epicfight:skillbook{skill:"epicfight:parrying"} 1```

## Impact Guard (Epic Fight Base Mod)
## Drop Source: Iron Golems (when hostile), Piglin Brutes
## Structure Loot: Village Weaponsmith Chests, Nether Fortresses, Bastion Remnants
```/give @s epicfight:skillbook{skill:"epicfight:impact_guard"} 1```

## Perfect Bulwark (Weapons of Miracles Addon)
## Drop Source: Evokers, Piglin Brutes, Drowned, Guardians
## Structure Loot: Woodland Mansions, Ocean Ruins, Buried Treasure Chests
```/give @s epicfight:skillbook{skill:"wom:perfect_bulwark"} 1```

## Vengeful Parry (Weapons of Miracles Addon)
## Drop Source: Piglin Brutes, Evokers
## Structure Loot: Bastion Remnants, Woodland Mansions, Ancient Cities
```/give @s epicfight:skillbook{skill:"wom:vengeful_parry"} 1```

## Counter Attack (Weapons of Miracles Addon)
## Drop Source: Illusioners, Evokers, Husks
## Structure Loot: Woodland Mansions, Stronghold Corridors, Desert Temples
```/give @s epicfight:skillbook{skill:"wom:counter_attack"} 1```


# ⚡ PASSIVE SKILLS

## Technician (Epic Fight Base Mod)
## Drop Source: Skeletons, Strays, Endermites
## Structure Loot: Ancient Cities, Abandoned Mineshafts, End Cities
```/give @s epicfight:skillbook{skill:"epicfight:technician"} 1```

## Berserker (Epic Fight Base Mod)
## Drop Source: Piglins, Zombified Piglins, Piglin Brutes
## Structure Loot: Bastion Remnants, Nether Fortresses
```/give @s epicfight:skillbook{skill:"epicfight:berserker"} 1```

## Sword Master (Epic Fight Base Mod)
## Drop Source: High-tier Armored Hostile Mobs, Wither Skeletons
## Structure Loot: Stronghold Libraries, Ancient Cities
```/give @s epicfight:skillbook{skill:"epicfight:sword_master"} 1```

## Stamina Pillager (Epic Fight Base Mod)
## Drop Source: Zombies, Husks, Pillager Captains
## Structure Loot: Desert Temples, Pillager Outposts, Woodland Mansions
```/give @s epicfight:skillbook{skill:"epicfight:stamina_pillager"} 1```

## Death Harvest (Epic Fight Base Mod)
## Drop Source: Wither Skeletons, Phantoms, Witches
## Structure Loot: Ancient Cities, Swamp Huts, Nether Fortresses
```/give @s epicfight:skillbook{skill:"epicfight:death_harvest"} 1```

## Forbidden Strength (Epic Fight Base Mod)
## Drop Source: Wardens, Wither Boss
## Structure Loot: Ancient Cities, Bastion Treasure Rooms
```/give @s epicfight:skillbook{skill:"epicfight:forbidden_strength"} 1```

## Emergency Escape (Epic Fight Base Mod)
## Drop Source: Witches, Creepers
## Structure Loot: Dungeon Spawners, Mineshaft Chests, Shipwrecks
```/give @s epicfight:skillbook{skill:"epicfight:emergency_escape"} 1```

## Hyper Vitality (Epic Fight Base Mod)
## Drop Source: Guardians, Elder Guardians, Ravagers
## Structure Loot: Ocean Monuments, Desert Temples, Jungle Temples
```/give @s epicfight:skillbook{skill:"epicfight:hyper_vitality"} 1```

## Endurance (Epic Fight Base Mod)
## Drop Source: Husks, Drowned, Iron Golems, Zoglins
## Structure Loot: Shipwrecks, Dungeon Chests, Ocean Ruins
```/give @s epicfight:skillbook{skill:"epicfight:endurance"} 1```

## Knocking Down Wakeup (Epic Fight Base Mod)
## Drop Source: Zombies
## Structure Loot: Simple Dungeon Spawners, Abandoned Mineshafts
```/give @s epicfight:skillbook{skill:"epicfight:knocking_down_wakeup"} 1```

## Critical Master (Weapons of Miracles Addon)
## Drop Source: Pillager Captains, Piglin Brutes
## Structure Loot: Pillager Outposts, Nether Fortresses, Woodland Mansions
```/give @s epicfight:skillbook{skill:"wom:critical_master"} 1```

## Stellar Restoration (Weapons of Miracles Addon)
## Drop Source: Phantoms, Endermen
## Structure Loot: Stronghold Corridors, End Cities, Buried Treasure
```/give @s epicfight:skillbook{skill:"wom:stellar_restoration"} 1```

## Latent Retribution (Weapons of Miracles Addon)
## Drop Source: Endermen, Wardens
## Structure Loot: End Cities, Ancient Cities, Stronghold Altars
```/give @s epicfight:skillbook{skill:"wom:latent_retribution"} 1```

## Dodge Master (Weapons of Miracles Addon)
## Drop Source: Vindicators, Phantoms
## Structure Loot: Woodland Mansions, Desert Temples, Jungle Temples
```/give @s epicfight:skillbook{skill:"wom:dodge_master"} 1```

## Bull Charge (Weapons of Miracles Addon)
## Drop Source: Ravagers
## Structure Loot: Village Raid Reward Chests, Pillager Outposts, Woodland Mansions
```/give @s epicfight:skillbook{skill:"wom:bull_charge"} 1```


# ⚔️ IDENTITY (ULTIMATE) SKILLS

## Ravager Force (Epic Fight ```/ Expansion Addons)
## Drop Source: Ravagers (Guaranteed rare drop during Raids)
## Structure Loot: Village Raid Victory Chests
```/give @s epicfight:skillbook{skill:"epicfight:ravager_force"} 1```

## Revelation (Epic Fight Base Mod)
## Drop Source: Warden, Ender Dragon
## Structure Loot: Ancient City Ice Boxes, End Cities, Stronghold Libraries
```/give @s epicfight:skillbook{skill:"epicfight:revelation"} 1```

## The Guillotine (Weapons of Miracles Addon)
## Drop Source: Evokers, Illusioners
## Structure Loot: Woodland Mansions, Pillager Outposts
```/give @s epicfight:skillbook{skill:"wom:the_guillotine"} 1```

## Punishment Kick (Weapons of Miracles Addon)
## Drop Source: Piglin Brutes
## Structure Loot: Bastion Remnants, Nether Fortresses
```/give @s epicfight:skillbook{skill:"wom:punishment_kick"} 1```

## Time Travel (Weapons of Miracles Addon)
## Drop Source: Ender Dragon, Wither Boss
## Structure Loot: Ancient Cities, End Cities
```/give @s epicfight:skillbook{skill:"wom:time_travel"} 1```

## Ash of Eternity (Weapons of Miracles Addon)
## Drop Source: Wither Boss
## Structure Loot: Nether Bastion Treasure Chests
```/give @s epicfight:skillbook{skill:"wom:ash_of_eternity"} 1```

## Moonless Curse (Weapons of Miracles Addon)
## Drop Source: Husks, Witches
## Structure Loot: Desert Temple Secret Chests, Witch Huts
```/give @s epicfight:skillbook{skill:"wom:moonless_curse"} 1```
