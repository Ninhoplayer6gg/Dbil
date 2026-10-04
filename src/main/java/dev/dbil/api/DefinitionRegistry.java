package dev.dbil.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Small startup registry for data definitions that do not require Minecraft's entity/item registry. */
public final class DefinitionRegistry<T> {
    private final Map<ResourceLocation, T> definitions = new LinkedHashMap<>();
    private final Function<T, ResourceLocation> id;

    public DefinitionRegistry(Function<T, ResourceLocation> id) { this.id = Objects.requireNonNull(id); }

    public synchronized void register(T definition) {
        Objects.requireNonNull(definition);
        ResourceLocation key = Objects.requireNonNull(id.apply(definition));
        if (definitions.putIfAbsent(key, definition) != null) {
            throw new IllegalArgumentException("Duplicate DBIL definition: " + key);
        }
    }

    public T get(ResourceLocation key) { return definitions.get(key); }
    public Collection<T> values() { return List.copyOf(definitions.values()); }
}
