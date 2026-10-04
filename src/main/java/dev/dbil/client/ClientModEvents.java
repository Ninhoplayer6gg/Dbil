package dev.dbil.client;

import dev.dbil.DBIL;
import dev.dbil.gui.DBILHud;
import dev.dbil.registry.ModEntities;
import dev.dbil.rendering.KiWaveRenderer;
import dev.dbil.rendering.SaiyanHairLayer;
import dev.dbil.rendering.TrainingEnemyRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(ClientEvents.CHARGE); event.register(ClientEvents.FLIGHT);
        event.register(ClientEvents.DASH); event.register(ClientEvents.TECHNIQUE);
        event.register(ClientEvents.TARGET); event.register(ClientEvents.MENU);
    }
    @SubscribeEvent public static void hud(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("status", DBILHud::render);
    }
    @SubscribeEvent public static void playerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new SaiyanHairLayer(playerRenderer));
            }
        }
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRAINING_ENEMY.get(), TrainingEnemyRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_WAVE.get(), KiWaveRenderer::new);
    }
}
