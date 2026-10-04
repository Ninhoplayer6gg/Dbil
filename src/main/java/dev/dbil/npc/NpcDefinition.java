package dev.dbil.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Shared tunable identity and combat baseline for DBIL NPC entity implementations. */
public record NpcDefinition(ResourceLocation id, Component displayName, Role role,
                            double health, double attackDamage, double movementSpeed,
                            double detectionRange, int experienceReward) {
    public NpcDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(role);
        if (!Double.isFinite(health) || health < 1 || health > 10_000
                || !Double.isFinite(attackDamage) || attackDamage < 0 || attackDamage > 1_000
                || !Double.isFinite(movementSpeed) || movementSpeed <= 0 || movementSpeed > 1
                || !Double.isFinite(detectionRange) || detectionRange < 1 || detectionRange > 48
                || experienceReward < 0 || experienceReward > 10_000) {
            throw new IllegalArgumentException("Invalid NPC definition: " + id);
        }
    }

    public enum Role { ENEMY, MASTER, ALLY, MERCHANT, STORY }
}
