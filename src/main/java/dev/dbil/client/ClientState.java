package dev.dbil.client;

import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.character.CharacterData;
import dev.dbil.network.Network;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.Techniques;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Server snapshots only. Client presentation never spends resources or changes attributes. */
public final class ClientState {
    private static CharacterData character = new CharacterData();
    private static final Map<Integer, VisualState> VISUALS = new HashMap<>();
    private static final Map<Integer, AppearanceEntry> APPEARANCES = new HashMap<>();
    private static boolean received;
    private static long snapshotSerial;
    private static long appearanceSerial;

    private ClientState() {}

    public static CharacterData data() { return character; }
    public static boolean received() { return received; }
    public static long snapshotSerial() { return snapshotSerial; }
    public static long appearanceSerial() { return appearanceSerial; }

    public static void acceptData(CompoundTag tag) {
        CharacterData replacement = new CharacterData();
        replacement.loadSnapshot(tag);
        character = replacement;
        received = true;
        snapshotSerial++;
        // The owner's own appearance is also known from the snapshot, before the tracking packet arrives.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            APPEARANCES.put(minecraft.player.getId(), new AppearanceEntry(replacement.created(),
                    replacement.appearance(), replacement.raceId()));
            appearanceSerial++;
        }
    }

    public static void acceptState(Network.StateSnapshot p) {
        VISUALS.put(p.id(), new VisualState(p.charging(), p.flying(), p.target(), Math.max(0, p.charge()),
                Math.max(0, p.cooldown()), gameTime(), p.guarding(), Math.max(0, p.guardBreak()), p.form(),
                Math.max(0, p.transformTicks()), Math.max(0, p.power()), Math.max(-1, p.targetPower()),
                CharacterData.BASE_FORM.equals(p.technique()) ? null : p.technique(), p.techniqueCharge(),
                Math.max(0, p.transformTotal()), p.fastFlight(), p.inCombat(), p.formMastery(), p.techniqueHolding()));
    }

    public static void acceptAppearance(Network.AppearanceSync p) {
        APPEARANCES.put(p.entityId(), new AppearanceEntry(p.created(), p.appearance(), p.race()));
        appearanceSerial++;
    }

    public static VisualState visual(int entityId) { return VISUALS.getOrDefault(entityId, VisualState.IDLE); }

    /** Null when the entity has no DBIL character (vanilla rendering is used). */
    public static AppearanceEntry appearance(int entityId) {
        AppearanceEntry entry = APPEARANCES.get(entityId);
        return entry == null || !entry.created() ? null : entry;
    }

    public static void reset() {
        character = new CharacterData();
        VISUALS.clear();
        APPEARANCES.clear();
        received = false;
        snapshotSerial = 0;
        appearanceSerial++;
        ClientControls.reset();
        ClientFlightController.reset();
    }

    /** Remove stale remote effects without scanning all world entities. */
    public static void prune() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            VISUALS.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
            APPEARANCES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        }
    }

    static long gameTime() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    public record AppearanceEntry(boolean created, CharacterAppearance appearance, ResourceLocation race) {}

    public record VisualState(boolean charging, boolean flying, int targetId, int techniqueTicks,
                              int cooldownTicks, long receivedTick, boolean guarding, int guardBreakTicks,
                              ResourceLocation transformation, int transformationTicks,
                              long power, long targetPower, ResourceLocation technique, float techniqueCharge,
                              int transformationTotal, boolean fastFlight, boolean inCombat, float formMastery,
                              boolean techniqueHolding) {
        public static final VisualState IDLE = new VisualState(false, false, -1, 0, 0, 0,
                false, 0, CharacterData.BASE_FORM, 0, 0, -1, null, 0, 0, false, false, 0, false);
        @Override public int cooldownTicks() { return remaining(cooldownTicks); }
        @Override public int guardBreakTicks() { return remaining(guardBreakTicks); }
        @Override public int transformationTicks() { return remaining(transformationTicks); }
        public boolean transformed() { return !CharacterData.BASE_FORM.equals(transformation); }
        public boolean transforming() { return transformationTicks() > 0; }
        /** 0..1 progress of the current activation, extrapolated between 2 Hz snapshots. */
        public float transformationProgress() {
            if (transformationTotal <= 0 || transformationTicks <= 0) return 0;
            return Math.max(0, Math.min(1, 1 - transformationTicks() / (float) transformationTotal));
        }
        public boolean chargingTechnique() { return technique != null && (techniqueTicks > 0 || techniqueHolding); }
        /** Charge fraction extrapolated from the last snapshot while the owner keeps holding. */
        public float techniqueChargeNow(float partialTick) {
            if (technique == null) return 0;
            TechniqueProfile profile = Techniques.profile(technique);
            if (!profile.chargeable() || !techniqueHolding) return techniqueCharge;
            float elapsed = Math.max(0, gameTime() - receivedTick + partialTick);
            return Math.min(1, techniqueCharge + elapsed / profile.chargeMaxTicks());
        }
        private int remaining(int ticks) {
            return Math.max(0, ticks - (int) Math.max(0, gameTime() - receivedTick));
        }
    }
}
