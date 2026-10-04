package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.Origins;
import dev.dbil.race.Races;
import dev.dbil.server.ServerRuntime;
import dev.dbil.training.TrainingChallenges;
import dev.dbil.transformation.Transformations;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Completion tests exercise the real server reward service and persistent character markers. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChallengeGameTests {
    private ChallengeGameTests() { }

    @GameTest(template = "empty", batch = "dbil_challenges", timeoutTicks = 40)
    public static void firstRewardIsEquippedAndNotRepeatedAfterLoad(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 1);
        CharacterData data = CharacterCapability.get(player);
        data.recordTraining("training_defeats", 1);
        TrainingChallenges.tick(player, data);
        ResourceLocation blast = DBIL.id("ki_blast");
        helper.assertTrue(TrainingChallenges.completed(data, TrainingChallenges.FIRST_COMBAT)
                        && data.unlockedTechniques().contains(blast) && data.equippedTechniques().contains(blast),
                "The first credited training defeat must grant and equip Ki Blast");
        helper.assertTrue(data.experience() > 0 && player.totalExperience == 0,
                "Challenge XP must use RPG progression rather than vanilla XP");
        helper.assertTrue(!TrainingChallenges.completed(data, TrainingChallenges.KI_CONTROL),
                "The first reward cannot finish a later challenge without its objectives");
        CompoundTag saved = data.save();
        data.load(saved);
        TrainingChallenges.tick(player, data);
        helper.assertTrue(data.save().equals(saved), "A repeated same-tick request must not repeat a reward");
        helper.runAtTickTime(25, () -> {
            TrainingChallenges.tick(player, data);
            helper.assertTrue(data.save().equals(saved),
                    "Reloading a completed challenge must retain its durable reward marker and XP");
            cleanup(player);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dbil_challenges", timeoutTicks = 55)
    public static void controlRequiresAllCountersBeforeReward(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 1);
        CharacterData data = CharacterCapability.get(player);
        data.recordTraining("training_defeats", 3);
        data.recordTraining("flight_ticks", 1200);
        data.recordTraining("technique_hits", 4);
        TrainingChallenges.tick(player, data);
        helper.runAtTickTime(21, () -> {
            long experience = data.experience();
            TrainingChallenges.tick(player, data);
            helper.assertTrue(!TrainingChallenges.completed(data, TrainingChallenges.KI_CONTROL)
                            && !data.unlockedTechniques().contains(DBIL.id("ki_barrage")) && data.experience() == experience,
                    "Defeats and flight time cannot bypass a missing technique hit");
            data.recordTraining("technique_hits", 1);
        });
        helper.runAtTickTime(42, () -> {
            TrainingChallenges.tick(player, data);
            helper.assertTrue(TrainingChallenges.completed(data, TrainingChallenges.KI_CONTROL)
                            && data.equippedTechniques().contains(DBIL.id("ki_barrage")),
                    "The fifth credited hit completes the second challenge with its equipped reward");
            helper.assertTrue(!TrainingChallenges.completed(data, TrainingChallenges.AWAKENING),
                    "Completing Ki control must not bypass awakening's independent requirements");
            cleanup(player);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dbil_challenges", timeoutTicks = 80)
    public static void awakeningFollowsChainAndRaceDefinitions(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 3);
        CharacterData data = CharacterCapability.get(player);
        data.recordTraining("training_defeats", 6);
        data.recordTraining("flight_ticks", 1200);
        data.recordTraining("ki_charged", 250);
        data.recordTraining("technique_hits", 8);
        TrainingChallenges.tick(player, data);
        helper.assertTrue(TrainingChallenges.completed(data, TrainingChallenges.FIRST_COMBAT)
                        && !TrainingChallenges.completed(data, TrainingChallenges.KI_CONTROL)
                        && !TrainingChallenges.completed(data, TrainingChallenges.AWAKENING),
                "Only one challenge can grant a reward in the first evaluation, even when all counters are ready");
        helper.runAtTickTime(21, () -> {
            TrainingChallenges.tick(player, data);
            helper.assertTrue(TrainingChallenges.completed(data, TrainingChallenges.KI_CONTROL)
                            && !TrainingChallenges.completed(data, TrainingChallenges.AWAKENING),
                    "The second evaluation must complete the predecessor before awakening");
        });
        helper.runAtTickTime(42, () -> {
            TrainingChallenges.tick(player, data);
            helper.assertTrue(TrainingChallenges.completed(data, TrainingChallenges.AWAKENING),
                    "All awakening requirements and completed predecessors must unlock a race-compatible form");
            var matching = Transformations.values().stream()
                    .filter(form -> form.unlockCondition().evaluator().equals(TrainingChallenges.UNLOCK_EVALUATOR))
                    .filter(form -> TrainingChallenges.AWAKENING.toString().equals(form.unlockCondition().parameters().get("challenge")))
                    .toList();
            helper.assertTrue(!matching.isEmpty(), "Awakening must have registered transformation rewards");
            for (var form : matching) {
                helper.assertTrue(data.unlockedTransformations().contains(form.id()) == form.races().contains(data.raceId()),
                        "Transformation rewards must use race eligibility from their definition: " + form.id());
            }
        });
        helper.runAtTickTime(63, () -> {
            CompoundTag saved = data.save();
            data.load(saved);
            TrainingChallenges.tick(player, data);
            helper.assertTrue(data.save().equals(saved), "The full training chain must remain completed without repeated XP after load");
            cleanup(player);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dbil_challenges")
    public static void fullStoryFlagsCannotGrantUnmarkedRewards(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 1);
        CharacterData data = CharacterCapability.get(player);
        for (int i = 0; i < 256; i++) data.setStoryFlag("occupied_" + i);
        data.recordTraining("training_defeats", 1);
        CompoundTag before = data.save();
        TrainingChallenges.tick(player, data);
        helper.assertTrue(data.storyFlags().size() == 256 && data.save().equals(before),
                "Full story flags must postpone the challenge without granting XP or an unmarked technique");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_challenges")
    public static void fullEquippedSlotsKeepRewardPendingWithoutPartialUnlock(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 1);
        CharacterData data = CharacterCapability.get(player);
        for (int i = 0; i < CharacterData.MAX_EQUIPPED; i++) {
            ResourceLocation technique = DBIL.id("slot_fixture_" + i);
            data.learn(technique);
            data.equip(technique);
        }
        data.recordTraining("training_defeats", 1);
        CompoundTag before = data.save();
        TrainingChallenges.tick(player, data);
        helper.assertTrue(data.save().equals(before)
                        && !TrainingChallenges.completed(data, TrainingChallenges.FIRST_COMBAT),
                "A reward that cannot be equipped must not mark the challenge, grant XP or partially learn the technique");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_challenges")
    public static void protectedFutureSchemaNeverReceivesQuestMutation(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 3);
        CharacterData data = CharacterCapability.get(player);
        CompoundTag future = data.save();
        future.putInt("schemaVersion", CharacterData.SCHEMA_VERSION + 1);
        CompoundTag unknown = new CompoundTag();
        unknown.putString("quest", "future_contract");
        future.put("futureQuestSystem", unknown);
        data.load(future);
        TrainingChallenges.tick(player, data);
        helper.assertTrue(data.save().equals(future) && TrainingChallenges.progress(data).isEmpty(),
                "Protected future data must not be replaced or rewarded by this challenge evaluator");
        cleanup(player);
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, ResourceLocation race, int level) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DBIL-Challenges"));
        Vec3 position = helper.absoluteVec(new Vec3(2, 1, 2));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", CharacterData.SCHEMA_VERSION);
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Desafio");
        tag.putString("race", race.toString());
        tag.putString("origin", Origins.EARTH_WARRIOR.toString());
        tag.putString("combatStyle", "balanced");
        tag.putInt("level", level);
        CharacterCapability.get(player).load(tag);
        return player;
    }

    private static void cleanup(ServerPlayer player) {
        player.discard();
        ServerRuntime.clear(player.getUUID());
    }
}
