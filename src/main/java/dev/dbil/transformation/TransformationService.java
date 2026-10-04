package dev.dbil.transformation;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.config.ServerConfig;
import dev.dbil.ki.KiService;
import dev.dbil.network.Network;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;

/** Forms compose temporary modifiers over base attributes; all costs and state live on the server. */
public final class TransformationService {
    private static final int ACTIVATION_COOLDOWN = 80;
    private static final int ACTIVE_MASTERY_INTERVAL = 100;
    private static final int COMBAT_MASTERY_INTERVAL = 20;
    private static final double MOVEMENT_INTERRUPT_SQUARED = 0.25 * 0.25;

    private TransformationService() {}

    public static TransformationEligibility.Result start(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isSleeping()
                || player.isPassenger() || player.isInWaterOrBubble()) return TransformationEligibility.Result.INVALID_STATE;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        if (!data.created()) return TransformationEligibility.Result.NO_CHARACTER;
        if (CharacterData.BASE_FORM.equals(id)) {
            revert(player);
            return TransformationEligibility.Result.READY;
        }
        if (id == null) return TransformationEligibility.Result.UNKNOWN_FORM;
        var definition = Transformations.get(id).orElse(null);
        if (definition == null) return TransformationEligibility.Result.UNKNOWN_FORM;
        if (data.currentTransformation().equals(id)) return TransformationEligibility.Result.ALREADY_ACTIVE;
        if (state.transformationChargeTicks > 0 || state.techniqueCharging || state.guarding
                || !data.currentTransformation().equals(CharacterData.BASE_FORM)) return TransformationEligibility.Result.BUSY;
        long now = player.serverLevel().getGameTime();
        if (now < state.nextTransformationTick) return TransformationEligibility.Result.COOLDOWN;
        TransformationEligibility.Result eligible = TransformationEligibility.check(data, definition);
        if (eligible != TransformationEligibility.Result.READY) return eligible;
        double cost = activationCost(definition);
        if (!data.spendKi(cost)) return TransformationEligibility.Result.INSUFFICIENT_KI;

