package dev.alshabab.colonyspeed.convert;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Sponge schematic (.schem) - the format WorldEdit writes and every build site hands out.
 *
 * Handles all three versions. v1 and v2 keep the block data at the root; v3 moved it into a "Blocks"
 * compound and wrapped the whole thing in a "Schematic" tag. Underneath they are the same idea:
 * a Palette mapping blockstate strings to indices, and a varint-per-block byte array in Y -> Z -> X
 * order, which is the same order a .blueprint uses.
 */
public final class SchemReader {
    @SuppressWarnings("unchecked")
    public static Structure read(final Map<String, Object> rootIn) throws IOException {
        // v3 wraps everything in a "Schematic" compound. v1/v2 are bare.
        final Map<String, Object> root = rootIn.get("Schematic") instanceof Map
                ? (Map<String, Object>) rootIn.get("Schematic")
                : rootIn;

        final Structure out = new Structure();
        out.sizeX = num(root.get("Width"), "Width");
        out.sizeY = num(root.get("Height"), "Height");
        out.sizeZ = num(root.get("Length"), "Length");

        // v3: Blocks { Palette, Data, BlockEntities }. v1/v2: Palette + BlockData at the root.
        final Map<String, Object> blocksTag = root.get("Blocks") instanceof Map
                ? (Map<String, Object>) root.get("Blocks")
                : root;

        final Map<String, Object> rawPalette = (Map<String, Object>) blocksTag.get("Palette");
        if (rawPalette == null) {
            throw new IOException("no Palette - not a Sponge schematic?");
        }
        final Object rawData = blocksTag.containsKey("Data") ? blocksTag.get("Data") : blocksTag.get("BlockData");
        if (!(rawData instanceof byte[])) {
            throw new IOException("no BlockData/Data byte array - not a Sponge schematic?");
        }

        // The schematic's own indices are arbitrary; remap them onto our palette, which starts at air.
        out.intern(Structure.air());
        final Map<Integer, Short> remap = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> e : rawPalette.entrySet()) {
            remap.put(((Number) e.getValue()).intValue(), out.intern(parseBlockState(e.getKey())));
        }

        final int volume = out.sizeX * out.sizeY * out.sizeZ;
        out.blocks = new short[volume];
        final byte[] data = (byte[]) rawData;

        // Palette indices are varints, so the array is not indexable - it has to be walked start to end.
        int cursor = 0;
        for (int i = 0; i < volume; i++) {
            if (cursor >= data.length) {
                throw new IOException("BlockData ran out at block " + i + " of " + volume);
            }
            int value = 0;
            int shift = 0;
            while (true) {
                final byte b = data[cursor++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 31) {
                    throw new IOException("malformed varint in BlockData at block " + i);
                }
            }
            final Short mapped = remap.get(value);
            // A schematic that indexes outside its own palette is corrupt; treat the block as air
            // rather than throwing away the whole build.
            out.blocks[i] = mapped == null ? 0 : mapped;
        }

        final Object rawTiles = blocksTag.containsKey("BlockEntities")
                ? blocksTag.get("BlockEntities")
                : root.get("TileEntities");
        if (rawTiles instanceof List) {
            for (final Object o : (List<Object>) rawTiles) {
                final Map<String, Object> te = toBlueprintTile((Map<String, Object>) o);
                if (te != null) {
                    out.tileEntities.add(te);
                }
            }
        }

        return out;
    }

    /**
     * Turns a Sponge BlockEntity into the shape a blueprint stores: the tile's own data, plus
     * lowercase x/y/z shorts and a lowercase "id".
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toBlueprintTile(final Map<String, Object> src) {
        final Object pos = src.get("Pos");
        if (!(pos instanceof int[]) || ((int[]) pos).length < 3) {
            return null;
        }
        final int[] p = (int[]) pos;

        // v3 nests the payload under "Data". v2 leaves it inline alongside Pos/Id.
        final Map<String, Object> out = new LinkedHashMap<>();
        if (src.get("Data") instanceof Map) {
            out.putAll((Map<String, Object>) src.get("Data"));
        } else {
            out.putAll(src);
            out.remove("Pos");
            out.remove("Id");
        }

        final Object id = src.get("Id") != null ? src.get("Id") : src.get("id");
        if (id != null) {
            out.put("id", id);
        }
        out.put("x", (short) p[0]);
        out.put("y", (short) p[1]);
        out.put("z", (short) p[2]);
        return out;
    }

    /**
     * Parses "minecraft:oak_stairs[facing=north,half=bottom]" into {Name, Properties}.
     *
     * Every property value stays a string, which is exactly how vanilla stores them, so Structurize
     * resolves them with its normal blockstate reader and we never touch a registry.
     */
    static Map<String, Object> parseBlockState(final String raw) {
        final Map<String, Object> state = new LinkedHashMap<>();
        final int bracket = raw.indexOf('[');
        if (bracket < 0) {
            state.put("Name", raw);
            return state;
        }

        state.put("Name", raw.substring(0, bracket));
        final String inner = raw.substring(bracket + 1, raw.endsWith("]") ? raw.length() - 1 : raw.length());
        if (inner.isEmpty()) {
            return state;
        }

        final Map<String, Object> props = new LinkedHashMap<>();
        for (final String pair : inner.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        if (!props.isEmpty()) {
            state.put("Properties", props);
        }
        return state;
    }

    private static short num(final Object o, final String what) throws IOException {
        if (!(o instanceof Number)) {
            throw new IOException("missing or bad " + what);
        }
        return ((Number) o).shortValue();
    }

    private SchemReader() {
    }
}
