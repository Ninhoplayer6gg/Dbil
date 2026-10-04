package dev.dbil.animation;

import dev.dbil.DBIL;
import dev.dbil.client.ClientState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge arm-pose extension: existing skins/model layers remain usable without an animation library. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class PlayerPoses {
    private static final HumanoidModel.ArmPose CHARGE = HumanoidModel.ArmPose.create("DBIL_KI_CHARGE", false,
            (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                float side = arm == HumanoidArm.RIGHT ? 1 : -1;
                part.xRot = -0.28F + Mth.sin(entity.tickCount * 0.35F) * 0.025F;
                part.yRot = -side * 0.12F;
                part.zRot = side * 0.28F;
            });
    private static final HumanoidModel.ArmPose WAVE = HumanoidModel.ArmPose.create("DBIL_KI_WAVE", true,
            (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                float side = arm == HumanoidArm.RIGHT ? 1 : -1;
                part.xRot = -1.35F + Mth.clamp(model.head.xRot, -0.7F, 0.7F);
                part.yRot = model.head.yRot - side * 0.30F;
                part.zRot = side * 0.08F;
            });
    private static final HumanoidModel.ArmPose GUARD = HumanoidModel.ArmPose.create("DBIL_GUARD", true,
            (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                float side = arm == HumanoidArm.RIGHT ? 1 : -1;
                part.xRot = -1.15F + Mth.clamp(model.head.xRot, -0.4F, 0.4F);
                part.yRot = model.head.yRot - side * 0.50F;
                part.zRot = -side * 0.25F;
            });
    private PlayerPoses() {}

    @SubscribeEvent public static void beforePlayer(RenderPlayerEvent.Pre event) {
        ClientState.VisualState state = ClientState.visual(event.getEntity().getId());
        HumanoidModel.ArmPose pose = state.guarding() ? GUARD : state.techniqueTicks() > 0 ? WAVE : state.charging() || state.transformationTicks() > 0 ? CHARGE : null;
        if (pose == null) return;
        // PlayerRenderer sets normal arm poses before this event; setupAnim then invokes these transformers.
        var model = event.getRenderer().getModel();
        model.rightArmPose = pose;
        model.leftArmPose = pose;
    }
}
