package dev.dbil.character;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import dev.dbil.stats.Stat;
import dev.dbil.transformation.TransformationService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Server-only entry point for creation and Minecraft attribute integration. */
public final class CharacterService {
    private static final UUID HEALTH_MODIFIER = UUID.fromString("e6470a90-c5e6-4197-980e-0f0bb2784eb4");
    private static final UUID SPEED_MODIFIER = UUID.fromString("34e8d142-8bb2-44d1-ad22-02cc15e53f7c");
    private static final UUID FORM_SPEED_MODIFIER = UUID.fromString("4a7e0e78-49a5-43f8-a794-40e8cc76431b");
    private CharacterService() {}

    public static boolean create(ServerPlayer player, String name, ResourceLocation raceId,
                                 ResourceLocation originId, String styleId) {
        CharacterData data = CharacterCapability.get(player);
        if (!data.compatibleSchema() || data.created() || player.isSpectator() || !player.isAlive() || name == null
                || name.length() > 96) return false;
        String checkedName = CharacterData.boundedName(name);
        if (checkedName.isBlank() || !checkedName.equals(name.strip())
                || checkedName.codePointCount(0, checkedName.length()) > 24) return false;
        RaceDefinition race = Races.get(raceId);
        OriginDefinition origin = Origins.get(originId);
        CombatStyle style = CombatStyle.get(styleId);
        if (race == null || origin == null || !origin.allows(raceId) || style == null) return false;
        data.initialize(checkedName, race, origin, style);
        applyAttributes(player, data);
        player.setHealth(player.getMaxHealth());
        PowerLevelCalculator.update(data, 1.0, false, false);
        return true;
    }

    public static void applyAttributes(ServerPlayer player, CharacterData data) {
        double healthBonus = data.created() ? Math.min(80.0, data.stat(Stat.VITALITY) * 0.7) : 0;
        double speedBonus = data.created() ? Math.min(0.25, Math.max(0, data.stat(Stat.SPEED) - 10) * 0.003) : 0;
        replaceModifier(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER, "DBIL vitality", healthBonus,
                AttributeModifier.Operation.ADDITION);
        replaceModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER, "DBIL speed", speedBonus,
                AttributeModifier.Operation.MULTIPLY_BASE);
        double formSpeedBonus = data.created() ? Math.max(0, Math.min(0.5, TransformationService.multiplier(data, Stat.SPEED) - 1)) : 0;
        replaceModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), FORM_SPEED_MODIFIER, "DBIL form speed", formSpeedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void replaceModifier(AttributeInstance attribute, UUID id, String name,
                                        double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        AttributeModifier previous = attribute.getModifier(id);
        if (previous != null && previous.getAmount() == amount && previous.getOperation() == operation) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    public static void reset(ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!data.compatibleSchema()) {
            player.displayClientMessage(Component.literal("DBIL: dados de personagem de uma versão mais nova estão protegidos. Use a versão mais nova do mod."), false);
            return;
        }
        data.reset();
        applyAttributes(player, data);
    }

    /** Reapplies transient modifiers after cloning; this remains usable by future Other World respawns. */
    public static void respawn(ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        applyAttributes(player, data);
        if (data.created()) {
            data.setKi(data.maxKi());
            data.setStamina(data.maxStamina());
            player.setHealth(player.getMaxHealth());
            PowerLevelCalculator.update(data, 1.0, false, false);
        }
    }
}
