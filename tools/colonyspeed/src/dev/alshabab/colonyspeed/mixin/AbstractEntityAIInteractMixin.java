package dev.alshabab.colonyspeed.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import dev.alshabab.colonyspeed.ColonySpeed;
import dev.alshabab.colonyspeed.SpeedScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block breaking.
 *
 * AbstractEntityAIInteract is the base of every worker that digs, so scaling its answer here covers
 * the Builder, Miner, Quarrier, Lumberjack and Farmer in one place. getBlockMiningTime has several
 * return points (no tool held, block is instant-break, the full formula) and we scale all of them.
 */
@Mixin(AbstractEntityAIInteract.class)
public abstract class AbstractEntityAIInteractMixin {
    @Inject(method = "getBlockMiningTime", at = @At("RETURN"), cancellable = true)
    private void colonyspeed$scaleMiningTime(final CallbackInfoReturnable<Integer> cir) {
        if (!ColonySpeed.Config.SCALE_BREAKING.get()) {
            return;
        }
        cir.setReturnValue(SpeedScaling.scale(this, cir.getReturnValue()));
    }
}
