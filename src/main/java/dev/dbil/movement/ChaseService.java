package dev.dbil.movement;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import dev.dbil.targeting.TargetingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Combo -> launcher/smash/finisher -> chase. The server remembers which opponent was launched and for how long;
 * a dash inside that window sweeps the attacker next to the opponent with normal block collision.
 */
public final class ChaseService {
    private static final double STOP_DISTANCE = 1.7;

    private ChaseService() {}

    public static boolean available(ServerPlayer player, PlayerState state) {
        return state.chaseTargetId >= 0 && player.serverLevel().getGameTime() < state.chaseWindowUntil;
    }

    static boolean tryChase(ServerPlayer player, CharacterData data, PlayerState state) {
        long now = player.serverLevel().getGameTime();
        if (!available(player, state) || state.charging || state.techniqueCharging || state.activeBeamId >= 0
                || player.isPassenger()) return false;
        if (!(player.level().getEntity(state.chaseTargetId) instanceof LivingEntity target) || !target.isAlive()
                || !TargetingService.eligible(player, target)) {
            state.chaseTargetId = -1;
            return false;
        }
        double range = ServerConfig.chaseRange.get();
        Vec3 from = player.position();
        // Aim slightly ahead of a target that is still flying from the hit.
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(3));
        Vec3 offset = predicted.subtract(from);
        double distance = offset.length();
        if (distance > range || !player.hasLineOfSight(target)) return false;
        double stamina = ServerConfig.chaseStaminaCost.get();
        if (!data.canSpendStamina(stamina)) return false;
        data.spendStamina(stamina);
        state.chaseTargetId = -1;
        state.chaseWindowUntil = 0;
        state.nextDashTick = now + 10;
        state.comboExpires = now + 30;
        state.markCombat(now);
        if (distance > STOP_DISTANCE) {
            Vec3 travel = offset.normalize().scale(distance - STOP_DISTANCE);
            // Airborne chases need flight-style vertical movement; a grounded attacker may still jump up to a launch.
            player.move(MoverType.SELF, travel);
        }
        FxService.entityAt(player, FxType.CHASE, 0, target.getId(), (float) distance, from, 0xE8F6FF);
        player.level().playSound(null, player.blockPosition(), ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
        DashService.finishReposition(player, state, target);
        return true;
    }
}
