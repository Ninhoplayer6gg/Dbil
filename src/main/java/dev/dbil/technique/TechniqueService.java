package dev.dbil.technique;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.network.Network;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.targeting.TargetingService;
import dev.dbil.transformation.TransformationService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A validated activation pays its base cost once and schedules a bounded server-side pattern.
 * Hold-mode (0.3) keeps charging after the wind-up: each charge tick pays a small extra amount of Ki and the
 * release fires with the reached charge fraction. Quick activation keeps the exact 0.2 behavior.
 */
public final class TechniqueService {
    private static final UUID CHARGE_SLOW = UUID.fromString("5b1f1d9a-3f0e-4b3c-9a7e-6d0c3c2b8e41");
    /** A fully charged technique held this long fires on its own, so a stuck key cannot drain forever. */
    public static final int FULL_HOLD_LIMIT = 40;

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

    /** Loadout edits; never while a paid sequence is in progress. */
    public static boolean equip(ServerPlayer player, ResourceLocation id, boolean equip) {
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        if (!data.created() || id == null || state.techniqueCharging || state.barrageRemaining > 0
                || Techniques.get(id) == null) return false;
        return equip ? data.equip(id) : data.unequip(id);
    }

    /** Quick cast (touch button, tests and 0.2 clients): wind-up then fire at minimum charge. */
    public static void start(ServerPlayer player) { begin(player, false); }

    /** Hold cast: wind-up, then charge while held; release fires. Non-chargeable techniques behave like quick cast. */
    public static void startHold(ServerPlayer player) { begin(player, true); }

    public static void release(ServerPlayer player) {
        PlayerState state = ServerRuntime.state(player);
        if (state.techniqueCharging && state.techniqueHolding) state.techniqueReleaseRequested = true;
    }

    private static void begin(ServerPlayer player, boolean hold) {
        if (!player.isAlive() || player.isRemoved()) return;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        FlightService.restorePosition(player, state);
        TechniqueDefinition technique = Techniques.get(data.selectedTechnique());
        long now = player.level().getGameTime();
        if (technique == null || !executable(technique) || !allowed(data, technique)
                || player.isSpectator() || player.isSleeping() || state.techniqueCharging
                || state.barrageRemaining > 0 || state.guarding || state.transformationChargeTicks > 0
                || beamActive(player, state) || now < state.guardBreakUntil || now < state.nextTechniqueTick) return;
        double cost = kiCost(technique, data);
        if (!data.canSpendKi(cost) || !data.canSpendStamina(technique.staminaCost())) return;
        data.spendKi(cost);
        data.spendStamina(technique.staminaCost());
        TechniqueProfile profile = Techniques.profile(technique.id());
        state.charging = false;
        state.techniqueCharging = true;
        state.chargingTechnique = technique.id();
        state.techniqueChargeTicks = technique.chargeTime();
        state.techniqueHolding = hold && profile.chargeable();
        state.techniqueReleaseRequested = false;
        state.techniqueChargedTicks = 0;
        state.techniqueFullHoldTicks = 0;
        state.techniqueFireCharge = 0;
        state.barrageRemaining = 0;
        state.barrageDelay = 0;
        state.markCombat(now);
        int burstTicks = (technique.projectiles().count() - 1) * technique.projectiles().intervalTicks();
        state.nextTechniqueTick = now + technique.chargeTime() + burstTicks + cooldown(technique);
        player.level().playSound(null, player.blockPosition(), technique.type() == TechniqueType.BEAM
                        ? ModSounds.BEAM_CHARGE.get() : ModSounds.TECHNIQUE_CHARGE.get(),
                SoundSource.PLAYERS, 0.55F, technique.type() == TechniqueType.BEAM ? 1.0F : 1.2F);
        Network.syncState(player);
    }

    private static boolean executable(TechniqueDefinition definition) {
        return definition.type() == TechniqueType.KI_BLAST || definition.type() == TechniqueType.BARRAGE
                || definition.type() == TechniqueType.BEAM && definition.beam() != null;
    }

