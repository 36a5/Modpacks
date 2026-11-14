package dev.alshabab.shababparty.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.solocraft.network.SololevelingModVariables;
import net.solocraft.procedures.JobChangeTimerProcedure;

/**
 * Skips the Job Change Quest's ending cutscene and hands out the new job straight away.
 *
 * When the quest is won, JobAdvTimerProcedure sets JobChange_timer = 1. JobChangeTimerProcedure
 * then steps it 1..9, one step every 3 seconds, printing a line of story in chat at each step,
 * and at 9 sets JOB = Shadow Monarch. That is about 25 seconds of chat before anything happens,
 * and the job it lands on is overwritten by our own roll anyway.
 *
 * This cancels the procedure whenever the timer is running: the timer goes straight back to 0 and
 * the slb-jobs datapack's quest_done function rolls the new job (random job, never the one held,
 * White Flames on the allowlisted players' 11th change). The datapack used to detect the end of
 * the cutscene itself by watching JobChange_timer fall back to 0; that detection is gone, this is
 * now the only trigger, so a job is never rolled twice for one run.
 *
 * HEAD-cancel is fine here, unlike Ability4WipMixin: the timer branches are the whole method, there
 * is nothing before them that other code relies on. remap = false: Solo Leveling's classes are not
 * obfuscated, and the private six-argument execute is named by its full descriptor because a public
 * five-argument overload of the same name exists.
 */
@Mixin(value = JobChangeTimerProcedure.class, remap = false)
public abstract class JobChangeTalkMixin {

    @Inject(
            method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void shababparty$skipCutscene(final Event event, final LevelAccessor world,
                                                 final double x, final double y, final double z,
                                                 final Entity entity, final CallbackInfo ci) {
        if (entity == null) {
            return;
        }
        final SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) entity)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
        if (vars == null || vars.JobChange_timer <= 0.0D) {
            return;
        }
        // The client runs this too, only for the panel sounds; it just stays quiet.
        ci.cancel();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        vars.JobChange_timer = 0.0D;
        vars.syncPlayerVariables(player);
        // m_129892_ = getCommands, m_230957_ = performPrefixedCommand, m_20203_ =
        // createCommandSourceStack, m_81324_ = withSuppressedOutput, m_81325_ = withPermission.
        // Level 4 because the roll calls /slr, which needs 3.
        player.f_8924_.m_129892_().m_230957_( // server
                player.m_20203_().m_81324_().m_81325_(4), "function slb_jobs:quest_done");
    }
}
