package dev.dbil.registry;

import dev.dbil.DBIL;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Common particle registrations (types are shared; providers are client-only). DBIL spawns these on the client
 * and passes packed RGB in the first velocity argument, size in the second and a behavior value in the third.
 */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, DBIL.MOD_ID);
    public static final RegistryObject<SimpleParticleType> KI_SPARK = PARTICLES.register("ki_spark", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> AURA_MOTE = PARTICLES.register("aura_mote", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> KI_TRAIL = PARTICLES.register("ki_trail", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> IMPACT_RING = PARTICLES.register("impact_ring", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = PARTICLES.register("shockwave", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> DUST_CLOUD = PARTICLES.register("dust_cloud", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FLASH = PARTICLES.register("flash", () -> new SimpleParticleType(true));

    private ModParticles() {}

    public static void register(IEventBus bus) { PARTICLES.register(bus); }
}