        state.charging = false;
        KiService.clearSlow(player);
        state.pendingTransformation = id;
        double mastery = data.mastery().getOrDefault(id, 0.0);
        state.transformationChargeTicks = Math.max(1, definition.mastery().activationTicks(definition.activationTicks(), mastery));
        state.transformationStartHealth = player.getHealth();
        state.transformationStartPosition = player.position();
        state.nextTransformationTick = now + state.transformationChargeTicks + ACTIVATION_COOLDOWN;
        player.setSprinting(false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.6F, 0.7F);
        updatePower(player, data, state);
        Network.sync(player);
        return TransformationEligibility.Result.READY;
    }

    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        if (!data.created() || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
            resetSession(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (state.transformationChargeTicks > 0) {
            TransformationDefinition pending = Transformations.get(state.pendingTransformation).orElse(null);
            boolean interrupted = pending == null || !eligibleWithoutCost(data, pending)
                    || player.isPassenger() || player.isInWaterOrBubble() || player.isSleeping()
                    || player.getHealth() + 0.001F < state.transformationStartHealth
                    || state.transformationStartPosition == null
                    || player.position().distanceToSqr(state.transformationStartPosition) > MOVEMENT_INTERRUPT_SQUARED
                    || state.techniqueCharging || state.charging || state.guarding;
            if (interrupted) {
                cancelCharge(state);
                Network.sync(player);
                return;
            }
            player.setSprinting(false);
            if (--state.transformationChargeTicks == 0) {
                data.setTransformation(pending.id());
                cancelCharge(state);
                state.lastTransformationMasteryTick = now;
                state.lastTransformationCombatTick = now;
                CharacterService.applyAttributes(player, data);
                updatePower(player, data, state);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.7F, 0.7F);
                Network.sync(player);
            }
        }
        if (CharacterData.BASE_FORM.equals(data.currentTransformation())) return;
        TransformationDefinition active = activeDefinition(data);
        if (active == null || player.isSleeping()) {
            revert(player);
            return;
        }
        double mastery = data.mastery().getOrDefault(active.id(), 0.0);
        double drain = active.mastery().drain(active.kiDrainPerTick(), mastery) * drainMultiplier();
        if (!data.spendKi(drain)) {
            revert(player);
            return;
        }
        // Time in an active form grants gradual stability; starting/cancelling grants no mastery.
        if (now - state.lastTransformationMasteryTick >= ACTIVE_MASTERY_INTERVAL) {
            state.lastTransformationMasteryTick = now;
            addMastery(data, active, active.mastery().gainPerUse());
        }
    }

    public static void cancelActivation(ServerPlayer player) {
        PlayerState state = ServerRuntime.state(player);
        if (state.transformationChargeTicks <= 0) return;
        cancelCharge(state);
        Network.syncState(player);
    }

    public static void revert(ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        cancelCharge(state);
        data.setTransformation(CharacterData.BASE_FORM);
        CharacterService.applyAttributes(player, data);
        updatePower(player, data, state);
        Network.sync(player);
    }

    /** Called before session state is discarded. Safe even after Forge has invalidated death capabilities. */
    public static void resetSession(ServerPlayer player) {
        cancelCharge(ServerRuntime.state(player));
        player.getCapability(CharacterCapability.CAPABILITY).ifPresent(data -> {
            data.setTransformation(CharacterData.BASE_FORM);
            CharacterService.applyAttributes(player, data);
        });
    }

    /** Validated combat systems call this after a real successful hit, never for incoming packets. */
    public static void recordCombat(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return;
        CharacterData data = CharacterCapability.get(player);
        TransformationDefinition active = activeDefinition(data);
        if (active == null) return;
        PlayerState state = ServerRuntime.state(player);
        long now = player.serverLevel().getGameTime();
        if (now - state.lastTransformationCombatTick < COMBAT_MASTERY_INTERVAL) return;
        state.lastTransformationCombatTick = now;
        addMastery(data, active, active.mastery().gainPerUse() * 1.6);
    }

    public static double multiplier(CharacterData data, Stat stat) {
        TransformationDefinition active = activeDefinition(data);
        return active == null ? 1.0 : active.multipliers().getOrDefault(stat, 1.0);
    }

    /** Output ratio uses the player's base allocation rather than level or a hardcoded form switch. */
    public static double powerMultiplier(CharacterData data) {
        TransformationDefinition active = activeDefinition(data);
        if (active == null) return 1.0;
        double base = 0;
        double transformed = 0;
        for (Stat stat : Stat.values()) {
            if (stat == Stat.MAX_KI || stat == Stat.MAX_STAMINA) continue;
            double value = data.stat(stat);
            base += value;
            transformed += value * active.multipliers().getOrDefault(stat, 1.0);
        }
        return base <= 0 ? 1.0 : transformed / base;
    }

    public static double activationCost(TransformationDefinition definition) {
        return definition.activationKiCost() * (ServerConfig.SPEC.isLoaded() ? ServerConfig.transformationCostMultiplier.get() : 1.0);
    }

    private static double drainMultiplier() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.transformationDrainMultiplier.get() : 1.0;
    }

    private static void addMastery(CharacterData data, TransformationDefinition definition, double amount) {
        data.setMastery(definition.id(), Math.min(definition.mastery().maximum(),
                data.mastery().getOrDefault(definition.id(), 0.0) + amount));
    }

    private static void cancelCharge(PlayerState state) {
        state.pendingTransformation = null;
        state.transformationChargeTicks = 0;
        state.transformationStartPosition = null;
        state.transformationStartHealth = 0;
    }

    private static void updatePower(ServerPlayer player, CharacterData data, PlayerState state) {
        PowerLevelCalculator.update(data, player.getHealth() / Math.max(1, player.getMaxHealth()), state.charging, state.flying);
    }

    @Nullable
    private static TransformationDefinition activeDefinition(CharacterData data) {
        if (!data.created() || CharacterData.BASE_FORM.equals(data.currentTransformation())) return null;
        TransformationDefinition definition = Transformations.get(data.currentTransformation()).orElse(null);
        return definition != null && eligibleWithoutCost(data, definition) ? definition : null;
    }

    private static boolean eligibleWithoutCost(CharacterData data, TransformationDefinition definition) {
        return definition.races().contains(data.raceId()) && data.unlockedTransformations().contains(definition.id())
                && definition.requirements().satisfiedBy(data);
    }
}
