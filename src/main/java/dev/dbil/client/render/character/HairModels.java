package dev.dbil.client.render.character;

import dev.dbil.appearance.AppearanceOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.dbil.client.render.character.PartBuilder.seg;

/**
 * Voxel hairstyles, attached to the head part. Every base style has a matching Super Saiyan variant built from
 * the same silhouette: spikes rise, lengthen and gain a few extra tips, so transformed players keep their own look.
 */
public final class HairModels {
    public static final int VARIANT_BASE = 0, VARIANT_SUPER = 1;
    private static final float PI = (float) Math.PI;
    private static final Map<String, List<PartBuilder.Piece>> CACHE = new HashMap<>();

    private HairModels() {}

    public static List<PartBuilder.Piece> get(ResourceLocation style, int variant) {
        String key = style + "#" + variant;
        return CACHE.computeIfAbsent(key, ignored -> build(style, variant == VARIANT_SUPER));
    }

    public static void clear() { CACHE.clear(); }

    private static List<PartBuilder.Piece> build(ResourceLocation style, boolean ssj) {
        PartBuilder b = new PartBuilder(64, 64);
        if (AppearanceOptions.HAIR_BALD.equals(style)) return b.bake();
        if (AppearanceOptions.HAIR_SPIKY.equals(style)) spiky(b, ssj);
        else if (AppearanceOptions.HAIR_SPIKY_TALL.equals(style)) spikyTall(b, ssj);
        else if (AppearanceOptions.HAIR_MESSY.equals(style)) messy(b, ssj);
        else if (AppearanceOptions.HAIR_MEDIUM.equals(style)) medium(b, ssj);
        else if (AppearanceOptions.HAIR_STRAIGHT.equals(style)) straight(b, ssj);
        else shortHair(b, ssj);
        return b.bake();
    }

    /** Shared skull cap: hides the head top and the upper back so spikes have a base to grow from. */
    private static void cap(PartBuilder b, float backDepth) {
        b.box(0, -4.5F, -9.0F, -4.5F, 9, 2.2F, 9);
        b.box(0, -4.5F, -7.0F, 3.4F, 9, backDepth, 1.1F);
        b.box(0, -4.5F, -7.0F, -3.6F, 1.0F, 2.0F, 7.2F);
        b.box(0, 3.5F, -7.0F, -3.6F, 1.0F, 2.0F, 7.2F);
    }

    private static void shortHair(PartBuilder b, boolean ssj) {
        cap(b, 4);
        if (!ssj) {
            b.box(0, -3, -10.0F, -3, 6, 1.2F, 6);
            b.box(0, -4.5F, -8.0F, -4.6F, 9, 1.0F, 1.1F);
            b.box(0, -3.5F, -7.2F, -4.7F, 2, 1.2F, 0.8F);
            b.box(0, 0.5F, -7.2F, -4.7F, 1.5F, 1.0F, 0.8F);
            b.spike(0, -2, -9.6F, 2, -0.6F, 0, -0.3F, seg(2.5F, 1.5F), seg(1.5F, 1.2F));
            b.spike(0, 2, -9.6F, 2, -0.6F, 0, 0.3F, seg(2.5F, 1.5F), seg(1.5F, 1.2F));
        } else {
            b.spike(0, 0, -9.2F, 0, -0.15F, 0, 0, seg(3.5F, 2.2F), seg(2.5F, 2.0F), seg(1.2F, 1.6F));
            b.spike(0, -2.5F, -9.0F, -1.5F, 0.15F, 0, -0.35F, seg(3, 2), seg(2, 1.8F), seg(1, 1.4F));
            b.spike(0, 2.5F, -9.0F, -1.5F, 0.15F, 0, 0.35F, seg(3, 2), seg(2, 1.8F), seg(1, 1.4F));
            b.spike(0, -2.5F, -9.0F, 2.5F, -0.4F, 0, -0.3F, seg(3, 2), seg(2, 1.6F), seg(1, 1.2F));
            b.spike(0, 2.5F, -9.0F, 2.5F, -0.4F, 0, 0.3F, seg(3, 2), seg(2, 1.6F), seg(1, 1.2F));
            b.spike(0, 0, -8.6F, 3.2F, -0.8F, 0, 0, seg(3, 2), seg(2, 1.6F), seg(1, 1.2F));
            b.spike(0, -1.2F, -8.0F, -4.3F, 2.75F, 0, 0.2F, seg(1.8F, 1.8F), seg(1, 1.2F));
        }
    }

