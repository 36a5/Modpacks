# MineColonies — schematics, styles & the Build Tool

MineColonies doesn't ship "buildings". It ships **schematics** (the mod calls the files
*blueprints*), and a Builder NPC who reads one and lays it brick by brick out of materials you
supply. Every hut you place, every road, every decoration is a schematic. Understanding that one
idea is most of understanding MineColonies.

Schematics come from **Structurize**, the sister mod. Structurize owns the file format, the
tools, and the folder they live in; MineColonies just consumes them.

---

## The three tools

Craft them, or `/give` them. All three are Structurize items.

| Tool | Item id | Recipe | What it does |
|---|---|---|---|
| **Build Tool** | `structurize:sceptergold` | 2 sticks diagonally + 1 stone/cobble | Place *any* schematic anywhere, as a preview you can move and rotate |
| **Scan Tool** | `structurize:sceptersteel` | 2 sticks diagonally + 1 iron ingot | Save a piece of your world as a new schematic |
| **Shape Tool** | `structurize:shapetool` | — | Stamp spheres, cylinders, domes — geometry, not schematics |

```
/give @s structurize:sceptergold 1
/give @s structurize:sceptersteel 1
```

The **Calipers** (`structurize:caliper`) just measure distance between two blocks. Handy, unrelated.

---

## Part 1 — Using the schematics that come with the pack

### Starting a colony

1. Craft a **Supply Camp** (`minecolonies:supplycampdeployer`) — or a Supply Ship if you're on
   water. Right-click it on the ground.
2. A **preview** appears. This is the schematic system already: you are looking at a blueprint
   that hasn't been placed yet.
   - **Arrow buttons** move it a block at a time.
   - **Rotate / Mirror** turn it.
   - The preview turns **red** where it would cut into terrain it can't. Move it until it's clear.
3. Confirm. The camp materialises, and inside it are your **Town Hall** and **Builder's Hut**.
4. Place the Town Hall block → your colony exists. Place the Builder's Hut → right-click it →
   **hire a citizen as your Builder**.

From here **the Builder does all construction**. You never hand-place a hut's walls.

### Placing a building

Right-click any hut block on the ground. You get the same preview GUI, and this is where the
**style** picker lives.

**A style is a whole set of schematics** — one Builder's Hut in Medieval Oak, one in Nordic, one
in Caledonia, and so on. The pack ships all of MineColonies' default styles. Pick one and stay
with it, or mix them freely; the mod doesn't care, your town's looks do.

The preview also has a **level** — huts go from level 1 to 5, and each level is a *different
schematic*. When you upgrade a hut, the Builder tears down the level-1 schematic and builds the
level-2 one in its place. That is why an upgrade takes real time and real materials.

Then:

1. Confirm the placement → the hut block goes down with a build order attached.
2. Right-click the hut → **Build** (or **Upgrade**).
3. The Builder walks over, and asks you for materials through the **request system** — check the
   Town Hall GUI, or the Builder's own **Requests** tab, and drop what it asks for into its chest.
4. It builds. Slowly. That's the game.

> **Nothing gets built while the Builder has an unfilled request.** If a build seems stuck, it is
> almost always waiting on a material nobody delivered. Open its Requests tab.

### Decorations (the Build Tool's real job)

Roads, walls, lamp posts, gardens, bridges — MineColonies ships these as **decoration
schematics**, and they are not hut blocks, so you place them with the **Build Tool**.

1. Right-click with the Build Tool.
2. Browse: **structure pack → category → decoration**.
3. Move / rotate the preview, confirm.
4. It becomes a **build order for your Builder** exactly like a hut — it does *not* appear
   instantly. Same request system, same wait.

---

## Part 2 — Making your own schematics

### Scanning

1. Build the thing you want to save, in the world.
2. **Right-click** with the **Scan Tool** → sets corner 1.
3. **Left-click** (or shift-right-click) the opposite corner → sets corner 2. A box appears.
4. Right-click again to open the scan GUI. Name it, and **Scan**.

Structurize confirms with `Scan successfully saved as <name>` and writes a `.blueprint` file into
your **Local** structure pack:

```
<your minecraft folder>/blueprints/Local/scans/<name>.blueprint
```

`Local` is a real structure pack that Structurize creates for you — its `pack.json` literally
describes it as *"This is your local Structurepack. This is where all your scans go."*

### Placing your scan

Right-click with the **Build Tool** and pick the **Local** pack in the browser. Your scan is in
there. Place it like any other schematic.

> **On the server, this needs `allowPlayerSchematics = true`.** It lives in Structurize's server
> config (`config/structurize-server.toml`, "Should player-made schematics be allowed?"). With it
> off, a non-OP player's own scans are rejected on placement — the client shows the preview and
> the server refuses. If someone reports "my scan won't place", that config is the first thing to
> check.

### Blocks that make schematics smarter

