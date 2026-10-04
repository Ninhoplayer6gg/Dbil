package dev.dbil.appearance;

import dev.dbil.DBIL;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shared catalogue of cosmetic choices. The server validates against these lists; the client maps the same
 * identifiers to geometry and texture painters. Nothing here affects stats or combat.
 */
public final class AppearanceOptions {
    public static final ResourceLocation HAIR_SHORT = DBIL.id("short");
    public static final ResourceLocation HAIR_SPIKY = DBIL.id("spiky");
    public static final ResourceLocation HAIR_SPIKY_TALL = DBIL.id("spiky_tall");
    public static final ResourceLocation HAIR_MESSY = DBIL.id("messy");
    public static final ResourceLocation HAIR_MEDIUM = DBIL.id("medium");
    public static final ResourceLocation HAIR_STRAIGHT = DBIL.id("straight");
    public static final ResourceLocation HAIR_BALD = DBIL.id("bald");
    public static final List<ResourceLocation> HAIRSTYLES = List.of(HAIR_SHORT, HAIR_SPIKY, HAIR_SPIKY_TALL,
            HAIR_MESSY, HAIR_MEDIUM, HAIR_STRAIGHT, HAIR_BALD);

    public static final ResourceLocation OUTFIT_TRAINING_GI = DBIL.id("training_gi");
    public static final ResourceLocation OUTFIT_BATTLE_ARMOR = DBIL.id("battle_armor");
    public static final ResourceLocation OUTFIT_FIGHTER_VEST = DBIL.id("fighter_vest");
    public static final ResourceLocation OUTFIT_SLEEVELESS = DBIL.id("sleeveless");
    public static final List<ResourceLocation> OUTFITS = List.of(OUTFIT_TRAINING_GI, OUTFIT_BATTLE_ARMOR,
            OUTFIT_FIGHTER_VEST, OUTFIT_SLEEVELESS);

    public static final int BODY_TYPES = 2;
    public static final int EYE_STYLES = 5;
    public static final int EYEBROW_STYLES = 4;
    public static final int MOUTH_STYLES = 3;

    public static final int ACCESSORY_TAIL = 1;
    public static final int ACCESSORY_WRISTBANDS = 1 << 1;
    public static final int ACCESSORY_HEADBAND = 1 << 2;
    public static final int ACCESSORY_MASK = ACCESSORY_TAIL | ACCESSORY_WRISTBANDS | ACCESSORY_HEADBAND;

    /** Palettes offered by the creation screen. Colors outside them stay valid for future custom pickers. */
    public static final int[] SKIN_TONES = {0xFCE1C8, 0xF2CBA8, 0xE8B48F, 0xD9A06F, 0xC08452, 0x9E6A3F,
            0x7A4F2C, 0x5A3820};
    public static final int[] HAIR_COLORS = {0x16161C, 0x3A2A1E, 0x5C3B22, 0x8A5A2B, 0xC9A35A, 0xE8D7A5,
            0xB33A2E, 0x6D3E91, 0x3A6FB8, 0x3E8A55, 0xE8E8EC, 0x8C8C94};
    public static final int[] EYE_COLORS = {0x1A1A1E, 0x4A2E1C, 0x2F6FB5, 0x3F8F4F, 0x7A5AA8, 0xB0423A,
            0x9A9AA4, 0xC89A2E};
    public static final int[] CLOTH_COLORS = {0xE0702A, 0x2B4C9A, 0x1D1D24, 0xF2F2F2, 0xB8312F, 0x2F7D46,
            0x6A3EA1, 0xE3C14B, 0x3D9BD8, 0x7A5134, 0x8C8F99, 0xD45D96, 0x2A6E6E, 0x4A4F5C};

    private AppearanceOptions() {}

    public static int index(List<ResourceLocation> values, ResourceLocation id) {
        int index = values.indexOf(id);
        return Math.max(0, index);
    }

    public static ResourceLocation cycle(List<ResourceLocation> values, ResourceLocation current, int direction) {
        int index = values.indexOf(current);
        if (index < 0) index = 0;
        return values.get(Math.floorMod(index + direction, values.size()));
    }

    public static int cycle(int[] palette, int current, int direction) {
        int index = 0;
        for (int i = 0; i < palette.length; i++) if (palette[i] == current) { index = i; break; }
        return palette[Math.floorMod(index + direction, palette.length)];
    }
}
