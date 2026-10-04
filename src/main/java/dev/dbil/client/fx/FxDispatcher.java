package dev.dbil.client.fx;

import dev.dbil.client.ClientState;
import dev.dbil.client.anim.AnimState;
import dev.dbil.client.anim.CharacterAnimator;
import dev.dbil.config.ClientConfig;
import dev.dbil.fx.FxType;
import dev.dbil.network.Network;
import dev.dbil.registry.ModParticles;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.TechniqueProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Turns server presentation events into animations, particles, flashes and camera reactions. */
public final class FxDispatcher {
    private FxDispatcher() {}

    public static void handle(Network.FxEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        Entity entity = event.entityId() >= 0 ? level.getEntity(event.entityId()) : null;
        LivingEntity living = entity instanceof LivingEntity l ? l : null;
        Vec3 at = new Vec3(event.x(), event.y(), event.z());
        boolean impacts = !ClientConfig.SPEC.isLoaded() || ClientConfig.impactEffects.get();
        boolean local = entity == minecraft.player;
        double distance = minecraft.player.position().distanceTo(entity != null ? entity.position() : at);
        switch (event.type()) {
            case FxType.MELEE_SWING -> {
                if (living == null) return;
                // The local player's strike was already animated on click.
                if (local && dev.dbil.client.ClientEvents.recentlyPredicted(level.getGameTime())) return;
                int length = switch (event.variant()) {
                    case FxType.MELEE_KICK -> 9; case FxType.MELEE_FINISHER -> 12; case FxType.MELEE_HEAVY, FxType.MELEE_SMASH -> 14;
                    case FxType.MELEE_LAUNCHER -> 13; default -> 7; };
                CharacterAnimator.trigger(living, AnimState.Action.MELEE, event.variant(), 0, length);
            }
            case FxType.HIT -> {
                if (living != null) CharacterAnimator.hit(living, event.variant());
                if (!impacts) return;
                int strength = event.variant();
                FxParticles.flash(at, event.color(), 0.35F + strength * 0.2F);
                FxParticles.burst(at, 0xFFF4D6, 3 + strength * 4, 0.12F, 0.18F + strength * 0.06F);
                if (strength >= 2) FxParticles.ring(at, 0xFFFFFF, 0.3F, 1.2F + strength * 0.5F);
                if (strength >= 3 && living != null && living.onGround()) FxParticles.dust(living.position(), 6, 0.45F, 1.2F);
                boolean attacker = event.otherId() == minecraft.player.getId();
                if (attacker) dev.dbil.gui.HudState.localHit();
                if (local) CameraEffects.shake(0.12F + strength * 0.1F);
                else if (attacker) CameraEffects.shake(strength >= 2 ? 0.12F + strength * 0.05F : 0.04F);
            }
            case FxType.DASH -> {
                if (living == null) return;
                CharacterAnimator.trigger(living, AnimState.Action.DASH, event.variant(), 0, 7);
                Vec3 center = living.position().add(0, living.getBbHeight() * 0.5, 0);
                FxParticles.burst(center, 0xDDF4FF, 6, 0.1F, 0.12F);
                if (living.onGround()) FxParticles.dust(living.position(), 4, 0.35F, 0.8F);
            }
            case FxType.VANISH -> {
                Vec3 from = at.add(0, 1, 0);
                FxParticles.flash(from, 0xFFFFFF, 0.9F);
                FxParticles.ring(from, 0xE8F4FF, 0.4F, 2.2F);
                FxParticles.burst(from, 0xFFFFFF, 10, 0.1F, 0.25F);
                afterimage(at, living);
                if (living != null) {
                    CharacterAnimator.trigger(living, AnimState.Action.VANISH, 0, 0, 8);
                    Vec3 arrival = living.position().add(0, 1, 0);
                    FxParticles.flash(arrival, 0xFFFFFF, 0.7F);
                    FxParticles.burst(arrival, 0xFFFFFF, 6, 0.08F, 0.2F);
                }
                if (local) CameraEffects.special(0.08F, 6);
            }
            case FxType.CHASE -> {
                if (living == null) return;
                Vec3 to = living.position().add(0, 1, 0);
                FxParticles.line(at.add(0, 1, 0), to, 0xE8F6FF, 0.35F, 14);
                FxParticles.flash(to, 0xFFFFFF, 0.6F);
                CharacterAnimator.trigger(living, AnimState.Action.DASH, FxType.DASH_FORWARD, 0, 7);
                if (local) CameraEffects.special(0.1F, 8);
            }
            case FxType.TECHNIQUE_FIRE -> {
                if (living == null) return;
                CharacterAnimator.trigger(living, AnimState.Action.TECHNIQUE_FIRE, event.variant(), event.otherId(), 10);
                Vec3 hands = KiBeamEntity.origin(living, living.getLookAngle());
                float charge = event.magnitude();
                FxParticles.flash(hands, event.color(), 0.5F + charge * 0.6F);
                FxParticles.burst(hands, event.color(), 4 + Math.round(charge * 8), 0.1F, 0.2F);
                boolean beam = event.variant() == TechniqueProfile.Pose.KAMEHAMEHA.ordinal()
                        || event.variant() == TechniqueProfile.Pose.GALICK_GUN.ordinal()
                        || event.variant() == TechniqueProfile.Pose.MASENKO.ordinal();
                if (local && beam) CameraEffects.special(0.15F + charge * 0.2F, 4 + charge * 6);
            }
            case FxType.KI_IMPACT -> {
                if (!impacts) return;
                float size = Math.max(0.2F, event.magnitude());
                FxParticles.flash(at, event.color(), size * 0.9F);
                FxParticles.burst(at, event.color(), 4 + Math.round(size * 5), 0.11F, 0.15F + size * 0.08F);
                if (event.variant() >= 1) FxParticles.ring(at, event.color(), size * 0.4F, size * 1.6F);
                if (event.variant() == 2) FxParticles.dust(at, 5, 0.4F, size);
                if (distance < 12) CameraEffects.shake(Math.min(0.2F, size * 0.08F) * (float) (1 - distance / 12));
            }
            case FxType.EXPLOSION -> {
                float radius = Math.max(0.5F, event.magnitude());
                FxParticles.flash(at, event.color(), radius * 1.4F);
                FxParticles.flash(at, 0xFFFFFF, radius * 0.8F);
                FxParticles.ring(at, event.color(), radius * 0.5F, radius * 2.6F);
                FxParticles.shockwave(at.add(0, 0.1, 0), event.color(), radius * 0.4F, radius * 3.2F);
                FxParticles.burst(at, event.color(), 14 + Math.round(radius * 6), 0.16F, 0.35F + radius * 0.05F);
                FxParticles.dust(at, 8 + Math.round(radius * 3), 0.7F, radius * 1.5F);
                int smoke = FxParticles.count(Math.round(radius * 3), at);
                for (int i = 0; i < smoke; i++) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + (level.random.nextDouble() - 0.5) * radius,
                            at.y + level.random.nextDouble() * radius * 0.5, at.z + (level.random.nextDouble() - 0.5) * radius, 0, 0.05, 0);
                }
                if (distance < 24) CameraEffects.special(Math.min(0.55F, radius * 0.12F) * (float) (1 - distance / 24), 0);
            }
            case FxType.TRANSFORM_COMPLETE -> {
                if (living == null) return;
                CharacterAnimator.trigger(living, AnimState.Action.POWER_BURST, 0, 0, 16);
                boolean reduced = ClientConfig.SPEC.isLoaded()
                        && ClientConfig.transformationEffects.get() == ClientConfig.TransformationEffects.REDUCED;
                Vec3 center = living.position().add(0, 1, 0);
                FxParticles.flash(center, event.color(), reduced ? 1.4F : 2.6F);
                FxParticles.flash(center, 0xFFFFFF, reduced ? 0.8F : 1.4F);
                FxParticles.shockwave(living.position().add(0, 0.1, 0), event.color(), 0.6F, reduced ? 4 : 7);
                FxParticles.ring(center, event.color(), 0.6F, reduced ? 3 : 5);
                FxParticles.burst(center, event.color(), reduced ? 12 : 30, 0.15F, 0.45F);
                if (living.onGround()) FxParticles.dust(living.position(), reduced ? 6 : 14, 0.7F, 3.0F);
                if (distance < 20) CameraEffects.special((local ? 0.45F : 0.25F) * (float) (1 - distance / 20), local ? 12 : 0);
            }
            case FxType.TRANSFORM_REVERT -> {
                if (living == null) return;
                Vec3 center = living.position().add(0, 1, 0);
                int n = FxParticles.count(10, center);
                for (int i = 0; i < n; i++) {
                    FxParticles.spawn(ModParticles.AURA_MOTE.get(), center.x + (level.random.nextDouble() - 0.5) * 0.8,
                            center.y + (level.random.nextDouble() - 0.5) * 1.6, center.z + (level.random.nextDouble() - 0.5) * 0.8,
                            event.color(), 0.18F, 0.06F);
                }
            }
            case FxType.GUARD_BLOCK -> {
                if (living == null || !impacts) return;
                Vec3 front = living.getEyePosition().add(living.getLookAngle().scale(0.6)).add(0, -0.3, 0);
                FxParticles.flash(front, event.color(), 0.5F);
                FxParticles.burst(front, event.color(), 6, 0.1F, 0.2F);
                FxParticles.ring(front, event.color(), 0.3F, 1.1F);
                if (local) CameraEffects.shake(0.08F);
            }
            case FxType.GUARD_BREAK -> {
                if (living == null) return;
                Vec3 center = living.position().add(0, 1.2, 0);
                FxParticles.burst(center, 0xFFFFFF, 14, 0.14F, 0.3F);
                FxParticles.ring(center, 0xFF8080, 0.4F, 2.0F);
                if (local) CameraEffects.shake(0.3F);
            }
            case FxType.TERRAIN_DEBRIS -> {
                float radius = Math.max(0.5F, event.magnitude());
                FxParticles.dust(at, 10 + Math.round(radius * 4), 0.8F, radius * 2);
            }
            default -> { }
        }
    }

    /** A brief ring of white motes where a vanishing character stood. */
    private static void afterimage(Vec3 feet, LivingEntity living) {
        float height = living == null ? 1.8F : living.getBbHeight();
        int n = FxParticles.count(14, feet);
        for (int i = 0; i < n; i++) {
            double y = feet.y + height * i / (double) Math.max(1, n);
            double angle = i * 2.4;
            FxParticles.spawn(ModParticles.KI_TRAIL.get(), feet.x + Mth.cos((float) angle) * 0.3, y,
                    feet.z + Mth.sin((float) angle) * 0.3, 0xEAF4FF, 0.35F, 0);
        }
    }

    /** Snapshot transitions with presentation meaning (transformation start, guard break). */
    public static void stateChanged(int entityId, ClientState.VisualState before, ClientState.VisualState after) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (before.guardBreakTicks() == 0 && after.guardBreakTicks() > 0) {
            handle(new Network.FxEvent(FxType.GUARD_BREAK, 0, entityId, -1, 0, 0, 0, 0, 0xFFFFFF));
        }
        if (entityId == minecraft.player.getId() && before.transformationTicks() == 0 && after.transformationTicks() > 0) {
            CameraEffects.special(0.1F, 2);
        }
    }
}
