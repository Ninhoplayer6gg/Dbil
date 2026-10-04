package dev.dbil.technique;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 0.3 charge, scaling and presentation data kept beside the 0.2 {@link TechniqueDefinition} record so existing
 * definitions and saves stay compatible. Factors interpolate linearly from 1.0 (minimum charge) to the maximum.
 *
 * @param chargeMaxTicks      ticks of holding needed for 100% charge; 0 means the technique is not chargeable
 * @param maxKiFactor         total Ki paid at 100% relative to the base cost (base is paid on start)
 * @param maxDamageFactor     damage multiplier at 100%
 * @param maxKnockbackFactor  knockback multiplier at 100%
 * @param maxSizeFactor       visual and collision size multiplier at 100%
 * @param size                base visual radius in blocks
 * @param color               outer energy color (RGB)
 * @param coreColor           inner core color (RGB)
 * @param pose                animation pose family
 * @param turnRate            beam steering in degrees per tick (beams only)
 */
public record TechniqueProfile(ResourceLocation id, int chargeMaxTicks, double maxKiFactor, double maxDamageFactor,
                               double maxKnockbackFactor, double maxSizeFactor, float size, int color, int coreColor,
                               Pose pose, float turnRate) {
    public enum Pose { BLAST, BARRAGE, WAVE, KAMEHAMEHA, GALICK_GUN, MASENKO }

    public TechniqueProfile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(pose);
        if (chargeMaxTicks < 0 || chargeMaxTicks > 200 || !(maxKiFactor >= 1 && maxKiFactor <= 5)
                || !(maxDamageFactor >= 1 && maxDamageFactor <= 6) || !(maxKnockbackFactor >= 1 && maxKnockbackFactor <= 4)
                || !(maxSizeFactor >= 1 && maxSizeFactor <= 4) || !(size > 0 && size <= 4) || !(turnRate >= 0 && turnRate <= 20)) {
            throw new IllegalArgumentException("Invalid technique profile: " + id);
        }
    }

    public boolean chargeable() { return chargeMaxTicks > 0; }
    public double damageFactor(float charge) { return 1 + (maxDamageFactor - 1) * clamp(charge); }
    public double knockbackFactor(float charge) { return 1 + (maxKnockbackFactor - 1) * clamp(charge); }
    public double sizeFactor(float charge) { return 1 + (maxSizeFactor - 1) * clamp(charge); }
    /** Extra Ki paid per charge tick so a full charge costs base * maxKiFactor in total. */
    public double extraKiPerTick(double baseCost) {
        return chargeable() ? baseCost * (maxKiFactor - 1) / chargeMaxTicks : 0;
    }

    private static double clamp(float charge) { return Float.isFinite(charge) ? Math.max(0, Math.min(1, charge)) : 0; }
}
