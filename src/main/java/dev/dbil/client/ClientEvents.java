package dev.dbil.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dbil.DBIL;
import dev.dbil.animation.PlayerPresentation;
import dev.dbil.gui.CharacterCreationScreen;
import dev.dbil.gui.DBILMenuScreen;
import dev.dbil.network.Network;
import dev.dbil.server.Action;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping CHARGE = key("charge", GLFW.GLFW_KEY_R);
    public static final KeyMapping FLIGHT = key("flight", GLFW.GLFW_KEY_G);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_X);
    public static final KeyMapping TECHNIQUE = key("technique", GLFW.GLFW_KEY_C);
    public static final KeyMapping TARGET = key("target", GLFW.GLFW_KEY_V);
    public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_J);

    private static boolean warnedSchema;
    private ClientEvents() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.dbil." + name, InputConstants.Type.KEYSYM, code, "key.categories.dbil");
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientState.received()) return;
        if (!ClientState.data().compatibleSchema()) {
            if (!warnedSchema) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.dbil.schema_protected"), false);
                warnedSchema = true;
            }
            while (MENU.consumeClick()) minecraft.setScreen(new DBILMenuScreen());
            return;
        }
        if (!ClientState.data().created()) {
            if (minecraft.player.isAlive() && (minecraft.screen == null || minecraft.screen instanceof DBILMenuScreen)) {
                minecraft.setScreen(new CharacterCreationScreen());
            }
            return;
        }
        if (minecraft.screen instanceof CharacterCreationScreen) minecraft.setScreen(null);
        if (!minecraft.player.isAlive()) { ClientControls.reset(); TargetCamera.reset(); return; }
        while (MENU.consumeClick()) minecraft.setScreen(minecraft.screen instanceof DBILMenuScreen ? null : new DBILMenuScreen());
        while (FLIGHT.consumeClick()) Network.sendAction(Action.FLIGHT_TOGGLE);
        while (DASH.consumeClick()) Network.sendAction(Action.DASH);
        while (TECHNIQUE.consumeClick()) Network.sendAction(Action.TECHNIQUE);
        while (TARGET.consumeClick()) Network.sendAction(Action.LOCK_ON);
        ClientControls.tick(minecraft);
        PlayerPresentation.tick(minecraft);
        if (minecraft.level.getGameTime() % 100 == 0) ClientState.prune();
    }

    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TargetCamera.render(Minecraft.getInstance(), event.renderTickTime);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) event.getPlayer().setNoGravity(false);
        ClientState.reset();
        warnedSchema = false;
        TargetCamera.reset();
    }
}
