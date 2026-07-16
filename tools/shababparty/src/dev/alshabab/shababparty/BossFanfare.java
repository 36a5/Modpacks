package dev.alshabab.shababparty;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Makes a big kill an event: when a boss over announceHpThreshold dies, the whole server hears about
 * it and fireworks go up at the corpse. Status is a reward too - the announcement is what makes other
 * players ask "how", and asking "how" is what makes them fight the ladder.
 *
 * Boss names come from the registry id's path (ancient_remnant -> "Ancient Remnant") rather than the
 * display-name component, which keeps this free of the Component string API and always matches what
 * /scaling tier calls the boss.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossFanfare {

    private static final int ROCKETS = 5;

    private BossFanfare() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        final LivingEntity dead = event.getEntity();
        if (!(dead.m_9236_() instanceof ServerLevel level) || !BossScaling.isScaled(dead)) {
            return;
        }
        final double maxHp = dead.m_21233_(); // getMaxHealth
        if (maxHp < ShababParty.Config.ANNOUNCE_HP_THRESHOLD.get()) {
            return;
        }

        final MinecraftServer server = level.m_7654_(); // getServer
        if (server == null) {
            return;
        }

        final Player earner = PartySupport.resolveXpEarner(event.getSource());
        final String slayer = earner != null ? earner.m_6302_() : "Someone"; // getScoreboardName
        server.m_6846_().m_240416_( // getPlayerList().broadcastSystemMessage
                Component.m_237113_(slayer + " has slain " + bossName(dead)
                                + "  (" + (int) (maxHp / 1000.0D) + "k HP)!")
                        .m_130944_(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);

        launchFireworks(level, dead);
    }

    /** "cataclysm:ancient_remnant" -> "Ancient Remnant". */
    private static String bossName(final LivingEntity boss) {
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(boss.m_6095_());
        if (id == null) {
            return "a boss";
        }
        final StringBuilder pretty = new StringBuilder();
        for (final String word : id.m_135815_().split("_")) { // getPath
            if (!word.isEmpty()) {
                pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return pretty.toString().trim();
    }

    private static void launchFireworks(final ServerLevel level, final LivingEntity dead) {
        final Item rocketItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "firework_rocket"));
        if (rocketItem == null) {
            return;
        }

        // A large gold-and-red burst. Fireworks read their look from item NBT:
        // {Fireworks:{Flight:1b,Explosions:[{Type:1b,Colors:[I;...]}]}}
        final ItemStack rocket = new ItemStack(rocketItem);
        final CompoundTag explosion = new CompoundTag();
        explosion.m_128344_("Type", (byte) 1); // putByte - large ball
        explosion.m_128385_("Colors", new int[]{0xFFD700, 0xFF3C3C, 0xFFFFFF}); // putIntArray
        final ListTag explosions = new ListTag();
        explosions.add(explosion);
        final CompoundTag fireworks = new CompoundTag();
        fireworks.m_128365_("Explosions", explosions); // put
        fireworks.m_128344_("Flight", (byte) 1);
        rocket.m_41784_().m_128365_("Fireworks", fireworks); // getOrCreateTag().put

        for (int i = 0; i < ROCKETS; i++) {
            final double dx = (level.m_213780_().m_188500_() - 0.5D) * 4.0D; // getRandom().nextDouble()
            final double dz = (level.m_213780_().m_188500_() - 0.5D) * 4.0D;
            level.m_7967_(new FireworkRocketEntity( // addFreshEntity
                    level, dead.m_20185_() + dx, dead.m_20186_() + 1.0D, dead.m_20189_() + dz,
                    rocket.m_41777_())); // copy
        }
    }
}
