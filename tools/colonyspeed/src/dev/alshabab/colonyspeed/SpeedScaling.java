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
        // Admin override: ignore the hut level entirely and force the delay.
        //
        // The hut-level scaling below deliberately leaves a level 1 hut at stock speed - the divisor
        // is speedPerLevel^0, which is 1. That is correct as a rule and useless in the one case where
        // you actually want speed: rebuilding a hut that got destroyed, or a brand new colony, where
        // every hut IS level 1. This is the escape hatch. Set it to 0 and a Builder puts a whole
        // blueprint down in a single tick.
        //
        // Math.min, not a plain return, so the override can only ever make a worker faster. Setting
        // it to 20 must not slow down a worker whose stock delay is already 4.
        final int override = ColonySpeed.Config.OVERRIDE_DELAY_TICKS.get();
        if (override >= 0) {
            return Math.min(delay, override);
        }

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
