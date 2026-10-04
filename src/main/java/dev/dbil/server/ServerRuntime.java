package dev.dbil.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class ServerRuntime {
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    public static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }
    public static void clear(UUID id) { STATES.remove(id); }
    public static void clearAll() { STATES.clear(); }
    private ServerRuntime() {}
}
