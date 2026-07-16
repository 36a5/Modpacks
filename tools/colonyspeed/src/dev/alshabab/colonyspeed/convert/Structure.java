package dev.alshabab.colonyspeed.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed structure, in the shape a .blueprint wants it, before it is written out.
 *
 * The one thing worth knowing: blocks is a flat array of palette indices in Y -> Z -> X order, so
 * index = (y * sizeZ + z) * sizeX + x. Both source formats we read use that same ordering, which is
 * why neither reader has to transpose anything.
 */
public final class Structure {
    public short sizeX;
    public short sizeY;
    public short sizeZ;

    /** Each entry is a blockstate as {Name: "minecraft:oak_stairs", Properties: {facing: "north"}}. */
    public final List<Map<String, Object>> palette = new ArrayList<>();

    /** Palette indices, Y -> Z -> X. */
    public short[] blocks;

    /** Block entities, already carrying lowercase x/y/z shorts and an "id", as a blueprint wants. */
    public final List<Map<String, Object>> tileEntities = new ArrayList<>();

    public int index(final int x, final int y, final int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /**
     * Interns a blockstate into the palette and returns its index.
     *
     * Both readers intern air first, so air always lands at index 0. Nothing in Structurize demands
     * that, but its own scans always emit air first, and matching what the mod produces is cheaper
     * than finding out the hard way that something assumed it.
     */
    public short intern(final Map<String, Object> state) {
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i).equals(state)) {
                return (short) i;
            }
        }
        palette.add(state);
        return (short) (palette.size() - 1);
    }

    /** The blockstate compound for plain air, which is the fill for anything a format leaves out. */
    public static Map<String, Object> air() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("Name", "minecraft:air");
        return m;
    }

    /**
     * Every mod namespace this structure references, minecraft excluded.
     *
     * Structurize stores this as required_mods and uses it to tell the player a build needs a mod
     * they do not have, rather than silently dropping the blocks.
     */
    public List<String> requiredMods() {
        final Set<String> mods = new LinkedHashSet<>();
        for (final Map<String, Object> state : palette) {
            addNamespace(mods, (String) state.get("Name"));
        }
        for (final Map<String, Object> te : tileEntities) {
            addNamespace(mods, (String) te.get("id"));
        }
        return new ArrayList<>(mods);
    }

    private static void addNamespace(final Set<String> mods, final String id) {
        if (id == null) {
            return;
        }
        final int colon = id.indexOf(':');
        if (colon <= 0) {
            return;
        }
        final String namespace = id.substring(0, colon);
        if (!"minecraft".equals(namespace)) {
            mods.add(namespace);
        }
    }
}
