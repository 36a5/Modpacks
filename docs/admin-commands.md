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

> **`job MonarchOfWhiteFlames` does not stick.** The `slb-jobs` datapack watches for it and
> re-rolls the player into one of the three allowed jobs within a tick. That is deliberate —
> see below. If you genuinely want to hand it out, disable the datapack first.

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

When a Gate opens, everyone gets a chat line with its coordinates and a `[ TELEPORT ]` button.
Clicking it runs `/trigger slb_gate set <n>` — **not** `/tp`, because only two players are OP
and `/tp` needs level 2. The datapack does the teleport on the player's behalf.

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
