package dev.alshabab.shababparty.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops Solo Leveling summons from spamming their owner's chat when they die.
 *
 * TamableAnimal.die() tells the owner about every death:
 *
 *     if (!level.isClientSide && gameRules.getBoolean(SHOW_DEATH_MESSAGES) && getOwner() instanceof ServerPlayer)
 *         getOwner().sendSystemMessage(getCombatTracker().getDeathMessage());
 *
 * That is fine for a wolf, which dies once. Every Solo Leveling summon and shadow soldier -
 * FireFly, IgrisShadow, BeruShadow, the orcs, the flame vortexes - also extends TamableAnimal, and
 * they die by the dozen in a single fight. The result is a screen of "Fire Fly was slain by
 * Abdulrhman-S" that buries actual chat.
 *
 * Turning the gamerule off would have fixed it and cost too much: showDeathMessages also controls
 * *player* death messages, which the Discord bot parses out of the server log for its death feed.
 *
 * So the message is swallowed only for entities from the sololeveling namespace. A dead wolf still
 * tells you it died.
 *
 * Written against SRG names because shababparty compiles straight against the production jars with
 * no reobfuscation step: m_6667_ = die, m_213846_ = sendSystemMessage, m_6095_ = getType.
 */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
    @Redirect(
            method = "m_6667_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_213846_(Lnet/minecraft/network/chat/Component;)V"))
    private void shababparty$silenceSummonDeathMessages(final LivingEntity owner, final Component message) {
        final TamableAnimal self = (TamableAnimal) (Object) this;
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(self.m_6095_());
        // toString() rather than getNamespace(): ResourceLocation's getters are SRG-mapped here
        // (m_135815_ and m_135827_ are getPath and getNamespace, and guessing which is which is
        // exactly the kind of silent wrong-behaviour bug that is impossible to spot later).
        // toString() is java.lang.Object's, so it is never remapped, and "namespace:path" always
        // starts with the namespace.
        if (id != null && id.toString().startsWith("sololeveling:")) {
            return;
        }
        owner.m_213846_(message);
    }
}
