package dev.dbil.client.fx;

import dev.dbil.DBIL;
import dev.dbil.client.ClientFlightController;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Moderate, configurable camera reactions: trauma-based shake and FOV pulses. No post-processing shaders. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class CameraEffects {
    private static float trauma;
    private static float fovKick;
    private static float fastBlend;
    private static long lastNanos;

    private CameraEffects() {}

    /** @param amount 0..1, accumulated and capped */
    public static void shake(float amount) {
        if (!ClientConfig.SPEC.isLoaded() || ClientConfig.screenShake.get() <= 0) return;
        trauma = Math.min(1, trauma + amount);
    }

    public static void special(float shake, float fov) {
        if (ClientConfig.SPEC.isLoaded() && !ClientConfig.specialCamera.get()) return;
        shake(shake);
        fovKick = Math.max(fovKick, fov);
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Math.min(0.1F, (now - lastNanos) / 1.0e9F);
        lastNanos = now;
        trauma = Math.max(0, trauma - dt * 1.6F);
        fovKick = Math.max(0, fovKick - dt * 18F);
        if (trauma <= 0 || !ClientConfig.SPEC.isLoaded()) return;
        float strength = trauma * trauma * ClientConfig.screenShake.get().floatValue();
        float time = now / 1.0e9F;
        event.setYaw(event.getYaw() + 3.2F * strength * Mth.sin(time * 37.1F));
        event.setPitch(event.getPitch() + 2.6F * strength * Mth.sin(time * 41.7F + 1.3F));
        event.setRoll(event.getRoll() + 2.4F * strength * Mth.sin(time * 29.3F + 2.1F));
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || !ClientConfig.SPEC.isLoaded() || !ClientConfig.fovEffects.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        boolean fast = ClientFlightController.active() && ClientState.visual(minecraft.player.getId()).fastFlight()
                && ClientFlightController.velocity().length() > ClientFlightController.cruiseSpeed() * 1.05;
        fastBlend += ((fast ? 1 : 0) - fastBlend) * 0.08F;
        double intensity = ClientConfig.fovIntensity.get();
        double extra = (fastBlend * 14 + fovKick) * intensity;
        if (extra > 0.01) event.setFOV(event.getFOV() + extra);
    }
}