    private static void spiky(PartBuilder b, boolean ssj) {
        cap(b, 5);
        float lift = ssj ? 0.45F : 1.0F;     // SSJ spikes lean much less sideways: they rise.
        float grow = ssj ? 1.3F : 1.0F;
        b.spike(0, 0, -9.0F, 0.5F, ssj ? -0.1F : -0.35F, 0, 0, seg(3.2F, 2.6F * grow), seg(2.2F, 2.2F * grow), seg(1.1F, 1.8F * grow));
        b.spike(0, -3.0F, -8.6F, 0, -0.1F, 0, -0.85F * lift, seg(3, 2.2F * grow), seg(2, 2.0F * grow), seg(1, 1.6F * grow));
        b.spike(0, 3.0F, -8.6F, 0, -0.1F, 0, 0.85F * lift, seg(3, 2.2F * grow), seg(2, 2.0F * grow), seg(1, 1.6F * grow));
        b.spike(0, -4.0F, -6.8F, 1.0F, -0.15F, 0, -1.45F * (ssj ? 0.6F : 1F), seg(2.6F, 2.0F * grow), seg(1.6F, 1.6F * grow), seg(1, 1.0F));
        b.spike(0, 4.0F, -6.8F, 1.0F, -0.15F, 0, 1.45F * (ssj ? 0.6F : 1F), seg(2.6F, 2.0F * grow), seg(1.6F, 1.6F * grow), seg(1, 1.0F));
        b.spike(0, 0, -8.2F, 3.0F, ssj ? -0.55F : -1.0F, 0, 0, seg(3.2F, 2.6F * grow), seg(2.2F, 2.0F * grow), seg(1.1F, 1.6F * grow));
        b.spike(0, -2.0F, -5.5F, 3.9F, ssj ? -1.0F : -1.6F, 0, -0.3F, seg(2.6F, 2.0F * grow), seg(1.6F, 1.6F), seg(1, 1));
        b.spike(0, 2.0F, -5.5F, 3.9F, ssj ? -1.0F : -1.6F, 0, 0.3F, seg(2.6F, 2.0F * grow), seg(1.6F, 1.6F), seg(1, 1));
        if (!ssj) {
            b.spike(0, -1.6F, -8.0F, -4.2F, 2.7F, 0, 0.25F, seg(2, 1.6F), seg(1, 1.6F));
            b.spike(0, 1.6F, -8.0F, -4.2F, 2.7F, 0, -0.25F, seg(2, 1.6F), seg(1, 1.6F));
            b.spike(0, 0, -8.0F, -4.3F, 2.9F, 0, 0, seg(1.6F, 2.0F), seg(1, 1.2F));
        } else {
            // Super Saiyan: a single falling front bang, two extra rising spikes.
            b.spike(0, -1.0F, -8.0F, -4.3F, 2.75F, 0, 0.15F, seg(2, 2.2F), seg(1, 2.0F));
            b.spike(0, -1.5F, -9.0F, -2.0F, 0.25F, 0, -0.3F, seg(2.6F, 2.4F), seg(1.6F, 2.2F), seg(1, 1.6F));
            b.spike(0, 1.5F, -9.0F, -2.0F, 0.25F, 0, 0.3F, seg(2.6F, 2.4F), seg(1.6F, 2.2F), seg(1, 1.6F));
        }
    }

