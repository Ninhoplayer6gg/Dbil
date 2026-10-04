package dev.dbil.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dbil.DBIL;
import dev.dbil.client.ClientState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Forty visible triangles form a golden spiked silhouette; no skin replacement or animation library. */
public final class SaiyanHairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation SUPER_SAIYAN = DBIL.id("super_saiyan");
    private static final ResourceLocation TEXTURE = DBIL.id("textures/entity/super_saiyan_hair.png");
    private static final Vertex[] MESH = createMesh();

    public SaiyanHairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                 float limbSwing, float limbAmount, float partialTick, float age,
                                 float headYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator() || !SUPER_SAIYAN.equals(ClientState.visual(player.getId()).transformation())) return;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        pose.scale(1.0F / 16, 1.0F / 16, 1.0F / 16);
        var transform = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (Vertex vertex : MESH) {
            consumer.vertex(transform.pose(), vertex.x, vertex.y, vertex.z).color(255, 255, 255, 255)
                    .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(transform.normal(), vertex.nx, vertex.ny, vertex.nz).endVertex();
        }
        pose.popPose();
    }

    private static Vertex[] createMesh() {
        List<Vertex> vertices = new ArrayList<>(136);
        cuboid(vertices, -4.3F, -8.7F, -4.3F, 4.3F, -6.6F, 4.3F);
        spike(vertices, 0, -8.7F, 0, 3.0F, 3.0F, 0, -15.8F, -0.3F);
        spike(vertices, 0, -8.5F, -2.8F, 3.4F, 2.2F, 0, -14.2F, -4.8F);
        spike(vertices, -2.8F, -8.1F, -2.4F, 2.5F, 2.5F, -5.3F, -12.8F, -4.0F);
        spike(vertices, 2.8F, -8.1F, -2.4F, 2.5F, 2.5F, 5.3F, -12.8F, -4.0F);
        spike(vertices, -2.8F, -8.2F, 1.8F, 2.5F, 2.8F, -5.5F, -13.0F, 3.5F);
        spike(vertices, 2.8F, -8.2F, 1.8F, 2.5F, 2.8F, 5.5F, -13.0F, 3.5F);
        spike(vertices, 0, -8.4F, 2.8F, 3.0F, 2.5F, 0, -12.8F, 5.4F);
        return vertices.toArray(Vertex[]::new);
    }

    private static void spike(List<Vertex> out, float x, float y, float z, float width, float depth,
                              float tipX, float tipY, float tipZ) {
        float[][] base = {{x - width / 2, y, z - depth / 2}, {x + width / 2, y, z - depth / 2},
                {x + width / 2, y, z + depth / 2}, {x - width / 2, y, z + depth / 2}};
        float[] tip = {tipX, tipY, tipZ};
        for (int i = 0; i < 4; i++) face(out, base[i], base[(i + 1) % 4], tip, tip);
    }

    private static void cuboid(List<Vertex> out, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[][] v = {{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},
                {x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}};
        int[][] faces = {{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};
        for (int[] face : faces) face(out, v[face[0]], v[face[1]], v[face[2]], v[face[3]]);
    }

    private static void face(List<Vertex> out, float[] a, float[] b, float[] c, float[] d) {
        float ux=b[0]-a[0], uy=b[1]-a[1], uz=b[2]-a[2];
        float vx=c[0]-a[0], vy=c[1]-a[1], vz=c[2]-a[2];
        float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        nx/=length; ny/=length; nz/=length;
        for (float[] position : new float[][] {a,b,c,d}) out.add(new Vertex(position[0],position[1],position[2],nx,ny,nz));
    }
    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {}
}
