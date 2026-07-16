package dev.alshabab.colonyspeed.convert;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a Structurize .blueprint.
 *
 * The format is gzipped NBT. Everything here was read back off a real blueprint out of the
 * MineColonies jar and off BlueprintUtil's own bytecode, not from documentation - there isn't any.
 *
 * The only non-obvious part is "blocks": it is an INT array, but it holds *two* palette indices per
 * int - the high short then the low short. BlueprintUtil.convertSaveDataToBlocks unpacks it as
 * flat[i*2] = v >> 16, flat[i*2+1] = v, and then walks that flat array in Y -> Z -> X order. An odd
 * block count means the last int carries one real index and one byte of padding that is never read.
 */
public final class BlueprintWriter {
    /** 1.20.1. Structurize runs its data fixers off this, so a wrong value silently mangles blocks. */
    private static final int MC_DATA_VERSION_1_20_1 = 3465;

    public static void write(final Structure s, final String blueprintName, final Path dest) throws IOException {
        final Map<String, Object> root = new LinkedHashMap<>();

        root.put("version", (byte) 1);
        root.put("mcversion", MC_DATA_VERSION_1_20_1);
        root.put("size_x", s.sizeX);
        root.put("size_y", s.sizeY);
        root.put("size_z", s.sizeZ);

        root.put("palette", new ArrayList<Object>(s.palette));
        root.put("blocks", pack(s.blocks));

        root.put("tile_entities", new ArrayList<Object>(s.tileEntities));
        root.put("entities", new ArrayList<Object>());

        final List<Object> mods = new ArrayList<>(s.requiredMods());
        root.put("required_mods", mods);

        root.put("name", blueprintName);

        // No optional_data: it is where a scan stores its placement anchor, and Blueprint falls back
        // to findPrimaryBlockOffset() when it is absent. Letting Structurize work the anchor out is
        // safer than inventing one and putting the player's build in the ground.

        final Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(dest)) {
            Nbt.write(out, root);
        }
    }

    /** Packs the flat palette indices two-to-an-int, high short first. */
    private static int[] pack(final short[] flat) {
        final int[] packed = new int[(flat.length + 1) / 2];
        for (int i = 0; i < packed.length; i++) {
            final int hi = flat[i * 2];
            final int lo = (i * 2 + 1) < flat.length ? flat[i * 2 + 1] : 0;
            packed[i] = (hi << 16) | (lo & 0xFFFF);
        }
        return packed;
    }

    private BlueprintWriter() {
    }
}
