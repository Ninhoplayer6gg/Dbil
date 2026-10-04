package dev.dbil.stats;

/** RPG attributes. Resource attributes represent capacity rather than vanilla hunger or XP. */
public enum Stat {
    STRENGTH("strength", 10),
    DEFENSE("defense", 10),
    SPEED("speed", 10),
    KI_POWER("ki_power", 10),
    KI_CONTROL("ki_control", 10),
    MAX_KI("max_ki", 120),
    MAX_STAMINA("max_stamina", 100),
    VITALITY("vitality", 10);

    private final String key;
    private final double initialValue;

    Stat(String key, double initialValue) {
        this.key = key;
        this.initialValue = initialValue;
    }

    public String key() { return key; }
    public double initialValue() { return initialValue; }
}
