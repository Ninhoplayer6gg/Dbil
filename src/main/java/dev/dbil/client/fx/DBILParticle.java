package dev.dbil.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * DBIL energy particles. Spawn arguments are reinterpreted: xd = packed RGB, yd = size, zd = kind-specific value
 * (speed for sparks, rise for motes, final scale for rings). Energy kinds are full-bright; dust uses world light.
 */
public final class DBILParticle extends TextureSheetParticle {
    public enum Kind { SPARK, MOTE, TRAIL, RING, SHOCKWAVE, DUST, FLASH }

    private final Kind kind;
    private final float baseSize;
    private final float extra;

    DBILParticle(ClientLevel level, double x, double y, double z, int color, float size, float extra, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.baseSize = Math.max(0.02F, size);
        this.extra = extra;
        pickSprite(sprites);
        rCol = ((color >> 16) & 0xFF) / 255F;
        gCol = ((color >> 8) & 0xFF) / 255F;
        bCol = (color & 0xFF) / 255F;
        hasPhysics = false;
        gravity = 0;
        quadSize = baseSize;
        switch (kind) {
            case SPARK -> {
                lifetime = 6 + random.nextInt(8);
                double speed = extra <= 0 ? 0.15 : extra;
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.8 + 0.2, random.nextGaussian()).normalize().scale(speed);
                xd = dir.x; yd = dir.y; zd = dir.z;
                friction = 0.82F;
            }
            case MOTE -> {
                lifetime = 12 + random.nextInt(12);
                xd = (random.nextDouble() - 0.5) * 0.02;
                yd = extra <= 0 ? 0.05 : extra;
                zd = (random.nextDouble() - 0.5) * 0.02;
                friction = 0.96F;
            }
            case TRAIL -> { lifetime = 5 + random.nextInt(4); xd = yd = zd = 0; }
            case RING -> { lifetime = 7; xd = yd = zd = 0; }
            case SHOCKWAVE -> { lifetime = 12; xd = yd = zd = 0; }
            case DUST -> {
                lifetime = 20 + random.nextInt(20);
                xd = (random.nextDouble() - 0.5) * (extra <= 0 ? 0.12 : extra);
                yd = 0.02 + random.nextDouble() * 0.05;
                zd = (random.nextDouble() - 0.5) * (extra <= 0 ? 0.12 : extra);
                friction = 0.9F;
                alpha = 0.75F;
            }
            case FLASH -> { lifetime = 4; xd = yd = zd = 0; }
        }
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) Math.max(1, lifetime);
        switch (kind) {
            case SPARK -> { quadSize = baseSize * (1 - t); alpha = 1 - t * 0.5F; }
            case MOTE -> { quadSize = baseSize * (1 - t * 0.6F); alpha = Mth.sin(Mth.PI * Math.min(1, t * 1.2F)); }
            case TRAIL -> { quadSize = baseSize * (1 - t * 0.7F); alpha = 1 - t; }
            case RING -> { quadSize = baseSize + (extra <= 0 ? baseSize * 3 : extra - baseSize) * easeOut(t); alpha = 1 - t; }
            case SHOCKWAVE -> { quadSize = baseSize + (extra <= 0 ? baseSize * 5 : extra - baseSize) * easeOut(t); alpha = (1 - t) * 0.9F; }
            case DUST -> { quadSize = baseSize * (1 + t * 1.5F); alpha = 0.75F * (1 - t); }
            case FLASH -> { quadSize = baseSize * (1 + t * 0.6F); alpha = 1 - t; }
        }
    }

    private static float easeOut(float t) { return 1 - (1 - t) * (1 - t); }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (kind != Kind.SHOCKWAVE) {
            super.render(buffer, camera, partialTick);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, xo, x) - cam.x());
        float py = (float) (Mth.lerp(partialTick, yo, y) - cam.y());
        float pz = (float) (Mth.lerp(partialTick, zo, z) - cam.z());
        float s = getQuadSize(partialTick);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTick);
        vertex(buffer, px - s, py, pz - s, u1, v1, light);
        vertex(buffer, px - s, py, pz + s, u1, v0, light);
        vertex(buffer, px + s, py, pz + s, u0, v0, light);
        vertex(buffer, px + s, py, pz - s, u0, v1, light);
        vertex(buffer, px + s, py, pz - s, u0, v1, light);
        vertex(buffer, px + s, py, pz + s, u0, v0, light);
        vertex(buffer, px - s, py, pz + s, u1, v0, light);
        vertex(buffer, px - s, py, pz - s, u1, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.DUST ? super.getLightColor(partialTick) : 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new DBILParticle(level, x, y, z, (int) xd, (float) yd, (float) zd, sprites, kind);
        }
    }
}
