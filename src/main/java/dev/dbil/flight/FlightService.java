package dev.dbil.flight;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.network.Network;
import dev.dbil.race.Races;
import dev.dbil.server.PlayerState;
import dev.dbil.stats.Stat;
import dev.dbil.transformation.TransformationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Authoritative input integration. Ordinary flight never opens a vanilla teleport handshake. */
public final class FlightService {
    private FlightService() {}

    public static void toggle(ServerPlayer player, CharacterData data, PlayerState state) {
        if (state.flying) { stop(player, state); return; }
        if (player.isPassenger() || player.isSpectator() || player.isSleeping() || player.isInWaterOrBubble()
                || !player.isAlive() || !data.created() || !data.canSpendKi(1)) return;
        state.flying = true;
        state.charging = false;
        state.forward = state.strafe = 0;
        state.ascend = state.descend = false;
        state.flightPosition = player.position();
        state.flightVelocity = new Vec3(0, 0.12, 0);
        state.flightInputTicks = 0;
        state.flightMaximumSpeed = maximumSpeed(data);
        state.flightAckRequired = state.flightHardReset = true;
        player.setNoGravity(true);
        player.fallDistance = 0;
        clearFloatingCheck(player);
        Network.syncState(player);
    }

    /** Called at PlayerTick START, before vanilla travel can double-apply our previous velocity. */
    public static void beforeTick(ServerPlayer player, CharacterData data, PlayerState state) {
        if (!state.flying || !player.isAlive()) return;
        if (state.flightPosition == null) state.flightPosition = player.position();
        double discrepancy = player.position().distanceToSqr(state.flightPosition);
        restorePosition(player, state);
        player.connection.resetPosition();
        if (discrepancy > FlightMotion.HARD_CORRECTION_DISTANCE * FlightMotion.HARD_CORRECTION_DISTANCE) {
            hardCorrect(player, state);
        }
        player.setNoGravity(true);
        player.setOnGround(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        clearFloatingCheck(player);
    }

    public static boolean acceptInput(ServerPlayer player, PlayerState state, int sequence,
                                      float forward, float strafe, boolean ascend, boolean descend,
                                      float yaw, float pitch) {
        if (sequence <= 0 || sequence <= state.flightInputSequence
                || !Float.isFinite(forward) || !Float.isFinite(strafe)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return false;
        state.flightInputSequence = sequence;
        state.flightInputTicks = 0;
        state.forward = Math.max(-1, Math.min(1, forward));
        state.strafe = Math.max(-1, Math.min(1, strafe));
        state.ascend = ascend;
        state.descend = descend;
        state.lastInputTick = player.serverLevel().getGameTime();
        player.setYRot(Mth.wrapDegrees(yaw));
        player.setXRot(Mth.clamp(pitch, -90, 90));
        return true;
    }

    /** Called at PlayerTick END. Integrates from stored server position, never client position packets. */
    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        if (!state.flying) return;
        var race = Races.get(data.raceId());
        double efficiency = race == null ? 1 : race.kiCostMultiplier();
        double cost = ServerConfig.flightKiCost.get() * efficiency / (1 + data.stat(Stat.KI_CONTROL) * 0.005);
        if (player.isPassenger() || player.isSpectator() || player.isSleeping() || player.isInWaterOrBubble() || !player.isAlive()
                || !data.created() || !data.spendKi(cost)) { stop(player, state); return; }
        long now = player.serverLevel().getGameTime();
        if (now - state.lastInputTick > FlightMotion.INPUT_TIMEOUT_TICKS) {
            state.forward = state.strafe = 0;
            state.ascend = state.descend = false;
        }
        restorePosition(player, state);
        state.flightMaximumSpeed = maximumSpeed(data);
        FlightMotion.Input input = new FlightMotion.Input(state.forward, state.strafe, state.ascend,
                state.descend, player.getYRot());
        Vec3 requested = FlightMotion.nextVelocity(state.flightVelocity, input, state.flightMaximumSpeed);
        if (!player.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(player.position().add(requested)))) {
            requested = Vec3.ZERO;
        }
        Vec3 start = player.position();
        player.setNoGravity(true);
        player.setOnGround(false);
        player.move(MoverType.SELF, requested);
        Vec3 actual = player.position().subtract(start);
        state.flightPosition = player.position();
        // Connection.tick restores firstGood after player.doTick; advance that baseline without a teleport.
        player.connection.resetPosition();
        state.flightVelocity = FlightMotion.afterCollision(requested, actual);
        state.flightInputTicks = Math.min(1200, state.flightInputTicks + 1);
        player.setDeltaMovement(state.flightVelocity);
        player.fallDistance = 0;
        clearFloatingCheck(player);
        if (now - state.lastFlightAckTick >= FlightMotion.ACK_INTERVAL_TICKS) {
            state.flightAckRequired = true;
        }
    }

    public static double maximumSpeed(CharacterData data) {
        return FlightMotion.maximumSpeed(data.stat(Stat.SPEED), TransformationService.multiplier(data, Stat.SPEED),
                ServerConfig.maxFlightSpeed.get());
    }

    public static void restorePosition(ServerPlayer player, PlayerState state) {
        if (state.flying && state.flightPosition != null) player.setPos(state.flightPosition);
    }

    /** Dash/launch integration retains server impulses and requests an immediate owner ACK. */
    public static void externalMotion(ServerPlayer player, PlayerState state, Vec3 velocity, boolean hardReset) {
        if (!state.flying) return;
        state.flightPosition = player.position();
        player.connection.resetPosition();
        state.flightVelocity = FlightMotion.finite(velocity) ? velocity : Vec3.ZERO;
        state.flightAckRequired = true;
        state.flightHardReset |= hardReset;
        player.setDeltaMovement(state.flightVelocity);
    }

    private static void hardCorrect(ServerPlayer player, PlayerState state) {
        state.flightCorrections++;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        state.flightAckRequired = state.flightHardReset = true;
    }

    public static void stop(ServerPlayer player, PlayerState state) {
        if (state.flying) restorePosition(player, state);
        state.flying = false;
        player.connection.resetPosition();
        state.forward = state.strafe = 0;
        state.ascend = state.descend = false;
        state.flightPosition = player.position();
        state.flightVelocity = Vec3.ZERO;
        state.flightInputTicks = 0;
        state.flightAckRequired = state.flightHardReset = true;
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        clearFloatingCheck(player);
        Network.syncState(player);
    }

    private static void clearFloatingCheck(ServerPlayer player) {
        player.connection.clientIsFloating = false;
        player.connection.aboveGroundTickCount = 0;
    }
}
