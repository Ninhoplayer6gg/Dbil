package dev.dbil.combat;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.targeting.TargetingService;
import dev.dbil.transformation.TransformationService;
import dev.dbil.technique.KiWaveEntity;
import dev.dbil.technique.Techniques;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** DBIL fists have their own stamina, combo window and server clock; tools retain vanilla combat. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID)
public final class CombatService {
    private static final double LIGHT_REACH = 3.4;
    private static final double HEAVY_REACH = 3.8;
    private static final int LIGHT_INTERVAL = 7;
    private static final int HEAVY_INTERVAL = 18;
    private static final int COMBO_WINDOW = 24;

    private CombatService() { }

    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        // Preserve the normal client interaction packet; only the logical server redirects damage.
        if (player.level().isClientSide) return;
        if (!player.getMainHandItem().isEmpty()) return;
        boolean created = player.getCapability(CharacterCapability.CAPABILITY)
                .map(CharacterData::created).orElse(false);
        if (!created) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer) attack(serverPlayer, player.isShiftKeyDown() || ServerRuntime.state(serverPlayer).flying && ServerRuntime.state(serverPlayer).descend);
    }

    /** Melee families. Light hits advance a four-stage combo; the others are standalone strikes. */
    public enum MeleeKind { LIGHT, HEAVY, LAUNCHER, SMASH }

    private static final int LAUNCH_INTERVAL = 18;
    private static final int SMASH_INTERVAL = 20;
    private static final int CHASE_WINDOW = 30;
    /** Damage, horizontal and vertical knockback for combo stages jab, cross, kick and finisher. */
    private static final double[] STAGE_DAMAGE = {1.0, 1.05, 1.2, 1.45};
    private static final double[] STAGE_PUSH = {0.12, 0.14, 0.22, 0.85};
    private static final double[] STAGE_LIFT = {0.04, 0.05, 0.12, 0.3};

    public static void attack(ServerPlayer player, boolean heavy) {
        attack(player, heavy ? MeleeKind.HEAVY : MeleeKind.LIGHT);
    }

    public static void attack(ServerPlayer player, MeleeKind kind) {
        if (!player.isAlive() || player.isRemoved()) return;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        FlightService.restorePosition(player, state);
        long now = player.level().getGameTime();
        if (!data.created() || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || state.techniqueCharging || state.barrageRemaining > 0 || state.charging || state.guarding
                || state.activeBeamId >= 0 || state.transformationChargeTicks > 0 || now < state.guardBreakUntil
                || now < state.nextMeleeTick) return;
        boolean heavy = kind != MeleeKind.LIGHT;
        double cost = switch (kind) { case LIGHT -> 2.0; case HEAVY -> 7.0; case LAUNCHER -> 9.0; case SMASH -> 10.0; };
        if (!data.canSpendStamina(cost)) return;
        data.spendStamina(cost);
        state.nextMeleeTick = now + switch (kind) {
            case LIGHT -> LIGHT_INTERVAL; case HEAVY -> HEAVY_INTERVAL; case LAUNCHER -> LAUNCH_INTERVAL; case SMASH -> SMASH_INTERVAL; };
        if (now > state.comboExpires) state.combo = 0;
        int stage = heavy ? 0 : state.combo;
        boolean finisher = kind == MeleeKind.LIGHT && stage == 3;
        state.combo = heavy || finisher ? 0 : stage + 1;
        state.comboExpires = now + COMBO_WINDOW;
        state.lastMeleeTick = now;
        state.markCombat(now);
        int swing = switch (kind) {
            case LIGHT -> stage; case HEAVY -> FxType.MELEE_HEAVY; case LAUNCHER -> FxType.MELEE_LAUNCHER; case SMASH -> FxType.MELEE_SMASH; };
        FxService.entity(player, FxType.MELEE_SWING, swing, -1, 0, 0);
        double reach = heavy ? HEAVY_REACH : LIGHT_REACH;
        LivingEntity target = TargetingService.target(player);
        // A locked target slightly beyond reach is still hit: the animation covers the short lunge.
        double lockedReach = reach + 0.8;
        if (target == null || player.distanceToSqr(target) > lockedReach * lockedReach) {
            target = TargetingService.select(player, reach, 0.35);
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.blockPosition(), ModSounds.MELEE_SWING.get(), SoundSource.PLAYERS,
                0.5F, heavy ? 0.8F : 1.0F + stage * 0.08F);
        if (target == null) return;
        // Lock-on does not allow a melee strike behind the player.
        Vec3 offset = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (offset.normalize().dot(player.getLookAngle()) < 0.10) return;
        double strength = data.stat(Stat.STRENGTH) * TransformationService.multiplier(data, Stat.STRENGTH);
        double factor = switch (kind) {
            case LIGHT -> STAGE_DAMAGE[stage]; case HEAVY -> 1.9; case LAUNCHER -> 1.4; case SMASH -> 1.7; };
        float rawDamage = (float) ((2.0 + strength * 0.14) * factor);
        double horizontal = switch (kind) {
            case LIGHT -> STAGE_PUSH[stage]; case HEAVY -> 0.95; case LAUNCHER -> 0.15; case SMASH -> 1.8; };
        double vertical = switch (kind) {
            case LIGHT -> STAGE_LIFT[stage]; case HEAVY -> 0.32; case LAUNCHER -> 1.05; case SMASH -> 0.22; };
        // Striking down at an airborne target from above turns a smash into a meteor strike.
        if (kind == MeleeKind.SMASH && !target.onGround() && player.getY() > target.getY() + 0.6) vertical = -1.1;
        boolean hit;
        if (heavy) GuardService.markHeavy(player, target);
        try {
            hit = damage(player, target, rawDamage, horizontal, vertical);
        } finally {
            if (heavy) GuardService.clearHeavy();
        }
        if (!hit) return;
        data.recordTraining("melee_hits", 1);
        if (player.isAlive() && !player.isRemoved()) TransformationService.recordCombat(player);
        if (kind == MeleeKind.LAUNCHER || kind == MeleeKind.SMASH || finisher) {
            state.chaseTargetId = target.getId();
            state.chaseWindowUntil = now + (finisher ? CHASE_WINDOW - 10 : CHASE_WINDOW);
        }
        // Air juggle: a flying attacker keeps light-hit targets suspended for the next blow.
        if (kind == MeleeKind.LIGHT && !finisher && state.flying && !(target instanceof Player) && !target.onGround()) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x * 0.5, Math.max(motion.y, 0.06), motion.z * 0.5);
            target.hurtMarked = true;
        }
        if (target instanceof TrainingEnemy enemy) enemy.stun(heavy || finisher ? 14 : 6);
        int strengthLevel = kind == MeleeKind.LIGHT ? (finisher ? 2 : 1) : 3;
        Vec3 point = target.getBoundingBox().getCenter().add(offset.normalize().scale(-Math.min(0.4, target.getBbWidth() * 0.5)));
        FxService.entityAt(target, FxType.HIT, strengthLevel, player.getId(), (float) Math.max(horizontal, Math.abs(vertical)), point, 0xFFF2C8);
        player.level().playSound(null, target.blockPosition(), heavy || finisher ? ModSounds.MELEE_HIT_HEAVY.get()
                : ModSounds.MELEE_HIT.get(), SoundSource.PLAYERS, 0.8F, finisher ? 0.85F : 1.0F + stage * 0.05F);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1,
                target.getZ(), heavy || finisher ? 5 : 2, 0.2, 0.2, 0.2, 0.05);
    }

    /** Beam ticks use the beam entity as the direct source so frontal guards and kill credit stay correct. */
    public static boolean damageBeam(ServerPlayer attacker, LivingEntity target, float amount, Vec3 direction,
                                     double horizontal, double vertical, Entity beam) {
        if (beam.level() != attacker.level() || target.level() != beam.level()) return false;
        boolean hit = damageInternal(attacker, target, amount, direction, horizontal, vertical, 160,
                attacker.damageSources().mobProjectile(beam, attacker), false);
        if (hit && target instanceof TrainingEnemy enemy) enemy.stun(8);
        return hit;
    }

    /** Bounded area damage around an energy impact. Each entity is hit at most once per explosion. */
    public static void explosion(ServerPlayer attacker, Entity source, Vec3 center, double radius, float amount, double push) {
        if (!attacker.isAlive() || amount <= 0 || radius <= 0) return;
        AABB area = new AABB(center, center).inflate(radius);
        int hits = 0;
        for (LivingEntity target : attacker.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != attacker && entity.isAlive() && TargetingService.eligible(attacker, entity))) {
            if (++hits > 12) break;
            Vec3 offset = target.getBoundingBox().getCenter().subtract(center);
            double distance = offset.length();
            if (distance > radius + target.getBbWidth()) continue;
            double falloff = Math.max(0.35, 1 - distance / (radius + 0.5));
            damageInternal(attacker, target, (float) (amount * falloff), offset, push * falloff, 0.3 * falloff, 160,
                    attacker.damageSources().mobProjectile(source, attacker), false);
        }
    }

    public static boolean damage(ServerPlayer attacker, LivingEntity target, float amount,
                                 double horizontal, double vertical) {
        return damageTechnique(attacker, target, amount, target.position().subtract(attacker.position()),
                horizontal, vertical, HEAVY_REACH + 0.5);
    }

    /** Projectiles share defense/PvP validation, but use their definition's bounded travel range. */
    public static boolean damageTechnique(ServerPlayer attacker, LivingEntity target, float amount,
                                          Vec3 direction, double horizontal, double vertical, double range) {
        return damageInternal(attacker, target, amount, direction, horizontal, vertical, range,
                attacker.damageSources().playerAttack(attacker), true);
    }

    /** Indirect energy damage retains the real projectile position for directional guarding and kill credit. */
    public static boolean damageTechnique(ServerPlayer attacker, LivingEntity target, float amount,
                                          Vec3 direction, double horizontal, double vertical, double range,
                                          Entity projectile) {
        return projectile instanceof KiWaveEntity wave
                && damageProjectile(attacker, target, amount, direction, horizontal, vertical, wave);
    }

    /** Server-clipped shots retain launch geometry when their caster moves or takes cover. */
    public static boolean damageProjectile(ServerPlayer attacker, LivingEntity target, float amount,
                                           Vec3 direction, double horizontal, double vertical, KiWaveEntity projectile) {
        if (projectile.getOwner() != attacker || projectile.level() != attacker.level()
                || target.level() != projectile.level()) return false;
        var definition = Techniques.get(projectile.techniqueId());
        if (definition == null) return false;
        Vec3 origin = projectile.launchOrigin();
        AABB bounds = target.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(origin.x, bounds.minX, bounds.maxX),
                Mth.clamp(origin.y, bounds.minY, bounds.maxY), Mth.clamp(origin.z, bounds.minZ, bounds.maxZ));
        double range = definition.range() + 0.5; // Projectile collision uses an expanded target hitbox.
        if (origin.distanceToSqr(nearest) > range * range) return false;
        return damageInternal(attacker, target, amount, direction, horizontal, vertical, range,
                attacker.damageSources().mobProjectile(projectile, attacker), false);
    }

    private static boolean damageInternal(ServerPlayer attacker, LivingEntity target, float amount,
                                          Vec3 direction, double horizontal, double vertical, double range,
                                          DamageSource source, boolean validateCasterReach) {
        if (!attacker.isAlive() || attacker.isRemoved() || !target.isAlive() || target.isRemoved()) return false;
        FlightService.restorePosition(attacker, ServerRuntime.state(attacker));
        if (target instanceof ServerPlayer other) FlightService.restorePosition(other, ServerRuntime.state(other));
        if (!Float.isFinite(amount) || amount <= 0 || !CharacterCapability.get(attacker).created()
                || !attacker.isAlive() || !TargetingService.eligible(attacker, target)
                || validateCasterReach && (attacker.distanceToSqr(target) > range * range
                || !attacker.hasLineOfSight(target))) return false;
        float damage = scaled(amount);
        long now = attacker.level().getGameTime();
        ServerRuntime.state(attacker).markCombat(now);
        if (target instanceof ServerPlayer defender) ServerRuntime.state(defender).markCombat(now);
        // Vanilla's ten-tick immunity would eat validated eight-tick combo hits.
        int immunity = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean result = target.hurt(source, damage);
        boolean blocked = GuardService.consumeBlocked(target);
        if (!result) target.invulnerableTime = immunity;
        if (result && blocked) {
            FxService.entity(target, FxType.GUARD_BLOCK, 0, attacker.getId(), (float) horizontal, 0xBFE6FF);
            target.level().playSound(null, target.blockPosition(), ModSounds.GUARD_BLOCK.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        if (result) KnockbackService.launch(target, direction, blocked ? horizontal * 0.2 : horizontal,
                blocked ? vertical * 0.2 : vertical);
        return result;
    }

    public static boolean npcAttack(Mob attacker, LivingEntity target, float amount) {
        if (target instanceof ServerPlayer other) FlightService.restorePosition(other, ServerRuntime.state(other));
        if (!Float.isFinite(amount) || amount <= 0 || !attacker.isAlive() || attacker.isRemoved()
                || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || attacker.isAlliedTo(target) || attacker.distanceToSqr(target) > 9
                || !attacker.hasLineOfSight(target)) return false;
        float scaledDamage = (float) (amount * ServerConfig.npcDifficulty.get());
        boolean hit = target.hurt(attacker.damageSources().mobAttack(attacker), scaled(scaledDamage));
        boolean blocked = GuardService.consumeBlocked(target);
        if (hit) KnockbackService.launch(target, target.position().subtract(attacker.position()),
                blocked ? 0.056 : 0.28, blocked ? 0.02 : 0.10);
        return hit;
    }

    private static float scaled(float raw) {
        // Defense/guard are applied exactly once in GuardService's server LivingHurtEvent listener.
        return (float) Math.max(0.25, Math.min(100_000, raw * ServerConfig.damageMultiplier.get()));
    }
}
