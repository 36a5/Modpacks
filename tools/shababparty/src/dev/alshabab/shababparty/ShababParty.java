package dev.alshabab.shababparty;

import java.util.Arrays;
import java.util.List;

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
        public static final ForgeConfigSpec.BooleanValue WHITE_FLAMES_ABILITY_3_COOLDOWN;
        public static final ForgeConfigSpec.IntValue ULTRA_INSTINCT_DURATION_TICKS;
        public static final ForgeConfigSpec.IntValue ULTRA_INSTINCT_COOLDOWN_TICKS;
        public static final ForgeConfigSpec.BooleanValue ULTRA_INSTINCT_DODGES;
        public static final ForgeConfigSpec.DoubleValue ULTRA_INSTINCT_BEHIND_DISTANCE;
        public static final ForgeConfigSpec.IntValue AFTER_IMAGE_LIFETIME_TICKS;

        public static final ForgeConfigSpec.BooleanValue BOSS_SCALING_ENABLED;
        public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue DRAGON_HEALTH_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue DRAGON_DAMAGE_MULTIPLIER;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_SCALING_EXCLUSIONS;

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

            WHITE_FLAMES_ABILITY_3_COOLDOWN = b
                    .comment("Whether Monarch of White Flames' C ability (Storm Burst) puts a cooldown up.",
                            "",
                            "Solo Leveling gives every job's C ability a 10-second cooldown. It is not a timer:",
                            "the ability applies a job_cooldown_3 mob effect to the caster for 200 ticks, and its",
                            "own branch refuses to fire while that effect is present.",
                            "",
                            "False stops that effect from ever landing on a Monarch of White Flames, so C can be",
                            "pressed as fast as the player likes. The other three jobs share the same effect for",
                            "their own C ability and are untouched.")
                    .define("whiteFlamesAbility3Cooldown", false);
            b.pop();

            b.push("ultraInstinct");
            ULTRA_INSTINCT_DURATION_TICKS = b
                    .comment("How long the counter stance lasts, in ticks. 20 ticks = 1 second.",
                            "While it is up, every attacker is answered: the player leaves an after-image,",
                            "appears behind whoever swung, and hits them with his held weapon.",
                            "Default 600 = 30 seconds.")
                    .defineInRange("durationTicks", 600, 20, 6000);

            ULTRA_INSTINCT_COOLDOWN_TICKS = b
                    .comment("Cooldown after the stance ends, in ticks. Measured from the end, not the start.",
                            "0 = no cooldown, which is the default here: V can be pressed again the moment the",
                            "stance drops, so with dodgeIncomingDamage on this is effectively permanent",
                            "invulnerability for anyone willing to keep pressing it. That is deliberate, but it",
                            "is the knob to turn first if the ability turns out to trivialise fights - either",
                            "raise this, or set dodgeIncomingDamage false to keep the counter without the dodge.")
                    .defineInRange("cooldownTicks", 0, 0, 24000);

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

            AFTER_IMAGE_LIFETIME_TICKS = b
                    .comment("Hard deadline for an after-image left by the counter, in ticks. 20 ticks = 1 second.",
                            "",
                            "Solo Leveling despawns its own after-images 10 ticks after they appear, but only when",
                            "they are spawned through finalizeSpawn - and addFreshEntity, which is how anything",
                            "outside the mod spawns one, does not call it. Every after-image this server has ever",
                            "produced therefore lived forever, ticking and saved into chunk NBT.",
                            "",
                            "This is the backstop. Every after-image entering a server level is discarded once this",
                            "many ticks have passed - including ones already leaked into a world by the old bug,",
                            "which die as soon as their chunk loads. Raise it only to make the after-image linger.")
                    .defineInRange("afterImageLifetimeTicks", 40, 10, 200);
            b.pop();

            b.push("bossScaling");
            BOSS_SCALING_ENABLED = b
                    .comment("Scale up every boss in the shababparty:scaled_bosses tag.",
                            "",
                            "Solo Leveling players out-level the rest of the pack badly enough that its bosses die",
                            "in seconds. This gives them back some weight. Solo Leveling's own bosses keep their",
                            "own numbers - they are not in the tag, and the exclusions below guard them anyway.")
                    .define("enabled", true);

            BOSS_HEALTH_MULTIPLIER = b
                    .comment("Max health multiplier for every boss in the tag except the Ender Dragon.",
                            "Applied once per boss as a permanent attribute modifier, never re-applied.",
                            "",
                            "NOTE: vanilla hard-caps generic.max_health at 1024 and silently clamps anything above",
                            "it - which is why the first attempt at this did nothing and a 360 HP Hydra came out at",
                            "exactly 1024. AttributeFix is in the pack to raise that ceiling to 1,000,000. Remove",
                            "AttributeFix and every boss here collapses back to 1024 no matter what this says.")
                    .defineInRange("healthMultiplier", 150.0D, 1.0D, 2000.0D);

            BOSS_DAMAGE_MULTIPLIER = b
                    .comment("Multiplier on all damage those bosses deal.",
                            "",
                            "Applied to the damage itself rather than to the ATTACK_DAMAGE attribute, because most",
                            "boss damage never touches that attribute - Cataclysm's bosses do their real damage with",
                            "projectiles and area attacks that carry their own numbers, and the Ender Dragon does not",
                            "read ATTACK_DAMAGE at all. Scaling the attribute would look like it worked and change",
                            "almost nothing.",
                            "",
                            "Unlike health, this has no vanilla ceiling - it is applied to the damage number itself,",
                            "not to an attribute - so what is set here is what lands.")
                    .defineInRange("damageMultiplier", 100.0D, 1.0D, 1000.0D);

            DRAGON_HEALTH_MULTIPLIER = b
                    .comment("The Ender Dragon gets its own numbers. Vanilla is 200 HP, so 500 = 100000.")
                    .defineInRange("enderDragonHealthMultiplier", 500.0D, 1.0D, 5000.0D);

            DRAGON_DAMAGE_MULTIPLIER = b
                    .comment("Multiplier on all damage the Ender Dragon deals.")
                    .defineInRange("enderDragonDamageMultiplier", 200.0D, 1.0D, 1000.0D);

            BOSS_SCALING_EXCLUSIONS = b
                    .comment("Never scaled, whatever the tag says. A bare namespace excludes all of its entities.",
                            "",
                            "  sololeveling         - its bosses are balanced against the levelling system already",
                            "  twilightforest:naga  - Twilight Forest's tutorial boss, 120 HP, meant for iron armour.",
                            "  twilightforest:lich  - It and the Naga gate the entire Twilight Forest ladder; at 30x",
                            "                         they would wall off a dimension instead of making it harder.")
                    .defineList("exclusions",
                            Arrays.asList("sololeveling", "twilightforest:naga", "twilightforest:lich"),
                            o -> o instanceof String);
            b.pop();

            SPEC = b.build();
        }

        private Config() {
        }
    }
}
