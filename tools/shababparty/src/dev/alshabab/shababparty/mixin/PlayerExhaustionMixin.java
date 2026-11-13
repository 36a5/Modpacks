package dev.alshabab.shababparty.mixin;

import dev.alshabab.shababparty.HungerScaling;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every source of hunger exhaustion (sprinting, jumping, swimming, attacking, natural regeneration)
 * goes through Player.causeFoodExhaustion (m_36399_), so scaling its argument here scales all of them.
 */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "m_36399_", at = @At("HEAD"), argsOnly = true)
    private float shababparty$scaleExhaustion(final float exhaustion) {
        return HungerScaling.scale((Player) (Object) this, exhaustion);
    }
}
