package dev.dbil.transformation;

import dev.dbil.DBIL;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.Races;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Definitions are registered once at startup, then immutable during gameplay. */
public final class Transformations {
    public static final ResourceLocation SUPER_SAIYAN = DBIL.id("super_saiyan");
    public static final ResourceLocation POTENTIAL_UNLEASHED = DBIL.id("potential_unleashed");
    private static final Map<ResourceLocation, TransformationDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean frozen;
    private static boolean initialized;

    private Transformations() {}

    public static synchronized void bootstrap() {
        if (initialized) return;
        initialized = true;
        var unlock = new TransformationDefinition.UnlockCondition(DBIL.id("training_challenge"),
                Map.of("challenge", "dbil:awakening"));
        var requirements = new TransformationDefinition.Requirements(3, Map.of(), Set.of());
        register(new TransformationDefinition(SUPER_SAIYAN, "transformation.dbil.super_saiyan",
                Set.of(Races.SAIYAN), requirements,
                Map.of(Stat.STRENGTH, 1.5, Stat.KI_POWER, 1.6, Stat.DEFENSE, 1.2, Stat.SPEED, 1.2),
                0.18, 25, 30,
                new TransformationDefinition.Appearance(DBIL.id("super_saiyan"), null,
                        DBIL.id("super_saiyan"), DBIL.id("super_saiyan"), null, Set.of()),
                Set.of(), new TransformationDefinition.MasteryRules(100, 0.55, 0.4, 0.10), unlock,
                DBIL.id("classic")));
        register(new TransformationDefinition(POTENTIAL_UNLEASHED, "transformation.dbil.potential_unleashed",
                Set.of(Races.HUMAN), requirements,
                Map.of(Stat.STRENGTH, 1.25, Stat.KI_POWER, 1.35, Stat.DEFENSE, 1.15, Stat.SPEED, 1.1),
                0.13, 20, 30,
                new TransformationDefinition.Appearance(DBIL.id("potential_unleashed"), null,
                        null, null, null, Set.of()),
                Set.of(), new TransformationDefinition.MasteryRules(100, 0.60, 0.4, 0.10), unlock,
                DBIL.id("technical")));
        PowerLevelCalculator.registerModifier(DBIL.id("active_transformation"),
                (data, context) -> TransformationService.powerMultiplier(data));
        freeze();
    }

    public static synchronized void register(TransformationDefinition definition) {
        if (frozen) throw new IllegalStateException("Transformation registry is frozen");
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate transformation id: " + definition.id());
        }
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static Optional<TransformationDefinition> get(ResourceLocation id) {
        bootstrap();
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Collection<TransformationDefinition> values() {
        bootstrap();
        return java.util.List.copyOf(DEFINITIONS.values());
    }
}
