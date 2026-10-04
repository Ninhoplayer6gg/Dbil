package dev.dbil.race;

import dev.dbil.api.DefinitionRegistry;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class Races {
    public static final ResourceLocation HUMAN = new ResourceLocation("dbil", "human");
    public static final ResourceLocation SAIYAN = new ResourceLocation("dbil", "saiyan");
    private static final DefinitionRegistry<RaceDefinition> REGISTRY = new DefinitionRegistry<>(RaceDefinition::id);
    private static boolean initialized;

    private Races() {}

    public static synchronized void bootstrap() {
        if (initialized) return;
        initialized = true;
        register(new RaceDefinition(HUMAN, "Humano", Map.of(Stat.KI_CONTROL, 12.0),
                Map.of(Stat.KI_CONTROL, 1.15), 0.92, 1.10,
                Set.of(new ResourceLocation("dbil", "energy_efficiency")), Set.of(),
                Set.of(new ResourceLocation("dbil", "potential_unleashed"))));
        register(new RaceDefinition(SAIYAN, "Saiyajin", Map.of(Stat.STRENGTH, 12.0, Stat.VITALITY, 12.0),
                Map.of(Stat.STRENGTH, 1.15, Stat.VITALITY, 1.10), 1.0, 1.0, Set.of(), Set.of(),
                Set.of(new ResourceLocation("dbil", "super_saiyan"))));
    }

    public static void register(RaceDefinition race) { REGISTRY.register(race); }
    public static RaceDefinition get(ResourceLocation id) { bootstrap(); return REGISTRY.get(id); }
    public static Collection<RaceDefinition> values() { bootstrap(); return REGISTRY.values(); }
}