These are placed *inside* a schematic before you scan it, and are Structurize's whole trick for
buildings that adapt:

| Block | What it does when the schematic is built |
|---|---|
| **Solid Substitution** | Replaced with whatever solid block the Builder has to hand — use it for ground/fill so a building sits on any terrain |
| **Substitution** | Replaced with **air**. Use it to carve out space the schematic must own but not fill |
| **Fluid Substitution** | Replaced with the fluid that was there (or air) — for builds that sit in water |
| **Light Substitution** | Replaced with a light source |

Use **Solid Substitution** liberally under a custom building's floor. It is the difference between
a schematic that only works on a flat plain and one that works anywhere.

---

## Part 3 — Custom schematic packs on the server

A **structure pack** is just a folder. This is how you'd add a downloaded style pack, or share
your own scans with everyone.

```
server/run/blueprints/
└── MyStyle/
    ├── pack.json          <- name, version, pack-format, desc, authors, mods, icon
    ├── icon.png
    └── ...  .blueprint files, in whatever category folders you like
```

Structurize's server loader reads `blueprints/` on boot and **syncs the packs to every client
that connects** — players do not need to install anything by hand. That sync is the whole reason
the folder is server-side.

To ship a pack as part of the modpack instead (so it's there before anyone joins), drop the same
folder into the pack's `blueprints/` directory and let packwiz distribute it.

Two Structurize config switches worth knowing:

| Setting | Effect |
|---|---|
| `allowPlayerSchematics` | Whether non-OP players may place their own scanned blueprints. Off by default. |
| `ignoreSchematicsFromJar` | Ignore the default styles shipped inside the mod jar. Only for a server that wants *nothing* but its own packs. |

---

## Why your Builder is slow — and what actually fixes it

Two things people assume are wrong.

**"Upgrading the Builder's Hut makes him build faster."** In stock MineColonies, *no*. The hut level
is not part of either speed formula — breaking is `500 * 0.85^(secondarySkill/2) * hardness /
toolSpeed`, placing is a flat `150 / (primarySkill/2 + 10)` ticks per block. Only the citizen's
skills, his tool, and colony research move them. A level 5 Builder lays bricks at exactly the pace of
a level 1 Builder.

**"He walks home constantly because he's dumb."** He walks home because his inventory fills up.
`AbstractEntityAIStructure` sends him to dump the moment `hasSpace()` goes false. Stock citizen
inventory is **27 slots**, and the *only* thing that changes it is research — there is no config for
it anywhere.

### What this server does about it

The **`colonyspeed`** mod (ours) makes both scale with the hut level:

| Builder's Hut | Block delay | Inventory |
|---|---|---|
| Level 1 | stock | 27 slots |
| Level 2 | 2× faster | 36 slots |
| Level 3 | 4× faster | 54 slots |
| Level 4 | 8× faster | 63 slots |
| Level 5 | 16× faster | **81 slots** |

**81 slots is the ceiling, and it is a UI limit, not a balance choice.** The citizen inventory screen
sizes itself as `114 + min(9, rows) * 18` pixels and does not scroll, so a tenth row would be drawn on
top of your own inventory. It is plenty: slots hold *stacks*, so 81 slots is 81 distinct block types
at 64 each. No blueprint uses 81 distinct blocks — which is why a level 5 Builder effectively stops
walking home.

The speed half saturates early (the per-block delay bottoms out at 1 tick around level 3–4), so past
that point **the inventory is the upgrade that still matters**.

### Stack this with research

Inventory research is in the **Technology** branch at the University, and stacks with the above (the
worker takes whichever is larger — they are not added):

| Research | Slots | Cost | Needs |
|---|---|---|---|
| **Deep Pockets** | 36 | 64 emeralds | Library level 4 |
| **Loaded** | 45 | 128 emeralds | Library level 5 |
| **Heavily Loaded** | 54 | 256 emeralds | — |

These are three levels of one effect, so Heavily Loaded gives **54 total**, not 27+9+18+27. It applies
to *every* citizen, so it's still worth doing for your Miners and Lumberjacks.

### The thing neither fixes

None of this touches **waiting for materials**. A Builder standing still because nobody delivered
cobblestone is not slow, he is blocked. Hire more **Couriers**, keep the **Warehouse** near the build
site, and keep his hut chest stocked. That is usually the real bottleneck.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Preview is red and won't confirm | It overlaps terrain or another building's claim. Move or raise it. |
| Placed the hut, nothing is happening | You never clicked **Build**, or the Builder is waiting on a material. Check its Requests tab. |
| My scan won't place on the server | `allowPlayerSchematics = false`. |
| The Builder builds a *different* style than I picked | Style is chosen per-building at placement, not per-colony. You picked it on one hut and not the next. |
| Decoration didn't appear | Decorations are build *orders*, not instant placement. The Builder has to get to it. |
