package dev.dbil.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation-only options. None of these values reach the server or change gameplay outcomes. */
public final class ClientConfig {
    public enum Quality { OFF, LOW, MEDIUM, HIGH }
    public enum Density { LOW, MEDIUM, HIGH }
    public enum HudMode { AUTO, FULL, COMPACT }
    public enum TransformationEffects { FULL, REDUCED }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue hud, particles, lockOnCamera, specialCamera, powerLevel,
            hideVanillaHealth, targetReticle, fovEffects, impactEffects, animationExtras, terrainDebris, speedLines,
            windSound, dbilCharacterModel;
    public static final ForgeConfigSpec.DoubleValue auraIntensity, hudScale, hudX, hudY, screenShake, fovIntensity;
    public static final ForgeConfigSpec.IntValue effectDistance;
    public static final ForgeConfigSpec.EnumValue<Quality> auraQuality;
    public static final ForgeConfigSpec.EnumValue<Density> particleDensity;
    public static final ForgeConfigSpec.EnumValue<HudMode> hudMode;
    public static final ForgeConfigSpec.EnumValue<TransformationEffects> transformationEffects;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("interface");
        hud = b.define("hud", true);
        powerLevel = b.define("powerLevel", true);
        hudX = b.comment("Normalized panel position: 0=left, 1=right.").defineInRange("hudX", 0.0, 0.0, 1.0);
        hudY = b.comment("Normalized panel position: 0=top, 1=bottom.").defineInRange("hudY", 0.0, 0.0, 1.0);
        hudScale = b.comment("HUD scale multiplier. The HUD also shrinks automatically on very small screens.")
                .defineInRange("hudScale", 1.0, 0.5, 2.0);
        hudMode = b.comment("AUTO expands the HUD in combat and compacts it outside combat.").defineEnum("hudMode", HudMode.AUTO);
        hideVanillaHealth = b.comment("Hide vanilla hearts while a DBIL character is active (the DBIL HP bar replaces them).")
                .define("hideVanillaHealth", true);
        targetReticle = b.define("targetReticle", true);
        lockOnCamera = b.comment("Follow the selected target. New key supersedes legacy camera=false without overriding an explicit lockOnCamera preference.").define("lockOnCamera", true);
        specialCamera = b.comment("Moderate camera reactions for transformations and heavy impacts (independent from lock-on).")
                .define("specialCamera", true);
        b.pop().push("visuals");
        dbilCharacterModel = b.comment("Render DBIL characters with their DBIL appearance. false = vanilla skin fallback.")
                .define("dbilCharacterModel", true);
        particles = b.comment("Master switch for DBIL particles.").define("particles", true);
        auraQuality = b.comment("OFF, LOW, MEDIUM or HIGH. LOW is recommended for Android.").defineEnum("auraQuality", Quality.MEDIUM);
        particleDensity = b.defineEnum("particleDensity", Density.MEDIUM);
        auraIntensity = b.comment("Brightness of aura shells.").defineInRange("auraIntensity", 0.8, 0.0, 1.0);
        effectDistance = b.comment("Blocks beyond which other players' auras and effects are skipped.").defineInRange("effectDistance", 48, 8, 128);
        screenShake = b.comment("0 disables screen shake.").defineInRange("screenShake", 0.6, 0.0, 1.0);
        fovEffects = b.define("fovEffects", true);
        fovIntensity = b.defineInRange("fovIntensity", 0.6, 0.0, 1.0);
        impactEffects = b.define("impactEffects", true);
        animationExtras = b.comment("Idle fidgets, hair and tail sway.").define("animationExtras", true);
        terrainDebris = b.define("terrainDebris", true);
        speedLines = b.define("speedLines", true);
        windSound = b.define("windSound", true);
        transformationEffects = b.defineEnum("transformationEffects", TransformationEffects.FULL);
        b.pop(); SPEC = b.build();
    }

    /** Scales particle counts: 0.4 / 0.75 / 1.0, or 0 when particles are disabled. */
    public static float particleScale() {
        if (!SPEC.isLoaded() || !particles.get()) return SPEC.isLoaded() ? 0F : 0.75F;
        return switch (particleDensity.get()) { case LOW -> 0.4F; case MEDIUM -> 0.75F; case HIGH -> 1.0F; };
    }

    public static Quality aura() { return SPEC.isLoaded() ? auraQuality.get() : Quality.MEDIUM; }
    public static double distance() { return SPEC.isLoaded() ? effectDistance.get() : 48; }

    private ClientConfig() {}
}
