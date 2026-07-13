package dev.alshabab.shababparty;

import java.util.Arrays;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import net.solocraft.procedures.FireReleaseAOEProcedure;
import net.solocraft.procedures.FireReleaseBeamProcedure;
import net.solocraft.procedures.FireReleaseSpreadProcedure;
import net.solocraft.procedures.FireTornadoShootProcedure;
import net.solocraft.procedures.FlameVortexShootProcedure;
import net.solocraft.procedures.HeavyFlameCastProcedure;

/**
 * The fourth ability (the V key) for the two jobs Solo Leveling never finished.
 *
 * Ability4OnKeyPressedProcedure dispatches on the player's JOB. Shadow Monarch (1) and Frost
 * Monarch (3) get a real ability; Grand Mage (2) and Monarch of White Flames (4) get a branch that
 * does nothing but print the literal string "ability wip" and return. No mana cost, no cooldown -
 * a pure stub.
 *
 * The odd part is that the abilities themselves exist. FireReleaseAOE, FireReleaseBeam,
 * FireReleaseSpread, FireTornadoShoot, FlameVortexShoot and HeavyFlameCast are all fully
 * implemented, with their own entities, models and renderers - and *nothing in the mod calls any of
 * them*. They were built and never wired to a key. So this is not writing new abilities, it is
 * plugging in ones that were already finished and left orphaned.
 *
 * Which one each job gets is config, so trying another is a config edit and a restart rather than a
 * jar rebuild and every player re-running the installer.
 */
public final class Ability4 {

    public static final String NONE = "none";
    public static final String ULTRA_INSTINCT = "ultra_instinct";

    /** Every value the config will accept. */
    public static final List<String> NAMES = Arrays.asList(
            NONE,
            ULTRA_INSTINCT,
            "fire_release_aoe",
            "fire_release_beam",
            "fire_release_spread",
            "fire_tornado",
            "flame_vortex",
            "heavy_flame");

    /**
     * @return true if the ability fired and the "ability wip" message should be swallowed, false to
     *         let Solo Leveling print whatever it was going to print
     */
    public static boolean cast(final String name, final ServerPlayer player) {
        final LevelAccessor world = player.m_9236_();
        final Vec3 at = player.m_20182_();

        switch (name) {
            case ULTRA_INSTINCT:
                return UltraInstinct.activate(player);
            case "fire_release_aoe":
                FireReleaseAOEProcedure.execute(world, player);
                return true;
            case "fire_release_beam":
                FireReleaseBeamProcedure.execute(world, at.m_7096_(), at.m_7098_(), at.m_7094_(), player);
                return true;
            case "fire_release_spread":
                FireReleaseSpreadProcedure.execute(world, at.m_7096_(), at.m_7098_(), at.m_7094_(), player);
                return true;
            case "fire_tornado":
                FireTornadoShootProcedure.execute(world, player);
                return true;
            case "flame_vortex":
                FlameVortexShootProcedure.execute(player);
                return true;
            case "heavy_flame":
                HeavyFlameCastProcedure.execute(player);
                return true;
            default:
                return false;
        }
    }

    private Ability4() {
    }
}
