package dev.dbil.client.fx;

import dev.dbil.DBIL;
import dev.dbil.transformation.Transformations;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aura looks, registered by id so new forms only add a style. Colors are RGB; height scales the flame shell;
 * lightning enables arcing discharges; turbulence controls how much the flames flicker.
 */
public final class AuraStyles {
    public record AuraStyle(ResourceLocation id, int color, int coreColor, int moteColor, float height, float radius,
                            boolean lightning, float turbulence) {}

    private static final Map<ResourceLocation, AuraStyle> STYLES = new LinkedHashMap<>();
    public static final AuraStyle BASE = register(new AuraStyle(DBIL.id("base"), 0x8FD3FF, 0xEAF8FF, 0xBFE8FF, 1.6F, 0.42F, false, 0.35F));
    public static final AuraStyle CHARGING = register(new AuraStyle(DBIL.id("charging"), 0x6FC4FF, 0xF2FBFF, 0xA8E2FF, 2.3F, 0.5F, false, 0.6F));
    public static final AuraStyle SUPER_SAIYAN = register(new AuraStyle(Transformations.SUPER_SAIYAN, 0xFFC93A, 0xFFF6C8, 0xFFE27A, 2.6F, 0.52F, true, 0.75F));
    public static final AuraStyle POTENTIAL = register(new AuraStyle(Transformations.POTENTIAL_UNLEASHED, 0xE8F2FF, 0xFFFFFF, 0xFFFFFF, 2.1F, 0.48F, false, 0.4F));

    private AuraStyles() {}

    public static AuraStyle register(AuraStyle style) {
        STYLES.put(style.id(), style);
        return style;
    }

    public static AuraStyle get(ResourceLocation id) {
        AuraStyle style = id == null ? null : STYLES.get(id);
        return style == null ? BASE : style;
    }
}
