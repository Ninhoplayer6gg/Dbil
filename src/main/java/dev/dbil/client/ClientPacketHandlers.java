package dev.dbil.client;

import dev.dbil.client.fx.FxDispatcher;
import dev.dbil.network.Network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Client entry points for S2C packets. Only referenced from {@code DistExecutor} lambdas in {@link Network},
 * so a dedicated server never loads this class or anything it touches.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void data(CompoundTag tag) { ClientState.acceptData(tag); }

    public static void state(Network.StateSnapshot snapshot) {
        ClientState.VisualState previous = ClientState.visual(snapshot.id());
        ClientState.acceptState(snapshot);
        FxDispatcher.stateChanged(snapshot.id(), previous, ClientState.visual(snapshot.id()));
    }

    public static void appearance(Network.AppearanceSync sync) { ClientState.acceptAppearance(sync); }

    public static void fx(Network.FxEvent event) { FxDispatcher.handle(event); }

    public static void flightAck(long tick, int sequence, int inputTicks, Vec3 position, Vec3 velocity,
                                 double speed, double fastSpeed, boolean flying, boolean hardReset) {
        ClientFlightController.acceptAck(tick, sequence, inputTicks, position, velocity, speed, fastSpeed, flying, hardReset);
    }
}
