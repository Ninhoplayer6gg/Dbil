package dev.dbil.combat;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightService;
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
    private static final int LIGHT_INTERVAL = 8;
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

    public static void attack(ServerPlayer player, boolean heavy) {
        if (!player.isAlive() || player.isRemoved()) return;
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        FlightService.restorePosition(player, state);
        long now = player.level().getGameTime();
        if (!data.created() || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || state.techniqueCharging || state.barrageRemaining > 0 || state.charging || state.guarding
                || state.transformationChargeTicks > 0 || now < state.guardBreakUntil || now < state.nextMeleeTick) return;
        double cost = heavy ? 7.0 : 2.0;
        if (!data.canSpendStamina(cost)) return;
        data.spendStamina(cost);
        state.nextMeleeTick = now + (heavy ? HEAVY_INTERVAL : LIGHT_INTERVAL);
        if (now > state.comboExpires) state.combo = 0;
        int stage = heavy ? 0 : state.combo;
        boolean finisher = !heavy && stage == 3;
        state.combo = heavy || finisher ? 0 : stage + 1;
        state.comboExpires = now + COMBO_WINDOW;
        double reach = heavy ? HEAVY_REACH : LIGHT_REACH;
        LivingEntity target = TargetingService.target(player);
        if (target == null || player.distanceToSqr(target) > reach * reach) {
            target = TargetingService.select(player, reach, 0.35);
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (target == null) return;
        // Lock-on does not allow a melee strike behind the player.
        Vec3 offset = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (offset.normalize().dot(player.getLookAngle()) < 0.10) return;
        double strength = data.stat(Stat.STRENGTH) * TransformationService.multiplier(data, Stat.STRENGTH);
        float rawDamage = (float) ((2.0 + strength * 0.14)
                * (heavy ? 1.9 : finisher ? 1.35 : 1.0));
        boolean hit;
        if (heavy) GuardService.markHeavy(player, target);
        try {
            hit = damage(player, target, rawDamage, heavy ? 0.95 : finisher ? 0.7 : 0.2,
                    heavy ? 0.32 : finisher ? 0.24 : 0.10);
        } finally {
            if (heavy) GuardService.clearHeavy();
        }
        if (hit) {
            data.recordTraining("melee_hits", 1);
            if (player.isAlive() && !player.isRemoved()) TransformationService.recordCombat(player);
            player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1,
                    target.getZ(), heavy || finisher ? 5 : 2, 0.2, 0.2, 0.2, 0.05);
            player.level().playSound(null, target.blockPosition(), heavy ? SoundEvents.PLAYER_ATTACK_STRONG
                    : SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, finisher ? 0.8F : 1.1F);
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
        // Vanilla's ten-tick immunity would eat validated eight-tick combo hits.
        int immunity = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean result = target.hurt(source, damage);
        boolean blocked = GuardService.consumeBlocked(target);
        if (!result) target.invulnerableTime = immunity;
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
