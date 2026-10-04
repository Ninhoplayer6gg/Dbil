package dev.dbil.client.render.character;

import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D clothing pieces that go beyond the 0.25px overlay layer: shoulder pads, sash knots, collars, wristbands,
 * headband tails, and the Saiyan tail. Pieces are cached per outfit/body type and tinted by slot at render time.
 */
public final class OutfitModels {
    public static final int HAIR = 0, PRIMARY = 1, SECONDARY = 2, ACCENT = 3, FUR = 4;
    public enum Attach { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    private static final Map<String, Map<Attach, List<PartBuilder.Piece>>> CACHE = new HashMap<>();
    private static final ModelPart[] TAILS = new ModelPart[1];

    private OutfitModels() {}

    public static Map<Attach, List<PartBuilder.Piece>> get(CharacterAppearance look) {
        String key = look.outfit() + "#" + look.bodyType() + "#" + look.accessories();
        return CACHE.computeIfAbsent(key, ignored -> build(look));
    }

    public static void clear() { CACHE.clear(); TAILS[0] = null; }

    private static Map<Attach, List<PartBuilder.Piece>> build(CharacterAppearance look) {
        boolean slim = look.slim();
        float armW = slim ? 3 : 4;
        float rightMin = slim ? -2 : -3;
        float leftMin = -1;
        ResourceLocation outfit = look.outfit();
        PartBuilder head = new PartBuilder(64, 64), body = new PartBuilder(64, 64);
        PartBuilder rightArm = new PartBuilder(64, 64), leftArm = new PartBuilder(64, 64);
        if (AppearanceOptions.OUTFIT_BATTLE_ARMOR.equals(outfit)) {
            rightArm.box(PRIMARY, 0, 32, rightMin - 0.7F, -2.7F, -2.7F, armW + 1.4F, 2.3F, 5.4F, 0);
            rightArm.box(ACCENT, 0, 32, rightMin - 0.75F, -0.6F, -2.75F, armW + 1.5F, 0.6F, 5.5F, 0);
            leftArm.box(PRIMARY, 0, 32, leftMin - 0.7F, -2.7F, -2.7F, armW + 1.4F, 2.3F, 5.4F, 0);
            leftArm.box(ACCENT, 0, 32, leftMin - 0.75F, -0.6F, -2.75F, armW + 1.5F, 0.6F, 5.5F, 0);
            body.box(PRIMARY, 0, 32, -3.9F, 10.4F, -2.75F, 3.4F, 2.8F, 0.7F, 0);
            body.box(PRIMARY, 0, 32, 0.5F, 10.4F, -2.75F, 3.4F, 2.8F, 0.7F, 0);
            body.box(PRIMARY, 0, 32, -3.9F, 10.4F, 2.05F, 3.4F, 2.8F, 0.7F, 0);
            body.box(PRIMARY, 0, 32, 0.5F, 10.4F, 2.05F, 3.4F, 2.8F, 0.7F, 0);
            body.box(ACCENT, 0, 32, -3.4F, -0.5F, -2.5F, 6.8F, 0.7F, 5.0F, 0);
        } else if (AppearanceOptions.OUTFIT_FIGHTER_VEST.equals(outfit)) {
            body.box(PRIMARY, 0, 0, -4.4F, -0.9F, -2.6F, 2.9F, 1.5F, 5.2F, 0);
            body.box(PRIMARY, 0, 0, 1.5F, -0.9F, -2.6F, 2.9F, 1.5F, 5.2F, 0);
            body.box(PRIMARY, 0, 0, -4.4F, -0.9F, 1.7F, 8.8F, 1.5F, 0.9F, 0);
            body.box(ACCENT, 0, 48, -1.0F, 8.9F, -2.6F, 2.0F, 1.3F, 0.5F, 0);
        } else if (AppearanceOptions.OUTFIT_SLEEVELESS.equals(outfit)) {
            body.box(ACCENT, 0, 48, -1.0F, 8.9F, -2.55F, 2.0F, 1.3F, 0.45F, 0);
        } else {
            body.box(SECONDARY, 0, 0, -1.3F, 9.1F, -2.9F, 2.6F, 1.8F, 1.0F, 0);
            body.rotatedBox(SECONDARY, -0.6F, 10.6F, -2.65F, -0.12F, 0, 0.28F, -0.5F, 0, -0.3F, 1.0F, 3.4F, 0.6F);
            body.rotatedBox(SECONDARY, 0.6F, 10.6F, -2.65F, -0.12F, 0, -0.18F, -0.5F, 0, -0.3F, 1.0F, 2.8F, 0.6F);
        }
        if (look.has(AppearanceOptions.ACCESSORY_WRISTBANDS)) {
            int slot = AppearanceOptions.OUTFIT_BATTLE_ARMOR.equals(outfit) ? ACCENT : SECONDARY;
            rightArm.box(slot, 0, 0, rightMin - 0.4F, 6.3F, -2.4F, armW + 0.8F, 2.0F, 4.8F, 0);
            leftArm.box(slot, 0, 0, leftMin - 0.4F, 6.3F, -2.4F, armW + 0.8F, 2.0F, 4.8F, 0);
        }
        if (look.has(AppearanceOptions.ACCESSORY_HEADBAND)) {
            head.rotatedBox(ACCENT, 0.4F, -6.6F, 4.45F, -0.35F, 0, 0.35F, -0.45F, 0, -0.2F, 0.9F, 3.6F, 0.4F);
            head.rotatedBox(ACCENT, -0.4F, -6.6F, 4.45F, -0.25F, 0, -0.25F, -0.45F, 0, -0.2F, 0.9F, 3.0F, 0.4F);
            head.box(ACCENT, 0, 0, -0.9F, -7.3F, 4.25F, 1.8F, 1.4F, 0.6F, 0);
        }
        Map<Attach, List<PartBuilder.Piece>> result = new EnumMap<>(Attach.class);
        result.put(Attach.HEAD, head.bake());
        result.put(Attach.BODY, body.bake());
        result.put(Attach.RIGHT_ARM, rightArm.bake());
        result.put(Attach.LEFT_ARM, leftArm.bake());
        return result;
    }

    /** Five nested voxel segments; the layer bends each joint for a living sway. */
    public static ModelPart tail() {
        if (TAILS[0] == null) {
            PartBuilder builder = new PartBuilder(64, 64);
            PartDefinition root = builder.root();
            PartDefinition s0 = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.2F, -1.2F, 0, 2.4F, 2.4F, 3.2F, CubeDeformation.NONE), PartPose.offset(0, 10.6F, 1.6F));
            PartDefinition s1 = s0.addOrReplaceChild("s1", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.1F, -1.1F, 0, 2.2F, 2.2F, 3.2F, CubeDeformation.NONE), PartPose.offset(0, 0, 3.0F));
            PartDefinition s2 = s1.addOrReplaceChild("s2", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.0F, -1.0F, 0, 2.0F, 2.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(0, 0, 3.0F));
            PartDefinition s3 = s2.addOrReplaceChild("s3", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-0.9F, -0.9F, 0, 1.8F, 1.8F, 2.8F, CubeDeformation.NONE), PartPose.offset(0, 0, 2.8F));
            s3.addOrReplaceChild("s4", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-0.7F, -0.7F, 0, 1.4F, 1.4F, 2.2F, CubeDeformation.NONE), PartPose.offset(0, 0, 2.6F));
            TAILS[0] = builder.bakeRoot().getChild("tail");
        }
        return TAILS[0];
    }
}
