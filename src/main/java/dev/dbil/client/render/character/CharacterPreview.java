package dev.dbil.client.render.character;

import dev.dbil.appearance.CharacterAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Lets the creation screen preview an unsaved appearance on the local player without touching server data. */
public final class CharacterPreview {
    private static CharacterAppearance appearance;
    private static ResourceLocation race;
    private static boolean superSaiyan;

    private CharacterPreview() {}

    public static void set(CharacterAppearance look, ResourceLocation previewRace, boolean ssj) {
        appearance = look;
        race = previewRace;
        superSaiyan = ssj;
    }

    public static void clear() { appearance = null; race = null; superSaiyan = false; }

    public static boolean active(LivingEntity entity) {
        return appearance != null && entity == Minecraft.getInstance().player;
    }

    public static CharacterAppearance appearance() { return appearance; }
    public static ResourceLocation race() { return race; }
    public static boolean superSaiyan() { return superSaiyan; }
}
