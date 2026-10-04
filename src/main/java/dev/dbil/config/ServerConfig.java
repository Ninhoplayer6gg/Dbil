package dev.dbil.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned tuning. Resource regeneration/drain values are per server tick. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue maxAttribute, maxLevel, syncInterval, kiBlastCooldown, kiBarrageCooldown, guardBreakTicks, sparringCooldownTicks, maxNearbyTrainingEnemies;
    public static final ForgeConfigSpec.DoubleValue xpMultiplier, damageMultiplier, kiRegen, kiCharge,
            staminaRegen, flightKiCost, dashKiCost, dashStaminaCost, maxFlightSpeed, techniqueKiCostMultiplier, npcDifficulty, kiBlastCost, kiBarrageCost,
            guardDamageReduction, guardStaminaPerDamage, transformationCostMultiplier, transformationDrainMultiplier;
    public static final ForgeConfigSpec.BooleanValue pvp;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("limits");
        maxAttribute = b.defineInRange("maxAttribute", 500, 120, 5000);
        maxLevel = b.defineInRange("maxLevel", 50, 1, 200);
        syncInterval = b.comment("Ticks between owner snapshots. 10 = two snapshots per second.")
                .defineInRange("syncInterval", 10, 5, 40);
        sparringCooldownTicks = b.defineInRange("sparringCooldownTicks", 600, 100, 2400);
        maxNearbyTrainingEnemies = b.defineInRange("maxNearbyTrainingEnemies", 4, 1, 8);
        maxFlightSpeed = b.defineInRange("maxFlightSpeed", 0.75, 0.1, 1.5);
        b.pop().push("balance");
        xpMultiplier = b.defineInRange("xpMultiplier", 1.0, 0.0, 10.0);
        damageMultiplier = b.defineInRange("damageMultiplier", 1.0, 0.1, 5.0);
        kiRegen = b.defineInRange("kiRegen", 0.06, 0.0, 5.0);
        kiCharge = b.defineInRange("kiCharge", 0.8, 0.0, 10.0);
        staminaRegen = b.defineInRange("staminaRegen", 0.35, 0.0, 10.0);
        flightKiCost = b.defineInRange("flightKiCost", 0.08, 0.01, 5.0);
        dashKiCost = b.defineInRange("dashKiCost", 3.0, 0.0, 100.0);
        dashStaminaCost = b.defineInRange("dashStaminaCost", 12.0, 1.0, 100.0);
        techniqueKiCostMultiplier = b.defineInRange("techniqueKiCostMultiplier", 1.0, 0.1, 5.0);
        npcDifficulty = b.comment("Training NPC damage multiplier.").defineInRange("npcDifficulty", 1.0, 0.25, 4.0);
        pvp = b.define("pvp", false);
        b.pop().push("techniques");
        kiBlastCost = b.defineInRange("kiBlastCost", 5.0, 0.1, 100.0);
        kiBarrageCost = b.defineInRange("kiBarrageCost", 24.0, 1.0, 200.0);
        kiBlastCooldown = b.defineInRange("kiBlastCooldown", 12, 4, 200);
        kiBarrageCooldown = b.defineInRange("kiBarrageCooldown", 60, 12, 400);
        b.pop().push("combat");
        guardDamageReduction = b.defineInRange("guardDamageReduction", 0.70, 0.0, 0.90);
        guardStaminaPerDamage = b.defineInRange("guardStaminaPerDamage", 2.0, 0.5, 10.0);
        guardBreakTicks = b.defineInRange("guardBreakTicks", 35, 10, 100);
        b.pop().push("transformations");
        transformationCostMultiplier = b.defineInRange("costMultiplier", 1.0, 0.1, 5.0);
        transformationDrainMultiplier = b.defineInRange("drainMultiplier", 1.0, 0.1, 5.0);
        b.pop(); SPEC = b.build();
    }
    private ServerConfig() {}
}
