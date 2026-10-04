package dev.dbil.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue hud, particles, lockOnCamera, specialCamera, powerLevel;
    public static final ForgeConfigSpec.DoubleValue auraIntensity, hudScale, hudX, hudY;
    public static final ForgeConfigSpec.IntValue effectDistance, effectQuality, explosionQuality;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("interface");
        hud = b.define("hud", true);
        powerLevel = b.define("powerLevel", true);
        hudX = b.comment("Normalized panel position: 0=left, 1=right.").defineInRange("hudX", 0.02, 0.0, 1.0);
        hudY = b.comment("Normalized panel position: 0=top, 1=bottom.").defineInRange("hudY", 0.04, 0.0, 1.0);
        hudScale = b.defineInRange("hudScale", 1.0, 0.6, 1.5);
        lockOnCamera = b.comment("Follow the selected target. New key supersedes legacy camera=false without overriding an explicit lockOnCamera preference.").define("lockOnCamera", true);
        specialCamera = b.comment("Separate opt-in policy for future cinematic effects; does not control lock-on.").define("specialCamera", false);
        b.pop().push("visuals");
        particles = b.define("particles", true);
        auraIntensity = b.defineInRange("auraIntensity", 0.6, 0.0, 1.0);
        effectDistance = b.defineInRange("effectDistance", 32, 8, 96);
        effectQuality = b.comment("0=minimal, 1=balanced, 2=more particles, all bounded.")
                .defineInRange("effectQuality", 1, 0, 2);
        explosionQuality = b.comment("Reserved visual quality policy for future terrain-neutral effects.")
                .defineInRange("explosionQuality", 0, 0, 2);
        b.pop(); SPEC = b.build();
    }
    private ClientConfig() {}
}
