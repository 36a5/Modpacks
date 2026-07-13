package dev.alshabab.shababparty;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Glue between FTB Teams (which owns party membership) and Solo Craft: Reawakening (which owns
 * levelling and friendly fire). See PartySupport for what it actually does.
 */
@Mod(ShababParty.MOD_ID)
public class ShababParty {
    public static final String MOD_ID = "shababparty";
    public static final Logger LOGGER = LogManager.getLogger("ShababParty");

    public ShababParty() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static final class Config {
        public static final ForgeConfigSpec SPEC;

        public static final ForgeConfigSpec.BooleanValue PARTY_BRIDGE_ENABLED;
        public static final ForgeConfigSpec.BooleanValue XP_SHARE_ENABLED;
        public static final ForgeConfigSpec.BooleanValue JOB_POINT_SHARE_ENABLED;
        public static final ForgeConfigSpec.DoubleValue XP_SHARE_RADIUS;

        public static final ForgeConfigSpec.ConfigValue<String> WHITE_FLAMES_ABILITY_4;
        public static final ForgeConfigSpec.ConfigValue<String> GRAND_MAGE_ABILITY_4;
        public static final ForgeConfigSpec.IntValue ULTRA_INSTINCT_DURATION_TICKS;
        public static final ForgeConfigSpec.IntValue ULTRA_INSTINCT_COOLDOWN_TICKS;
        public static final ForgeConfigSpec.BooleanValue ULTRA_INSTINCT_DODGES;
        public static final ForgeConfigSpec.DoubleValue ULTRA_INSTINCT_BEHIND_DISTANCE;

        static {
            ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

            b.push("party");
            PARTY_BRIDGE_ENABLED = b
                    .comment("Copy each player's FTB Teams party into Solo Leveling's party field.",
                            "This is what stops party members from damaging each other: Solo Leveling",
                            "cancels attacks between two players whose party field matches.",
                            "With this on, FTB Teams is the single source of truth for who is partied,",
                            "and Solo Leveling's own /Party command is overridden.")
                    .define("bridgeFtbTeamsToSoloLeveling", true);

            XP_SHARE_ENABLED = b
                    .comment("Share Solo Leveling XP with party members when a party member kills a mob.",
                            "Each member is paid by Solo Leveling's own XP routine, so their personal XP",
                            "multiplier, rank and the soloLevelingXPMultiplier gamerule all still apply.",
                            "The killer is paid by Solo Leveling directly and is not double-paid here.")
                    .define("shareXpWithParty", true);

            JOB_POINT_SHARE_ENABLED = b
                    .comment("Share Solo Leveling job advancement points the same way as XP.",
                            "Without this, only the player who lands the killing blow earns job points,",
                            "so partied players fall behind on job progression.")
                    .define("shareJobPointsWithParty", true);

            XP_SHARE_RADIUS = b
                    .comment("How close a party member must be to the kill to earn a share, in blocks.",
                            "Applies to both XP and job points.",
                            "Members in another dimension never earn a share.",
                            "Set to 0 for no distance limit (anyone in the same dimension earns).")
                    .defineInRange("xpShareRadius", 64.0D, 0.0D, 1024.0D);
            b.pop();

            b.push("abilities");
            WHITE_FLAMES_ABILITY_4 = b
                    .comment("The V key (ability 4) for Monarch of White Flames.",
                            "",
                            "Solo Leveling never finished this one: its branch of Ability4OnKeyPressedProcedure",
                            "prints the literal string \"ability wip\" and returns. Grand Mage's does the same.",
                            "The abilities themselves were finished, with their own entities and renderers, and",
                            "then never wired to a key - nothing in the mod calls any of them. This picks one.",
                            "",
                            "  ultra_instinct     - counter stance. See the ultraInstinct section below.",
                            "  fire_release_aoe   - flame burst centred on you",
                            "  fire_release_beam  - flame beam",
                            "  fire_release_spread- flame cone",
                            "  fire_tornado       - launches a fire tornado",
                            "  flame_vortex       - launches a flame vortex",
                            "  heavy_flame        - heavy flame projectile",
                            "  none               - leave the mod's \"ability wip\" message alone")
                    .defineInList("whiteFlamesAbility4", Ability4.ULTRA_INSTINCT, Ability4.NAMES);

            GRAND_MAGE_ABILITY_4 = b
                    .comment("The V key (ability 4) for Grand Mage. Same list of values as above.")
                    .defineInList("grandMageAbility4", "fire_tornado", Ability4.NAMES);
            b.pop();

            b.push("ultraInstinct");
            ULTRA_INSTINCT_DURATION_TICKS = b
                    .comment("How long the counter stance lasts, in ticks. 20 ticks = 1 second.",
                            "While it is up, every attacker is answered: the player leaves an after-image,",
                            "appears behind whoever swung, and hits them with his held weapon.")
                    .defineInRange("durationTicks", 200, 20, 6000);

            ULTRA_INSTINCT_COOLDOWN_TICKS = b
                    .comment("Cooldown after the stance ends, in ticks. Measured from the end, not the start.",
                            "The mod's own stub had no cooldown at all, because it did nothing - so this whole",
                            "cost exists only here, and it is the only thing stopping the ability being free.")
                    .defineInRange("cooldownTicks", 1200, 0, 24000);

            ULTRA_INSTINCT_DODGES = b
                    .comment("Whether the incoming hit is cancelled outright (a true dodge) or still lands.",
                            "Cancelled on LivingAttackEvent, which is before damage is worked out - so no armour",
                            "damage, no knockback, no hurt animation. Set false if untouchable-while-active is",
                            "too strong and you only want the counter-attack.")
                    .define("dodgeIncomingDamage", true);

            ULTRA_INSTINCT_BEHIND_DISTANCE = b
                    .comment("How far behind the attacker the player lands, in blocks.",
                            "Behind means behind where the attacker is *facing*, not where the player came from,",
                            "so a player already stood at his back still gets moved.")
                    .defineInRange("behindDistance", 1.5D, 0.5D, 6.0D);
            b.pop();

            SPEC = b.build();
        }

        private Config() {
        }
    }
}
