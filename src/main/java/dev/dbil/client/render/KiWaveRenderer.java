package dev.dbil.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dbil.client.fx.FxParticles;
import dev.dbil.client.fx.WorldEffectsRenderer;
import dev.dbil.registry.ModParticles;
import dev.dbil.technique.KiWaveEntity;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.Techniques;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Energy shot: white-hot core, colored outer glow, pulsing ring and a fading trail of past positions. */
public final class KiWaveRenderer extends EntityRenderer<KiWaveEntity> {
    public KiWaveRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public boolean shouldRender(KiWaveEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 96 * 96;
    }

    @Override
    public void render(KiWaveEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        TechniqueProfile profile = Techniques.profile(entity.techniqueId());
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float radius = (float) entity.radius();
        float time = entity.clientAge + partialTick;
        float pulse = 0.9F + 0.1F * Mth.sin(time * 1.6F);
        float[] outer = WorldEffectsRenderer.rgb(profile.color());
        float[] core = WorldEffectsRenderer.rgb(profile.coreColor());
        // Renderer pose is at the entity's interpolated position; billboards use camera-relative offsets of zero.
        Vec3 center = new Vec3(0, entity.getBbHeight() * 0.5, 0);
        WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.GLOW)), camera, center,
                radius * 4.2F * pulse, outer, 0.75F, 0);
        WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.CORE)), camera, center,
                radius * 2.0F, core, 1.0F, 0);
        WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.RING)), camera, center,
                radius * 3.0F * pulse, outer, 0.45F, time * 0.3F);
        // Trail: billboards at previous positions, relative to the current interpolated position.
        Vec3 now = WorldEffectsRenderer.lerp(entity, partialTick);
        for (int i = 1; i < entity.trailCount; i++) {
            Vec3 past = entity.trail[i];
            if (past == null) break;
            float fade = 1 - i / (float) KiWaveEntity.TRAIL_LENGTH;
            Vec3 offset = past.subtract(now).add(center);
            WorldEffectsRenderer.billboard(pose, buffers.getBuffer(RenderType.eyes(WorldEffectsRenderer.GLOW)), camera, offset,
                    radius * 3.0F * fade, outer, 0.55F * fade, 0);
        }
        if (entity.clientAge % 2 == 0 && FxParticles.count(1, entity.position()) > 0) {
            FxParticles.spawn(ModParticles.KI_TRAIL.get(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    profile.color(), radius * 1.4F, 0);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KiWaveEntity entity) { return WorldEffectsRenderer.GLOW; }
}
