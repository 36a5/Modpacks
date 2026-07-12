package dev.alshabab.colonyspeed;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Makes a MineColonies worker's block breaking, block placing, and inventory scale with his hut level.
 *
 * Stock MineColonies does not use the hut level for any of the three. Breaking is
 * 500 * 0.85^(secondarySkill/2) * hardness / toolSpeed, placing is a flat 150 / (primarySkill/2 + 10)
 * ticks per block, and inventory is a flat 27 slots plus a research bonus. Only the citizen's skills
 * and the colony's research move any of them. A builder in a level 5 hut therefore works at the same
 * pace, and carries exactly as much, as one who just moved into a level 1 hut.
 *
 * Speed is handled in SpeedScaling, inventory in InventoryScaling. They are separate because they cap
 * for different reasons: speed bottoms out at a one-tick delay, inventory tops out at what the citizen
 * inventory screen can actually draw.
 */
@Mod(ColonySpeed.MOD_ID)
public class ColonySpeed {
    public static final String MOD_ID = "colonyspeed";
    public static final Logger LOGGER = LogManager.getLogger("ColonySpeed");

    public ColonySpeed() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static final class Config {
        public static final ForgeConfigSpec SPEC;

        public static final ForgeConfigSpec.DoubleValue SPEED_PER_LEVEL;
        public static final ForgeConfigSpec.IntValue MIN_DELAY_TICKS;
        public static final ForgeConfigSpec.BooleanValue SCALE_BREAKING;
        public static final ForgeConfigSpec.BooleanValue SCALE_PLACING;
        public static final ForgeConfigSpec.BooleanValue SCALE_INVENTORY;
        public static final ForgeConfigSpec.IntValue MAX_SLOTS;

        static {
            ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

            b.push("hutLevelScaling");
            SPEED_PER_LEVEL = b
                    .comment("How much faster a worker gets for each level his hut gains.",
                            "The delay between blocks is divided by speedPerLevel^(hutLevel - 1), so at 2.0 a",
                            "level 1 hut works at stock speed, level 2 is twice as fast, level 3 four times,",
                            "level 4 eight times and level 5 sixteen times.",
                            "Set to 1.0 to turn the whole mod off and leave MineColonies alone.")
                    .defineInRange("speedPerLevel", 2.0, 1.0, 10.0);

            MIN_DELAY_TICKS = b
                    .comment("Floor on the per-block delay, in ticks, after scaling.",
                            "MineColonies happily accepts a delay of 0, which makes the worker place his whole",
                            "blueprint in a single tick with no animation and a large chunk-update spike. 1 tick",
                            "is 20 blocks a second, which is already far past what a player can do, and it keeps",
                            "the worker visibly working.")
                    .defineInRange("minDelayTicks", 1, 0, 100);

            SCALE_BREAKING = b
                    .comment("Scale how fast the worker breaks blocks.",
                            "Applies to every worker that digs: Builder, Miner, Quarrier, Lumberjack, Farmer and",
                            "anything else built on the same AI. Each is scaled off his own hut's level.")
                    .define("scaleBreaking", true);

            SCALE_PLACING = b
                    .comment("Scale how fast the worker places blocks from a blueprint.",
                            "Applies to the workers that build structures: Builder, Miner and Quarrier.")
                    .define("scalePlacing", true);

            b.pop();

            b.push("hutLevelInventory");
            SCALE_INVENTORY = b
                    .comment("Grow a worker's inventory with his hut level, so he makes fewer trips",
                            "back to his hut for materials.",
                            "Stock MineColonies gives every citizen 27 slots plus a research bonus, and never",
                            "reads the hut level. Off by default in MineColonies terms; on here.")
                    .define("scaleInventory", true);

            MAX_SLOTS = b
                    .comment("Slots a worker reaches at hut level 5. Level 1 stays at the stock 27 and the",
                            "levels in between are spread evenly, floored to whole rows of nine:",
                            "  L1 = 27    L2 = 36    L3 = 54    L4 = 63    L5 = maxSlots",
                            "81 is a hard ceiling, not a preference. The citizen inventory screen sizes itself",
                            "as 114 + min(9, rows) * 18 pixels and does not scroll, so slots past the ninth row",
                            "are drawn on top of the player's own inventory and cannot be clicked. The worker",
                            "would still use them; you just could not see them.",
                            "81 slots is 81 *stacks* of distinct blocks, which is more than any blueprint asks",
                            "for - that is what makes a level 5 Builder effectively never run out of room.")
                    .defineInRange("maxSlots", 81, 27, 81);
            b.pop();

            SPEC = b.build();
        }

        private Config() {
        }
    }
}
