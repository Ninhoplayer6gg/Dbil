package dev.dbil.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.client.anim.CharacterAnimator;
import dev.dbil.client.render.character.CharacterLayer;
import dev.dbil.client.render.character.CharacterLook;
import dev.dbil.client.render.character.DBILCharacterModel;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.race.Races;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Sparring rivals use the DBIL character model with a stable look derived from their UUID. */
public final class TrainingEnemyRenderer extends MobRenderer<TrainingEnemy, PlayerModel<TrainingEnemy>> {
    private CharacterLook current;

    public TrainingEnemyRenderer(EntityRendererProvider.Context context) {
        super(context, new DBILCharacterModel<>(false), 0.5F);
        addLayer(new CharacterLayer<>(this, enemy -> current));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public static CharacterAppearance appearance(UUID id) {
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int a = (int) (seed & 0x7FFFFFFF), b = (int) ((seed >>> 32) & 0x7FFFFFFF);
        ResourceLocation hair = AppearanceOptions.HAIRSTYLES.get(a % (AppearanceOptions.HAIRSTYLES.size() - 1));
        ResourceLocation outfit = AppearanceOptions.OUTFITS.get(b % AppearanceOptions.OUTFITS.size());
        int[] cloth = AppearanceOptions.CLOTH_COLORS;
        return new CharacterAppearance(0, AppearanceOptions.SKIN_TONES[(a >> 3) % AppearanceOptions.SKIN_TONES.length],
                (b >> 2) % AppearanceOptions.EYE_STYLES, AppearanceOptions.EYE_COLORS[(a >> 6) % AppearanceOptions.EYE_COLORS.length],
                3, 0, hair, AppearanceOptions.HAIR_COLORS[(b >> 5) % AppearanceOptions.HAIR_COLORS.length], outfit,
                cloth[(a >> 9) % cloth.length], cloth[(b >> 9) % cloth.length], cloth[(a >> 12) % cloth.length],
                AppearanceOptions.ACCESSORY_WRISTBANDS | ((b >> 14) % 2 == 0 ? AppearanceOptions.ACCESSORY_HEADBAND : 0));
    }

    @Override
    public void render(TrainingEnemy enemy, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        current = CharacterLook.resolve(enemy, appearance(enemy.getUUID()), Races.HUMAN);
        super.render(enemy, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingEnemy enemy) {
        CharacterLook look = current != null ? current : CharacterLook.resolve(enemy, appearance(enemy.getUUID()), Races.HUMAN);
        return look.texture();
    }

    @Override
    protected void scale(TrainingEnemy enemy, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(TrainingEnemy enemy, PoseStack pose, float age, float bodyYaw, float partialTick) {
        super.setupRotations(enemy, pose, age, bodyYaw, partialTick);
        if (enemy.isDeadOrDying()) return;
        CharacterAnimator.Root root = CharacterAnimator.root(enemy, partialTick);
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
