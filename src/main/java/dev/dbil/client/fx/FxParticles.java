package dev.dbil.client.fx;

import dev.dbil.config.ClientConfig;
import dev.dbil.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Budgeted particle helpers. Counts scale with the particle density option and distance to the camera. */
public final class FxParticles {
    private static final int FRAME_BUDGET = 220;
    private static long budgetTick;
    private static int used;

    private FxParticles() {}

    /** Scaled count for an effect at a position; 0 when particles are disabled or the effect is too far away. */
    public static int count(int base, Vec3 at) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return 0;
        double distance = minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        if (distance > ClientConfig.distance()) return 0;
        float falloff = distance < 16 ? 1 : (float) Math.max(0.3, 1 - (distance - 16) / ClientConfig.distance());
        long tick = minecraft.level.getGameTime();
        if (tick != budgetTick) { budgetTick = tick; used = 0; }
        int wanted = Math.round(base * ClientConfig.particleScale() * falloff);
        int allowed = Math.max(0, Math.min(wanted, FRAME_BUDGET - used));
        used += allowed;
        return allowed;
    }

    public static void spawn(SimpleParticleType type, double x, double y, double z, int color, float size, float extra) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) level.addParticle(type, true, x, y, z, color, size, extra);
    }

    public static void burst(Vec3 at, int color, int base, float size, float speed) {
        int n = count(base, at);
        for (int i = 0; i < n; i++) spawn(ModParticles.KI_SPARK.get(), at.x, at.y, at.z, color, size, speed);
    }

    public static void flash(Vec3 at, int color, float size) {
        if (count(1, at) > 0 || ClientConfig.particleScale() > 0) spawn(ModParticles.FLASH.get(), at.x, at.y, at.z, color, size, 0);
    }

    public static void ring(Vec3 at, int color, float size, float finalSize) {
        if (ClientConfig.particleScale() > 0) spawn(ModParticles.IMPACT_RING.get(), at.x, at.y, at.z, color, size, finalSize);
    }

    public static void shockwave(Vec3 at, int color, float size, float finalSize) {
        if (ClientConfig.particleScale() > 0) spawn(ModParticles.SHOCKWAVE.get(), at.x, at.y, at.z, color, size, finalSize);
    }

    public static void dust(Vec3 at, int base, float size, float spread) {
        if (ClientConfig.SPEC.isLoaded() && !ClientConfig.terrainDebris.get()) return;
        int n = count(base, at);
        RandomSource random = RandomSource.create();
        for (int i = 0; i < n; i++) {
            spawn(ModParticles.DUST_CLOUD.get(), at.x + (random.nextDouble() - 0.5) * spread, at.y + random.nextDouble() * 0.3,
                    at.z + (random.nextDouble() - 0.5) * spread, 0xB8AC98, size, spread * 0.12F);
        }
    }

    public static void line(Vec3 from, Vec3 to, int color, float size, int steps) {
        int n = Math.min(steps, count(steps, from));
        for (int i = 0; i < n; i++) {
            Vec3 p = from.lerp(to, i / (float) Math.max(1, n - 1));
            spawn(ModParticles.KI_TRAIL.get(), p.x, p.y, p.z, color, size, 0);
        }
    }
}
