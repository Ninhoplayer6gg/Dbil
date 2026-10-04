package dev.dbil.server;

/** Volatile per-session state; never saved into player NBT. All mutation occurs on the server thread. */
public final class PlayerState {
    public boolean charging, flying, techniqueCharging, ascend, descend;
    public long lastInputTick, nextDashTick, nextMeleeTick, comboExpires, nextTechniqueTick, lastSyncTick;
    public int combo, techniqueChargeTicks, targetId = -1;
    public float forward, strafe;
    public net.minecraft.nbt.CompoundTag lastDataSnapshot;
    public long chargeHeartbeatTick;
    public int flightInputSequence, flightInputTicks;
    public long lastFlightAckTick, flightCorrections;
    public double flightMaximumSpeed = 0.32;
    public boolean flightAckRequired, flightHardReset;
    public net.minecraft.world.phys.Vec3 flightPosition;
    public net.minecraft.world.phys.Vec3 flightVelocity = net.minecraft.world.phys.Vec3.ZERO;
    public net.minecraft.resources.ResourceLocation chargingTechnique;
    public int barrageRemaining, barrageDelay;
    public boolean guarding, guardBroken;
    public long guardBreakUntil, guardHeartbeatTick;
    public net.minecraft.resources.ResourceLocation pendingTransformation;
    public int transformationChargeTicks;
    public long nextTransformationTick, lastTransformationMasteryTick, lastTransformationCombatTick;
    public float transformationStartHealth;
    public net.minecraft.world.phys.Vec3 transformationStartPosition;
    public java.util.UUID sparringEnemy;
    public long nextSparringTick;
    long packetWindowTick;
    int actionPackets, movementPackets;
    public boolean admitAction(long tick) {
        resetWindow(tick); return ++actionPackets <= 30;
    }
    public boolean admitMovement(long tick) {
        resetWindow(tick); return ++movementPackets <= 24;
    }
    private void resetWindow(long tick) {
        if (tick - packetWindowTick >= 20 || tick < packetWindowTick) {
            packetWindowTick = tick; actionPackets = 0; movementPackets = 0;
        }
    }
}
