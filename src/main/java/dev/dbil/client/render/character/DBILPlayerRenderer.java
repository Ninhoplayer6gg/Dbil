package dev.dbil.client.render.character;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.dbil.client.ClientState;
import dev.dbil.client.anim.CharacterAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.Map;

/**
 * Renders DBIL characters with their own model and painted skin instead of the account skin. Vanilla layers
 * (armor, held items, elytra, arrows, heads, parrots, capes) are kept so normal Minecraft gameplay still reads
 * correctly. Installed through {@code RenderPlayerEvent.Pre}; players without a DBIL character stay vanilla.
 */
public final class DBILPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final DBILCharacterModel<AbstractClientPlayer> character;
    private CharacterLook current;

    public DBILPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, new DBILCharacterModel<>(slim), 0.5F);
        character = (DBILCharacterModel<AbstractClientPlayer>) model;
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new CharacterLayer<>(this, player -> current));
        addLayer(new PlayerItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new ArrowLayer<>(context, this));
        addLayer(new CapeLayer(this));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
        addLayer(new ParrotOnShoulderLayer<>(this, context.getModelSet()));
        addLayer(new SpinAttackEffectLayer<>(this, context.getModelSet()));
        addLayer(new BeeStingerLayer<>(this));
    }

    /** Resolves the look and renders. Returns false when the player has no DBIL appearance. */
    public boolean renderCharacter(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        CharacterLook look = lookFor(player);
        if (look == null) return false;
        current = look;
        setModelProperties(player);
        render(player, yaw, partialTick, pose, buffers, light);
        return true;
    }

    public static CharacterLook lookFor(AbstractClientPlayer player) {
        if (CharacterPreview.active(player)) {
            return CharacterLook.resolve(player, CharacterPreview.appearance(), CharacterPreview.race());
        }
        ClientState.AppearanceEntry entry = ClientState.appearance(player.getId());
        return entry == null ? null : CharacterLook.resolve(player, entry.appearance(), entry.race());
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        CharacterLook look = current != null ? current : lookFor(player);
        return look == null ? player.getSkinTextureLocation() : look.texture();
    }

    @Override
    protected void scale(AbstractClientPlayer player, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(AbstractClientPlayer player, PoseStack pose, float age, float bodyYaw, float partialTick) {
        float swim = player.getSwimAmount(partialTick);
        if (player.isFallFlying()) {
            super.setupRotations(player, pose, age, bodyYaw, partialTick);
            float ticks = player.getFallFlyingTicks() + partialTick;
            float amount = Mth.clamp(ticks * ticks / 100.0F, 0.0F, 1.0F);
            if (!player.isAutoSpinAttack()) pose.mulPose(Axis.XP.rotationDegrees(amount * (-90.0F - player.getXRot())));
            Vec3 view = player.getViewVector(partialTick);
            Vec3 motion = player.getDeltaMovementLerped(partialTick);
            double motionSq = motion.horizontalDistanceSqr(), viewSq = view.horizontalDistanceSqr();
            if (motionSq > 0.0D && viewSq > 0.0D) {
                double dot = (motion.x * view.x + motion.z * view.z) / Math.sqrt(motionSq * viewSq);
                double cross = motion.x * view.z - motion.z * view.x;
                pose.mulPose(Axis.YP.rotation((float) (Math.signum(cross) * Math.acos(Mth.clamp(dot, -1, 1)))));
            }
        } else if (swim > 0.0F) {
            super.setupRotations(player, pose, age, bodyYaw, partialTick);
            float target = player.isInWater() ? -90.0F - player.getXRot() : -90.0F;
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, target)));
            if (player.isVisuallySwimming()) pose.translate(0.0F, -1.0F, 0.3F);
        } else {
            super.setupRotations(player, pose, age, bodyYaw, partialTick);
            if (player.isPassenger() || player.isSleeping()) return;
            CharacterAnimator.Root root = CharacterAnimator.root(player, partialTick);
            if (root.y != 0) pose.translate(0, root.y, 0);
            if (root.pitch != 0 || root.roll != 0 || root.yaw != 0) {
                pose.translate(0, 0.9F, 0);
                if (root.yaw != 0) pose.mulPose(Axis.YP.rotationDegrees(root.yaw));
                if (root.pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(root.pitch));
                if (root.roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(root.roll));
                pose.translate(0, -0.9F, 0);
            }
        }
    }

    private void setModelProperties(AbstractClientPlayer player) {
        PlayerModel<AbstractClientPlayer> m = getModel();
        if (player.isSpectator()) {
            m.setAllVisible(false);
            m.head.visible = true;
            m.hat.visible = true;
            return;
        }
        m.setAllVisible(true);
        // DBIL clothing lives on the overlay layer, so it is always shown; the vanilla cape setting still applies.
        m.hat.visible = true;
        m.jacket.visible = m.leftPants.visible = m.rightPants.visible = m.leftSleeve.visible = m.rightSleeve.visible = true;
        m.crouching = player.isCrouching();
        HumanoidModel.ArmPose main = armPose(player, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose off = armPose(player, InteractionHand.OFF_HAND);
        if (main.isTwoHanded()) off = player.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            m.rightArmPose = main;
            m.leftArmPose = off;
        } else {
            m.rightArmPose = off;
            m.leftArmPose = main;
        }
    }

    private static HumanoidModel.ArmPose armPose(AbstractClientPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            UseAnim use = stack.getUseAnimation();
            if (use == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
            if (use == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (use == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (use == UseAnim.CROSSBOW && hand == player.getUsedItemHand()) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (use == UseAnim.SPYGLASS) return HumanoidModel.ArmPose.SPYGLASS;
            if (use == UseAnim.TOOT_HORN) return HumanoidModel.ArmPose.TOOT_HORN;
            if (use == UseAnim.BRUSH) return HumanoidModel.ArmPose.BRUSH;
        } else if (!player.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        HumanoidModel.ArmPose forge = IClientItemExtensions.of(stack).getArmPose(player, hand, stack);
        return forge != null ? forge : HumanoidModel.ArmPose.ITEM;
    }

    /** First-person arm with the DBIL skin, sleeve layer and arm-attached outfit pieces (wristbands, pads). */
    public boolean renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidArm side) {
        CharacterLook look = lookFor(player);
        if (look == null) return false;
        current = look;
        setModelProperties(player);
        character.attackTime = 0.0F;
        character.crouching = false;
        character.swimAmount = 0.0F;
        character.firstPerson = true;
        character.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        character.firstPerson = false;
        ModelPart arm = side == HumanoidArm.RIGHT ? character.rightArm : character.leftArm;
        ModelPart sleeve = side == HumanoidArm.RIGHT ? character.rightSleeve : character.leftSleeve;
        arm.xRot = 0.0F;
        sleeve.xRot = 0.0F;
        ResourceLocation texture = look.texture();
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);
        Map<OutfitModels.Attach, List<PartBuilder.Piece>> outfit = OutfitModels.get(look.appearance());
        List<PartBuilder.Piece> pieces = outfit.get(side == HumanoidArm.RIGHT ? OutfitModels.Attach.RIGHT_ARM : OutfitModels.Attach.LEFT_ARM);
        if (pieces != null && !pieces.isEmpty()) {
            var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(CharacterLayer.PARTS));
            for (PartBuilder.Piece piece : pieces) {
                int color = CharacterLayer.slotColor(look.appearance(), piece.slot());
                pose.pushPose();
                arm.translateAndRotate(pose);
                piece.part().render(pose, consumer, light, OverlayTexture.NO_OVERLAY,
                        CharacterLayer.red(color), CharacterLayer.green(color), CharacterLayer.blue(color), 1);
                pose.popPose();
            }
        }
        return true;
    }

    /** Vanilla cape option is respected by CapeLayer itself; parts shown checks keep the API used. */
    static boolean capeShown(AbstractClientPlayer player) { return player.isModelPartShown(PlayerModelPart.CAPE); }
}
