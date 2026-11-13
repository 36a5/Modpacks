package dev.alshabab.shababparty;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import net.solocraft.network.SololevelingModVariables;

/**
 * Normal movement speed unless the player is dashing.
 *
 * <h2>What Solo Leveling does</h2>
 * Its SpeedUpdateProcedure sets the movement_speed BASE value from the player tick: walking is
 * dash * 0.1, and sprinting is dash * (0.13 + 0.0005 * Speed * speedpercent / 100). dash is 1 normally
 * and 1.5 while the dash (C outside combat mode, costs mana) is toggled on. With Agility in the
 * hundreds or thousands, plain sprinting became many times vanilla speed, which also burned hunger.
 *
 * <h2>What this changes</h2>
 * While dash is off (1 or less), the base is put back to vanilla 0.1, so walking and sprinting feel
 * normal whatever the Agility. While dash is on, the mod's own formula is left alone, so a dashing
 * player is as fast as their stats. Runs at LOWEST priority on the END phase, after Solo Leveling's
 * own handler in the same tick, on both sides so the client's prediction matches the server.
 * Only the base value is touched: modifiers from potions, gear and other mods still apply on top.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpeedControl {

    private static final double VANILLA_BASE = 0.1D;
    private static Attribute movementSpeed;

    private SpeedControl() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ShababParty.Config.NORMAL_SPEED_ENABLED.get()) {
            return;
        }
        final Player player = event.player;
        final SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
        if (vars == null || vars.dash > 1.0D) {
            return; // dashing: Solo Leveling's Agility-based speed stands
        }
        if (movementSpeed == null) {
            movementSpeed = ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation("minecraft", "generic.movement_speed"));
            if (movementSpeed == null) {
                return;
            }
        }
        final AttributeInstance speed = player.m_21051_(movementSpeed); // getAttribute
        if (speed != null && speed.m_22115_() != VANILLA_BASE) { // getBaseValue
            speed.m_22100_(VANILLA_BASE); // setBaseValue
        }
    }
}
