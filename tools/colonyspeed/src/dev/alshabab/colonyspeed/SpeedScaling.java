package dev.alshabab.colonyspeed;

import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;

/**
 * Turns a stock MineColonies per-block delay into a hut-level-scaled one.
 *
 * Both of the delays we touch are "ticks until the worker may touch the next block", so faster means
 * a smaller number and the scaling is a division, not a multiplication.
 */
public final class SpeedScaling {
    /**
     * @param ai    the worker AI, which is always an AbstractEntityAIBasic and so always carries the
     *              building it belongs to
     * @param delay the delay MineColonies computed, in ticks
     * @return the delay after dividing by speedPerLevel^(hutLevel - 1), floored at minDelayTicks
     */
    public static int scale(final Object ai, final int delay) {
        final double perLevel = ColonySpeed.Config.SPEED_PER_LEVEL.get();
        if (perLevel <= 1.0 || delay <= 0) {
            return delay;
        }

        final int level = hutLevel(ai);
        // A hut that is not built yet reports level 0. Treat it as level 1 so the scaling can never
        // divide by a fraction and make the worker slower than stock.
        if (level <= 1) {
            return delay;
        }

        final double divisor = Math.pow(perLevel, level - 1);
        final int scaled = (int) Math.round(delay / divisor);
        return Math.max(ColonySpeed.Config.MIN_DELAY_TICKS.get(), scaled);
    }

    private static int hutLevel(final Object ai) {
        if (!(ai instanceof AbstractEntityAIBasic)) {
            return 0;
        }
        final AbstractBuilding building = ((AbstractEntityAIBasic<?, ?>) ai).building;
        return building == null ? 0 : building.getBuildingLevel();
    }

    private SpeedScaling() {
    }
}
