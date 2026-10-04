package dev.dbil.client;

import dev.dbil.config.ClientConfig;
import dev.dbil.gui.DBILMenuScreen;
import dev.dbil.targeting.TargetingService;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Render-frame presentation only: target selection and action validation remain on the server. */
public final class TargetCamera {
    private static final double FOLLOW_RATE = 12.0;
    private static long previousFrame;
    private TargetCamera() {}

    public static void render(Minecraft minecraft, float partialTick) {
        long now = System.nanoTime();
        double elapsed = previousFrame == 0 ? 1.0 / 60.0 : Math.min(0.1, Math.max(0, (now - previousFrame) * 1.0e-9));
        previousFrame = now;
        boolean actionsMenu = minecraft.screen instanceof DBILMenuScreen menu && menu.isActionsTab();
        if (!ClientConfig.lockOnCamera.get() || minecraft.player == null || minecraft.level == null
                || !ClientState.received() || !ClientState.data().created() || minecraft.isPaused()
                || !minecraft.player.isAlive() || minecraft.player.isPassenger()
                || minecraft.getCameraEntity() != minecraft.player
                || (minecraft.screen != null && !actionsMenu)) return;
        int targetId = ClientState.visual(minecraft.player.getId()).targetId();
        if (!(minecraft.level.getEntity(targetId) instanceof LivingEntity target)
                || !target.isAlive() || target.distanceToSqr(minecraft.player) > TargetingService.LOCK_RANGE * TargetingService.LOCK_RANGE) return;
        float t = Mth.clamp(partialTick, 0, 1);
        double dx = Mth.lerp(t, target.xo, target.getX()) - Mth.lerp(t, minecraft.player.xo, minecraft.player.getX());
        double dz = Mth.lerp(t, target.zo, target.getZ()) - Mth.lerp(t, minecraft.player.zo, minecraft.player.getZ());
        double dy = Mth.lerp(t, target.yo, target.getY()) + target.getBbHeight() * 0.65
                - Mth.lerp(t, minecraft.player.yo, minecraft.player.getY()) - minecraft.player.getEyeHeight();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = horizontal < 0.01 ? minecraft.player.getYRot() : (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        float response = (float) (1 - Math.exp(-FOLLOW_RATE * elapsed));
        minecraft.player.setYRot(minecraft.player.getYRot() + Mth.wrapDegrees(yaw - minecraft.player.getYRot()) * response);
        minecraft.player.setXRot(Mth.clamp(Mth.lerp(response, minecraft.player.getXRot(), pitch), -90, 90));
        // Frame smoothing already interpolates the view; avoid applying tick interpolation again.
        minecraft.player.yRotO = minecraft.player.getYRot();
        minecraft.player.xRotO = minecraft.player.getXRot();
    }
    public static void reset() { previousFrame = 0; }
}
