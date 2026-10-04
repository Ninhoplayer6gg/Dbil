package dev.dbil.client.render.character;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dbil.DBIL;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.race.Races;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Renders DBIL geometry layers: voxel hair (with SSJ glow pass), outfit pieces and the Saiyan tail. */
public final class CharacterLayer<T extends LivingEntity, M extends PlayerModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation HAIR = DBIL.id("textures/entity/character/hair.png");
    public static final ResourceLocation PARTS = DBIL.id("textures/entity/character/outfit_parts.png");
    private static final int FUR = 0x6A4528;
    private final Function<T, CharacterLook> look;

    public CharacterLayer(RenderLayerParent<T, M> parent, Function<T, CharacterLook> look) {
        super(parent);
        this.look = look;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (entity.isInvisible()) return;
        CharacterLook current = look.apply(entity);
        if (current == null) return;
        M model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        boolean extras = !ClientConfig.SPEC.isLoaded() || ClientConfig.animationExtras.get();
        ClientState.VisualState visual = ClientState.visual(entity.getId());
        float wind = visual.charging() || visual.transforming() ? 1.0F : visual.fastFlight() ? 0.7F : visual.flying() ? 0.3F : 0.08F;
        CharacterAppearance appearance = current.appearance();
        if (entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && model.head.visible) {
            List<PartBuilder.Piece> hair = HairModels.get(appearance.hairstyle(), current.hairVariant());
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(HAIR));
            float r = red(current.hairColor()), g = green(current.hairColor()), b = blue(current.hairColor());
            renderPieces(pose, model.head, hair, consumer, light, overlay, r, g, b, extras ? wind : 0, age);
            if (current.glow() > 0.01F) {
                VertexConsumer glow = buffers.getBuffer(RenderType.eyes(HAIR));
                float intensity = 0.22F * current.glow() * (0.85F + 0.15F * Mth.sin(age * 0.6F));
                renderPieces(pose, model.head, hair, glow, light, overlay, r * intensity, g * intensity, b * intensity * 0.6F,
                        extras ? wind : 0, age);
            }
        }
        Map<OutfitModels.Attach, List<PartBuilder.Piece>> outfit = OutfitModels.get(appearance);
        VertexConsumer parts = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        for (Map.Entry<OutfitModels.Attach, List<PartBuilder.Piece>> entry : outfit.entrySet()) {
            ModelPart parent = part(model, entry.getKey());
            if (!parent.visible) continue;
            for (PartBuilder.Piece piece : entry.getValue()) {
                int color = slotColor(appearance, piece.slot());
                pose.pushPose();
                parent.translateAndRotate(pose);
                piece.part().render(pose, parts, light, overlay, red(color), green(color), blue(color), 1);
                pose.popPose();
            }
        }
        if (appearance.has(AppearanceOptions.ACCESSORY_TAIL) && Races.SAIYAN.equals(current.race()) && model.body.visible) {
            renderTail(pose, buffers, light, overlay, model, entity, age, visual);
        }
    }

    private static void renderPieces(PoseStack pose, ModelPart head, List<PartBuilder.Piece> pieces, VertexConsumer consumer,
                                     int light, int overlay, float r, float g, float b, float wind, float age) {
        for (int i = 0; i < pieces.size(); i++) {
            PartBuilder.Piece piece = pieces.get(i);
            ModelPart part = piece.part();
            float dx = 0, dz = 0;
            if (piece.sway() && wind > 0) {
                dx = Mth.sin(age * 0.45F + i * 1.7F) * 0.05F * wind;
                dz = Mth.cos(age * 0.38F + i * 2.3F) * 0.05F * wind;
                part.xRot += dx;
                part.zRot += dz;
            }
            pose.pushPose();
            head.translateAndRotate(pose);
            part.render(pose, consumer, light, overlay, r, g, b, 1);
            pose.popPose();
            if (dx != 0 || dz != 0) {
                part.xRot -= dx;
                part.zRot -= dz;
            }
        }
    }

    private void renderTail(PoseStack pose, MultiBufferSource buffers, int light, int overlay, M model, T entity, float age,
                            ClientState.VisualState visual) {
        ModelPart tail = OutfitModels.tail();
        float speed = visual.fastFlight() ? 0.2F : 1.0F;
        float droop = visual.flying() ? (visual.fastFlight() ? -0.15F : 0.35F) : 0.75F;
        tail.xRot = droop;
        tail.yRot = Mth.sin(age * 0.12F) * 0.35F * speed;
        ModelPart segment = tail;
        int depth = 0;
        while (segment.hasChild("s" + (depth + 1))) {
            segment = segment.getChild("s" + (depth + 1));
            depth++;
            segment.xRot = (visual.fastFlight() ? 0.02F : -0.32F) + Mth.sin(age * 0.16F + depth) * 0.06F;
            segment.yRot = Mth.sin(age * 0.12F - depth * 0.7F) * 0.32F * speed;
        }
        pose.pushPose();
        model.body.translateAndRotate(pose);
        tail.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAIR)), light, overlay,
                red(FUR), green(FUR), blue(FUR), 1);
        pose.popPose();
    }

    public static ModelPart part(PlayerModel<?> model, OutfitModels.Attach attach) {
        return switch (attach) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    public static int slotColor(CharacterAppearance appearance, int slot) {
        return switch (slot) {
            case OutfitModels.PRIMARY -> appearance.outfitPrimary();
            case OutfitModels.SECONDARY -> appearance.outfitSecondary();
            case OutfitModels.ACCENT -> appearance.outfitAccent();
            case OutfitModels.FUR -> FUR;
            default -> appearance.hairColor();
        };
    }

    static float red(int rgb) { return ((rgb >> 16) & 0xFF) / 255F; }
    static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255F; }
    static float blue(int rgb) { return (rgb & 0xFF) / 255F; }
}
