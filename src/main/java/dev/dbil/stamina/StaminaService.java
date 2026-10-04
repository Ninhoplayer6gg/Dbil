package dev.dbil.stamina;
import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.server.PlayerState;
import net.minecraft.server.level.ServerPlayer;
public final class StaminaService {
    public static void tick(ServerPlayer p, CharacterData data, PlayerState s) {
        long tick = p.serverLevel().getGameTime();
        if (tick >= s.nextMeleeTick + 10 && tick >= s.nextDashTick && !s.techniqueCharging && !s.guarding && tick >= s.guardBreakUntil && s.transformationChargeTicks <= 0) {
            data.addStamina(ServerConfig.staminaRegen.get());
        }
    }
    private StaminaService() {}
}
