package dev.dbil.appearance;

import dev.dbil.race.Races;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable cosmetic description of a DBIL character. Every constructor path clamps values, so a packet or save
 * can never produce an index the renderer does not know. Colors are 24-bit RGB.
 */
public record CharacterAppearance(int bodyType, int skinTone, int eyeStyle, int eyeColor, int eyebrowStyle,
                                  int mouthStyle, ResourceLocation hairstyle, int hairColor,
                                  ResourceLocation outfit, int outfitPrimary, int outfitSecondary,
                                  int outfitAccent, int accessories) {
    public static final CharacterAppearance HUMAN_DEFAULT = new CharacterAppearance(0, 0xE8B48F, 0, 0x4A2E1C,
            0, 0, AppearanceOptions.HAIR_SHORT, 0x3A2A1E, AppearanceOptions.OUTFIT_FIGHTER_VEST,
            0x2B4C9A, 0x1D1D24, 0x7A5134, AppearanceOptions.ACCESSORY_WRISTBANDS);
    public static final CharacterAppearance SAIYAN_DEFAULT = new CharacterAppearance(0, 0xF2CBA8, 1, 0x1A1A1E,
            1, 0, AppearanceOptions.HAIR_SPIKY, 0x16161C, AppearanceOptions.OUTFIT_TRAINING_GI,
            0xE0702A, 0x2B4C9A, 0x2B4C9A, AppearanceOptions.ACCESSORY_TAIL | AppearanceOptions.ACCESSORY_WRISTBANDS);

    public CharacterAppearance {
        bodyType = clamp(bodyType, AppearanceOptions.BODY_TYPES);
        skinTone &= 0xFFFFFF;
        eyeStyle = clamp(eyeStyle, AppearanceOptions.EYE_STYLES);
        eyeColor &= 0xFFFFFF;
        eyebrowStyle = clamp(eyebrowStyle, AppearanceOptions.EYEBROW_STYLES);
        mouthStyle = clamp(mouthStyle, AppearanceOptions.MOUTH_STYLES);
        if (hairstyle == null || !AppearanceOptions.HAIRSTYLES.contains(hairstyle)) hairstyle = AppearanceOptions.HAIR_SHORT;
        hairColor &= 0xFFFFFF;
        if (outfit == null || !AppearanceOptions.OUTFITS.contains(outfit)) outfit = AppearanceOptions.OUTFIT_TRAINING_GI;
        outfitPrimary &= 0xFFFFFF;
        outfitSecondary &= 0xFFFFFF;
        outfitAccent &= 0xFFFFFF;
        accessories &= AppearanceOptions.ACCESSORY_MASK;
    }

    public static CharacterAppearance defaultFor(ResourceLocation race) {
        return Races.SAIYAN.equals(race) ? SAIYAN_DEFAULT : HUMAN_DEFAULT;
    }

    public boolean has(int accessory) { return (accessories & accessory) != 0; }
    public boolean slim() { return bodyType == 1; }

    /** The tail is a Saiyan trait; other races may not keep the flag through validation. */
    public CharacterAppearance forRace(ResourceLocation race) {
        if (Races.SAIYAN.equals(race) || !has(AppearanceOptions.ACCESSORY_TAIL)) return this;
        return withAccessories(accessories & ~AppearanceOptions.ACCESSORY_TAIL);
    }

    public CharacterAppearance withBodyType(int value) { return new CharacterAppearance(value, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withSkinTone(int value) { return new CharacterAppearance(bodyType, value, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withEyeStyle(int value) { return new CharacterAppearance(bodyType, skinTone, value, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withEyeColor(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, value, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withEyebrowStyle(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, value, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withMouthStyle(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, value, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withHairstyle(ResourceLocation value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, value, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withHairColor(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, value, outfit, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withOutfit(ResourceLocation value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, value, outfitPrimary, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withOutfitPrimary(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, value, outfitSecondary, outfitAccent, accessories); }
    public CharacterAppearance withOutfitSecondary(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, value, outfitAccent, accessories); }
    public CharacterAppearance withOutfitAccent(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, value, accessories); }
    public CharacterAppearance withAccessories(int value) { return new CharacterAppearance(bodyType, skinTone, eyeStyle, eyeColor, eyebrowStyle, mouthStyle, hairstyle, hairColor, outfit, outfitPrimary, outfitSecondary, outfitAccent, value); }
    public CharacterAppearance toggle(int accessory) { return withAccessories(accessories ^ accessory); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("bodyType", bodyType);
        tag.putInt("skinTone", skinTone);
        tag.putInt("eyeStyle", eyeStyle);
        tag.putInt("eyeColor", eyeColor);
        tag.putInt("eyebrowStyle", eyebrowStyle);
        tag.putInt("mouthStyle", mouthStyle);
        tag.putString("hairstyle", hairstyle.toString());
        tag.putInt("hairColor", hairColor);
        tag.putString("outfit", outfit.toString());
        tag.putInt("outfitPrimary", outfitPrimary);
        tag.putInt("outfitSecondary", outfitSecondary);
        tag.putInt("outfitAccent", outfitAccent);
        tag.putInt("accessories", accessories);
        return tag;
    }

    /** Missing keys fall back to the race default instead of zero, so partial or older tags stay sensible. */
    public static CharacterAppearance load(CompoundTag tag, ResourceLocation race) {
        CharacterAppearance base = defaultFor(race);
        if (tag == null || tag.isEmpty()) return base;
        return new CharacterAppearance(
                intOr(tag, "bodyType", base.bodyType), intOr(tag, "skinTone", base.skinTone),
                intOr(tag, "eyeStyle", base.eyeStyle), intOr(tag, "eyeColor", base.eyeColor),
                intOr(tag, "eyebrowStyle", base.eyebrowStyle), intOr(tag, "mouthStyle", base.mouthStyle),
                idOr(tag, "hairstyle", base.hairstyle), intOr(tag, "hairColor", base.hairColor),
                idOr(tag, "outfit", base.outfit), intOr(tag, "outfitPrimary", base.outfitPrimary),
                intOr(tag, "outfitSecondary", base.outfitSecondary), intOr(tag, "outfitAccent", base.outfitAccent),
                intOr(tag, "accessories", base.accessories)).forRace(race);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(bodyType);
        buffer.writeInt(skinTone);
        buffer.writeByte(eyeStyle);
        buffer.writeInt(eyeColor);
        buffer.writeByte(eyebrowStyle);
        buffer.writeByte(mouthStyle);
        buffer.writeUtf(hairstyle.toString(), 64);
        buffer.writeInt(hairColor);
        buffer.writeUtf(outfit.toString(), 64);
        buffer.writeInt(outfitPrimary);
        buffer.writeInt(outfitSecondary);
        buffer.writeInt(outfitAccent);
        buffer.writeByte(accessories);
    }

    public static CharacterAppearance read(FriendlyByteBuf buffer) {
        int bodyType = buffer.readByte();
        int skin = buffer.readInt();
        int eyeStyle = buffer.readByte();
        int eyeColor = buffer.readInt();
        int brows = buffer.readByte();
        int mouth = buffer.readByte();
        ResourceLocation hair = ResourceLocation.tryParse(buffer.readUtf(64));
        int hairColor = buffer.readInt();
        ResourceLocation outfit = ResourceLocation.tryParse(buffer.readUtf(64));
        int primary = buffer.readInt();
        int secondary = buffer.readInt();
        int accent = buffer.readInt();
        int accessories = buffer.readByte();
        return new CharacterAppearance(bodyType, skin, eyeStyle, eyeColor, brows, mouth, hair, hairColor, outfit,
                primary, secondary, accent, accessories);
    }

    private static int clamp(int value, int count) { return value < 0 || value >= count ? 0 : value; }
    private static int intOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }
    private static ResourceLocation idOr(CompoundTag tag, String key, ResourceLocation fallback) {
        if (!tag.contains(key, Tag.TAG_STRING)) return fallback;
        String value = tag.getString(key);
        ResourceLocation id = value.length() > 64 ? null : ResourceLocation.tryParse(value);
        return id == null ? fallback : id;
    }
}
