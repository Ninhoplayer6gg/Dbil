package dev.dbil.targeting;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.config.ServerConfig;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Bounded server-side selection; an entity ID from a client never chooses an opponent. */
public final class TargetingService {
    public static final double LOCK_RANGE = 32.0;

    private TargetingService() { }

    public static boolean eligible(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isRemoved() || candidate.isSpectator()
                || candidate instanceof ArmorStand || candidate.isAlliedTo(player)) return false;
        if (candidate instanceof Player other) {
            return ServerConfig.pvp.get() && player.server.isPvpAllowed()
                    && player.canHarmPlayer(other) && CharacterCapability.get(other).created();
        }
        return true;
    }

    public static void toggle(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) return;
        PlayerState state = ServerRuntime.state(player);
        if (!CharacterCapability.get(player).created()) return;
        if (state.targetId != -1) {
            state.targetId = -1;
            return;
        }
        LivingEntity selected = select(player, LOCK_RANGE, 0.30);
        state.targetId = selected == null ? -1 : selected.getId();
    }

    /** Cone scoring favors the crosshair, with distance breaking similar-angle ties. */
    @Nullable
    public static LivingEntity select(ServerPlayer player, double range, double minimumDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = -Double.MAX_VALUE;
        AABB bounds = player.getBoundingBox().inflate(range);
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> eligible(player, entity))) {
            Vec3 offset = candidate.getBoundingBox().getCenter().subtract(eye);
            double distance = offset.length();
            if (distance > range || !player.hasLineOfSight(candidate)) continue;
            double alignment = distance < 0.01 ? 1.0 : offset.scale(1.0 / distance).dot(look);
            if (alignment < minimumDot) continue;
            double score = alignment * 8.0 - distance / range;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    public static void validate(ServerPlayer player, PlayerState state) {
        if (state.targetId == -1) return;
        if (!(player.level().getEntity(state.targetId) instanceof LivingEntity entity)
                || !eligible(player, entity) || player.distanceToSqr(entity) > LOCK_RANGE * LOCK_RANGE
                || !player.hasLineOfSight(entity)) state.targetId = -1;
    }

    @Nullable
    public static LivingEntity target(ServerPlayer player) {
        PlayerState state = ServerRuntime.state(player);
        validate(player, state);
        return player.level().getEntity(state.targetId) instanceof LivingEntity living ? living : null;
    }
}