    private static void spikyTall(PartBuilder b, boolean ssj) {
        b.box(0, -4.5F, -9.0F, -4.5F, 9, 2.0F, 9);
        b.box(0, -4.5F, -7.0F, -4.0F, 1.0F, 3.0F, 8.5F);
        b.box(0, 3.5F, -7.0F, -4.0F, 1.0F, 3.0F, 8.5F);
        b.box(0, -4.5F, -7.0F, 3.4F, 9, 3.0F, 1.1F);
        b.box(0, -1.0F, -8.2F, -4.6F, 2, 1.4F, 0.6F);
        float grow = ssj ? 1.25F : 1.0F;
        float spread = ssj ? 1.25F : 1.0F;
        b.spike(0, 0, -9.0F, 1.0F, -0.15F, 0, 0, seg(4, 3 * grow), seg(3, 3 * grow), seg(2, 2.5F * grow), seg(1, 2 * grow));
        b.spike(0, -2.2F, -9.0F, 0.5F, -0.15F, 0, -0.3F * spread, seg(3, 3 * grow), seg(2, 2.5F * grow), seg(1, 2 * grow));
        b.spike(0, 2.2F, -9.0F, 0.5F, -0.15F, 0, 0.3F * spread, seg(3, 3 * grow), seg(2, 2.5F * grow), seg(1, 2 * grow));
        b.spike(0, -3.5F, -8.5F, 1.0F, -0.1F, 0, -0.6F * spread, seg(2.5F, 2.5F * grow), seg(1.5F, 2 * grow), seg(1, 1));
        b.spike(0, 3.5F, -8.5F, 1.0F, -0.1F, 0, 0.6F * spread, seg(2.5F, 2.5F * grow), seg(1.5F, 2 * grow), seg(1, 1));
        b.spike(0, 0, -8.5F, 2.5F, -0.45F, 0, 0, seg(3, 3 * grow), seg(2, 2.5F * grow), seg(1, 2 * grow));
        if (ssj) {
            b.spike(0, -1.2F, -9.0F, -1.5F, 0.1F, 0, -0.15F, seg(2.6F, 2.6F), seg(1.6F, 2.4F), seg(1, 1.8F));
            b.spike(0, 1.2F, -9.0F, -1.5F, 0.1F, 0, 0.15F, seg(2.6F, 2.6F), seg(1.6F, 2.4F), seg(1, 1.8F));
        }
    }

    private static void messy(PartBuilder b, boolean ssj) {
        cap(b, 4);
        float up = ssj ? 0.45F : 1.0F;
        float grow = ssj ? 1.35F : 1.0F;
        b.spike(0, -3, -9, -3, 0.5F * up, 0, -0.6F * up, seg(2.2F, 1.6F * grow), seg(1.2F, 1.2F * grow));
        b.spike(0, 2, -9, -2, 0.4F * up, 0, 0.5F * up, seg(2.2F, 1.6F * grow), seg(1.2F, 1.2F * grow));
        b.spike(0, 0, -9, 1, -0.6F * up, 0, 0, seg(2.4F, 1.8F * grow), seg(1.4F, 1.4F * grow));
        b.spike(0, -3.5F, -8, 2, -0.5F * up, 0, -1.1F * up, seg(2.2F, 1.6F * grow), seg(1.2F, 1.2F * grow));
        b.spike(0, 3.5F, -8, 2, -0.4F * up, 0, 1.2F * up, seg(2.2F, 1.6F * grow), seg(1.2F, 1.2F * grow));
        b.spike(0, -1, -9, -4, ssj ? 0.35F : 1.2F, 0, 0, seg(2, 1.6F * grow), seg(1, 1.2F * grow));
        b.spike(0, 4, -6, -2, 0, 0, 1.6F * (ssj ? 0.55F : 1F), seg(2, 1.4F * grow), seg(1, 1));
        b.spike(0, -4, -6, 0, 0, 0, -1.7F * (ssj ? 0.55F : 1F), seg(2, 1.4F * grow), seg(1, 1));
        b.spike(0, 1, -7, 4, ssj ? -0.9F : -1.5F, 0, 0.2F, seg(2.2F, 1.6F * grow), seg(1.2F, 1.2F));
        if (!ssj) {
            b.spike(0, -2, -8, -4.2F, 2.6F, 0, 0.4F, seg(1.6F, 2), seg(1, 1));
            b.spike(0, 1, -8, -4.2F, 2.5F, 0, -0.3F, seg(1.6F, 1.8F), seg(1, 1));
        } else {
            b.spike(0, 0, -9.2F, -0.5F, 0, 0, 0, seg(3, 2.4F), seg(2, 2.2F), seg(1, 1.8F));
            b.spike(0, -1.0F, -8.0F, -4.3F, 2.7F, 0, 0.2F, seg(1.8F, 2.0F), seg(1, 1.4F));
        }
    }

