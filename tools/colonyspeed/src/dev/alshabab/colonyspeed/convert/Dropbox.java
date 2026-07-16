package dev.alshabab.colonyspeed.convert;

import dev.alshabab.colonyspeed.ColonySpeed;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The drag-and-drop folder.
 *
 * A player drops a .schem or a .nbt into blueprints/drop/ and gets a decoration the Builder can
 * build. Everything that makes that work lives here.
 *
 * Why this is client-side only: Structurize already ships a client's blueprint up to the server when
 * it is placed (BlueprintSyncMessage, gated on the server's allowPlayerSchematics, which is on). So
 * a blueprint that exists on one player's disk is buildable on the server without anyone touching
 * the server files. Nothing here needs to run server-side.
 *
 * Why a whole pack rather than loose files: Structurize only looks inside folders that have a
 * pack.json, and only treats blueprints under decorations/ as decorations. So we generate a pack
 * and drop the converted blueprints into it.
 */
public final class Dropbox {
    /** Folder name of the generated pack, under .minecraft/blueprints/. */
    private static final String PACK_DIR = "mydecorations";
    /** Category the converted blueprints land in - this is the tab name the player sees. */
    private static final String CATEGORY = "custom";

    /**
     * The "icon" key is not optional, and an empty string is the right value.
     *
     * StructurePackMeta parses it as json.get("icon").getAsString() with no null check, so a pack.json
     * without the key throws an NPE during discovery and the pack is skipped in silence - it simply
     * never appears in the pack list, with nothing in the log to say why. Structurize's own per-player
     * scan packs ship "icon":"" and fall back to the scepter icon, so that is what we copy.
     */
    private static final String PACK_JSON = "{\n"
            + "  \"name\": \"My Decorations\",\n"
            + "  \"icon\": \"\",\n"
            + "  \"authors\": [\n"
            + "    \"You\"\n"
            + "  ],\n"
            + "  \"desc\": \"Schematics you dropped into blueprints/drop/.\",\n"
            + "  \"mods\": [\n"
            + "    \"structurize\",\n"
            + "    \"minecolonies\"\n"
            + "  ],\n"
            + "  \"version\": 1,\n"
            + "  \"pack-format\": 1\n"
            + "}\n";

    public static Path blueprintsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("blueprints");
    }

    /** Where the player drops .schem / .nbt files. Has no pack.json, so Structurize ignores it. */
    public static Path dropFolder() {
        return blueprintsRoot().resolve("drop");
    }

    private static Path outputFolder() {
        return blueprintsRoot().resolve(PACK_DIR).resolve("decorations").resolve(CATEGORY);
    }

    /**
     * Creates the drop folder and the pack if they are missing.
     *
     * This has to happen before Structurize scans for packs, otherwise the pack is not registered and
     * nothing shows up until the next restart - hence the hook at the head of its client loader.
     */
    public static void ensureFolders() {
        try {
            Files.createDirectories(dropFolder());
            Files.createDirectories(outputFolder());

            // Rewrite a pack.json that predates the icon fix, otherwise players who already have the
            // broken one keep an invisible pack forever. Anything already carrying an "icon" key is
            // left alone, so a player who has edited the name or description keeps their edits.
            final Path packJson = blueprintsRoot().resolve(PACK_DIR).resolve("pack.json");
            final boolean broken = Files.exists(packJson)
                    && !new String(Files.readAllBytes(packJson), StandardCharsets.UTF_8).contains("\"icon\"");
            if (!Files.exists(packJson) || broken) {
                Files.write(packJson, PACK_JSON.getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException e) {
            ColonySpeed.LOGGER.error("could not create the blueprint drop folders", e);
        }
    }

    /**
     * Converts everything in the drop folder that does not already have an up-to-date blueprint.
     *
     * Safe to call as often as you like: it skips a file whose blueprint already exists and is newer,
     * so reopening the build tool costs a directory listing and nothing more. Source files are left
     * where they are - moving a player's files out from under them is rude, and leaving them means a
     * failed conversion can be retried by fixing the file rather than digging it back out of a bin.
     *
     * @return one human-readable line per file it actually did something about.
     */
    public static List<String> convertAll() {
        final List<String> report = new ArrayList<>();
        ensureFolders();

        try (Stream<Path> files = Files.list(dropFolder())) {
            for (final Path src : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                final String fileName = src.getFileName().toString();
                final String lower = fileName.toLowerCase(Locale.ROOT);

                final boolean isSchem = lower.endsWith(".schem") || lower.endsWith(".schematic");
                final boolean isNbt = lower.endsWith(".nbt");
                if (!isSchem && !isNbt) {
                    continue;
                }

                final String base = fileName.substring(0, fileName.lastIndexOf('.'));
                final Path dest = outputFolder().resolve(base + ".blueprint");

                try {
                    if (Files.exists(dest) && Files.getLastModifiedTime(dest).toMillis() >= Files.getLastModifiedTime(src).toMillis()) {
                        continue;
                    }

                    final Map<String, Object> root;
                    try (InputStream in = Files.newInputStream(src)) {
                        root = Nbt.read(in);
                    }

                    // A .schematic can be either the ancient MCEdit format or a modern Sponge one; the
                    // reader tells them apart by looking for a Palette, and says so if it is neither.
                    final Structure structure = isNbt && root.containsKey("size")
                            ? StructureNbtReader.read(root)
                            : SchemReader.read(root);

                    BlueprintWriter.write(structure, base + ".blueprint", dest);
                    report.add("converted " + fileName + " (" + structure.sizeX + "x" + structure.sizeY
                            + "x" + structure.sizeZ + ")");
                    ColonySpeed.LOGGER.info("converted {} -> {}", fileName, dest);
                } catch (final Exception e) {
                    report.add("FAILED " + fileName + ": " + e.getMessage());
                    ColonySpeed.LOGGER.error("could not convert {}", fileName, e);
                }
            }
        } catch (final IOException e) {
            ColonySpeed.LOGGER.error("could not read the blueprint drop folder", e);
        }

        return report;
    }

    private Dropbox() {
    }
}
