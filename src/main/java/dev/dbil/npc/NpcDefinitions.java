package dev.dbil.npc;

import dev.dbil.DBIL;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcDefinitions {
    private static final Map<ResourceLocation, NpcDefinition> DEFINITIONS = new LinkedHashMap<>();
    public static final NpcDefinition TRAINING_ENEMY = register(new NpcDefinition(DBIL.id("training_enemy"),
            Component.translatable("entity.dbil.training_enemy"), NpcDefinition.Role.ENEMY,
            40, 4, 0.27, 20, 35));

    private NpcDefinitions() { }

    public static NpcDefinition register(NpcDefinition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate NPC definition: " + definition.id());
        }
        return definition;
    }

    public static NpcDefinition get(ResourceLocation id) { return DEFINITIONS.get(id); }
    public static Collection<NpcDefinition> values() { return Collections.unmodifiableCollection(DEFINITIONS.values()); }
}