    private static void medium(PartBuilder b, boolean ssj) {
        if (!ssj) {
            b.box(0, -4.6F, -9.0F, -4.6F, 9.2F, 2.0F, 9.2F);
            b.box(0, -3.0F, -10.0F, -3.0F, 6, 1.0F, 6);
            b.box(0, -4.6F, -7.0F, -4.0F, 1.2F, 6.0F, 7.5F);
            b.box(0, 3.4F, -7.0F, -4.0F, 1.2F, 6.0F, 7.5F);
            b.box(0, -4.6F, -7.0F, 3.4F, 9.2F, 7.5F, 1.4F);
            b.box(0, -4.6F, -8.0F, -4.7F, 9.2F, 1.5F, 1.0F);
            b.box(0, -3.6F, -6.6F, -4.8F, 2.0F, 2.0F, 0.8F);
            b.box(0, -0.6F, -6.6F, -4.8F, 1.6F, 1.5F, 0.8F);
            b.box(0, 2.0F, -6.6F, -4.8F, 2.0F, 2.5F, 0.8F);
            b.spike(0, -1.8F, -9.6F, 0.5F, -0.35F, 0, -0.45F, seg(2.6F, 1.3F), seg(1.6F, 1.0F));
            b.spike(0, 1.8F, -9.6F, 0.5F, -0.35F, 0, 0.45F, seg(2.6F, 1.3F), seg(1.6F, 1.0F));
            b.spike(0, 0, -9.0F, 3.4F, -1.2F, 0, 0, seg(2.8F, 1.4F), seg(1.6F, 1.0F));
        } else {
            cap(b, 5);
            b.spike(0, 0, -9.0F, 0.5F, -0.3F, 0, 0, seg(3.4F, 2.8F), seg(2.4F, 2.4F), seg(1.2F, 1.8F));
            b.spike(0, -2.8F, -8.8F, 0.5F, -0.25F, 0, -0.5F, seg(3, 2.6F), seg(2, 2.2F), seg(1, 1.6F));
            b.spike(0, 2.8F, -8.8F, 0.5F, -0.25F, 0, 0.5F, seg(3, 2.6F), seg(2, 2.2F), seg(1, 1.6F));
            b.spike(0, -4.0F, -6.5F, 1.5F, -0.4F, 0, -1.0F, seg(2.6F, 2.2F), seg(1.6F, 1.8F), seg(1, 1.2F));
            b.spike(0, 4.0F, -6.5F, 1.5F, -0.4F, 0, 1.0F, seg(2.6F, 2.2F), seg(1.6F, 1.8F), seg(1, 1.2F));
            b.spike(0, 0, -7.5F, 3.6F, -1.2F, 0, 0, seg(3.2F, 2.6F), seg(2.2F, 2.2F), seg(1.1F, 1.6F));
            b.spike(0, -2.2F, -4.5F, 4.0F, -1.9F, 0, -0.2F, seg(2.6F, 2.4F), seg(1.6F, 2.0F), seg(1, 1.2F));
            b.spike(0, 2.2F, -4.5F, 4.0F, -1.9F, 0, 0.2F, seg(2.6F, 2.4F), seg(1.6F, 2.0F), seg(1, 1.2F));
            b.spike(0, -1.0F, -8.0F, -4.3F, 2.75F, 0, 0.15F, seg(2, 2.4F), seg(1, 2.0F));
        }
    }

