package dev.dbil.client.render.character;

import dev.dbil.client.anim.CharacterAnimator;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * DBIL base body: the vanilla Steve/Alex proportions (so items, armor and first person stay compatible), with
 * DBIL's own procedural animation layered after vanilla's. Hair, outfit pieces and the tail are render layers.
 */
public class DBILCharacterModel<T extends LivingEntity> extends PlayerModel<T> {
    /** First-person arm rendering must not receive stance or action poses. */
    public boolean firstPerson;

    public DBILCharacterModel(boolean slim) {
        super(bake(slim), slim);
    }

    private static ModelPart bake(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (firstPerson) return;
        CharacterAnimator.apply(this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
    }
}
