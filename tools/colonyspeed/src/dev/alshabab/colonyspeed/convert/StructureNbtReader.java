package dev.alshabab.colonyspeed.convert;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a vanilla structure block file (.nbt) - what the structure block saves, what Create's
 * schematics use, and what most online converters will hand you.
 *
 * This is the easy one: its palette entries are already {Name, Properties} compounds, the exact
 * shape a .blueprint stores, so they get copied across untouched.
 *
 * The one trap is that "blocks" is a sparse list of positions, not a dense grid, and exporters
 * disagree about whether air is included. So the grid is pre-filled with air and only the listed
 * positions are written over.
 */
public final class StructureNbtReader {
    @SuppressWarnings("unchecked")
    public static Structure read(final Map<String, Object> root) throws IOException {
        final Object rawSize = root.get("size");
        if (!(rawSize instanceof List) || ((List<Object>) rawSize).size() < 3) {
            throw new IOException("no size list - not a vanilla structure .nbt?");
        }
        final List<Object> size = (List<Object>) rawSize;

        final Structure out = new Structure();
        out.sizeX = ((Number) size.get(0)).shortValue();
        out.sizeY = ((Number) size.get(1)).shortValue();
        out.sizeZ = ((Number) size.get(2)).shortValue();

        final Object rawPalette = root.get("palette");
        if (!(rawPalette instanceof List)) {
            throw new IOException("no palette - not a vanilla structure .nbt?");
        }

        // Air first, so it lands at index 0 and doubles as the fill for any position the file omits.
        out.intern(Structure.air());
        final short[] remap = new short[((List<Object>) rawPalette).size()];
        int i = 0;
        for (final Object o : (List<Object>) rawPalette) {
            remap[i++] = out.intern((Map<String, Object>) o);
        }

        out.blocks = new short[out.sizeX * out.sizeY * out.sizeZ]; // zero-filled == air

        final Object rawBlocks = root.get("blocks");
        if (!(rawBlocks instanceof List)) {
            throw new IOException("no blocks list - not a vanilla structure .nbt?");
        }

        for (final Object o : (List<Object>) rawBlocks) {
            final Map<String, Object> entry = (Map<String, Object>) o;
            final Object rawPos = entry.get("pos");
            if (!(rawPos instanceof List) || ((List<Object>) rawPos).size() < 3) {
                continue;
            }
            final List<Object> pos = (List<Object>) rawPos;
            final int x = ((Number) pos.get(0)).intValue();
            final int y = ((Number) pos.get(1)).intValue();
            final int z = ((Number) pos.get(2)).intValue();
            if (x < 0 || y < 0 || z < 0 || x >= out.sizeX || y >= out.sizeY || z >= out.sizeZ) {
                continue;
            }

            final int state = ((Number) entry.get("state")).intValue();
            if (state < 0 || state >= remap.length) {
                continue;
            }
            out.blocks[out.index(x, y, z)] = remap[state];

            // A block entity rides along in "nbt", already carrying its own "id".
            if (entry.get("nbt") instanceof Map) {
                final Map<String, Object> te = new LinkedHashMap<>((Map<String, Object>) entry.get("nbt"));
                te.put("x", (short) x);
                te.put("y", (short) y);
                te.put("z", (short) z);
                out.tileEntities.add(te);
            }
        }

        return out;
    }

    private StructureNbtReader() {
    }
}
