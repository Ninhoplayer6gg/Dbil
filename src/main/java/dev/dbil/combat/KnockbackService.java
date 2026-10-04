package dev.dbil.combat;

import dev.dbil.server.PlayerState;
import dev.dbil.flight.FlightService;
import dev.dbil.network.Network;
import dev.dbil.server.ServerRuntime;
import dev.dbil.flight.FlightService;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Explicit launch impulse. Vertical launches can be extended without changing damage calculation. */
public final class KnockbackService {
    private KnockbackService() { }

    public static void launch(LivingEntity entity, Vec3 direction, double horizontal, double vertical) {
        double resistance = Math.max(0, 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 planar = new Vec3(direction.x, 0, direction.z);
        if (planar.lengthSqr() < 1.0E-6) planar = new Vec3(0, 0, 1);
        planar = planar.normalize();
        Vec3 velocity = entity.getDeltaMovement().scale(0.35)
                .add(planar.scale(Math.min(horizontal, 2.5) * resistance))
                .add(0, Math.min(vertical, 1.5) * resistance, 0);
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            PlayerState state = ServerRuntime.state(player);
            if (state.flying) {
                // The flight integrator owns position/velocity; preserve launches in that authority too.
                FlightService.externalMotion(player, state, velocity, false);
            }
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            if (state.flying) Network.sendFlightAck(player, true);
        }
    }
}
