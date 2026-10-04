package dev.dbil.character;

import dev.dbil.stats.Stat;

import java.util.List;
import java.util.Map;

/** Styles affect starting allocation only, leaving later progression open. */
public record CombatStyle(String id, String displayName, Map<Stat, Double> bonuses) {
    private static final List<CombatStyle> STYLES = List.of(
            new CombatStyle("balanced", "Equilibrado", Map.of()),
            new CombatStyle("brawler", "Lutador", Map.of(Stat.STRENGTH, 3.0, Stat.DEFENSE, 1.0, Stat.KI_CONTROL, -2.0)),
            new CombatStyle("speed", "Veloz", Map.of(Stat.SPEED, 3.0, Stat.MAX_STAMINA, 10.0, Stat.DEFENSE, -2.0)),
            new CombatStyle("ki_specialist", "Especialista em Ki", Map.of(Stat.KI_POWER, 3.0, Stat.KI_CONTROL, 2.0, Stat.STRENGTH, -2.0)),
            new CombatStyle("defensive", "Defensivo", Map.of(Stat.DEFENSE, 3.0, Stat.VITALITY, 2.0, Stat.SPEED, -2.0)));

    public CombatStyle {
        bonuses = Map.copyOf(bonuses);
        if (id == null || !id.matches("[a-z_]{1,32}") || displayName == null) throw new IllegalArgumentException("Invalid style");
    }
    public static CombatStyle get(String id) { return STYLES.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
    public static List<CombatStyle> values() { return STYLES; }
}
