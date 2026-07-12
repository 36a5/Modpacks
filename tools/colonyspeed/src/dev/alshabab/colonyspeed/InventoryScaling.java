package dev.alshabab.colonyspeed;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;

/**
 * Grows a worker's inventory with his hut level.
 *
 * A citizen's inventory is 27 slots plus whatever the CITIZEN_INV_SLOTS research effect adds, and
 * nothing else: there is no config for it, and the hut level is not part of the sum. The research is
 * three levels of one effect (Deep Pockets +9, Loaded +18, Heavily Loaded +27), so the stock ceiling
 * is 54 slots and it costs a level 5 Library and 256 emeralds to get there.
 *
 * That inventory is what decides how often a Builder walks back to his hut: AbstractEntityAIStructure
 * sends him off to dump the moment InventoryCitizen.hasSpace() goes false. Slots are *stacks*, not
 * items, so 81 slots is 81 distinct block types at 64 each — more than any MineColonies blueprint
 * asks for, which is what "he never has to go back" actually means in practice.
 *
 * <h2>Why 81 is the hard ceiling</h2>
 * WindowCitizenInventory sizes itself as {@code 114 + Math.min(9, inventoryRows) * 18} and does not
 * scroll. Past nine rows the extra slots still exist and the worker still uses them, but they are
 * drawn below the background, on top of the player's own inventory. So the GUI, not the AI, is what
 * caps this — and maxSlots is range-limited to 81 in the config so the cap cannot be configured away.
 */
public final class InventoryScaling {
    /** MineColonies' own default, and the size a level 1 hut still gets. */
    public static final int BASE_SLOTS = 27;

    /** The inventory is laid out in rows of nine, and a partial row is slots you cannot click. */
    private static final int ROW = 9;

    /** Hut levels run 1..5; level 5 is where maxSlots is reached. */
    private static final int MAX_HUT_LEVEL = 5;

    /**
     * @param citizen   the citizen whose inventory is being resized; may be null for a citizen with
     *                  no colony data yet
     * @param stockSize the size MineColonies computed, i.e. 27 + the research effect
     * @return the size to actually resize to
     */
    public static int scale(final ICitizenData citizen, final int stockSize) {
        if (!ColonySpeed.Config.SCALE_INVENTORY.get() || citizen == null) {
            return stockSize;
        }

        // Only workers scale. A child or an unemployed citizen has no hut to have a level.
        final IBuilding work = citizen.getWorkBuilding();
        if (work == null) {
            return stockSize;
        }

        final int level = work.getBuildingLevel();
        if (level <= 1) {
            return stockSize;
        }

        final int max = ColonySpeed.Config.MAX_SLOTS.get();
        if (max <= BASE_SLOTS) {
            return stockSize;
        }

        // Straight line from BASE_SLOTS at level 1 to max at level 5, then floored to whole rows:
        //   level 2 -> 36    level 3 -> 54    level 4 -> 63    level 5 -> 81   (at max = 81)
        final int capped = Math.min(level, MAX_HUT_LEVEL);
        final int size = BASE_SLOTS + (max - BASE_SLOTS) * (capped - 1) / (MAX_HUT_LEVEL - 1);
        final int rows = Math.min(size, max) / ROW * ROW;

        // Never shrink. A colony that has researched Heavily Loaded may already hand a level 2 worker
        // more than his hut level would, and resizing downwards drops whatever sat in the slots that
        // disappear. Growing only is also what makes a destroyed or downgraded hut harmless.
        return Math.max(stockSize, rows);
    }

    private InventoryScaling() {
    }
}
