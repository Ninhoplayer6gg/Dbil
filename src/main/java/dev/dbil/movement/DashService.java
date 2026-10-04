package dev.dbil.movement;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.network.Network;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Short swept bursts. Direction is an intent; distance, collision and costs are decided here. */
public final class DashService {
    public enum Direction { FORWARD, LEFT, RIGHT, BACK }

    public static void dash(ServerPlayer p, CharacterData data, PlayerState s) {
        dash(p, data, s, Direction.FORWARD);
    }

    public static void dash(ServerPlayer p, CharacterData data, PlayerState s, Direction direction) {
        long now = p.serverLevel().getGameTime();
        // A dash during the chase window becomes a pursuit of the launched opponent.
        if (direction == Direction.FORWARD && ChaseService.tryChase(p, data, s)) return;
        double ki = ServerConfig.dashKiCost.get() * dev.dbil.race.Races.get(data.raceId()).kiCostMultiplier(), stamina = ServerConfig.dashStaminaCost.get();
        if (now < s.nextDashTick || s.charging || s.techniqueCharging || s.activeBeamId >= 0 || p.isPassenger()
                || !data.canSpendKi(ki) || !data.canSpendStamina(stamina)) return;
        data.spendKi(ki); data.spendStamina(stamina); s.nextDashTick = now + 20;
        Vec3 look = p.getLookAngle();
        Vec3 planar = new Vec3(look.x, 0, look.z);
        if (planar.lengthSqr() < 0.01) planar = new Vec3(0, 0, 1);
        planar = planar.normalize();
        Vec3 vector = switch (direction) {
            case FORWARD -> s.flying ? look : planar;
            case BACK -> planar.scale(-1);
            case LEFT -> new Vec3(planar.z, 0, -planar.x);
            case RIGHT -> new Vec3(-planar.z, 0, planar.x);
        };
        if (vector.lengthSqr() < 0.01) vector = planar;
        // A short swept move collides with blocks; no teleport through a wall or unloaded chunk.
        p.move(MoverType.SELF, vector.normalize().scale(direction == Direction.FORWARD ? 2.3 : 2.0));
        if (s.flying) FlightService.externalMotion(p, s, s.flightVelocity, true);
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (s.flying) Network.sendFlightAck(p, true);
        int variant = switch (direction) {
            case FORWARD -> FxType.DASH_FORWARD; case LEFT -> FxType.DASH_LEFT; case RIGHT -> FxType.DASH_RIGHT; case BACK -> FxType.DASH_BACK; };
        FxService.entity(p, FxType.DASH, variant, -1, 1, 0xDDF4FF);
        p.level().playSound(null, p.blockPosition(), ModSounds.DASH.get(), SoundSource.PLAYERS, 0.6f, 1.0f + p.getRandom().nextFloat() * 0.2f);
    }

    static void finishReposition(ServerPlayer p, PlayerState s, LivingEntity faceTarget) {
        if (faceTarget != null) {
            Vec3 toward = faceTarget.getEyePosition().subtract(p.getEyePosition());
            double horizontal = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
            p.setYRot((float) (Math.atan2(-toward.x, toward.z) * 180 / Math.PI));
            p.setXRot((float) (-Math.atan2(toward.y, horizontal) * 180 / Math.PI));
            p.setYHeadRot(p.getYRot());
        }
        if (s.flying) FlightService.externalMotion(p, s, Vec3.ZERO, true);
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (s.flying) Network.sendFlightAck(p, true);
    }

    private DashService() {}
}
