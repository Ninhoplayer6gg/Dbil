package dev.dbil.server;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterService;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.ki.KiService;
import dev.dbil.combat.GuardService;
import dev.dbil.transformation.TransformationService;
import dev.dbil.training.TrainingChallenges;
import dev.dbil.network.Network;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.stamina.StaminaService;
import dev.dbil.targeting.TargetingService;
import dev.dbil.technique.TechniqueService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID)
public final class ServerEvents {
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Entity> event) { CharacterCapability.attach(event); }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        try {
            CharacterCapability.get(e.getEntity()).copyFrom(CharacterCapability.get(e.getOriginal()));
        } finally { e.getOriginal().invalidateCaps(); }
        ServerRuntime.clear(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            resetSession(p); CharacterService.applyAttributes(p, CharacterCapability.get(p)); Network.sync(p);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            resetSession(p); CharacterService.respawn(p); Network.sync(p);
        }
    }
    @SubscribeEvent public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { resetSession(p); Network.sync(p); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { resetSession(p); ServerRuntime.clear(p.getUUID()); }
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) resetSession(p);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void teleport(net.minecraftforge.event.entity.EntityTeleportEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        var state = ServerRuntime.state(p);
        if (state.flying) FlightService.stop(p, state);
        state.charging = false;
        KiService.clearSlow(p);
        GuardService.stop(p);
        TechniqueService.cancel(p);
        TransformationService.cancelActivation(p);
        if (state.flightAckRequired) Network.sendFlightAck(p, true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ServerRuntime.state(p).flying) e.setCanceled(true);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer observer && e.getTarget() instanceof ServerPlayer subject)
            Network.syncStateTo(subject, observer);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ServerRuntime.clearAll(); }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (!(e.player instanceof ServerPlayer p) || !p.isAlive() || p.isRemoved()) return;
        var d = CharacterCapability.get(p); var s = ServerRuntime.state(p);
        long now = p.serverLevel().getGameTime();
        if (!d.created()) return;
        if (e.phase == TickEvent.Phase.START) {
            FlightService.beforeTick(p, d, s);
            return;
        }
        TransformationService.tick(p, d, s);
        GuardService.tick(p, d, s);
        if (s.charging && now - s.chargeHeartbeatTick > 60) { s.charging = false; Network.syncState(p); }
        double kiBefore = d.ki();
        KiService.tick(p, d, s);
        if (s.charging && d.ki() > kiBefore) d.recordTraining("ki_charged", d.ki() - kiBefore);
        StaminaService.tick(p, d, s);
        FlightService.tick(p, d, s); TechniqueService.tick(p, d, s);
        if (s.flying) d.recordTraining("flight_ticks", 1);
        if (s.flightAckRequired) Network.sendFlightAck(p, s.flightHardReset);
        TrainingChallenges.tick(p, d);
        if (now - s.lastSyncTick >= ServerConfig.syncInterval.get()) {
            s.lastSyncTick = now;
            TargetingService.validate(p, s);
            PowerLevelCalculator.update(d, p.getHealth() / p.getMaxHealth(), s.charging, s.flying);
            Network.syncPeriodic(p);
        }
    }
    public static void resetSession(ServerPlayer p) {
        var s = ServerRuntime.state(p);
        TransformationService.resetSession(p);
        GuardService.stop(p);
        if (s.flying) FlightService.stop(p, s);
        p.setNoGravity(false); s.charging = false; KiService.clearSlow(p);
        TechniqueService.cancel(p);
        if (s.flightAckRequired && !p.isRemoved()) Network.sendFlightAck(p, true);
        ServerRuntime.clear(p.getUUID());
    }
    private ServerEvents() {}
}
