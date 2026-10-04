package dev.dbil.transformation;

import dev.dbil.character.CharacterData;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A registered form's rules and appearance. A definition alone never grants the player's unlock. */
public record TransformationDefinition(
        ResourceLocation id,
        String displayName,
        Set<ResourceLocation> races,
        Requirements requirements,
        Map<Stat, Double> multipliers,
        double kiDrainPerTick,
        double activationKiCost,
        int activationTicks,
        Appearance appearance,
        Set<ResourceLocation> abilities,
        MasteryRules mastery,
        UnlockCondition unlockCondition,
        ResourceLocation branch) {

    public TransformationDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(requirements);
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(mastery);
        Objects.requireNonNull(unlockCondition);
        Objects.requireNonNull(branch);
        races = Set.copyOf(races);
        multipliers = Map.copyOf(multipliers);
        abilities = Set.copyOf(abilities);
        if (displayName.isBlank() || races.isEmpty()) {
            throw new IllegalArgumentException("Transformation name and race eligibility are required: " + id);
        }
        if (!finiteNonnegative(kiDrainPerTick) || !finiteNonnegative(activationKiCost)
                || activationTicks < 0 || activationTicks > 1200) {
            throw new IllegalArgumentException("Invalid transformation costs: " + id);
        }
        for (double multiplier : multipliers.values()) {
            if (!Double.isFinite(multiplier) || multiplier <= 0 || multiplier > 100) {
                throw new IllegalArgumentException("Transformation multipliers must be finite and between 0 and 100: " + id);
            }
        }
    }

    private static boolean finiteNonnegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    public record Requirements(int minimumLevel, Map<Stat, Double> minimumStats,
                               Set<ResourceLocation> techniques) {
        public Requirements {
            if (minimumLevel < 1) throw new IllegalArgumentException("Minimum level must be positive");
            minimumStats = Map.copyOf(minimumStats);
            techniques = Set.copyOf(techniques);
            if (minimumStats.values().stream().anyMatch(value -> !finiteNonnegative(value))) {
                throw new IllegalArgumentException("Invalid transformation attribute requirement");
            }
        }

        public boolean satisfiedBy(CharacterData data) {
            if (data.level() < minimumLevel) return false;
            for (var entry : minimumStats.entrySet()) {
                if (data.stat(entry.getKey()) < entry.getValue()) return false;
            }
            return data.unlockedTechniques().containsAll(techniques);
        }
    }

    /** Resource references are optional; renderer integrations decide how each channel is used. */
    public record Appearance(ResourceLocation aura, ResourceLocation model,
                             ResourceLocation hair, ResourceLocation eyes,
                             ResourceLocation texture, Set<ResourceLocation> effects) {
        public Appearance {
            Objects.requireNonNull(aura);
            effects = Set.copyOf(effects);
        }
    }

    /** Mastery interpolates towards these efficiency factors; power does not grow exponentially. */
    public record MasteryRules(double maximum, double masteredDrainFactor,
                               double masteredActivationFactor, double gainPerUse) {
        public MasteryRules {
            if (!Double.isFinite(maximum) || maximum <= 0 || !finiteNonnegative(gainPerUse)
                    || !Double.isFinite(masteredDrainFactor) || masteredDrainFactor < 0 || masteredDrainFactor > 1
                    || !Double.isFinite(masteredActivationFactor) || masteredActivationFactor < 0 || masteredActivationFactor > 1) {
                throw new IllegalArgumentException("Invalid transformation mastery rules");
            }
        }

        public double drain(double baseDrain, double currentMastery) {
            return baseDrain * interpolate(masteredDrainFactor, currentMastery);
        }

        public int activationTicks(int baseTicks, double currentMastery) {
            return (int) Math.ceil(baseTicks * interpolate(masteredActivationFactor, currentMastery));
        }

        private double interpolate(double masteredFactor, double currentMastery) {
            double fraction = Double.isFinite(currentMastery)
                    ? Math.max(0, Math.min(1, currentMastery / maximum)) : 0;
            return 1 + (masteredFactor - 1) * fraction;
        }
    }

    /** A stable identifier allows quests, masters and training paths to supply their own evaluators. */
    public record UnlockCondition(ResourceLocation evaluator, Map<String, String> parameters) {
        public UnlockCondition {
            Objects.requireNonNull(evaluator);
            parameters = Map.copyOf(parameters);
        }
    }
}
