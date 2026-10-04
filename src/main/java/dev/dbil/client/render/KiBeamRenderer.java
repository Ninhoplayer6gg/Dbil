package dev.dbil.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dbil.DBIL;
import dev.dbil.client.fx.FxParticles;
import dev.dbil.client.fx.WorldEffectsRenderer;
import dev.dbil.registry.ModParticles;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.Techniques;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Beam: origin glow glued to the caster's hands (interpolated owner position), a white core tube, a colored
 * outer tube with scrolling energy, traveling rings, a bulbous head and sparks at the impact point.
 */
public final class KiBeamRenderer extends EntityRenderer<KiBeamEntity> {
    private static final ResourceLocation BEAM = DBIL.id("textures/entity/effect/beam.png");
    private static final int SIDES = 6;

    public KiBeamRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public boolean shouldRender(KiBeamEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public void render(KiBeamEntity beam, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        TechniqueProfile profile = Techniques.profile(beam.techniqueId());
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float beamYaw = Mth.rotLerp(partialTick, beam.clientPrevYaw, beam.clientYaw);
        float beamPitch = Mth.lerp(partialTick, beam.clientPrevPitch, beam.clientPitch);
        float length = Mth.lerp(partialTick, beam.clientPrevLength, beam.clientLength);
        Vec3 direction = KiBeamEntity.direction(beamYaw, beamPitch);
        Vec3 renderPos = WorldEffectsRenderer.lerp(beam, partialTick);
        Entity owner = beam.level().getEntity(beam.ownerId());
        // Interpolated owner eye position keeps the beam glued to the hands even for fast-moving casters.
        Vec3 origin = owner != null ? WorldEffectsRenderer.lerp(owner, partialTick).add(0, owner.getEyeHeight() - 0.38, 0)
                .add(direction.scale(0.75)) : renderPos;
        Vec3 start = origin.subtract(renderPos);
        Vec3 end = start.add(direction.scale(Math.max(0.05, length)));
        float fade = beam.phase() == KiBeamEntity.PHASE_FADING ? 0.45F : 1.0F;
        float time = beam.clientAge + partialTick;
        float width = (float) beam.beamWidth() * fade * (0.95F + 0.05F * Mth.sin(time * 1.9F));
        float[] outer = WorldEffectsRenderer.rgb(profile.color());
        float[] core = WorldEffectsRenderer.rgb(profile.coreColor());
        float grow = Math.min(1, beam.clientAge / 4F);
        VertexConsumer tube = buffers.getBuffer(RenderType.eyes(BEAM));
        tube(pose, tube, start, end, width * 0.5F * grow, outer, 0.85F * fade, time * 0.35F, length);
        tube(pose, tube, start, end, width * 0.24F * grow, core, 1.0F * fade, time * 0.6F, length);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.GLOW));
        WorldEffectsRenderer.billboard(pose, glow, camera, start, width * 2.4F, outer, 0.8F * fade, 0);
        WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.CORE)), camera, start, width * 1.1F, core, fade, 0);
        WorldEffectsRenderer.billboard(pose, glow, camera, end, width * 3.4F * (0.9F + 0.1F * Mth.sin(time * 2.3F)), outer, 0.9F * fade, 0);
        WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.CORE)), camera, end, width * 1.8F, core, fade, 0);
        // Rings travel from the hands to the head so the energy visibly moves.
        VertexConsumer ring = buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.RING));
        int rings = Math.max(2, Math.min(8, Mth.ceil(length / 4)));
        for (int i = 0; i < rings; i++) {
            float t = ((time * 0.08F + i / (float) rings) % 1F);
            Vec3 at = start.lerp(end, t);
            WorldEffectsRenderer.billboard(pose, ring, camera, at, width * 1.6F, outer, 0.45F * fade * Mth.sin(t * Mth.PI), time * 0.2F + i);
        }
        Vec3 headWorld = renderPos.add(end);
        if (beam.clientAge % 2 == 0) {
            FxParticles.burst(headWorld, profile.color(), 2, 0.12F, 0.25F);
            Vec3 along = renderPos.add(start.lerp(end, beam.level().random.nextFloat()));
            if (FxParticles.count(1, along) > 0) {
                FxParticles.spawn(ModParticles.KI_SPARK.get(), along.x, along.y, along.z, profile.coreColor(), 0.1F, 0.1F);
            }
        }
        super.render(beam, yaw, partialTick, pose, buffers, light);
    }

    /** Hexagonal tube with UVs scrolling along the beam. Both windings are emitted for the culled eyes type. */
    private static void tube(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float radius, float[] rgb, float brightness,
                             float scroll, float length) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0e-6) return;
        Vec3 dir = axis.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0).cross(dir).normalize() : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = rgb[0] * brightness, g = rgb[1] * brightness, bl = rgb[2] * brightness;
        float u0 = -scroll, u1 = -scroll + Math.max(1, length / 3F);
        for (int i = 0; i < SIDES; i++) {
            double a0 = i * Math.PI * 2 / SIDES, a1 = (i + 1) * Math.PI * 2 / SIDES;
            Vec3 o0 = side.scale(Math.cos(a0) * radius).add(up.scale(Math.sin(a0) * radius));
            Vec3 o1 = side.scale(Math.cos(a1) * radius).add(up.scale(Math.sin(a1) * radius));
            Vec3 p0 = a.add(o0), p1 = a.add(o1), p2 = b.add(o1), p3 = b.add(o0);
            float v0 = i / (float) SIDES, v1 = (i + 1) / (float) SIDES;
            vertex(vc, m, n, p0, u0, v0, r, g, bl);
            vertex(vc, m, n, p1, u0, v1, r, g, bl);
            vertex(vc, m, n, p2, u1, v1, r, g, bl);
            vertex(vc, m, n, p3, u1, v0, r, g, bl);
            vertex(vc, m, n, p3, u1, v0, r, g, bl);
            vertex(vc, m, n, p2, u1, v1, r, g, bl);
            vertex(vc, m, n, p1, u0, v1, r, g, bl);
            vertex(vc, m, n, p0, u0, v0, r, g, bl);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, float r, float g, float b) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KiBeamEntity entity) { return BEAM; }
}
