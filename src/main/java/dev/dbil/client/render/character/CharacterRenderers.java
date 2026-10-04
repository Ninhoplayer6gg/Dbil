package dev.dbil.client.render.character;

import dev.dbil.DBIL;
import dev.dbil.config.ClientConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Swaps the vanilla player renderer for DBIL characters, in third person, first person and GUIs. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class CharacterRenderers {
    private static DBILPlayerRenderer classic, slim;

    private CharacterRenderers() {}

    /** Called from EntityRenderersEvent.AddLayers, i.e. on every resource reload. */
    public static void create(EntityRendererProvider.Context context) {
        classic = new DBILPlayerRenderer(context, false);
        slim = new DBILPlayerRenderer(context, true);
        HairModels.clear();
        OutfitModels.clear();
    }

    private static boolean enabled() {
        return classic != null && (!ClientConfig.SPEC.isLoaded() || ClientConfig.dbilCharacterModel.get());
    }

    private static DBILPlayerRenderer rendererFor(AbstractClientPlayer player) {
        CharacterLook look = DBILPlayerRenderer.lookFor(player);
        if (look == null) return null;
        return look.appearance().slim() ? slim : classic;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void renderPlayer(RenderPlayerEvent.Pre event) {
        if (!enabled() || !(event.getEntity() instanceof AbstractClientPlayer player)) return;
        DBILPlayerRenderer renderer = rendererFor(player);
        if (renderer == null) return;
        float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
        if (renderer.renderCharacter(player, yaw, event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void renderArm(RenderArmEvent event) {
        if (!enabled()) return;
        AbstractClientPlayer player = event.getPlayer();
        DBILPlayerRenderer renderer = rendererFor(player);
        if (renderer != null && renderer.renderFirstPersonArm(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), player, event.getArm())) {
            event.setCanceled(true);
        }
    }
}
