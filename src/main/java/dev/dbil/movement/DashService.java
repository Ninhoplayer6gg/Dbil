package dev.dbil.movement;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.network.Network;
import dev.dbil.server.PlayerState;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class DashService {
    public static void dash(ServerPlayer p, CharacterData data, PlayerState s) {
        long now = p.serverLevel().getGameTime();
        double ki = ServerConfig.dashKiCost.get() * dev.dbil.race.Races.get(data.raceId()).kiCostMultiplier(), stamina = ServerConfig.dashStaminaCost.get();
        if (now < s.nextDashTick || s.charging || s.techniqueCharging || p.isPassenger()
                || !data.canSpendKi(ki) || !data.canSpendStamina(stamina)) return;
        data.spendKi(ki); data.spendStamina(stamina); s.nextDashTick = now + 20;
        Vec3 direction = p.getLookAngle();
        if (!s.flying) direction = new Vec3(direction.x, 0, direction.z);
        if (direction.lengthSqr() < 0.01) direction = new Vec3(0, 0, 1);
        // A short swept move collides with blocks; no teleport through a wall or unloaded chunk.
        p.move(MoverType.SELF, direction.normalize().scale(2.3));
        if (s.flying) FlightService.externalMotion(p, s, s.flightVelocity, true);
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (s.flying) Network.sendFlightAck(p, true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
    }
    private DashService() {}
}
