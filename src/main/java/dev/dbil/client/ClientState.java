package dev.dbil.client;

import dev.dbil.character.CharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** Server snapshots only. Client presentation never spends resources or changes attributes. */
public final class ClientState {
    private static CharacterData character = new CharacterData();
    private static final Map<Integer, VisualState> VISUALS = new HashMap<>();
    private static boolean received;
    private static long snapshotSerial;

    private ClientState() {}

    public static CharacterData data() { return character; }
    public static boolean received() { return received; }
    public static long snapshotSerial() { return snapshotSerial; }

    public static void acceptData(CompoundTag tag) {
        CharacterData replacement = new CharacterData();
        replacement.loadSnapshot(tag);
        character = replacement;
        received = true;
        snapshotSerial++;
    }

    public static void acceptState(int entityId, boolean charging, boolean flying, int targetId,
                                   int techniqueTicks, int cooldownTicks) {
        acceptState(entityId, charging, flying, targetId, techniqueTicks, cooldownTicks,
                false, 0, CharacterData.BASE_FORM, 0, 0, -1);
    }
    public static void acceptState(int entityId, boolean charging, boolean flying, int targetId,
                                   int techniqueTicks, int cooldownTicks, boolean guarding, int guardBreakTicks,
                                   net.minecraft.resources.ResourceLocation transformation, int transformationTicks,
                                   long power, long targetPower) {
        VISUALS.put(entityId, new VisualState(charging, flying, targetId,
                Math.max(0, techniqueTicks), Math.max(0, cooldownTicks), gameTime(), guarding,
                Math.max(0, guardBreakTicks), transformation, Math.max(0, transformationTicks),
                Math.max(0, power), Math.max(-1, targetPower)));
    }

    public static VisualState visual(int entityId) { return VISUALS.getOrDefault(entityId, VisualState.IDLE); }

    public static void reset() {
        character = new CharacterData();
        VISUALS.clear();
        received = false;
        snapshotSerial = 0;
        ClientControls.reset();
        ClientFlightController.reset();
    }

    /** Remove stale remote effects without scanning all world entities. */
    public static void prune() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            VISUALS.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        }
    }

    private static long gameTime() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    public record VisualState(boolean charging, boolean flying, int targetId, int techniqueTicks,
                              int cooldownTicks, long receivedTick, boolean guarding, int guardBreakTicks,
                              net.minecraft.resources.ResourceLocation transformation, int transformationTicks,
                              long power, long targetPower) {
        public static final VisualState IDLE = new VisualState(false, false, -1, 0, 0, 0,
                false, 0, CharacterData.BASE_FORM, 0, 0, -1);
        @Override public int cooldownTicks() { return remaining(cooldownTicks); }
        @Override public int guardBreakTicks() { return remaining(guardBreakTicks); }
        private int remaining(int ticks) {
            return Math.max(0, ticks - (int) Math.max(0, gameTime() - receivedTick));
        }
    }
}
