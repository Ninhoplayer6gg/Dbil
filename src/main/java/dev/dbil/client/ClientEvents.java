package dev.dbil.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dbil.DBIL;
import dev.dbil.client.anim.AnimState;
import dev.dbil.client.anim.CharacterAnimator;
import dev.dbil.client.fx.LoopingSounds;
import dev.dbil.client.fx.WorldEffectsRenderer;
import dev.dbil.client.render.character.CharacterPreview;
import dev.dbil.client.render.character.CharacterTextures;
import dev.dbil.config.ClientConfig;
import dev.dbil.fx.FxType;
import dev.dbil.gui.CharacterCreationScreen;
import dev.dbil.gui.DBILHud;
import dev.dbil.gui.DBILMenuScreen;
import dev.dbil.gui.HudState;
import dev.dbil.network.Network;
import dev.dbil.server.Action;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping CHARGE = key("charge", GLFW.GLFW_KEY_R);
    public static final KeyMapping FLIGHT = key("flight", GLFW.GLFW_KEY_G);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_X);
    public static final KeyMapping TECHNIQUE = key("technique", GLFW.GLFW_KEY_C);
    public static final KeyMapping TARGET = key("target", GLFW.GLFW_KEY_V);
    public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_J);
    public static final KeyMapping VANISH = key("vanish", GLFW.GLFW_KEY_Z);
    public static final KeyMapping TARGET_NEXT = key("target_next", GLFW.GLFW_KEY_B);
    public static final KeyMapping TECHNIQUE_NEXT = key("technique_next", GLFW.GLFW_KEY_N);
    private static final double LOCKED_MELEE_RANGE = 6.0;

    private static boolean warnedSchema;
    private static boolean techniqueHeld, attackHeldLastTick;
    private static int lightCount;
    private static long lastLightTick, lastMeleeSent = -100, predictedSwingTick = -100;
    private ClientEvents() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.dbil." + name, InputConstants.Type.KEYSYM, code, "key.categories.dbil");
    }

    public static KeyMapping[] keys() {
        return new KeyMapping[] {CHARGE, FLIGHT, DASH, TECHNIQUE, TARGET, MENU, VANISH, TARGET_NEXT, TECHNIQUE_NEXT};
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
        if (minecraft.screen instanceof CharacterCreationScreen screen && !screen.editing()) minecraft.setScreen(null);
        if (!minecraft.player.isAlive()) { ClientControls.reset(); TargetCamera.reset(); techniqueHeld = false; return; }
        while (MENU.consumeClick()) minecraft.setScreen(minecraft.screen instanceof DBILMenuScreen ? null : new DBILMenuScreen());
        while (FLIGHT.consumeClick()) Network.sendAction(Action.FLIGHT_TOGGLE);
        while (DASH.consumeClick()) Network.sendAction(dashDirection(minecraft));
        while (TARGET.consumeClick()) Network.sendAction(Action.LOCK_ON);
        while (TARGET_NEXT.consumeClick()) Network.sendAction(Action.TARGET_NEXT);
        while (VANISH.consumeClick()) Network.sendAction(Action.VANISH);
        while (TECHNIQUE_NEXT.consumeClick()) cycleTechnique();
        while (TECHNIQUE.consumeClick()) { /* edge detection below handles press/release */ }
        boolean techniqueDown = minecraft.screen == null && TECHNIQUE.isDown();
        if (techniqueDown != techniqueHeld) {
            Network.sendAction(techniqueDown ? Action.TECHNIQUE_HOLD : Action.TECHNIQUE_RELEASE);
            techniqueHeld = techniqueDown;
        }
        ClientControls.tick(minecraft);
        attackHeldLastTick = minecraft.options.keyAttack.isDown();
        long tick = minecraft.level.getGameTime();
        if (tick - lastLightTick > 24) lightCount = 0;
        WorldEffectsRenderer.tickParticles(minecraft);
        dev.dbil.client.fx.CameraEffects.tickLocal(ClientState.visual(minecraft.player.getId()), tick);
        LoopingSounds.tick(minecraft);
        if (tick % 100 == 0) {
            ClientState.prune();
            CharacterAnimator.prune(tick);
            CharacterTextures.prune();
        }
    }

    private static Action dashDirection(Minecraft minecraft) {
        if (minecraft.options.keyLeft.isDown() || ClientControls.left) return Action.DASH_LEFT;
        if (minecraft.options.keyRight.isDown() || ClientControls.right) return Action.DASH_RIGHT;
        if (minecraft.options.keyDown.isDown() || ClientControls.backward) return Action.DASH_BACK;
        return Action.DASH;
    }

    public static void cycleTechnique() {
        var data = ClientState.data();
        List<ResourceLocation> equipped = new ArrayList<>(data.equippedTechniques());
        if (equipped.size() < 2) return;
        int index = equipped.indexOf(data.selectedTechnique());
        Network.sendSelectTechnique(equipped.get((index + 1) % equipped.size()));
    }

    /**
     * Empty-hand attacks become DBIL melee when they target a living entity, the air, or a locked opponent nearby.
     * Breaking blocks with an empty hand keeps working whenever no opponent is locked close by.
     */
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientState.received() || !ClientState.data().created()
                || !minecraft.player.getMainHandItem().isEmpty() || minecraft.player.isSpectator()) return;
        HitResult hit = minecraft.hitResult;
        int targetId = ClientState.visual(minecraft.player.getId()).targetId();
        boolean lockedNear = targetId >= 0 && minecraft.level.getEntity(targetId) instanceof LivingEntity target
                && target.isAlive() && target.distanceTo(minecraft.player) <= LOCKED_MELEE_RANGE;
        boolean living = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity;
        boolean air = hit == null || hit.getType() == HitResult.Type.MISS;
        boolean entityOther = hit != null && hit.getType() == HitResult.Type.ENTITY && !living;
        if (entityOther && !lockedNear) return;
        if (!lockedNear && !living && !air) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long tick = minecraft.level.getGameTime();
        boolean fresh = !attackHeldLastTick || tick - lastMeleeSent > 6;
        if (!fresh || tick - lastMeleeSent < 3) return;
        lastMeleeSent = tick;
        Action action = meleeAction(minecraft, tick);
        Network.sendAction(action);
        predict(minecraft, action, tick);
    }

    private static Action meleeAction(Minecraft minecraft, long tick) {
        boolean heavy = minecraft.options.keyShift.isDown();
        boolean jump = minecraft.options.keyJump.isDown();
        if (jump && !heavy) return Action.LAUNCHER;
        if (heavy) return lightCount >= 2 && tick - lastLightTick <= 24 ? Action.SMASH : Action.HEAVY;
        return Action.LIGHT;
    }

    /** Immediate local animation so the strike feels responsive; the server echo is then skipped. */
    public static void predict(Minecraft minecraft, Action action, long tick) {
        int variant = switch (action) {
            case HEAVY -> FxType.MELEE_HEAVY;
            case LAUNCHER -> FxType.MELEE_LAUNCHER;
            case SMASH -> FxType.MELEE_SMASH;
            default -> Math.min(3, lightCount);
        };
        if (action == Action.LIGHT) {
            lightCount = tick - lastLightTick <= 24 ? (lightCount + 1) % 4 : 1;
            lastLightTick = tick;
        } else {
            lightCount = 0;
        }
        int length = variant == FxType.MELEE_KICK ? 9 : variant == FxType.MELEE_FINISHER ? 12 : variant >= FxType.MELEE_HEAVY ? 14 : 7;
        CharacterAnimator.trigger(minecraft.player, AnimState.Action.MELEE, variant, 0, length);
        HudState.localSwing(variant);
        predictedSwingTick = tick;
    }

    public static boolean recentlyPredicted(long tick) { return tick - predictedSwingTick <= 4; }

    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientConfig.hud.get() && ClientConfig.hideVanillaHealth.get() && minecraft.player != null
                && ClientState.received() && ClientState.data().created()) event.setCanceled(true);
    }

    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TargetCamera.render(Minecraft.getInstance(), event.renderTickTime);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) event.getPlayer().setNoGravity(false);
        ClientState.reset();
        warnedSchema = false;
        techniqueHeld = false;
        TargetCamera.reset();
        CharacterAnimator.clear();
        CharacterTextures.clear();
        CharacterPreview.clear();
        LoopingSounds.reset();
        HudState.reset();
        DBILHud.reset();
    }
}
