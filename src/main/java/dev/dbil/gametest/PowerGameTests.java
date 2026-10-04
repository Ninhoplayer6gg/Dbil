package dev.dbil.gametest;

import dev.dbil.DBIL;
import dev.dbil.character.CharacterData;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.Races;
import dev.dbil.stats.Stat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Power mastery must reward broader training without diluting existing progress or growing without a cap. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerGameTests {
    private PowerGameTests() {}

    @GameTest(template = "empty", batch = "dbil_power")
    public static void learningLowMasteryDoesNotReduceBasePower(GameTestHelper helper) {
        CharacterData data = fixture();
        data.setMastery(DBIL.id("ki_wave"), 80);
        long before = PowerLevelCalculator.calculate(data, 1, false, false).basePower();
        double ki = data.ki();
        double stamina = data.stamina();
        CompoundTag attributes = data.save().getCompound("attributes").copy();
        data.setMastery(DBIL.id("ki_blast"), 1);
        long after = PowerLevelCalculator.calculate(data, 1, false, false).basePower();
        helper.assertTrue(after >= before,
                "Adding low mastery to a new technique cannot dilute an existing 80 mastery power bonus");
        helper.assertTrue(data.ki() == ki && data.stamina() == stamina
                        && data.save().getCompound("attributes").equals(attributes),
                "The mastery regression must keep attributes and reserves fixed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_power")
    public static void combinedMasteryBonusRemainsCapped(GameTestHelper helper) {
        CharacterData data = fixture();
        long untrained = PowerLevelCalculator.calculate(data, 1, false, false).basePower();
        data.setMastery(DBIL.id("ki_wave"), 100);
        long capped = PowerLevelCalculator.calculate(data, 1, false, false).basePower();
        helper.assertTrue(capped <= Math.round(untrained * 1.25) + 1,
                "Total mastery may increase base power by at most 25%, allowing only rounding tolerance");
        for (int i = 0; i < 120; i++) data.setMastery(DBIL.id("power_cap_test_" + i), Double.MAX_VALUE);
        data.setMastery(DBIL.id("invalid_mastery"), Double.NaN);
        long manyMasteries = PowerLevelCalculator.calculate(data, 1, false, false).basePower();
        helper.assertTrue(manyMasteries == capped,
                "Many large mastery entries must preserve the already reached aggregate cap exactly");
        for (Stat stat : Stat.values()) {
            helper.assertTrue(Double.isFinite(data.stat(stat)), "Mastery updates cannot corrupt attributes");
        }
        helper.succeed();
    }

    private static CharacterData fixture() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", CharacterData.SCHEMA_VERSION);
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Power mastery");
        tag.putString("race", Races.HUMAN.toString());
        tag.putInt("level", 1);
        CharacterData data = new CharacterData();
        data.load(tag);
        data.setKi(80);
        data.setStamina(65);
        return data;
    }
}
