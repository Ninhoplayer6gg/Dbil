package dev.dbil.power;

import dev.dbil.character.CharacterData;
import dev.dbil.stats.Stat;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent power model: attributes determine potential; reserves, health and state determine output. */
public final class PowerLevelCalculator {
    private static final Map<ResourceLocation, PowerModifier> MODIFIERS = new LinkedHashMap<>();
    private static final double MAX_AGGREGATE_MASTERY = 100.0;
    private static final double MASTERY_POWER_DIVISOR = 400.0;
    private PowerLevelCalculator() {}

    public record Context(double healthFraction, boolean charging, boolean flying) {}
    public record Result(long basePower, long currentPower) {}

    /** Future transformation/buff/suppression systems register output modifiers during common setup. */
    @FunctionalInterface
    public interface PowerModifier {
        double multiplier(CharacterData data, Context context);
    }

    public static synchronized void registerModifier(ResourceLocation id, PowerModifier modifier) {
        if (MODIFIERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(modifier)) != null) {
            throw new IllegalArgumentException("Duplicate DBIL power modifier: " + id);
        }
    }

    public static Result calculate(CharacterData data, double healthFraction, boolean charging, boolean flying) {
        if (!data.created()) return new Result(0, 0);
        double attributes = data.stat(Stat.STRENGTH) * 2.0 + data.stat(Stat.DEFENSE) * 1.4
                + data.stat(Stat.SPEED) * 1.2 + data.stat(Stat.KI_POWER) * 2.2
                + data.stat(Stat.KI_CONTROL) * 1.4 + data.stat(Stat.VITALITY) * 1.3
                + data.maxKi() * 0.06 + data.maxStamina() * 0.04;
        double mastery = 0;
        for (double value : data.mastery().values()) {
            if (!Double.isFinite(value) || value <= 0) continue;
            mastery = Math.min(MAX_AGGREGATE_MASTERY, mastery + value);
            if (mastery >= MAX_AGGREGATE_MASTERY) break;
        }
        // Learning a new technique must not dilute existing mastery. Total power bonus remains at most 25%.
        double masteryFactor = 1 + mastery / MASTERY_POWER_DIVISOR;
        long base = Math.max(1, Math.round(attributes * 10 * masteryFactor));
        double health = Double.isFinite(healthFraction) ? Math.max(0, Math.min(1, healthFraction)) : 1;
        double kiFactor = 0.35 + 0.65 * data.ki() / Math.max(1, data.maxKi());
        double staminaFactor = 0.85 + 0.15 * data.stamina() / Math.max(1, data.maxStamina());
        double state = (0.45 + 0.55 * health) * kiFactor * staminaFactor;
        if (charging) state *= 1.02;
        if (flying) state *= 0.98;
        Context context = new Context(health, charging, flying);
        for (PowerModifier modifier : MODIFIERS.values()) {
            double factor = modifier.multiplier(data, context);
            if (Double.isFinite(factor)) state *= Math.max(0, Math.min(100, factor));
        }
        long current = Math.max(0, Math.min(1_000_000_000L, Math.round(base * state)));
        return new Result(base, current);
    }

    public static void update(CharacterData data, double healthFraction, boolean charging, boolean flying) {
        Result result = calculate(data, healthFraction, charging, flying);
        data.setPower(result.basePower(), result.currentPower());
    }
}
