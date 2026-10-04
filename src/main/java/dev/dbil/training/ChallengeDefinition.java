package dev.dbil.training;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Immutable training objectives and rewards; progress belongs to the player's persistent data. */
public record ChallengeDefinition(ResourceLocation id, Component displayName,
                                  @Nullable ResourceLocation prerequisite, List<Objective> objectives,
                                  int rewardXp, @Nullable ResourceLocation rewardTechnique,
                                  boolean unlockRaceTransformation) {
    public ChallengeDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        objectives = List.copyOf(objectives);
        String flag = "challenge:" + id;
        if (!flag.matches("[a-zA-Z0-9_:.\\-/]{1,64}") || id.equals(prerequisite)
                || objectives.isEmpty() || objectives.size() > 8 || rewardXp < 0 || rewardXp > 1_000_000) {
            throw new IllegalArgumentException("Invalid training challenge definition: " + id);
        }
    }

    public String completionFlag() { return "challenge:" + id; }

    /** Keys refer to server-produced training counters, except 'level', which reads current RPG level. */
    public record Objective(String key, double target) {
        public Objective {
            if (key == null || !key.matches("[a-zA-Z0-9_:.\\-/]{1,64}")
                    || !Double.isFinite(target) || target <= 0 || target > 1_000_000) {
                throw new IllegalArgumentException("Invalid training objective: " + key);
            }
        }

        public Component displayName() { return Component.translatable("challenge.dbil.objective." + key); }
    }
}
