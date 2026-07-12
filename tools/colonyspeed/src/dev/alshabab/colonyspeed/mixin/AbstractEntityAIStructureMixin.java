package dev.alshabab.colonyspeed.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import dev.alshabab.colonyspeed.ColonySpeed;
import dev.alshabab.colonyspeed.SpeedScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Block placing.
 *
 * structureStep ends a successful placement with setDelay(150 / (placeSpeedLevel / 2 + 10) * research),
 * and that is the only setDelay call in the class, so modifying its argument is enough to control how
 * fast a blueprint goes up.
 */
@Mixin(AbstractEntityAIStructure.class)
public abstract class AbstractEntityAIStructureMixin {
    @ModifyArg(
            method = "structureStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/entity/ai/workers/AbstractEntityAIStructure;setDelay(I)V"),
            index = 0)
    private int colonyspeed$scalePlaceDelay(final int delay) {
        if (!ColonySpeed.Config.SCALE_PLACING.get()) {
            return delay;
        }
        return SpeedScaling.scale(this, delay);
    }
}