    private static void straight(PartBuilder b, boolean ssj) {
        if (!ssj) {
            b.box(0, -4.6F, -9.0F, -4.6F, 9.2F, 2.0F, 9.2F);
            b.box(0, -4.6F, -7.0F, -3.6F, 1.2F, 9.0F, 7.2F);
            b.box(0, 3.4F, -7.0F, -3.6F, 1.2F, 9.0F, 7.2F);
            b.box(0, -4.6F, -7.0F, 3.4F, 9.2F, 12.0F, 1.4F);
            b.box(0, -4.6F, -8.0F, -4.7F, 9.2F, 2.4F, 1.0F);
            b.box(0, -4.0F, 4.6F, 3.6F, 8.0F, 1.4F, 1.3F);
            // A side part and a few loose strands break the helmet silhouette.
            b.box(0, -4.0F, -9.8F, -3.5F, 3.2F, 0.9F, 7.5F);
            b.box(0, 0.6F, -9.6F, -3.5F, 3.4F, 0.7F, 7.5F);
            b.spike(0, 2.6F, -8.2F, -4.4F, 2.9F, 0, -0.2F, seg(1.6F, 2.6F), seg(1, 1.6F));
            b.spike(0, -3.8F, 1.5F, 0.5F, PI - 0.1F, 0, 0.05F, seg(1.4F, 1.6F), seg(1, 1.2F));
            b.spike(0, 3.8F, 1.5F, 0.5F, PI - 0.1F, 0, -0.05F, seg(1.4F, 1.6F), seg(1, 1.2F));
        } else {
            cap(b, 5);
            b.spike(0, 0, -9.0F, 0.5F, -0.2F, 0, 0, seg(3.2F, 2.6F), seg(2.2F, 2.2F), seg(1.1F, 1.6F));
            b.spike(0, -2.6F, -8.8F, 0.5F, -0.2F, 0, -0.45F, seg(3, 2.4F), seg(2, 2.0F), seg(1, 1.6F));
            b.spike(0, 2.6F, -8.8F, 0.5F, -0.2F, 0, 0.45F, seg(3, 2.4F), seg(2, 2.0F), seg(1, 1.6F));
            // The long back becomes a cascade of downward voxel spikes.
            b.spike(0, -2.5F, -5.5F, 4.0F, PI - 0.25F, 0, -0.15F, seg(3, 3.0F), seg(2, 3.0F), seg(1, 2.2F));
            b.spike(0, 0, -5.5F, 4.1F, PI - 0.2F, 0, 0, seg(3.4F, 3.4F), seg(2.4F, 3.2F), seg(1.2F, 2.4F));
            b.spike(0, 2.5F, -5.5F, 4.0F, PI - 0.25F, 0, 0.15F, seg(3, 3.0F), seg(2, 3.0F), seg(1, 2.2F));
            b.spike(0, -4.2F, -6.0F, 0.0F, 0, 0, -1.25F, seg(2.4F, 2.2F), seg(1.4F, 1.8F), seg(1, 1));
            b.spike(0, 4.2F, -6.0F, 0.0F, 0, 0, 1.25F, seg(2.4F, 2.2F), seg(1.4F, 1.8F), seg(1, 1));
            b.spike(0, -1.0F, -8.0F, -4.3F, 2.75F, 0, 0.15F, seg(2, 2.4F), seg(1, 2.0F));
        }
    }
}
