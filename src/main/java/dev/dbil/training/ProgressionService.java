package dev.dbil.training;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.config.ServerConfig;
import dev.dbil.network.Network;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Rewards are decided on the server, separate from vanilla XP orbs and enchanting levels. */
public final class ProgressionService {
    private ProgressionService() {}

    public static void award(ServerPlayer player, int experience) {
        CharacterData data = CharacterCapability.get(player);
        if (!data.created() || experience <= 0 || !player.isAlive()) return;
        RaceDefinition race = Races.get(data.raceId());
        double multiplier = ServerConfig.SPEC.isLoaded() ? ServerConfig.xpMultiplier.get() : 1.0;
        multiplier *= race == null ? 1.0 : race.experienceMultiplier();
        if (!Double.isFinite(multiplier) || multiplier <= 0) return;
        int previousLevel = data.level();
        data.addExperience(Math.max(1, Math.min(1_000_000L, Math.round(experience * multiplier))));
        data.recordTraining("combat", experience);
        CharacterService.applyAttributes(player, data);
        PowerLevelCalculator.update(data, player.getHealth() / Math.max(1, player.getMaxHealth()), false, false);
        if (data.level() > previousLevel) {
            player.displayClientMessage(Component.translatable("message.dbil.level_up", data.level()), false);
        }
        Network.sync(player);
    }
}
