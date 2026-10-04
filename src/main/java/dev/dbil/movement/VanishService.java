package dev.dbil.movement;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import dev.dbil.targeting.TargetingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Vanish: a short-range reposition next to the locked opponent. The client only asks; the server picks one of a
 * few fixed spots around the target and accepts it only if it is free of blocks, loaded, inside the border and in
 * sight of the target. There is no arbitrary destination.
 */
public final class VanishService {
    private static final double OFFSET = 1.8;

    private VanishService() {}

    public static boolean vanish(ServerPlayer player, CharacterData data, PlayerState state) {
        long now = player.serverLevel().getGameTime();
        if (now < state.nextVanishTick || state.charging || state.techniqueCharging || state.activeBeamId >= 0
                || state.guarding || state.transformationChargeTicks > 0 || now < state.guardBreakUntil
                || player.isPassenger() || player.isSleeping()) return false;
        LivingEntity target = TargetingService.target(player);
        double range = ServerConfig.vanishRange.get();
        if (target == null || player.distanceToSqr(target) > range * range) return false;
        double stamina = ServerConfig.vanishStaminaCost.get();
        if (!data.canSpendStamina(stamina) || !data.canSpendKi(4)) return false;
        Vec3 destination = findSpot(player, target, state.flying);
        if (destination == null) return false;
        data.spendStamina(stamina);
        data.spendKi(4);
        state.nextVanishTick = now + ServerConfig.vanishCooldownTicks.get();
        state.markCombat(now);
        Vec3 from = player.position();
        player.setPos(destination.x, destination.y, destination.z);
        FxService.entityAt(player, FxType.VANISH, 0, target.getId(), 1, from, 0xFFFFFF);
        player.level().playSound(null, from.x, from.y, from.z, ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        DashService.finishReposition(player, state, target);
        return true;
    }

    private static Vec3 findSpot(ServerPlayer player, LivingEntity target, boolean flying) {
        Vec3 facing = target.getLookAngle();
        Vec3 planar = new Vec3(facing.x, 0, facing.z);
        if (planar.lengthSqr() < 1.0e-4) planar = new Vec3(0, 0, 1);
        planar = planar.normalize();
        Vec3 side = new Vec3(-planar.z, 0, planar.x);
        Vec3 base = target.position();
        Vec3[] spots = flying || !target.onGround()
                ? new Vec3[] {base.subtract(planar.scale(OFFSET)), base.add(side.scale(OFFSET)), base.subtract(side.scale(OFFSET)),
                base.add(0, target.getBbHeight() + 0.6, 0).subtract(planar.scale(0.8))}
                : new Vec3[] {base.subtract(planar.scale(OFFSET)), base.add(side.scale(OFFSET)), base.subtract(side.scale(OFFSET))};
        for (Vec3 spot : spots) {
            BlockPos block = BlockPos.containing(spot);
            if (!player.level().isLoaded(block) || !player.level().getWorldBorder().isWithinBounds(block)) continue;
            AABB box = player.getBoundingBox().move(spot.subtract(player.position()));
            if (!player.level().noCollision(player, box) || player.level().containsAnyLiquid(box)) continue;
            Vec3 eye = spot.add(0, player.getEyeHeight(), 0);
            if (player.level().clip(new net.minecraft.world.level.ClipContext(eye, target.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            return spot;
        }
        return null;
    }
}
