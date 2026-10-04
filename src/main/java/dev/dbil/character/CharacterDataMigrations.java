package dev.dbil.character;

import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Sequential migrations use a copy, never changing the caller's saved tag or network payload. */
public final class CharacterDataMigrations {
    private CharacterDataMigrations() {}

    public static CompoundTag upgrade(CompoundTag original) {
        CompoundTag tag = original.copy();
        int version = Math.max(0, tag.getInt("schemaVersion"));
        if (version < 1) {
            CompoundTag attributes = tag.getCompound("attributes");
            for (Stat stat : Stat.values()) {
                if (!attributes.contains(stat.key()) && tag.contains(stat.key(), Tag.TAG_ANY_NUMERIC)) {
                    attributes.putDouble(stat.key(), tag.getDouble(stat.key()));
                }
            }
            if (!attributes.contains(Stat.MAX_KI.key()) && tag.contains("maxKi", Tag.TAG_ANY_NUMERIC)) {
                attributes.putDouble(Stat.MAX_KI.key(), tag.getDouble("maxKi"));
            }
            if (!attributes.contains(Stat.MAX_STAMINA.key()) && tag.contains("maxStamina", Tag.TAG_ANY_NUMERIC)) {
                attributes.putDouble(Stat.MAX_STAMINA.key(), tag.getDouble("maxStamina"));
            }
            tag.put("attributes", attributes);
            version = 1;
        }
        if (version < 2) {
            if (!tag.contains("origin", Tag.TAG_STRING)) tag.putString("origin", Origins.EARTH_WARRIOR.toString());
            if (!tag.contains("combatStyle", Tag.TAG_STRING)) tag.putString("combatStyle", "balanced");
            if (!tag.contains("currentTransformation", Tag.TAG_STRING)) {
                tag.putString("currentTransformation", CharacterData.BASE_FORM.toString());
            }
            version = 2;
        }
        if (version < 3) {
            if (!tag.contains("selectedTechnique", Tag.TAG_STRING)) tag.putString("selectedTechnique", "dbil:ki_wave");
            version = 3;
        }
        if (version < 4) {
            // 0.3 visual identity: existing characters receive their race's default look and can edit it later.
            if (!tag.contains("appearance", Tag.TAG_COMPOUND)) {
                ResourceLocation race = ResourceLocation.tryParse(tag.getString("race"));
                tag.put("appearance", CharacterAppearance.defaultFor(race).save());
            }
            version = 4;
        }
        // CharacterData protects unknown newer schemas before reaching migration; never lower their version here.
        tag.putInt("schemaVersion", version);
        return tag;
    }
}
