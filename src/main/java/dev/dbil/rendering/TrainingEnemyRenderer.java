package dev.dbil.rendering;

import dev.dbil.npc.TrainingEnemy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Temporary vanilla humanoid appearance, isolated from NPC stats and AI for later replacement. */
public final class TrainingEnemyRenderer extends HumanoidMobRenderer<TrainingEnemy, HumanoidModel<TrainingEnemy>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");
    public TrainingEnemyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.45F);
    }
    @Override public ResourceLocation getTextureLocation(TrainingEnemy entity) { return TEXTURE; }
}
