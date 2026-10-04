package dev.dbil.technique;

import dev.dbil.DBIL;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Techniques {
    public static final ResourceLocation KI_WAVE = DBIL.id("ki_wave");
    public static final ResourceLocation KI_BLAST = DBIL.id("ki_blast");
    public static final ResourceLocation KI_BARRAGE = DBIL.id("ki_barrage");
    private static final Map<ResourceLocation, TechniqueDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean initialized;

    private Techniques() { }

    public static synchronized void bootstrap() {
        if (initialized) return;
        register(new TechniqueDefinition(KI_WAVE, Component.translatable("technique.dbil.ki_wave"),
                TechniqueType.KI_BLAST, 12, 0, 7.0F, 12, 50, 1.15, 32, 0.175,
                0.55, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of()), 0, null,
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.20, 0)));
        register(new TechniqueDefinition(KI_BLAST, Component.translatable("technique.dbil.ki_blast"),
                TechniqueType.KI_BLAST, 5, 0, 2.5F, 2, 12, 1.45, 24, 0.125,
                0.16, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of(KI_WAVE)), 0, null,
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.10, 0)));
        register(new TechniqueDefinition(KI_BARRAGE, Component.translatable("technique.dbil.ki_barrage"),
                TechniqueType.BARRAGE, 24, 3, 3.2F, 8, 60, 1.2, 28, 0.15,
                0.20, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of(KI_BLAST)), 0, null,
                new TechniqueDefinition.ProjectilePattern(3, 4, 0.10, 1.5)));
        initialized = true;
    }

    public static void register(TechniqueDefinition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate technique: " + definition.id());
        }
    }

    @Nullable
    public static TechniqueDefinition get(ResourceLocation id) { return DEFINITIONS.get(id); }

    public static Collection<TechniqueDefinition> values() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }
}
