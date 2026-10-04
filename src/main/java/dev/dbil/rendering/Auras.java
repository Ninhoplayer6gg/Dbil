package dev.dbil.rendering;

import dev.dbil.DBIL;
import dev.dbil.api.DefinitionRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Register new aura visuals independently of character skins, races and gameplay services. */
public final class Auras {
    private static final DefinitionRegistry<AuraDefinition> REGISTRY = new DefinitionRegistry<>(AuraDefinition::id);
    public static final AuraDefinition CHARGING = new AuraDefinition(DBIL.id("charging"),
            new DustParticleOptions(new Vector3f(0.25F, 0.85F, 1.0F), 0.8F), 0.45, 0.05, 4);
    public static final AuraDefinition TECHNIQUE = new AuraDefinition(DBIL.id("technique_charge"),
            new DustParticleOptions(new Vector3f(0.4F, 0.65F, 1.0F), 1.0F), 0.5, 0.07, 3);
    public static final AuraDefinition SUPER_SAIYAN = new AuraDefinition(DBIL.id("super_saiyan"),
            new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.18F), 0.95F), 0.50, 0.07, 4);
    public static final AuraDefinition POTENTIAL = new AuraDefinition(DBIL.id("potential_unleashed"),
            new DustParticleOptions(new Vector3f(0.95F, 0.98F, 1.0F), 0.85F), 0.48, 0.06, 4);
    static { REGISTRY.register(CHARGING); REGISTRY.register(TECHNIQUE); REGISTRY.register(SUPER_SAIYAN); REGISTRY.register(POTENTIAL); }
    private Auras() {}
    public static void register(AuraDefinition definition) { REGISTRY.register(definition); }
    public static AuraDefinition get(ResourceLocation id) {
        AuraDefinition definition = REGISTRY.get(id);
        return definition == null ? CHARGING : definition;
    }
}
