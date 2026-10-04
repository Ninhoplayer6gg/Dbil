package dev.dbil;

import com.mojang.logging.LogUtils;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.config.ClientConfig;
import dev.dbil.config.ServerConfig;
import dev.dbil.network.Network;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.registry.ModItems;
import dev.dbil.registry.ModParticles;
import dev.dbil.registry.ModSounds;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import dev.dbil.training.TrainingChallenges;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DBIL.MOD_ID)
public final class DBIL {
    public static final String MOD_ID = "dbil";
    public static final Logger LOGGER = LogUtils.getLogger();
    public DBIL() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(CharacterCapability::register);
        ModEntities.register(bus);
        ModItems.register(bus);
        ModSounds.register(bus);
        ModParticles.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        Races.bootstrap();
        Techniques.bootstrap();
        Transformations.bootstrap();
        TrainingChallenges.bootstrap();
        Network.register();
        LOGGER.info("DBIL 0.3.0: visual and combat overhaul initialized");
    }
    public static ResourceLocation id(String path) { return new ResourceLocation(MOD_ID, path); }
}
