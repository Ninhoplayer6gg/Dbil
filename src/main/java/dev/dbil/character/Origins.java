package dev.dbil.character;

import dev.dbil.api.DefinitionRegistry;
import dev.dbil.race.Races;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class Origins {
    public static final ResourceLocation EARTH_WARRIOR = new ResourceLocation("dbil", "earth_warrior");
    public static final ResourceLocation SURVIVOR = new ResourceLocation("dbil", "survivor");
    private static final DefinitionRegistry<OriginDefinition> REGISTRY = new DefinitionRegistry<>(OriginDefinition::id);
    private static boolean initialized;
    private Origins() {}
    public static synchronized void bootstrap() {
        if (initialized) return;
        initialized = true;
        register(new OriginDefinition(EARTH_WARRIOR, "Guerreiro da Terra", Set.of(), Map.of(Stat.DEFENSE, 1.0)));
        register(new OriginDefinition(SURVIVOR, "Saiyajin sobrevivente", Set.of(Races.SAIYAN), Map.of(Stat.VITALITY, 1.0)));
    }
    public static void register(OriginDefinition definition) { REGISTRY.register(definition); }
    public static OriginDefinition get(ResourceLocation id) { bootstrap(); return REGISTRY.get(id); }
    public static Collection<OriginDefinition> values() { bootstrap(); return REGISTRY.values(); }
}
