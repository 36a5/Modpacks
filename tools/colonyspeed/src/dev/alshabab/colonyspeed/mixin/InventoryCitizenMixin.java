package dev.alshabab.colonyspeed.mixin;

import com.minecolonies.api.colony.ICitizenData;
import dev.alshabab.colonyspeed.InventoryScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.minecolonies.api.inventory.InventoryCitizen;

/**
 * Inventory size.
 *
 * InventoryCitizen recomputes its own size in exactly one place — write(CompoundTag) ends with
 * resizeInventory(mainInventory.size(), 27 + researchEffect(CITIZEN_INV_SLOTS)) — so modifying that
 * second argument is the whole job. It also means the new size lands on the next citizen save rather
 * than the instant a hut is upgraded, which is the same latency MineColonies' own research bonus has.
 *
 * resizeInventory is private, but Mixin matches an INVOKE by owner + name + descriptor, not by access.
 */
@Mixin(InventoryCitizen.class)
public abstract class InventoryCitizenMixin {
    @Shadow
    private ICitizenData citizen;

    @ModifyArg(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/inventory/InventoryCitizen;resizeInventory(II)V"),
            index = 1)
    private int colonyspeed$scaleInventorySize(final int newSize) {
        return InventoryScaling.scale(this.citizen, newSize);
    }
}
