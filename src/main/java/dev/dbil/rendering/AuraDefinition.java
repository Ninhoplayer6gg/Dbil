package dev.dbil.rendering;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Client visual data; costs and transformation eligibility are never part of an aura. */
public record AuraDefinition(ResourceLocation id, ParticleOptions particle, double radius,
                             double riseSpeed, int particlesPerBurst) {
    public AuraDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(particle);
        if (!Double.isFinite(radius) || radius <= 0 || radius > 2
                || !Double.isFinite(riseSpeed) || riseSpeed < 0 || riseSpeed > 0.3
                || particlesPerBurst < 1 || particlesPerBurst > 4) {
            throw new IllegalArgumentException("Invalid bounded DBIL aura: " + id);
        }
    }
}
