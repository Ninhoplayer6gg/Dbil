package dev.dbil.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.dbil.DBIL;
import dev.dbil.technique.KiWaveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** One unlit billboard per projectile; no shader dependency, mesh or render-time particles. */
public final class KiWaveRenderer extends EntityRenderer<KiWaveEntity> {
    private static final ResourceLocation TEXTURE = DBIL.id("textures/entity/ki_wave.png");
    public KiWaveRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(KiWaveEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(0.85F, 0.85F, 0.85F);
        PoseStack.Pose current = pose.last();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        vertex(vertices, current.pose(), current.normal(), -0.5F, -0.5F, 0, 1);
        vertex(vertices, current.pose(), current.normal(), 0.5F, -0.5F, 1, 1);
        vertex(vertices, current.pose(), current.normal(), 0.5F, 0.5F, 1, 0);
        vertex(vertices, current.pose(), current.normal(), -0.5F, 0.5F, 0, 0);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Matrix3f normal,
                               float x, float y, float u, float v) {
        vertices.vertex(pose, x, y, 0).color(255, 255, 255, 240).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 1, 0).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(KiWaveEntity entity) { return TEXTURE; }
}
