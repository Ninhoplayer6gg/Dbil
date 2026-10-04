package dev.dbil.combat;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.network.Network;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.transformation.TransformationService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned frontal guarding; environmental damage cannot be blocked. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID)
public final class GuardService {
    private static final double START_STAMINA = 3.0;
    private static final double HOLD_DRAIN = 0.08;
    private static final double MIN_HIT_COST = 3.0;
    private static final double HEAVY_EXTRA_COST = 12.0;
    private static final double FRONTAL_DOT = 0.15;
    private static final int HEARTBEAT_TIMEOUT = 40;
    // These contexts exist only around synchronous hurt calls on the server thread.
    private static final ThreadLocal<HeavyStrike> HEAVY_STRIKE = new ThreadLocal<>();
    private static final ThreadLocal<BlockedHit> BLOCKED_TARGET = new ThreadLocal<>();

    private GuardService() { }

    /** Repeated starts refresh the hold heartbeat without sending repeated state snapshots. */
    public static void start(ServerPlayer player, CharacterData data, PlayerState state) {
        long now = player.serverLevel().getGameTime();
        if (!canAct(player, data) || state.charging || state.techniqueCharging || state.barrageRemaining > 0
                || state.transformationChargeTicks > 0 || now < state.guardBreakUntil
                || !data.canSpendStamina(START_STAMINA)) return;
        state.guardHeartbeatTick = now;
        if (state.guarding) return;
        state.guarding = true;
        state.guardBroken = false;
        player.setSprinting(false);
        Network.syncState(player);
    }

    public static void stop(ServerPlayer player, CharacterData data, PlayerState state) {
        stop(player, state);
    }

    /** Logout/reset can stop a hold even after Forge has invalidated character capabilities. */
    public static void stop(ServerPlayer player) {
        stop(player, ServerRuntime.state(player));
    }

    private static void stop(ServerPlayer player, PlayerState state) {
        if (!state.guarding) return;
        state.guarding = false;
        Network.syncState(player);
    }

    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        long now = player.serverLevel().getGameTime();
        if (state.guardBroken && now >= state.guardBreakUntil) {
            state.guardBroken = false;
            Network.syncState(player);
        }
        if (!state.guarding) return;
        if (!canAct(player, data) || state.charging || state.techniqueCharging || state.barrageRemaining > 0
                || state.transformationChargeTicks > 0 || now < state.guardHeartbeatTick
                || now - state.guardHeartbeatTick > HEARTBEAT_TIMEOUT) {
            stop(player, data, state);
            return;
        }
        player.setSprinting(false);
        if (!data.spendStamina(HOLD_DRAIN)) breakGuard(player, data, state, now);
    }

    /** Call before a heavy strike's target.hurt, then clearHeavy in a finally block. */
    public static void markHeavy(ServerPlayer attacker, LivingEntity target) {
        HEAVY_STRIKE.set(new HeavyStrike(attacker, target));
        BLOCKED_TARGET.remove();
    }

    public static void clearHeavy() {
        HEAVY_STRIKE.remove();
    }

    /** Consume immediately after target.hurt to suppress DBIL knockback only on a real block. */
    public static boolean consumeBlocked(LivingEntity target) {
        BlockedHit blocked = BLOCKED_TARGET.get();
        BLOCKED_TARGET.remove();
        return matches(blocked, target);
    }

    /** Defense is applied once here, for vanilla, NPC and DBIL tangible attacks alike. */
    @SubscribeEvent
    public static void modifyHurt(LivingHurtEvent event) {
        BLOCKED_TARGET.remove();
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isRemoved()
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0) return;
        DamageSource source = event.getSource();
        Vec3 sourcePosition = tangibleSourcePosition(source);
        if (sourcePosition == null || !Double.isFinite(sourcePosition.x)
                || !Double.isFinite(sourcePosition.y) || !Double.isFinite(sourcePosition.z)) return;
        CharacterData data = player.getCapability(CharacterCapability.CAPABILITY).orElse(null);
        if (data == null || !data.created()) return;

        double defense = data.stat(Stat.DEFENSE) * TransformationService.multiplier(data, Stat.DEFENSE);
        if (!Double.isFinite(defense)) defense = 0;
        defense = Math.max(0, defense);
        double reduction = Math.min(0.65, defense / (defense + 80.0));
        float damage = (float) (event.getAmount() * (1 - reduction));
        event.setAmount(damage);

        PlayerState state = ServerRuntime.state(player);
        if (!state.guarding || !canAct(player, data) || state.charging || state.techniqueCharging
                || state.barrageRemaining > 0
                || state.transformationChargeTicks > 0) return;
        Vec3 incoming = sourcePosition.subtract(player.getEyePosition());
        if (incoming.lengthSqr() < 1.0e-8 || incoming.normalize().dot(player.getLookAngle()) < FRONTAL_DOT) return;
        long now = player.serverLevel().getGameTime();
        if (now < state.guardHeartbeatTick || now - state.guardHeartbeatTick > HEARTBEAT_TIMEOUT) {
            stop(player, data, state);
            return;
        }
        HeavyStrike heavy = HEAVY_STRIKE.get();
        boolean heavyHit = heavy != null && heavy.target() == player && source.getEntity() == heavy.attacker();
        double cost = Math.max(MIN_HIT_COST, damage * ServerConfig.guardStaminaPerDamage.get())
                + (heavyHit ? HEAVY_EXTRA_COST : 0);
        // spendStamina is one validated transaction; an exhausted guard receives the full defended hit.
        if (!data.spendStamina(cost)) {
            breakGuard(player, data, state, now);
            return;
        }
        event.setAmount((float) (damage * (1 - ServerConfig.guardDamageReduction.get())));
        BLOCKED_TARGET.set(new BlockedHit(player.getUUID(), now));
        data.recordTraining("guarded_hits", 1);
    }

    /** Vanilla adds its impulse after LivingHurtEvent; a real block leaves DBIL's smaller impulse. */
    @SubscribeEvent
    public static void modifyKnockback(LivingKnockBackEvent event) {
        if (matches(BLOCKED_TARGET.get(), event.getEntity())) event.setCanceled(true);
    }

    private static boolean matches(BlockedHit blocked, LivingEntity target) {
        return blocked != null && blocked.target().equals(target.getUUID())
                && blocked.gameTick() == target.level().getGameTime();
    }

    private static boolean canAct(ServerPlayer player, CharacterData data) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isSleeping() && data.created();
    }

    private static void breakGuard(ServerPlayer player, CharacterData data, PlayerState state, long now) {
        data.setStamina(0);
        state.guarding = false;
        state.guardBroken = true;
        state.guardBreakUntil = now + ServerConfig.guardBreakTicks.get();
        Network.syncState(player);
    }

    private static Vec3 tangibleSourcePosition(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.STARVE) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return null;
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity living) return living.getEyePosition();
        if (direct != null) return direct.position();
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) return living.getEyePosition();
        if (attacker != null) return attacker.position();
        return source.getSourcePosition();
    }

    private record HeavyStrike(ServerPlayer attacker, LivingEntity target) { }
    private record BlockedHit(UUID target, long gameTick) { }
}
