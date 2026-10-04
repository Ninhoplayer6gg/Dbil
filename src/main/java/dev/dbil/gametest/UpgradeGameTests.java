package dev.dbil.gametest;

import dev.dbil.DBIL;
import dev.dbil.character.CharacterData;
import dev.dbil.stats.Stat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Cross-version regressions protect the user's existing 0.1 worlds. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UpgradeGameTests {
    private UpgradeGameTests() {}

    @GameTest(template = "empty", batch = "dbil_upgrade")
    public static void existingVersionTwoCharacterKeepsProgress(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putInt("schemaVersion", 2);
        old.putBoolean("characterCreated", true);
        old.putString("characterName", "Existing warrior");
        old.putString("race", "dbil:saiyan");
        old.putString("origin", "dbil:survivor");
        old.putString("combatStyle", "brawler");
        old.putInt("level", 2);
        old.putLong("experience", 70);
        old.putDouble("currentKi", 47);
        old.putDouble("currentStamina", 81);
        CompoundTag stats = new CompoundTag();
        stats.putDouble("strength", 16);
        stats.putDouble(Stat.MAX_KI.key(), 125);
        stats.putDouble(Stat.MAX_STAMINA.key(), 103);
        old.put("attributes", stats);
        ListTag techniques = new ListTag();
        techniques.add(StringTag.valueOf("dbil:ki_wave"));
        old.put("unlockedTechniques", techniques.copy());
        old.put("equippedTechniques", techniques.copy());
        CompoundTag mastery = new CompoundTag();
        mastery.putDouble("dbil:ki_wave", 3.5);
        old.put("mastery", mastery);
        CompoundTag training = new CompoundTag();
        training.putDouble("ki_wave_casts", 70);
        old.put("trainingStats", training);
        ListTag flags = new ListTag();
        flags.add(StringTag.valueOf("private_story"));
        old.put("storyFlags", flags);
        CompoundTag untouched = old.copy();
        CharacterData data = new CharacterData();
        data.load(old);
        helper.assertTrue(old.equals(untouched), "Upgrade must not mutate the original world NBT");
        helper.assertTrue(data.created() && data.level() == 2 && data.experience() == 70
                && data.name().equals("Existing warrior") && data.raceId().equals(DBIL.id("saiyan")),
                "Existing identity and progression must survive upgrading");
        helper.assertTrue(data.stat(Stat.STRENGTH) == 16 && data.maxKi() == 125
                && data.ki() == 47 && data.maxStamina() == 103 && data.stamina() == 81,
                "Existing attributes and resource balances must remain unchanged");
        helper.assertTrue(data.mastery().get(DBIL.id("ki_wave")) == 3.5
                && data.trainingStats().get("ki_wave_casts") == 70 && data.storyFlags().contains("private_story"),
                "Old mastery, counters and story flags must be preserved");
        helper.assertTrue(data.selectedTechnique().equals(DBIL.id("ki_wave"))
                && data.schemaVersion() == CharacterData.SCHEMA_VERSION, "Upgrade initializes the new selected-technique channel");
        helper.succeed();
    }
}