    private static boolean allowed(CharacterData data, TechniqueDefinition definition) {
        return data.created() && data.unlockedTechniques().contains(definition.id())
                && data.equippedTechniques().contains(definition.id()) && definition.requirements().test(data)
                && data.mastery().getOrDefault(definition.id(), 0.0) >= definition.masteryRequirement();
    }

    public static boolean beamActive(ServerPlayer player, PlayerState state) {
        if (state.activeBeamId < 0) return false;
        if (player.level().getEntity(state.activeBeamId) instanceof KiBeamEntity beam && beam.isAlive()
                && beam.phase() == KiBeamEntity.PHASE_FIRING) return true;
        state.activeBeamId = -1;
        return false;
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
        return base * (1 - efficiency) * (race == null ? 1.0 : race.kiCostMultiplier()) * global
                * TransformationService.controlPenalty(data);
    }

    /** Current charge fraction for presentation and for the fired attack. */
    public static float chargeFraction(PlayerState state) {
        if (state.chargingTechnique == null) return 0;
        TechniqueProfile profile = Techniques.profile(state.chargingTechnique);
        if (!profile.chargeable()) return 0;
        if (!state.techniqueCharging) return state.techniqueFireCharge;
        return Math.min(1F, state.techniqueChargedTicks / (float) profile.chargeMaxTicks());
    }

    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        applySlow(player, state);
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
            TechniqueProfile profile = Techniques.profile(technique.id());
            boolean windUp = state.techniqueChargeTicks > 0;
            if (windUp) state.techniqueChargeTicks--;
            if (state.techniqueHolding) {
                chargeTick(player, data, state, technique, profile);
                boolean full = state.techniqueChargedTicks >= profile.chargeMaxTicks();
                if (full) state.techniqueFullHoldTicks++;
                boolean fire = state.techniqueChargeTicks <= 0
                        && (state.techniqueReleaseRequested || full && state.techniqueFullHoldTicks >= FULL_HOLD_LIMIT);
                if (!fire) return;
            } else if (state.techniqueChargeTicks > 0) {
                return;
            }
            float charge = state.techniqueHolding
                    ? Math.min(1F, state.techniqueChargedTicks / (float) Math.max(1, profile.chargeMaxTicks())) : 0F;
            state.techniqueCharging = false;
            state.techniqueChargeTicks = 0;
            state.techniqueFireCharge = charge;
            long now = player.level().getGameTime();
            if (state.techniqueHolding) {
                int burstTicks = (technique.projectiles().count() - 1) * technique.projectiles().intervalTicks();
                state.nextTechniqueTick = now + burstTicks + cooldown(technique);
            }
            state.techniqueHolding = false;
            state.techniqueReleaseRequested = false;
            fire(player, data, technique, charge, 0);
            data.setMastery(technique.id(), data.mastery().getOrDefault(technique.id(), 0.0) + 0.05);
            data.recordTraining("technique_casts", 1);
            if (technique.id().equals(Techniques.KI_WAVE)) data.recordTraining("ki_wave_casts", 1);
            state.barrageRemaining = technique.projectiles().count() - 1;
            state.barrageDelay = technique.projectiles().intervalTicks();
            Network.syncState(player);
        } else if (--state.barrageDelay <= 0) {
            int index = technique.projectiles().count() - state.barrageRemaining;
            fire(player, data, technique, 0F, index);
            state.barrageRemaining--;
            state.barrageDelay = technique.projectiles().intervalTicks();
        }
        if (!state.techniqueCharging && state.barrageRemaining == 0) state.chargingTechnique = null;
    }

    private static void chargeTick(ServerPlayer player, CharacterData data, PlayerState state,
                                   TechniqueDefinition technique, TechniqueProfile profile) {
        if (state.techniqueChargedTicks >= profile.chargeMaxTicks()) return;
        double extra = profile.extraKiPerTick(kiCost(technique, data));
        // Charging stalls instead of failing when Ki runs out; the player can still release what they have.
        if (extra > 0 && !data.spendKi(extra)) return;
        state.techniqueChargedTicks++;
        int max = profile.chargeMaxTicks();
        int ticks = state.techniqueChargedTicks;
        if (ticks == Math.round(max * 0.3F) || ticks == Math.round(max * 0.6F) || ticks == Math.round(max * 0.9F)) {
            player.level().playSound(null, player.blockPosition(), ModSounds.TECHNIQUE_CHARGE.get(), SoundSource.PLAYERS,
                    0.35F + ticks / (float) max * 0.4F, 0.9F + ticks / (float) max * 0.6F);
            Network.syncState(player);
        }
    }

    private static void fire(ServerPlayer player, CharacterData data, TechniqueDefinition technique, float charge, int index) {
        FlightService.restorePosition(player, ServerRuntime.state(player));
        Vec3 direction = player.getLookAngle();
        LivingEntity lock = TargetingService.target(player);
        if (lock != null) direction = lock.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        TechniqueProfile profile = Techniques.profile(technique.id());
        double power = data.stat(Stat.KI_POWER) * TransformationService.multiplier(data, Stat.KI_POWER);
        float damage = (float) ((technique.damage() + power * technique.projectiles().statScaling()) * profile.damageFactor(charge));
        PlayerState state = ServerRuntime.state(player);
        state.markCombat(player.level().getGameTime());
        FxService.entity(player, FxType.TECHNIQUE_FIRE, profile.pose().ordinal(), index, charge, profile.color());
        if (technique.type() == TechniqueType.BEAM) {
            KiBeamEntity beam = new KiBeamEntity(ModEntities.KI_BEAM.get(), player.level());
            beam.initialize(player, technique, profile, direction, damage, charge);
            if (player.serverLevel().addFreshEntity(beam)) state.activeBeamId = beam.getId();
            player.level().playSound(null, player.blockPosition(), ModSounds.BEAM_FIRE.get(),
                    SoundSource.PLAYERS, 0.8F + charge * 0.4F, 1.1F - charge * 0.25F);
            return;
        }
        KiWaveEntity wave = new KiWaveEntity(ModEntities.KI_WAVE.get(), player.level());
        wave.initialize(player, technique, direction, damage);
        wave.setCharge(charge);
        // Spread remains small and server generated; it never changes the selected target or damage values.
        if (technique.projectiles().spreadDegrees() > 0) {
            wave.shoot(direction.x, direction.y, direction.z, (float) technique.projectileSpeed(),
                    (float) technique.projectiles().spreadDegrees());
        }
        player.serverLevel().addFreshEntity(wave);
        player.level().playSound(null, player.blockPosition(), technique.id().equals(Techniques.KI_WAVE)
                        ? ModSounds.KI_WAVE_FIRE.get() : ModSounds.KI_BLAST_FIRE.get(),
                SoundSource.PLAYERS, 0.5F + charge * 0.3F, 1.0F + player.getRandom().nextFloat() * 0.15F);
    }

    /** Charging, firing beams and paid wind-ups root the caster partially. */
    private static void applySlow(ServerPlayer player, PlayerState state) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        boolean slowed = state.techniqueCharging && state.techniqueHolding || state.activeBeamId >= 0 && beamActive(player, state);
        AttributeModifier current = movement.getModifier(CHARGE_SLOW);
        if (slowed && current == null) {
            movement.addTransientModifier(new AttributeModifier(CHARGE_SLOW, "DBIL technique charge", -0.6,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!slowed && current != null) {
            movement.removeModifier(CHARGE_SLOW);
        }
    }

    public static void cancel(ServerPlayer player) {
        PlayerState state = ServerRuntime.state(player);
        state.techniqueCharging = false;
        state.techniqueChargeTicks = 0;
        state.chargingTechnique = null;
        state.barrageRemaining = 0;
        state.barrageDelay = 0;
        state.techniqueHolding = false;
        state.techniqueReleaseRequested = false;
        state.techniqueChargedTicks = 0;
        state.techniqueFullHoldTicks = 0;
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(CHARGE_SLOW);
        if (state.activeBeamId >= 0 && player.level().getEntity(state.activeBeamId) instanceof KiBeamEntity beam) {
            state.activeBeamId = -1;
            beam.discard();
        }
        state.activeBeamId = -1;
    }
}
