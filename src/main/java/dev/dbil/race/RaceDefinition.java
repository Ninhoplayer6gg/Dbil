package dev.dbil.race;

import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/** Race behavior is data driven; services ask the definition instead of comparing race IDs. */
public record RaceDefinition(ResourceLocation id, String displayName,
                             Map<Stat, Double> baseAttributes, Map<Stat, Double> growthMultipliers,
                             double kiCostMultiplier, double experienceMultiplier,
                             Set<ResourceLocation> passives, Set<ResourceLocation> racialTechniques,
                             Set<ResourceLocation> allowedTransformations) {
    public RaceDefinition {
        if (id == null || displayName == null || displayName.isBlank()
                || !Double.isFinite(kiCostMultiplier) || kiCostMultiplier <= 0
                || !Double.isFinite(experienceMultiplier) || experienceMultiplier <= 0) {
            throw new IllegalArgumentException("Invalid race definition");
        }
        baseAttributes = Map.copyOf(baseAttributes);
        growthMultipliers = Map.copyOf(growthMultipliers);
        passives = Set.copyOf(passives);
        racialTechniques = Set.copyOf(racialTechniques);
        allowedTransformations = Set.copyOf(allowedTransformations);
        for (double value : baseAttributes.values()) {
            if (!Double.isFinite(value) || value < 1) throw new IllegalArgumentException("Invalid base attribute");
        }
        for (double value : growthMultipliers.values()) {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid growth multiplier");
        }
    }

    public double base(Stat stat) { return baseAttributes.getOrDefault(stat, stat.initialValue()); }
    public double growth(Stat stat) { return growthMultipliers.getOrDefault(stat, 1.0); }
}
