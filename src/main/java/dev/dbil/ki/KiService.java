package dev.dbil.ki;

import dev.dbil.character.CharacterData;
import dev.dbil.config.ServerConfig;
import dev.dbil.server.PlayerState;
import dev.dbil.stats.Stat;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class KiService {
    private static final UUID CHARGE_SLOW = UUID.fromString("e60bebef-5b5e-4895-b4ee-31f7f8e5e81");
    public static void tick(ServerPlayer player, CharacterData data, PlayerState state) {
        // 0.3: powering up also works while hovering; flight upkeep is still paid separately.
        if (state.charging && !state.techniqueCharging && !state.guarding && state.transformationChargeTicks <= 0) {
            data.addKi(ServerConfig.kiCharge.get() * (1 + Math.min(0.5, data.stat(Stat.KI_CONTROL) / 100)));
            player.setSprinting(false);
        } else if (!state.flying && !state.techniqueCharging && state.transformationChargeTicks <= 0 && player.tickCount % 2 == 0) {
            data.addKi(ServerConfig.kiRegen.get() * 2);
        }
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            if (state.charging && movement.getModifier(CHARGE_SLOW) == null) {
                movement.addTransientModifier(new AttributeModifier(CHARGE_SLOW, "DBIL Ki charge", -0.65,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!state.charging) movement.removeModifier(CHARGE_SLOW);
        }
    }
    public static void clearSlow(ServerPlayer p) {
        var attribute = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(CHARGE_SLOW);
    }
    private KiService() {}
}
