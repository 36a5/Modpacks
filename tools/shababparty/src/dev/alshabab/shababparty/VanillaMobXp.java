package dev.alshabab.shababparty;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.solocraft.init.SololevelingModGameRules;
import net.solocraft.network.SololevelingModVariables;

/**
 * A trickle of Solo Leveling XP for ordinary hostile mobs (zombies, skeletons, other mods' monsters).
 *
 * <p>Solo Leveling pays XP from a fixed table of its own mobs. Anything else falls through to a flat
 * 1 XP, but only when the soloDungeonProgressionOnly gamerule is off, and that gamerule is on by
 * default, so a zombie is worth nothing. Turning the gamerule off would pay passive animals too, at
 * a rate this pack does not want. This pays a small, configurable amount for hostile mobs only and
 * leaves the gamerule alone.
 *
 * <p>The amount goes through the same two multipliers Solo Leveling uses: the player's personal
 * xpmultiplier and soloLevelingXPMultiplier / 10. A level needs 16 * level + 8 XP, so with the
 * defaults (0.8 base, gamerule 30) a kill is 2.4 XP and level 1 takes 10 zombies, level 10 takes 70.
 *
 * <p>If soloDungeonProgressionOnly is ever turned off, Solo Leveling starts paying for these mobs
 * itself, so this steps aside rather than pay twice.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaMobXp {

    private VanillaMobXp() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!ShababParty.Config.VANILLA_MOB_XP_ENABLED.get()) {
            return;
        }
        double base = ShababParty.Config.VANILLA_MOB_XP.get();
        if (base <= 0.0D) {
            return;
        }

        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy)) {
            return; // hostile mobs only; cows and sheep stay worthless
        }
        Level level = dead.m_9236_(); // level()
        if (level.m_5776_()) { // isClientSide()
            return;
        }

        // Solo Leveling's own mobs are already priced by its XP table.
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.m_6095_()); // getType()
        if (id == null || "sololeveling".equals(id.m_135827_())) { // getNamespace()
            return;
        }

        GameRules rules = level.m_46469_(); // getGameRules()
        if (!rules.m_46207_(SololevelingModGameRules.SOLO_DUNGEON_PROGRESSION_ONLY)) { // getBoolean
            return; // Solo Leveling pays these itself in this mode
        }
        double gameruleFactor = rules.m_46215_(SololevelingModGameRules.SOLO_LEVELING_XP_MULTIPLIER) / 10.0D; // getInt

        Player killer = PartySupport.resolveXpEarner(event.getSource());
        if (!(killer instanceof ServerPlayer)) {
            return;
        }

        List<ServerPlayer> recipients = ShababParty.Config.XP_SHARE_ENABLED.get()
                ? PartySupport.partyRecipients(killer, dead, level)
                : List.of();
        if (recipients.isEmpty()) {
            recipients = List.of((ServerPlayer) killer);
        }

        for (ServerPlayer player : recipients) {
            pay(player, base * gameruleFactor);
        }
    }

    private static void pay(ServerPlayer player, double amount) {
        SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
        if (vars == null || !vars.Player) {
            return; // not awakened as a hunter, same rule Solo Leveling applies
        }

        double gained = amount * vars.xpmultiplier;
        if (gained <= 0.0D) {
            return;
        }
        vars.Xp += gained;
        vars.syncPlayerVariables(player);

        // Same action-bar line and number format Solo Leveling prints for its own kills.
        String shown = String.format(Locale.FRANCE, "%,.1f", gained);
        player.m_5661_(Component.m_237113_("§bGained §f" + shown + "§b XP"), true); // displayClientMessage
    }
}
