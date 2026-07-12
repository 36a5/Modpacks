# Server datapacks

Source copies. **`world/datapacks/` is inside the world**, so `reset-world.ps1` deletes them —
copy them back in after a world reset:

```powershell
Copy-Item .\datapacks\* .\run\world\datapacks\ -Recurse -Force
```

## faster-builders

MineColonies **1.1.1255 has no builder-speed config**. `builderBuildBlockDelay` and
`blockMiningDelayModifier` are gone from `ServerConfiguration` — only stale lang keys remain, which
is why guides that tell you to edit `minecolonies-common.toml` no longer work.

Build speed in this version is a **research effect**, and research is datapack-driven — so it can be
overridden server-side with no client update and no jar patch.

| file | stock | ours |
|---|---|---|
| `effects/blockplacespeedmultiplier.json` | +10% / 25% / 50% / 100% / 200% | **+100% / 200% / 300% / 400% / 500%** |
| `effects/blockbreakspeedmultiplier.json` | same | same |
| `technology/ability.json` (grants place-speed lvl 1) | 64 iron | **16 iron** |

So the *first* research alone now doubles build speed, and the full Technology chain
(ability → skills → tools → seemsautomatic → madness) tops out at 6x instead of 3x.

**Do not lower `researchLevel` on `ability.json`.** Its parent `hittingiron` is `researchLevel: 1`;
a child at the same level is read as a *sibling*, so MineColonies rejects the research —
and every research below it then fails with "Parent does not exist", silently deleting the whole
build-speed chain (208 researches drop to 203). It must stay strictly above its parent.

The research still has to be **completed in the University** — the datapack changes what the research
is worth, not whether you have it. Builders are also gated by their own level and by having the
materials, so a fast builder with an empty hut still waits.

Applies on `/reload` or a server restart.
