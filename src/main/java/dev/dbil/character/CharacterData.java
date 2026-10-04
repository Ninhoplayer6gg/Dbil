package dev.dbil.character;

import dev.dbil.DBIL;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.config.ServerConfig;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import dev.dbil.stats.Stat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent player RPG data. Mutate on the logical server; clients receive a read-only-by-convention mirror.
 * All deserialization and resource mutations are bounded, including non-finite doubles and collection sizes.
 */
public final class CharacterData {
    public static final int SCHEMA_VERSION = 4;
    /** Loadout size. 0.2 used four slots; 0.3 adds beams, so six slots keep every starter technique equipped. */
    public static final int MAX_EQUIPPED = 6;
    public static final ResourceLocation BASE_FORM = new ResourceLocation("dbil", "base");
    private static final int MAX_DEFINITIONS = 128;
    private static final long MAX_EXPERIENCE = 10_000_000L;
    private final EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);
    private final Map<ResourceLocation, Double> mastery = new LinkedHashMap<>();
    private final Set<ResourceLocation> unlockedTechniques = new LinkedHashSet<>();
    private final Set<ResourceLocation> equippedTechniques = new LinkedHashSet<>();
    private final Set<ResourceLocation> unlockedTransformations = new LinkedHashSet<>();
    private final Map<String, Double> trainingStats = new LinkedHashMap<>();
    private final Set<String> storyFlags = new LinkedHashSet<>();
    private final Map<ResourceLocation, Double> masteryView = Collections.unmodifiableMap(mastery);
    private final Set<ResourceLocation> unlockedTechniquesView = Collections.unmodifiableSet(unlockedTechniques);
    private final Set<ResourceLocation> equippedTechniquesView = Collections.unmodifiableSet(equippedTechniques);
    private final Set<ResourceLocation> unlockedTransformationsView = Collections.unmodifiableSet(unlockedTransformations);
    private final Map<String, Double> trainingStatsView = Collections.unmodifiableMap(trainingStats);
    private final Set<String> storyFlagsView = Collections.unmodifiableSet(storyFlags);
    private CompoundTag protectedFutureSchema;
    private int lastWarnedSchema;
    private int snapshotAttributeLimit = -1;
    private int snapshotLevelLimit = -1;
    private boolean created;
    private String name = "";
    private ResourceLocation race = Races.HUMAN;
    private ResourceLocation origin = Origins.EARTH_WARRIOR;
    private String style = "balanced";
    private int level = 1;
    private long experience;
    private long basePower;
    private long currentPower;
    private double ki;
    private double stamina;
    private ResourceLocation transformation = BASE_FORM;
    private ResourceLocation selectedTechnique = new ResourceLocation("dbil", "ki_wave");
    private CharacterAppearance appearance = CharacterAppearance.HUMAN_DEFAULT;

    public CharacterData() { reset(); }

    public boolean created() { return created && compatibleSchema(); }
    public boolean compatibleSchema() { return protectedFutureSchema == null; }
    public int schemaVersion() { return compatibleSchema() ? SCHEMA_VERSION : protectedFutureSchema.getInt("schemaVersion"); }
    public String name() { return name; }
    public ResourceLocation raceId() { return race; }
    public ResourceLocation originId() { return origin; }
    public String styleId() { return style; }
    public int level() { return level; }
    public long experience() { return experience; }
    public long basePower() { return basePower; }
    public long currentPower() { return currentPower; }
    public double stat(Stat stat) { return Math.min(stats.getOrDefault(stat, stat.initialValue()), effectiveAttributeLimit()); }
    public double ki() { return Math.min(ki, maxKi()); }
    public double maxKi() { return stat(Stat.MAX_KI); }
    public double stamina() { return Math.min(stamina, maxStamina()); }
    public double maxStamina() { return stat(Stat.MAX_STAMINA); }
    public Map<ResourceLocation, Double> mastery() { return masteryView; }
    public Set<ResourceLocation> unlockedTechniques() { return unlockedTechniquesView; }
    public Set<ResourceLocation> equippedTechniques() { return equippedTechniquesView; }
    public Set<ResourceLocation> unlockedTransformations() { return unlockedTransformationsView; }
    public ResourceLocation currentTransformation() { return transformation; }
    public ResourceLocation selectedTechnique() { return selectedTechnique; }
    public Map<String, Double> trainingStats() { return trainingStatsView; }
    public Set<String> storyFlags() { return storyFlagsView; }
    public CharacterAppearance appearance() { return appearance; }

    /** Cosmetic only; the race filter removes traits such as the tail from races that do not have them. */
    public boolean setAppearance(CharacterAppearance value) {
        if (!compatibleSchema() || value == null) return false;
        appearance = value.forRace(race);
        return true;
    }

    public void setStat(Stat stat, double value) {
        if (!compatibleSchema()) return;
        if (stat == null) throw new IllegalArgumentException("Attribute cannot be null");
        stats.put(stat, finiteClamp(value, 1, effectiveAttributeLimit(), stat.initialValue()));
        ki = Math.min(ki, maxKi());
        stamina = Math.min(stamina, maxStamina());
    }
    public void setKi(double value) { if (!compatibleSchema()) return; ki = finiteClamp(value, 0, maxKi(), 0); }
    public void setStamina(double value) { if (!compatibleSchema()) return; stamina = finiteClamp(value, 0, maxStamina(), 0); }
    public void setPower(long base, long current) {
        if (!compatibleSchema()) return;
        basePower = Math.max(0, Math.min(base, 1_000_000_000L));
        currentPower = Math.max(0, Math.min(current, 1_000_000_000L));
    }
    public boolean canSpendKi(double cost) { return compatibleSchema() && validCost(cost) && ki() + 1.0e-8 >= cost; }
    public boolean spendKi(double cost) {
        if (!canSpendKi(cost)) return false;
        setKi(ki() - cost);
        return true;
    }
    public void addKi(double amount) { if (validCost(amount)) setKi(ki() + amount); }
    public boolean canSpendStamina(double cost) { return compatibleSchema() && validCost(cost) && stamina() + 1.0e-8 >= cost; }
    public boolean spendStamina(double cost) {
        if (!canSpendStamina(cost)) return false;
        setStamina(stamina() - cost);
        return true;
    }
    public void addStamina(double amount) { if (validCost(amount)) setStamina(stamina() + amount); }

    public boolean learn(ResourceLocation technique) {
        return compatibleSchema() && technique != null && unlockedTechniques.size() < MAX_DEFINITIONS && unlockedTechniques.add(technique);
    }
    public boolean equip(ResourceLocation technique) {
        if (!compatibleSchema() || technique == null || !unlockedTechniques.contains(technique)
                || equippedTechniques.size() >= MAX_EQUIPPED || !equippedTechniques.add(technique)) return false;
        if (!equippedTechniques.contains(selectedTechnique)) selectedTechnique = technique;
        return true;
    }
    /** The selected technique must stay equipped, so unequipping it moves selection to another slot. */
    public boolean unequip(ResourceLocation technique) {
        if (!compatibleSchema() || technique == null || equippedTechniques.size() <= 1
                || !equippedTechniques.remove(technique)) return false;
        if (technique.equals(selectedTechnique)) selectedTechnique = equippedTechniques.iterator().next();
        return true;
    }
    public boolean setSelectedTechnique(ResourceLocation id) {
        if (!compatibleSchema() || id == null || !unlockedTechniques.contains(id) || !equippedTechniques.contains(id)) return false;
        selectedTechnique = id;
        return true;
    }
    public boolean setRace(ResourceLocation raceId) {
        if (!compatibleSchema() || Races.get(raceId) == null) return false;
        race = raceId;
        appearance = appearance.forRace(race);
        OriginDefinition currentOrigin = Origins.get(origin);
        if (currentOrigin == null || !currentOrigin.allows(race)) origin = Origins.EARTH_WARRIOR;
        transformation = BASE_FORM;
        return true;
    }
    public void setMastery(ResourceLocation id, double value) {
        if (compatibleSchema() && id != null && (mastery.containsKey(id) || mastery.size() < MAX_DEFINITIONS)) {
            mastery.put(id, finiteClamp(value, 0, 100, 0));
        }
    }
    public void recordTraining(String key, double amount) {
        if (compatibleSchema() && validFlag(key) && validCost(amount) && (trainingStats.containsKey(key) || trainingStats.size() < MAX_DEFINITIONS)) {
            trainingStats.put(key, Math.min(1_000_000, trainingStats.getOrDefault(key, 0.0) + amount));
        }
    }
    public void setStoryFlag(String key) { if (compatibleSchema() && validFlag(key) && storyFlags.size() < 256) storyFlags.add(key); }
    public boolean unlockTransformation(ResourceLocation id) {
        return compatibleSchema() && id != null && unlockedTransformations.size() < MAX_DEFINITIONS && unlockedTransformations.add(id);
    }
    public boolean setTransformation(ResourceLocation id) {
        if (!compatibleSchema() || id == null || (!BASE_FORM.equals(id) && !unlockedTransformations.contains(id))) return false;
        transformation = id;
        return true;
    }

    /** Linear XP thresholds and additive growth deliberately avoid exponential runaway. */
    public long nextLevelExperience() { return 80L + 45L * level; }
    public void addExperience(long amount) {
        if (!created() || amount <= 0 || level >= effectiveLevelLimit()) return;
        experience = Math.min(MAX_EXPERIENCE, experience + Math.min(amount, MAX_EXPERIENCE));
        RaceDefinition definition = Races.get(race);
        while (level < effectiveLevelLimit() && experience >= nextLevelExperience()) {
            experience -= nextLevelExperience();
            level++;
            for (Stat stat : Stat.values()) {
                double growth = switch (stat) {
                    case MAX_KI -> 5.0;
                    case MAX_STAMINA -> 3.0;
                    default -> 0.65;
                };
                setStat(stat, stat(stat) + growth * (definition == null ? 1.0 : definition.growth(stat)));
            }
        }
        if (level >= effectiveLevelLimit()) experience = 0;
    }

    void initialize(String characterName, RaceDefinition definition, OriginDefinition originDefinition, CombatStyle combatStyle) {
        initialize(characterName, definition, originDefinition, combatStyle, CharacterAppearance.defaultFor(definition.id()));
    }

    void initialize(String characterName, RaceDefinition definition, OriginDefinition originDefinition, CombatStyle combatStyle,
                    CharacterAppearance chosenAppearance) {
        reset();
        created = true;
        name = characterName;
        race = definition.id();
        origin = originDefinition.id();
        style = combatStyle.id();
        appearance = (chosenAppearance == null ? CharacterAppearance.defaultFor(race) : chosenAppearance).forRace(race);
        for (Stat stat : Stat.values()) {
            setStat(stat, definition.base(stat) + originDefinition.initialBonuses().getOrDefault(stat, 0.0)
                    + combatStyle.bonuses().getOrDefault(stat, 0.0));
        }
        setKi(maxKi());
        setStamina(maxStamina());
        ResourceLocation firstTechnique = new ResourceLocation("dbil", "ki_wave");
        learn(firstTechnique);
        equip(firstTechnique);
        for (ResourceLocation technique : definition.racialTechniques()) learn(technique);
    }

    public void reset() {
        if (!compatibleSchema()) return;
        resetFields();
    }

    private void resetFields() {
        protectedFutureSchema = null;
        snapshotAttributeLimit = snapshotLevelLimit = -1;
        created = false;
        name = "";
        race = Races.HUMAN;
        origin = Origins.EARTH_WARRIOR;
        style = "balanced";
        level = 1;
        experience = 0;
        basePower = currentPower = 0;
        transformation = BASE_FORM;
        selectedTechnique = new ResourceLocation("dbil", "ki_wave");
        appearance = CharacterAppearance.HUMAN_DEFAULT;
        stats.clear();
        for (Stat stat : Stat.values()) stats.put(stat, stat.initialValue());
        ki = maxKi();
        stamina = maxStamina();
        mastery.clear();
        unlockedTechniques.clear();
        equippedTechniques.clear();
        unlockedTransformations.clear();
        trainingStats.clear();
        storyFlags.clear();
    }

    public CompoundTag save() {
        if (!compatibleSchema()) return protectedFutureSchema.copy();
        CompoundTag result = new CompoundTag();
        result.putInt("schemaVersion", SCHEMA_VERSION);
        result.putBoolean("characterCreated", created);
        result.putString("characterName", name);
        result.putString("race", race.toString());
        result.putString("origin", origin.toString());
        result.putString("combatStyle", style);
        result.putInt("level", level);
        result.putLong("experience", experience);
        result.putLong("basePower", basePower);
        result.putLong("currentPower", currentPower);
        CompoundTag attributes = new CompoundTag();
        for (Stat stat : Stat.values()) attributes.putDouble(stat.key(), stat(stat));
        result.put("attributes", attributes);
        result.putDouble("maxKi", maxKi());
        result.putDouble("currentKi", ki());
        result.putDouble("maxStamina", maxStamina());
        result.putDouble("currentStamina", stamina());
        CompoundTag masteryTag = new CompoundTag();
        mastery.forEach((id, value) -> masteryTag.putDouble(id.toString(), value));
        result.put("mastery", masteryTag);
        result.put("unlockedTechniques", saveIds(unlockedTechniques));
        result.put("equippedTechniques", saveIds(equippedTechniques));
        result.putString("selectedTechnique", selectedTechnique.toString());
        result.put("unlockedTransformations", saveIds(unlockedTransformations));
        result.putString("currentTransformation", transformation.toString());
        CompoundTag training = new CompoundTag();
        trainingStats.forEach(training::putDouble);
        result.put("trainingStats", training);
        ListTag flags = new ListTag();
        storyFlags.forEach(flag -> flags.add(StringTag.valueOf(flag)));
        result.put("storyFlags", flags);
        result.put("appearance", appearance.save());
        return result;
    }

    /** Snapshot caps are transport metadata; ordinary persistent loads always use server configuration. */
    public CompoundTag saveSnapshot() {
        CompoundTag tag = save();
        tag.putInt("snapshotAttributeLimit", effectiveAttributeLimit());
        tag.putInt("snapshotLevelLimit", effectiveLevelLimit());
        return tag;
    }

    public void load(CompoundTag input) { loadValidated(input, -1, -1); }

    public void loadSnapshot(CompoundTag input) {
        int attributeCap = input == null ? attributeLimit() : input.getInt("snapshotAttributeLimit");
        int levelCap = input == null ? levelLimit() : input.getInt("snapshotLevelLimit");
        loadValidated(input, attributeCap <= 0 ? attributeLimit() : Math.min(10_000, attributeCap),
                levelCap <= 0 ? levelLimit() : Math.min(1000, levelCap));
    }

    private void loadValidated(CompoundTag input, int attributeCap, int levelCap) {
        resetFields();
        snapshotAttributeLimit = attributeCap;
        snapshotLevelLimit = levelCap;
        if (input == null || input.isEmpty()) return;
        if (input.getInt("schemaVersion") > SCHEMA_VERSION) {
            protectedFutureSchema = input.copy();
            if (attributeCap > 0) {
                protectedFutureSchema.remove("snapshotAttributeLimit");
                protectedFutureSchema.remove("snapshotLevelLimit");
            }
            if (lastWarnedSchema != schemaVersion()) {
                lastWarnedSchema = schemaVersion();
                DBIL.LOGGER.warn("DBIL character schema {} is newer than supported schema {}. RPG actions are disabled; saved data is preserved. Use the newer DBIL version to load this character.",
                        schemaVersion(), SCHEMA_VERSION);
            }
            return;
        }
        CompoundTag tag = CharacterDataMigrations.upgrade(input);
        created = tag.getBoolean("characterCreated");
        name = boundedName(tag.getString("characterName"));
        if (created && name.isBlank()) name = "Guerreiro";
        ResourceLocation readRace = readId(tag.getString("race"));
        race = Races.get(readRace) == null ? Races.HUMAN : readRace;
        ResourceLocation readOrigin = readId(tag.getString("origin"));
        OriginDefinition originDefinition = Origins.get(readOrigin);
        origin = originDefinition != null && originDefinition.allows(race) ? readOrigin : Origins.EARTH_WARRIOR;
        String readStyle = tag.getString("combatStyle");
        style = CombatStyle.get(readStyle) == null ? "balanced" : readStyle;
        level = Math.max(1, Math.min(tag.getInt("level"), effectiveLevelLimit()));
        experience = level >= effectiveLevelLimit() ? 0
                : Math.max(0, Math.min(tag.getLong("experience"), nextLevelExperience() - 1));
        CompoundTag attributes = tag.getCompound("attributes");
        for (Stat stat : Stat.values()) {
            if (attributes.contains(stat.key(), Tag.TAG_ANY_NUMERIC)) setStat(stat, attributes.getDouble(stat.key()));
        }
        setKi(tag.contains("currentKi", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("currentKi") : maxKi());
        setStamina(tag.contains("currentStamina", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("currentStamina") : maxStamina());
        setPower(tag.getLong("basePower"), tag.getLong("currentPower"));
        CompoundTag masteryTag = tag.getCompound("mastery");
        int readCount = 0;
        for (String key : masteryTag.getAllKeys()) {
            if (readCount++ >= MAX_DEFINITIONS) break;
            ResourceLocation id = readId(key);
            if (id != null) setMastery(id, masteryTag.getDouble(key));
        }
        loadIds(tag.getList("unlockedTechniques", Tag.TAG_STRING), unlockedTechniques, MAX_DEFINITIONS);
        loadIds(tag.getList("equippedTechniques", Tag.TAG_STRING), equippedTechniques, MAX_EQUIPPED);
        equippedTechniques.retainAll(unlockedTechniques);
        ResourceLocation selected = readId(tag.getString("selectedTechnique"));
        if (!setSelectedTechnique(selected)) {
            selectedTechnique = equippedTechniques.stream().findFirst().orElse(new ResourceLocation("dbil", "ki_wave"));
        }
        loadIds(tag.getList("unlockedTransformations", Tag.TAG_STRING), unlockedTransformations, MAX_DEFINITIONS);
        ResourceLocation readTransformation = readId(tag.getString("currentTransformation"));
        setTransformation(readTransformation);
        CompoundTag training = tag.getCompound("trainingStats");
        readCount = 0;
        for (String key : training.getAllKeys()) {
            if (readCount++ >= MAX_DEFINITIONS) break;
            recordTraining(key, finiteClamp(training.getDouble(key), 0, 1_000_000, 0));
        }
        ListTag flags = tag.getList("storyFlags", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(256, flags.size()); i++) setStoryFlag(flags.getString(i));
        appearance = CharacterAppearance.load(tag.getCompound("appearance"), race);
    }

    public void copyFrom(CharacterData other) { load(other.save()); }

    static String boundedName(String input) {
        if (input == null) return "";
        if (input.length() > 96) input = input.substring(0, 96);
        String cleaned = input.strip().replaceAll("[\\p{Cntrl}§]", "");
        return cleaned.codePointCount(0, cleaned.length()) <= 24 ? cleaned
                : cleaned.substring(0, cleaned.offsetByCodePoints(0, 24));
    }
    private static boolean validCost(double value) { return Double.isFinite(value) && value >= 0; }
    private static boolean validFlag(String key) { return key != null && key.matches("[a-zA-Z0-9_:.\\-/]{1,64}"); }
    private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
        return Math.max(minimum, Math.min(Double.isFinite(value) ? value : fallback, maximum));
    }
    private static ResourceLocation readId(String value) {
        return value == null || value.length() > 128 ? null : ResourceLocation.tryParse(value);
    }
    private static ListTag saveIds(Set<ResourceLocation> ids) {
        ListTag result = new ListTag();
        ids.forEach(id -> result.add(StringTag.valueOf(id.toString())));
        return result;
    }
    private static void loadIds(ListTag list, Set<ResourceLocation> destination, int limit) {
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            ResourceLocation id = readId(list.getString(i));
            if (id != null) destination.add(id);
        }
    }
    private int effectiveAttributeLimit() { return snapshotAttributeLimit > 0 ? snapshotAttributeLimit : attributeLimit(); }
    private int effectiveLevelLimit() { return snapshotLevelLimit > 0 ? snapshotLevelLimit : levelLimit(); }

    public static int attributeLimit() {
        if (!ServerConfig.SPEC.isLoaded()) return 500;
        return Math.max(1, Math.min(10_000, ServerConfig.maxAttribute.get()));
    }
    public static int levelLimit() {
        if (!ServerConfig.SPEC.isLoaded()) return 50;
        return Math.max(1, Math.min(1000, ServerConfig.maxLevel.get()));
    }
}
