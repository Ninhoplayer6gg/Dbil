package dev.dbil.character;

import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

public record OriginDefinition(ResourceLocation id, String displayName,
                               Set<ResourceLocation> allowedRaces, Map<Stat, Double> initialBonuses) {
    public OriginDefinition {
        allowedRaces = Set.copyOf(allowedRaces);
        initialBonuses = Map.copyOf(initialBonuses);
        if (id == null || displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Invalid origin");
        for (double value : initialBonuses.values()) {
            if (!Double.isFinite(value) || Math.abs(value) > 20) throw new IllegalArgumentException("Invalid origin bonus");
        }
    }
    public boolean allows(ResourceLocation race) { return allowedRaces.isEmpty() || allowedRaces.contains(race); }
}
