package dev.dbil.technique;

import dev.dbil.DBIL;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Techniques {
    public static final ResourceLocation KI_WAVE = DBIL.id("ki_wave");
    public static final ResourceLocation KI_BLAST = DBIL.id("ki_blast");
    public static final ResourceLocation KI_BARRAGE = DBIL.id("ki_barrage");
    public static final ResourceLocation KAMEHAMEHA = DBIL.id("kamehameha");
    public static final ResourceLocation GALICK_GUN = DBIL.id("galick_gun");
    public static final ResourceLocation MASENKO = DBIL.id("masenko");
    public static final ResourceLocation BEAM_CLASH_GROUP = DBIL.id("energy_beam");
    private static final Map<ResourceLocation, TechniqueDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, TechniqueProfile> PROFILES = new LinkedHashMap<>();
    private static final TechniqueProfile FALLBACK = new TechniqueProfile(DBIL.id("fallback"), 0, 1, 1, 1, 1, 0.4F,
            0x66CCFF, 0xF0FAFF, TechniqueProfile.Pose.BLAST, 0);
    private static boolean initialized;

    private Techniques() { }

    public static synchronized void bootstrap() {
        if (initialized) return;
        // Ki Wave: the original 0.2 starter shot, now chargeable into a large sphere.
        register(new TechniqueDefinition(KI_WAVE, Component.translatable("technique.dbil.ki_wave"),
                TechniqueType.KI_BLAST, 12, 0, 7.0F, 12, 50, 1.15, 32, 0.175,
                0.55, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of()), 0, null,
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.20, 0)),
                new TechniqueProfile(KI_WAVE, 30, 2.0, 2.2, 1.7, 2.2, 0.32F, 0x55C3FF, 0xE8FAFF, TechniqueProfile.Pose.WAVE, 0));
        // Ki Blast: fast, cheap and spammable; a quick arm thrust.
        register(new TechniqueDefinition(KI_BLAST, Component.translatable("technique.dbil.ki_blast"),
                TechniqueType.KI_BLAST, 5, 0, 2.5F, 2, 12, 1.45, 24, 0.125,
                0.16, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of(KI_WAVE)), 0, null,
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.10, 0)),
                new TechniqueProfile(KI_BLAST, 0, 1, 1, 1, 1, 0.2F, 0x7FD6FF, 0xF2FCFF, TechniqueProfile.Pose.BLAST, 0));
        // Ki Barrage: six smaller shots from alternating hands, one payment.
        register(new TechniqueDefinition(KI_BARRAGE, Component.translatable("technique.dbil.ki_barrage"),
                TechniqueType.BARRAGE, 24, 3, 1.6F, 8, 60, 1.25, 28, 0.13,
                0.12, false, false, false,
                new TechniqueDefinition.Requirements(1, Set.of(), Set.of(KI_BLAST)), 0, null,
                new TechniqueDefinition.ProjectilePattern(6, 3, 0.06, 4.0)),
                new TechniqueProfile(KI_BARRAGE, 0, 1, 1, 1, 1, 0.17F, 0x6FD0FF, 0xEAF9FF, TechniqueProfile.Pose.BARRAGE, 0));
        // Kamehameha: balanced blue beam with a long, rewarding charge.
        register(new TechniqueDefinition(KAMEHAMEHA, Component.translatable("technique.dbil.kamehameha"),
                TechniqueType.BEAM, 30, 4, 15.0F, 10, 120, 1.7, 40, 0.55,
                1.0, true, true, false,
                new TechniqueDefinition.Requirements(2, Set.of(), Set.of(KI_WAVE)), 0,
                new TechniqueDefinition.BeamProperties(0.9, 30, BEAM_CLASH_GROUP, true),
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.32, 0)),
                new TechniqueProfile(KAMEHAMEHA, 40, 2.2, 2.4, 1.8, 1.6, 0.45F, 0x3FA8FF, 0xEFFBFF, TechniqueProfile.Pose.KAMEHAMEHA, 2.5F));
        // Galick Gun: violet beam; quicker charge, more damage and push, slower travel, narrower and shorter.
        register(new TechniqueDefinition(GALICK_GUN, Component.translatable("technique.dbil.galick_gun"),
                TechniqueType.BEAM, 34, 6, 17.0F, 8, 130, 1.3, 36, 0.48,
                1.3, true, true, false,
                new TechniqueDefinition.Requirements(3, Set.of(), Set.of(KI_WAVE)), 0,
                new TechniqueDefinition.BeamProperties(0.75, 24, BEAM_CLASH_GROUP, true),
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.36, 0)),
                new TechniqueProfile(GALICK_GUN, 32, 2.3, 2.6, 2.0, 1.5, 0.4F, 0x9A4BFF, 0xF6EAFF, TechniqueProfile.Pose.GALICK_GUN, 2.0F));
        // Masenko: yellow, overhead pose, very fast charge and travel, wide but weaker.
        register(new TechniqueDefinition(MASENKO, Component.translatable("technique.dbil.masenko"),
                TechniqueType.BEAM, 22, 3, 10.5F, 6, 90, 2.1, 34, 0.62,
                0.85, true, true, false,
                new TechniqueDefinition.Requirements(2, Set.of(), Set.of(KI_WAVE)), 0,
                new TechniqueDefinition.BeamProperties(1.05, 18, BEAM_CLASH_GROUP, true),
                new TechniqueDefinition.ProjectilePattern(1, 0, 0.26, 0)),
                new TechniqueProfile(MASENKO, 18, 1.8, 2.0, 1.5, 1.4, 0.5F, 0xFFD23C, 0xFFFBE2, TechniqueProfile.Pose.MASENKO, 3.0F));
        initialized = true;
    }

    public static void register(TechniqueDefinition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate technique: " + definition.id());
        }
    }

    public static void register(TechniqueDefinition definition, TechniqueProfile profile) {
        if (!definition.id().equals(profile.id())) throw new IllegalArgumentException("Profile id mismatch: " + profile.id());
        register(definition);
        PROFILES.put(profile.id(), profile);
    }

    @Nullable
    public static TechniqueDefinition get(ResourceLocation id) { return DEFINITIONS.get(id); }

    /** Never null: unknown or third-party techniques receive a neutral non-chargeable profile. */
    public static TechniqueProfile profile(ResourceLocation id) {
        TechniqueProfile profile = id == null ? null : PROFILES.get(id);
        return profile == null ? FALLBACK : profile;
    }

    public static Collection<TechniqueDefinition> values() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }
}
