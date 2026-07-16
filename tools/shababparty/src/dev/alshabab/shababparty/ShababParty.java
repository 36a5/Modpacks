package dev.alshabab.shababparty;

import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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

    /** The mod's first and only item registry. */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    /**
     * Stat Redistribution - a respec. Crafted from a Class Chooser ringed by eight ancient debris;
     * on use, every allocated Solo Leveling stat point returns to SP. See StatRedistributionItem.
     */
    public static final RegistryObject<Item> STAT_REDISTRIBUTION =
            ITEMS.register("stat_redistribution", StatRedistributionItem::new);

    public ShababParty() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Forge watches the config file and fires Reloading when it changes on disk, so a hand-edit
        // to shababparty-common.toml on a running server also lands without a restart - the flat
        // knobs are read per-event anyway, and this drops BossScaling's parsed tier cache.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (final ModConfigEvent.Reloading event) -> BossScaling.invalidateTiers());
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
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_TIERS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_SCALING_EXCLUSIONS;

        public static final ForgeConfigSpec.BooleanValue BOSS_LOOT_ENABLED;
        public static final ForgeConfigSpec.IntValue BOSS_LOOT_DROP_MULTIPLIER;
        public static final ForgeConfigSpec.IntValue BOSS_LOOT_BONUS_ENCHANT_LEVELS;
        public static final ForgeConfigSpec.IntValue BOSS_LOOT_ENCHANT_LEVEL;
        public static final ForgeConfigSpec.IntValue BOSS_LOOT_XP_MULTIPLIER;

        public static final ForgeConfigSpec.BooleanValue BOSS_LEVELS_ENABLED;
        public static final ForgeConfigSpec.DoubleValue LEVELS_PER_10K_HP;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HP_BOUNTY_ITEMS;
        public static final ForgeConfigSpec.DoubleValue PLAYER_DAMAGE_TAKEN;

        public static final ForgeConfigSpec.BooleanValue SHADOW_SCALING_ENABLED;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SHADOW_ELITES;
        public static final ForgeConfigSpec.DoubleValue SHADOW_ELITE_FRACTION;
        public static final ForgeConfigSpec.DoubleValue SHADOW_STANDARD_FRACTION;
        public static final ForgeConfigSpec.DoubleValue SHADOW_DAMAGE_PER_INTELLIGENCE;
        public static final ForgeConfigSpec.IntValue SHADOW_RESYNC_TICKS;

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
                    .comment("Fallback max-health multiplier for a scaled boss NOT named in bossTiers.",
                            "The overworld floor. A boss with an explicit target in bossTiers ignores this.",
                            "",
                            "NOTE: vanilla hard-caps generic.max_health at 1024 and silently clamps anything above",
                            "it - which is why an early attempt at this did nothing and a 360 HP Hydra came out at",
                            "exactly 1024. AttributeFix is in the pack to raise that ceiling to 1,000,000. Remove",
                            "AttributeFix and every boss here collapses back to 1024 no matter what this says.")
                    .defineInRange("healthMultiplier", 10.0D, 1.0D, 2000.0D);

            BOSS_DAMAGE_MULTIPLIER = b
                    .comment("Fallback damage multiplier for a scaled boss NOT named in bossTiers.",
                            "",
                            "Applied to the damage itself rather than to the ATTACK_DAMAGE attribute, because most",
                            "boss damage never touches that attribute - Cataclysm's bosses do their real damage with",
                            "projectiles and area attacks that carry their own numbers, and the Ender Dragon does not",
                            "read ATTACK_DAMAGE at all. Scaling the attribute would look like it worked and change",
                            "almost nothing.")
                    .defineInRange("damageMultiplier", 5.0D, 1.0D, 1000.0D);

            BOSS_TIERS = b
                    .comment("The progression. One entry per boss: \"entity_id=targetHealth,damageMultiplier,levelReward\".",
                            "",
                            "Health here is an absolute total, not a multiplier - because base health does not track",
                            "difficulty (Twilight Forest's Knight Phantom has 35 base HP but comes after the 360 HP",
                            "Hydra), so a multiplier cannot express a progression. Setting the endpoint guarantees each",
                            "boss is tankier than the one before it. levelReward is how many Solo Leveling levels the",
                            "kill grants the party - see [bossLevels].",
                            "",
                            "A boss not listed here falls back to healthMultiplier / damageMultiplier above and",
                            "bossBaseLevels below. Overworld and Nether wandering minibosses are the lowest rung; the",
                            "Ender Dragon is the wall at the end.")
                    .defineList("bossTiers", Arrays.asList(
                            // Overworld / Nether - the lowest tier, brought DOWN from the old flat multiplier.
                            "mutantmonsters:mutant_zombie=1200,3,4",
                            "mutantmonsters:mutant_skeleton=1000,3,3",
                            "mutantmonsters:mutant_creeper=800,3,3",
                            "mutantmonsters:mutant_enderman=1500,4,5",
                            "mowziesmobs:frostmaw=1500,3,5",
                            "mowziesmobs:ferrous_wroughtnaut=1200,3,4",
                            "mowziesmobs:umvuthi=1400,3,4",
                            "mowziesmobs:sculptor=1600,3,5",
                            "conjurer_illager:conjurer=900,3,3",
                            "illagerinvasion:invoker=800,3,3",
                            "deeperdarker:stalker=1500,4,5",
                            "born_in_chaos_v1:sir_pumpkinhead=1200,3,4",
                            "born_in_chaos_v1:lord_pumpkinhead=2000,4,6",
                            "born_in_chaos_v1:fallen_chaos_knight=1000,3,3",
                            "born_in_chaos_v1:nightmare_stalker=1000,3,3",
                            "minecraft:elder_guardian=1500,3,4",
                            "minecraft:wither=4000,4,6",
                            "minecraft:warden=6000,5,8",
                            // Twilight Forest - the gated ladder.
                            "twilightforest:naga=2000,3,10",
                            "twilightforest:lich=3500,4,12",
                            "twilightforest:minoshroom=5500,5,15",
                            "twilightforest:hydra=8000,6,18",
                            "twilightforest:knight_phantom=11000,7,22",
                            "twilightforest:ur_ghast=15000,8,26",
                            "twilightforest:alpha_yeti=20000,9,30",
                            "twilightforest:snow_queen=26000,10,35",
                            // Aether - Bronze, Silver, Gold.
                            "aether:slider=22000,8,30",
                            "aether:valkyrie_queen=32000,11,38",
                            "aether:sun_spirit=42000,14,45",
                            // Cataclysm - Ancient Remnant last.
                            "cataclysm:netherite_monstrosity=12000,8,20",
                            "cataclysm:ender_guardian=16000,9,25",
                            "cataclysm:ignis=22000,11,30",
                            "cataclysm:maledictus=22000,11,30",
                            "cataclysm:the_harbinger=32000,13,38",
                            "cataclysm:scylla=32000,13,38",
                            "cataclysm:the_leviathan=48000,16,48",
                            "cataclysm:ancient_remnant=75000,20,60",
                            // Blue Skies.
                            "blue_skies:summoner=20000,8,25",
                            "blue_skies:starlit_crusher=24000,9,28",
                            "blue_skies:alchemist=28000,10,30",
                            "blue_skies:arachnarch=32000,11,35",
                            // Bosses'Rise - its own chain.
                            "block_factorys_bosses:yeti=16000,8,22",
                            "block_factorys_bosses:sandworm=24000,11,28",
                            "block_factorys_bosses:underworld_knight=36000,14,35",
                            "block_factorys_bosses:infernal_dragon=55000,16,42",
                            "block_factorys_bosses:kraken=55000,16,45",
                            // Standalone endgame.
                            "undergarden:forgotten_guardian=16000,8,22",
                            "deep_aether:eots_controller=45000,13,50",
                            "lost_aether_content:aerwhale_king=52000,14,50",
                            // The wall at the end of the game.
                            "minecraft:ender_dragon=150000,30,100"),
                            o -> o instanceof String);

            BOSS_SCALING_EXCLUSIONS = b
                    .comment("Never scaled at all, whatever the tag or tiers say. A bare namespace excludes all of",
                            "its entities.",
                            "",
                            "  sololeveling - its bosses are balanced against the levelling system already.",
                            "",
                            "Note: Twilight Forest's Naga and Lich are NOT excluded here - in a progression they are the",
                            "dimension's entry rung (2000 / 3500 HP), killable in upgraded overworld gear.")
                    .defineList("exclusions",
                            Arrays.asList("sololeveling"),
                            o -> o instanceof String);
            b.pop();

            b.push("bossLoot");
            BOSS_LOOT_ENABLED = b
                    .comment("Make a scaled boss's drops worth the fight.",
                            "",
                            "A boss with 150x health that drops what it always dropped is a worse deal than not",
                            "fighting it. This applies only to bosses that were actually scaled, so the Naga, the Lich",
                            "and everything of Solo Leveling's keep their vanilla loot along with their vanilla health.")
                    .define("enabled", true);

            BOSS_LOOT_DROP_MULTIPLIER = b
                    .comment("How many copies of each non-gear drop a boss leaves. 1 = vanilla.",
                            "Trophies, materials, Fiery Blood, and so on.",
                            "",
                            "Delivered as extra item drops rather than by inflating the stack size, because a stack of",
                            "320 on an item that maxes at 64 is not a legal stack.")
                    .defineInRange("dropMultiplier", 5, 1, 64);

            BOSS_LOOT_BONUS_ENCHANT_LEVELS = b
                    .comment("Levels added to every enchantment already on a boss's dropped gear.",
                            "",
                            "This deliberately goes past the vanilla ceiling: at 3, a Power V bow comes off the Hydra",
                            "as Power VIII. That is the point - it is what makes boss gear better than anything you can",
                            "build, which is what a 54,000 HP fight has to be worth.")
                    .defineInRange("bonusEnchantLevels", 3, 0, 10);

            BOSS_LOOT_ENCHANT_LEVEL = b
                    .comment("Gear that drops with no enchantments at all is enchanted as if from a table at this",
                            "level, treasure enchantments included. The vanilla table stops at 30.")
                    .defineInRange("enchantLevel", 40, 1, 100);

            BOSS_LOOT_XP_MULTIPLIER = b
                    .comment("Multiplier on the experience a scaled boss drops.",
                            "A boss that takes ten minutes and hands over 20 XP feels worse than no reward at all.")
                    .defineInRange("xpMultiplier", 20, 1, 1000);
            b.pop();

            b.push("bossLevels");
            BOSS_LEVELS_ENABLED = b
                    .comment("Killing a scaled boss grants Solo Leveling levels to the whole party in range.",
                            "",
                            "The reward is computed from the boss's actual max health at death, so a harder boss always",
                            "pays more, and retuning a boss with /scaling tier automatically retunes its reward. The",
                            "third number in a bossTiers entry is no longer used for this. Every party member within",
                            "[party] xpShareRadius of the kill, in the same dimension, is paid - not just the killer.",
                            "Levels arrive through Solo Leveling's own level-up path, so they come with stat points",
                            "and rank like a normal level-up.")
                    .define("enabled", true);

            LEVELS_PER_10K_HP = b
                    .comment("Solo Leveling levels granted per 10,000 max health the dead boss had.",
                            "40 -> a 10,000 HP boss pays ~40 levels, the 150,000 HP Ender Dragon ~600.")
                    .defineInRange("levelsPer10kHp", 40.0D, 0.0D, 1000.0D);

            HP_BOUNTY_ITEMS = b
                    .comment("Items dropped at a scaled boss's corpse, per 10,000 max health it had:",
                            "\"item_id=countPer10k\". A 20,000 HP boss with diamond=32 drops 64 diamonds.",
                            "Add any item here - no rebuild needed. Counts scale linearly and round down (min 1).")
                    .defineList("hpBountyItems",
                            Arrays.asList("minecraft:diamond=32", "minecraft:ancient_debris=2"),
                            o -> o instanceof String);
            b.pop();

            b.push("difficulty");
            PLAYER_DAMAGE_TAKEN = b
                    .comment("Multiplier on all damage players take FROM MOBS. 0.5 = players take half.",
                            "Stacks with the boss damage multipliers: a 10x boss hitting through 0.5 lands 5x.",
                            "Fall damage, lava, starving and other non-mob damage are untouched.")
                    .defineInRange("playerDamageTaken", 0.5D, 0.05D, 10.0D);
            b.pop();

            b.push("shadowScaling");
            SHADOW_SCALING_ENABLED = b
                    .comment("Tie the Shadow Monarch's shadow soldiers to their owner's own stats.",
                            "",
                            "The mod's shadows are static - a level 200 Monarch summons the same Igris a level 40 one",
                            "does. Here a shadow's max health and armour are a fraction of the OWNER's, and its damage",
                            "grows with the owner's Intelligence, so the army is a reflection of the Monarch. A periodic",
                            "re-sync keeps a standing army tracking its owner as he levels, re-gears, or redistributes",
                            "stats. Only the 13 entities in the minecraft:shadows tag are touched - not the mod's other",
                            "tamed entities like flame vortexes and bear traps.",
                            "",
                            "A shadow is never scaled BELOW its vanilla stats: a fresh 20 HP Monarch does not get a",
                            "5 HP Igris. The fraction only ever raises.")
                    .define("enabled", true);

            SHADOW_ELITES = b
                    .comment("The elite shadows: these get eliteFraction of the owner's health and armour;",
                            "every other shadow in the tag gets standardFraction.")
                    .defineList("eliteShadows",
                            Arrays.asList("sololeveling:igris_shadow",
                                    "sololeveling:tusk_shadow",
                                    "sololeveling:beru_shadow"),
                            o -> o instanceof String);

            SHADOW_ELITE_FRACTION = b
                    .comment("Igris, Tusk and Beru get this fraction of the owner's max health and armour.")
                    .defineInRange("eliteFraction", 0.5D, 0.0D, 2.0D);

            SHADOW_STANDARD_FRACTION = b
                    .comment("Every other shadow gets this fraction of the owner's max health and armour.")
                    .defineInRange("standardFraction", 0.25D, 0.0D, 2.0D);

            SHADOW_DAMAGE_PER_INTELLIGENCE = b
                    .comment("Shadow attack-damage multiplier is (1 + ownerIntelligence * this).",
                            "Deliberately generous: a Monarch who pours every stat point into Intelligence fields a",
                            "genuinely dangerous army. 0.08 -> INT 100 = 9x damage, INT 300 = 25x.")
                    .defineInRange("damagePerIntelligence", 0.08D, 0.0D, 10.0D);

            SHADOW_RESYNC_TICKS = b
                    .comment("How often, in ticks, loaded shadows are re-synced to their owner's current stats.",
                            "20 ticks = 1 second. This is what makes an already-summoned army track its Monarch.")
                    .defineInRange("resyncIntervalTicks", 100, 20, 1200);
            b.pop();

            SPEC = b.build();
        }

        private Config() {
        }
    }
}
