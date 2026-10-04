package dev.dbil.technique;

import dev.dbil.character.CharacterData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

/** Data describes attacks. Execution, collision and visual presentation remain separate systems. */
public record TechniqueDefinition(ResourceLocation id, Component displayName, TechniqueType type,
                                  double kiCost, double staminaCost, float damage, int chargeTime,
                                  int cooldown, double projectileSpeed, double range, double radius,
                                  double knockback, boolean piercing, boolean explosion, boolean homing,
                                  Requirements requirements, double masteryRequirement,
                                  BeamProperties beam, ProjectilePattern projectiles) {
    public TechniqueDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(type);
        Objects.requireNonNull(requirements);
        Objects.requireNonNull(projectiles);
        if (!Double.isFinite(kiCost) || kiCost < 0 || !Double.isFinite(staminaCost) || staminaCost < 0
                || !Float.isFinite(damage) || damage <= 0 || chargeTime < 0 || cooldown < 1
                || !Double.isFinite(projectileSpeed) || projectileSpeed <= 0 || projectileSpeed > 4
                || !Double.isFinite(range) || range <= 0 || range > 128
                || !Double.isFinite(radius) || radius < 0 || radius > 16
                || !Double.isFinite(knockback) || knockback < 0 || knockback > 2.5
                || !Double.isFinite(masteryRequirement) || masteryRequirement < 0) {
            throw new IllegalArgumentException("Invalid technique definition: " + id);
        }
        if (type == TechniqueType.BEAM && beam == null) {
            throw new IllegalArgumentException("Beam techniques require collision/clash geometry: " + id);
        }
    }

    /** One activation may schedule a bounded sequence; each projectile uses the same collision executor. */
    public record ProjectilePattern(int count, int intervalTicks, double statScaling, double spreadDegrees) {
        public ProjectilePattern {
            if (count < 1 || count > 8 || intervalTicks < 0 || intervalTicks > 40
                    || count > 1 && intervalTicks < 1
                    || !Double.isFinite(statScaling) || statScaling < 0 || statScaling > 2
                    || !Double.isFinite(spreadDegrees) || spreadDegrees < 0 || spreadDegrees > 30) {
                throw new IllegalArgumentException("Invalid projectile pattern");
            }
        }
    }

    public record Requirements(int minimumLevel, Set<ResourceLocation> races,
                               Set<ResourceLocation> prerequisiteTechniques) {
        public Requirements {
            if (minimumLevel < 1) throw new IllegalArgumentException("Minimum level must be positive");
            races = Set.copyOf(races);
            prerequisiteTechniques = Set.copyOf(prerequisiteTechniques);
        }

        public boolean test(CharacterData data) {
            return data.created() && data.level() >= minimumLevel
                    && (races.isEmpty() || races.contains(data.raceId()))
                    && data.unlockedTechniques().containsAll(prerequisiteTechniques);
        }
    }

    /** Future beams retain volume and clash identity independently of their renderer. */
    public record BeamProperties(double width, int durationTicks, ResourceLocation clashGroup,
                                 boolean clashCompatible) {
        public BeamProperties {
            Objects.requireNonNull(clashGroup);
            if (!Double.isFinite(width) || width <= 0 || width > 8 || durationTicks < 1 || durationTicks > 400) {
                throw new IllegalArgumentException("Invalid beam geometry");
            }
        }
    }
}
