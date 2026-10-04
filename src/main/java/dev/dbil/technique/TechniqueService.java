package dev.dbil.technique;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.network.Network;
import dev.dbil.registry.ModEntities;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.targeting.TargetingService;
import dev.dbil.transformation.TransformationService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A validated activation pays once and schedules a bounded server-side projectile pattern. */
public final class TechniqueService {
    private TechniqueService() { }

    public static boolean select(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        TechniqueDefinition technique = Techniques.get(id);
        if (state.techniqueCharging || state.barrageRemaining > 0 || technique == null
                || !executable(technique) || !allowed(data, technique) || !data.setSelectedTechnique(id)) return false;
        Network.sync(player);
        return true;
    }

    public static void start(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) return;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        FlightService.restorePosition(player, state);
        TechniqueDefinition technique = Techniques.get(data.selectedTechnique());
        long now = player.level().getGameTime();
        if (technique == null || !executable(technique) || !allowed(data, technique)
                || player.isSpectator() || player.isSleeping() || state.techniqueCharging
                || state.barrageRemaining > 0 || state.guarding || state.transformationChargeTicks > 0
                || now < state.guardBreakUntil || now < state.nextTechniqueTick) return;
        double cost = kiCost(technique, data);
        if (!data.canSpendKi(cost) || !data.canSpendStamina(technique.staminaCost())) return;
        data.spendKi(cost);
        data.spendStamina(technique.staminaCost());
        state.charging = false;
        state.techniqueCharging = true;
        state.chargingTechnique = technique.id();
        state.techniqueChargeTicks = technique.chargeTime();
        state.barrageRemaining = 0;
        state.barrageDelay = 0;
        int burstTicks = (technique.projectiles().count() - 1) * technique.projectiles().intervalTicks();
        state.nextTechniqueTick = now + technique.chargeTime() + burstTicks + cooldown(technique);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.4F, 0.8F);
    }

    private static boolean executable(TechniqueDefinition definition) {
        return definition.type() == TechniqueType.KI_BLAST || definition.type() == TechniqueType.BARRAGE;
    }

    private static boolean allowed(CharacterData data, TechniqueDefinition definition) {
        return data.created() && data.unlockedTechniques().contains(definition.id())
                && data.equippedTechniques().contains(definition.id()) && definition.requirements().test(data)
                && data.mastery().getOrDefault(definition.id(), 0.0) >= definition.masteryRequirement();
    }

    public static int cooldown(TechniqueDefinition definition) {
        if (!ServerConfig.SPEC.isLoaded()) return definition.cooldown();
        if (definition.id().equals(Techniques.KI_BLAST)) return ServerConfig.kiBlastCooldown.get();
        if (definition.id().equals(Techniques.KI_BARRAGE)) return ServerConfig.kiBarrageCooldown.get();
        return definition.cooldown();
    }

    public static double kiCost(TechniqueDefinition technique, CharacterData data) {
        double base = technique.kiCost();
        double global = 1.0;
        if (ServerConfig.SPEC.isLoaded()) {
            global = ServerConfig.techniqueKiCostMultiplier.get();
            if (technique.id().equals(Techniques.KI_BLAST)) base = ServerConfig.kiBlastCost.get();
            if (technique.id().equals(Techniques.KI_BARRAGE)) base = ServerConfig.kiBarrageCost.get();
        }
        // Efficiency improves gently; even perfect control retains an appreciable cost.
        double efficiency = Math.min(0.35, data.stat(Stat.KI_CONTROL) / 400.0);
        RaceDefinition race = Races.get(data.raceId());
        return base * (1 - efficiency) * (race == null ? 1.0 : race.kiCostMultiplier()) * global;
    }

    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        if (!state.techniqueCharging && state.barrageRemaining <= 0) return;
        if (!data.created() || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || state.transformationChargeTicks > 0 || state.guarding) {
            cancel(player);
            return;
        }
        TechniqueDefinition technique = Techniques.get(state.chargingTechnique);
        if (technique == null || !allowed(data, technique)) {
            cancel(player);
            return;
        }
        if (state.techniqueCharging) {
            if (--state.techniqueChargeTicks > 0) return;
            state.techniqueCharging = false;
            state.techniqueChargeTicks = 0;
            fire(player, data, technique);
            data.setMastery(technique.id(), data.mastery().getOrDefault(technique.id(), 0.0) + 0.05);
            data.recordTraining("technique_casts", 1);
            if (technique.id().equals(Techniques.KI_WAVE)) data.recordTraining("ki_wave_casts", 1);
            state.barrageRemaining = technique.projectiles().count() - 1;
            state.barrageDelay = technique.projectiles().intervalTicks();
        } else if (--state.barrageDelay <= 0) {
            fire(player, data, technique);
            state.barrageRemaining--;
            state.barrageDelay = technique.projectiles().intervalTicks();
        }
        if (!state.techniqueCharging && state.barrageRemaining == 0) state.chargingTechnique = null;
    }

    private static void fire(ServerPlayer player, CharacterData data, TechniqueDefinition technique) {
        FlightService.restorePosition(player, ServerRuntime.state(player));
        Vec3 direction = player.getLookAngle();
        LivingEntity lock = TargetingService.target(player);
        if (lock != null) direction = lock.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        KiWaveEntity wave = new KiWaveEntity(ModEntities.KI_WAVE.get(), player.level());
        double power = data.stat(Stat.KI_POWER) * TransformationService.multiplier(data, Stat.KI_POWER);
        float damage = (float) (technique.damage() + power * technique.projectiles().statScaling());
        wave.initialize(player, technique, direction, damage);
        // Spread remains small and server generated; it never changes the selected target or damage values.
        if (technique.projectiles().spreadDegrees() > 0) {
            wave.shoot(direction.x, direction.y, direction.z, (float) technique.projectileSpeed(),
                    (float) technique.projectiles().spreadDegrees());
        }
        player.serverLevel().addFreshEntity(wave);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    public static void cancel(ServerPlayer player) {
        PlayerState state = ServerRuntime.state(player);
        state.techniqueCharging = false;
        state.techniqueChargeTicks = 0;
        state.chargingTechnique = null;
        state.barrageRemaining = 0;
        state.barrageDelay = 0;
    }
}
