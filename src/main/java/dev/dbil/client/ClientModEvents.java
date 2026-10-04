package dev.dbil.client;

import dev.dbil.DBIL;
import dev.dbil.client.fx.DBILParticle;
import dev.dbil.client.render.KiBeamRenderer;
import dev.dbil.client.render.KiWaveRenderer;
import dev.dbil.client.render.TrainingEnemyRenderer;
import dev.dbil.client.render.character.CharacterRenderers;
import dev.dbil.gui.DBILHud;
import dev.dbil.registry.ModEntities;
import dev.dbil.registry.ModParticles;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ClientEvents.keys()) event.register(key);
    }
    @SubscribeEvent public static void hud(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("status", DBILHud::render);
    }
    /** Fired on every resource reload: rebuilds the DBIL character renderers with fresh model parts. */
    @SubscribeEvent public static void playerLayers(EntityRenderersEvent.AddLayers event) {
        CharacterRenderers.create(event.getContext());
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRAINING_ENEMY.get(), TrainingEnemyRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_WAVE.get(), KiWaveRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_BEAM.get(), KiBeamRenderer::new);
    }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.KI_SPARK.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.SPARK));
        event.registerSpriteSet(ModParticles.AURA_MOTE.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.MOTE));
        event.registerSpriteSet(ModParticles.KI_TRAIL.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.TRAIL));
        event.registerSpriteSet(ModParticles.IMPACT_RING.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.RING));
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.SHOCKWAVE));
        event.registerSpriteSet(ModParticles.DUST_CLOUD.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.DUST));
        event.registerSpriteSet(ModParticles.FLASH.get(), sprites -> new DBILParticle.Provider(sprites, DBILParticle.Kind.FLASH));
    }
}
