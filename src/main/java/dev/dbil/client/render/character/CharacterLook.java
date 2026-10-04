package dev.dbil.client.render.character;

import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.client.ClientState;
import dev.dbil.client.anim.AnimState;
import dev.dbil.client.anim.CharacterAnimator;
import dev.dbil.race.Races;
import dev.dbil.transformation.Transformations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per-frame visual resolution for one character: which painted skin variant, which hair geometry variant and
 * color, and which facial expression. Transformations flicker between base and Super Saiyan hair while they
 * charge, then lock to the transformed look.
 */
public record CharacterLook(CharacterAppearance appearance, ResourceLocation race, int skinVariant, int hairVariant,
                            int hairColor, int expression, float glow) {

    public static CharacterLook resolve(LivingEntity entity, CharacterAppearance appearance, ResourceLocation race) {
        ClientState.VisualState visual = ClientState.visual(entity.getId());
        boolean superSaiyan = Transformations.SUPER_SAIYAN.equals(visual.transformation());
        boolean potential = Transformations.POTENTIAL_UNLEASHED.equals(visual.transformation());
        float glow = 0;
        if (visual.transforming() && Transformations.SUPER_SAIYAN.equals(visual.transformation()) == false) {
            float progress = visual.transformationProgress();
            boolean saiyanCandidate = Races.SAIYAN.equals(race);
            if (saiyanCandidate && progress > 0.85F) superSaiyan = true;
            else if (saiyanCandidate && progress > 0.4F) superSaiyan = (entity.tickCount / 3) % (progress > 0.65F ? 2 : 3) == 0;
            glow = progress;
        }
        if (CharacterPreview.active(entity)) {
            superSaiyan = CharacterPreview.superSaiyan();
            potential = false;
        }
        int skin = superSaiyan ? SkinPainter.VARIANT_SUPER_SAIYAN : potential ? SkinPainter.VARIANT_POTENTIAL : SkinPainter.VARIANT_BASE;
        int hair = superSaiyan ? SkinPainter.SSJ_HAIR : appearance.hairColor();
        int expression = SkinPainter.EXPRESSION_NEUTRAL;
        AnimState anim = CharacterAnimator.state(entity);
        if (visual.charging() || visual.transforming() || visual.chargingTechnique()) expression = SkinPainter.EXPRESSION_SHOUT;
        else if (visual.inCombat() || visual.targetId() >= 0 || visual.guarding()) expression = SkinPainter.EXPRESSION_FOCUSED;
        if (superSaiyan) glow = Math.max(glow, 0.6F);
        if (AppearanceOptions.HAIR_BALD.equals(appearance.hairstyle())) glow = 0;
        return new CharacterLook(appearance, race, skin, superSaiyan ? HairModels.VARIANT_SUPER : HairModels.VARIANT_BASE,
                hair, expression, glow);
    }

    public ResourceLocation texture() {
        return CharacterTextures.get(appearance, skinVariant, expression);
    }
}
