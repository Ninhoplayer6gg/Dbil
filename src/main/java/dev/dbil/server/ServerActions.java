package dev.dbil.server;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.combat.CombatService;
import dev.dbil.combat.GuardService;
import dev.dbil.transformation.TransformationService;
import dev.dbil.training.TrainingSessionService;
import dev.dbil.flight.FlightService;
import dev.dbil.movement.DashService;
import dev.dbil.network.Network;
import dev.dbil.targeting.TargetingService;
import dev.dbil.technique.TechniqueService;
import net.minecraft.server.level.ServerPlayer;

public final class ServerActions {
    public static void handle(ServerPlayer player, Action action) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return;
        var data = CharacterCapability.get(player);
        var state = ServerRuntime.state(player);
        FlightService.restorePosition(player, state);
        long now = player.serverLevel().getGameTime();
        if (!state.admitAction(now) || !data.created() || !player.isAlive() || player.isSpectator()) return;
        switch (action) {
            case CHARGE_START -> {
                if (!state.flying && !state.techniqueCharging && !state.guarding && state.transformationChargeTicks <= 0 && !player.isPassenger()) {
                    state.charging = true; state.chargeHeartbeatTick = now;
                }
            }
            case CHARGE_STOP -> state.charging = false;
            case FLIGHT_TOGGLE -> { if (state.transformationChargeTicks <= 0) FlightService.toggle(player, data, state); }
            case DASH -> { if (!state.guarding && state.transformationChargeTicks <= 0) DashService.dash(player, data, state); }
            case LIGHT -> CombatService.attack(player, false);
            case HEAVY -> CombatService.attack(player, true);
            case TECHNIQUE -> { state.charging = false; TechniqueService.start(player); }
            case LOCK_ON -> TargetingService.toggle(player);
            case GUARD_START -> GuardService.start(player, data, state);
            case GUARD_STOP -> GuardService.stop(player);
            case TRANSFORM_REVERT -> TransformationService.revert(player);
            case SPAR_START -> TrainingSessionService.start(player);
            case STOP_ALL -> {
                GuardService.stop(player);
                state.charging = false;
                TechniqueService.cancel(player);
                state.forward = 0; state.strafe = 0; state.ascend = false; state.descend = false;
            }
        }
        if (state.flightAckRequired) Network.sendFlightAck(player, state.flightHardReset);
        Network.syncState(player);
    }
    private ServerActions() {}
}
