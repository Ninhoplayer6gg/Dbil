package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.character.Origins;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.Races;
import dev.dbil.server.ServerEvents;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.transformation.TransformationDefinition;
import dev.dbil.transformation.TransformationEligibility;
import dev.dbil.transformation.TransformationService;
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

/** Gameplay regressions for temporary form modifiers, authorization and their resource economy. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransformationGameTests {
    private TransformationGameTests() {}

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void bootstrapIsIdempotentAndPowerIsTemporary(GameTestHelper helper) {
        Transformations.bootstrap();
        int definitions = Transformations.values().size();
        CharacterData data = fixture(Races.SAIYAN, 3);
        data.unlockTransformation(Transformations.SUPER_SAIYAN);
        var base = PowerLevelCalculator.calculate(data, 1, false, false);
        data.setTransformation(Transformations.SUPER_SAIYAN);
        var transformed = PowerLevelCalculator.calculate(data, 1, false, false);
        for (int call = 0; call < 10; call++) Transformations.bootstrap();
        helper.assertTrue(Transformations.values().size() == definitions,
                "Repeated setup must not duplicate form definitions");
        helper.assertTrue(PowerLevelCalculator.calculate(data, 1, false, false).equals(transformed),
                "Repeated setup must not multiply power modifiers again");
        helper.assertTrue(transformed.basePower() == base.basePower() && transformed.currentPower() > base.currentPower(),
                "A form raises current output without changing the character's underlying potential");
        data.setTransformation(CharacterData.BASE_FORM);
        helper.assertTrue(PowerLevelCalculator.calculate(data, 1, false, false).equals(base),
                "Returning to base must restore the same power at the same resources and condition");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void activationRejectsLockedWrongRaceAndLowLevel(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 3);
        CharacterData data = CharacterCapability.get(player);
        double ki = data.ki();
        helper.assertTrue(TransformationService.start(player, Transformations.POTENTIAL_UNLEASHED)
                        == TransformationEligibility.Result.LOCKED && data.ki() == ki,
                "A transformation cannot be activated before its server unlock");
        data.unlockTransformation(Transformations.SUPER_SAIYAN);
        helper.assertTrue(TransformationService.start(player, Transformations.SUPER_SAIYAN)
                        == TransformationEligibility.Result.WRONG_RACE && data.ki() == ki,
                "An unlock alone cannot bypass a form's race rule");
        data.copyFrom(fixture(Races.HUMAN, 1));
        data.unlockTransformation(Transformations.POTENTIAL_UNLEASHED);
        helper.assertTrue(TransformationService.start(player, Transformations.POTENTIAL_UNLEASHED)
                        == TransformationEligibility.Result.REQUIREMENTS,
                "An administrative unlock must not bypass the level requirement");
        helper.assertTrue(ServerRuntime.state(player).pendingTransformation == null
                        && ServerRuntime.state(player).transformationChargeTicks == 0,
                "Rejected transformations must not leave a pending charge");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void failedActivationLeavesResourcesAndCooldownUnchanged(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.SAIYAN, 3);
        CharacterData data = CharacterCapability.get(player);
        data.unlockTransformation(Transformations.SUPER_SAIYAN);
        data.setKi(0);
        double stamina = data.stamina();
        CompoundTag attributes = data.save().getCompound("attributes").copy();
        helper.assertTrue(TransformationService.start(player, Transformations.SUPER_SAIYAN)
                        == TransformationEligibility.Result.INSUFFICIENT_KI,
                "A transformation must reject an unaffordable activation cost");
        helper.assertTrue(data.ki() == 0 && data.stamina() == stamina
                        && data.save().getCompound("attributes").equals(attributes)
                        && ServerRuntime.state(player).nextTransformationTick == 0,
                "A rejected request must not deduct another resource, alter attributes or create a cooldown");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void activationAndReversionNeverAccumulateBaseAttributes(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.SAIYAN, 3);
        CharacterData data = CharacterCapability.get(player);
        data.unlockTransformation(Transformations.SUPER_SAIYAN);
        TransformationDefinition definition = Transformations.get(Transformations.SUPER_SAIYAN).orElseThrow();
        CompoundTag attributes = data.save().getCompound("attributes").copy();
        double strength = data.stat(Stat.STRENGTH);
        double maxKi = data.maxKi();
        float healthCapacity = player.getMaxHealth();
        for (int cycle = 0; cycle < 3; cycle++) {
            ServerEvents.resetSession(player);
            data.setKi(data.maxKi());
            double before = data.ki();
            helper.assertTrue(TransformationService.start(player, definition.id()) == TransformationEligibility.Result.READY,
                    "An eligible, funded player must begin activation");
            double charged = data.ki();
            helper.assertTrue(Math.abs(before - charged - TransformationService.activationCost(definition)) < 0.00001,
                    "Activation must deduct exactly one server-defined Ki cost");
            helper.assertTrue(TransformationService.start(player, definition.id()) == TransformationEligibility.Result.BUSY
                            && data.ki() == charged,
                    "Repeated requests while charging must not charge the cost twice");
            completeCharge(player);
            helper.assertTrue(data.currentTransformation().equals(definition.id())
                            && TransformationService.multiplier(data, Stat.STRENGTH) == 1.5,
                    "Completing the server charge must activate the registered multiplier");
            helper.assertTrue(data.stat(Stat.STRENGTH) == strength && data.maxKi() == maxKi
                            && player.getMaxHealth() == healthCapacity
                            && data.save().getCompound("attributes").equals(attributes),
                    "Form activation must never write multipliers into base attributes or health/resource pools");
            TransformationService.revert(player);
            helper.assertTrue(TransformationService.multiplier(data, Stat.STRENGTH) == 1
                            && data.save().getCompound("attributes").equals(attributes),
                    "Reversion must restore base modifiers without accumulated growth");
        }
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void damageAndMovementInterruptActivationWithoutMastery(GameTestHelper helper) {
        ServerPlayer damaged = player(helper, Races.HUMAN, 3);
        CharacterData damagedData = CharacterCapability.get(damaged);
        damagedData.unlockTransformation(Transformations.POTENTIAL_UNLEASHED);
        helper.assertTrue(TransformationService.start(damaged, Transformations.POTENTIAL_UNLEASHED)
                == TransformationEligibility.Result.READY, "Damage interruption fixture must begin activation");
        double paidKi = damagedData.ki();
        damaged.setHealth(damaged.getHealth() - 1);
        TransformationService.tick(damaged, damagedData, ServerRuntime.state(damaged));
        helper.assertTrue(damagedData.currentTransformation().equals(CharacterData.BASE_FORM)
                        && ServerRuntime.state(damaged).transformationChargeTicks == 0
                        && damagedData.ki() == paidKi,
                "Damage must interrupt an unfinished charge without refunding its already committed cost");
        helper.assertTrue(damagedData.mastery().getOrDefault(Transformations.POTENTIAL_UNLEASHED, 0.0) == 0,
                "An interrupted activation must not grant mastery");
        helper.assertTrue(TransformationService.start(damaged, Transformations.POTENTIAL_UNLEASHED)
                        == TransformationEligibility.Result.COOLDOWN,
                "Interruption must retain its activation cooldown");
        cleanup(damaged);

        ServerPlayer moving = player(helper, Races.HUMAN, 3);
        CharacterData movingData = CharacterCapability.get(moving);
        movingData.unlockTransformation(Transformations.POTENTIAL_UNLEASHED);
        helper.assertTrue(TransformationService.start(moving, Transformations.POTENTIAL_UNLEASHED)
                == TransformationEligibility.Result.READY, "Movement interruption fixture must begin activation");
        moving.setPos(moving.getX() + 0.5, moving.getY(), moving.getZ());
        TransformationService.tick(moving, movingData, ServerRuntime.state(moving));
        helper.assertTrue(movingData.currentTransformation().equals(CharacterData.BASE_FORM)
                        && ServerRuntime.state(moving).pendingTransformation == null,
                "Moving away from the activation position must interrupt a charge");
        cleanup(moving);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void drainAndExhaustionPreserveUnlockAndMastery(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.HUMAN, 3);
        CharacterData data = CharacterCapability.get(player);
        data.unlockTransformation(Transformations.POTENTIAL_UNLEASHED);
        data.setMastery(Transformations.POTENTIAL_UNLEASHED, 40);
        helper.assertTrue(TransformationService.start(player, Transformations.POTENTIAL_UNLEASHED)
                == TransformationEligibility.Result.READY, "Exhaustion fixture must begin activation");
        completeCharge(player);
        double before = data.ki();
        TransformationService.tick(player, data, ServerRuntime.state(player));
        helper.assertTrue(data.ki() < before, "An active form must continuously pay Ki drain on the server");
        data.setKi(0);
        TransformationService.tick(player, data, ServerRuntime.state(player));
        helper.assertTrue(data.currentTransformation().equals(CharacterData.BASE_FORM)
                        && TransformationService.multiplier(data, Stat.KI_POWER) == 1,
                "Ki exhaustion must remove the active form and all temporary multipliers");
        helper.assertTrue(data.unlockedTransformations().contains(Transformations.POTENTIAL_UNLEASHED)
                        && data.mastery().get(Transformations.POTENTIAL_UNLEASHED) == 40,
                "Exhaustion must preserve the player's permanent unlock and mastery");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void masteryGrowsOnlyInUseAndHasTimeBudgets(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.SAIYAN, 3);
        CharacterData data = CharacterCapability.get(player);
        ResourceLocation form = Transformations.SUPER_SAIYAN;
        data.unlockTransformation(form);
        helper.assertTrue(TransformationService.start(player, form) == TransformationEligibility.Result.READY,
                "Mastery fixture must begin activation");
        TransformationService.revert(player);
        helper.assertTrue(data.mastery().getOrDefault(form, 0.0) == 0,
                "Starting and cancelling a form must never award mastery");
        ServerEvents.resetSession(player);
        data.setKi(data.maxKi());
        helper.assertTrue(TransformationService.start(player, form) == TransformationEligibility.Result.READY,
                "Mastery fixture must begin a fresh activation");
        completeCharge(player);
        helper.assertTrue(data.mastery().getOrDefault(form, 0.0) == 0, "Completing activation alone must not award mastery");
        var state = ServerRuntime.state(player);
        long now = player.serverLevel().getGameTime();
        state.lastTransformationMasteryTick = now - 100;
        TransformationService.tick(player, data, state);
        double activeGain = data.mastery().get(form);
        helper.assertTrue(activeGain > 0 && activeGain <= 0.1, "Time spent in an active form must grant modest mastery");
        for (int request = 0; request < 20; request++) TransformationService.tick(player, data, state);
        helper.assertTrue(data.mastery().get(form) == activeGain, "Repeated processing in one server tick cannot spam mastery");
        state.lastTransformationCombatTick = now - 20;
        TransformationService.recordCombat(player);
        double combatGain = data.mastery().get(form);
        for (int hit = 0; hit < 100; hit++) TransformationService.recordCombat(player);
        helper.assertTrue(combatGain > activeGain && data.mastery().get(form) == combatGain,
                "Successful combat can improve mastery only within a bounded time budget");
        data.setMastery(form, 100);
        state.lastTransformationMasteryTick = now - 100;
        TransformationService.tick(player, data, state);
        helper.assertTrue(data.mastery().get(form) == 100, "Use cannot exceed the definition's mastery cap");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_transformations")
    public static void masteryImprovesDrainAndChargeButResetKeepsProgress(GameTestHelper helper) {
        ServerPlayer player = player(helper, Races.SAIYAN, 3);
        CharacterData data = CharacterCapability.get(player);
        ResourceLocation form = Transformations.SUPER_SAIYAN;
        data.unlockTransformation(form);
        helper.assertTrue(TransformationService.start(player, form) == TransformationEligibility.Result.READY,
                "Efficiency fixture must begin activation");
        int noviceTicks = ServerRuntime.state(player).transformationChargeTicks;
        completeCharge(player);
        double noviceKi = data.ki();
        TransformationService.tick(player, data, ServerRuntime.state(player));
        double noviceDrain = noviceKi - data.ki();
        ServerEvents.resetSession(player);
        data.setMastery(form, 100);
        data.setKi(data.maxKi());
        helper.assertTrue(TransformationService.start(player, form) == TransformationEligibility.Result.READY,
                "A mastered form must begin activation");
        int masteredTicks = ServerRuntime.state(player).transformationChargeTicks;
        completeCharge(player);
        double masteredKi = data.ki();
        TransformationService.tick(player, data, ServerRuntime.state(player));
        double masteredDrain = masteredKi - data.ki();
        helper.assertTrue(masteredTicks < noviceTicks && masteredDrain > 0 && masteredDrain < noviceDrain,
                "Mastery must reduce activation time and recurring drain without eliminating its server cost");
        TransformationService.resetSession(player);
        helper.assertTrue(data.currentTransformation().equals(CharacterData.BASE_FORM)
                        && data.unlockedTransformations().contains(form) && data.mastery().get(form) == 100,
                "Death/login/logout reset must clear the form while retaining permanent progression");
        cleanup(player);
        helper.succeed();
    }

    private static CharacterData fixture(ResourceLocation race, int level) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Teste de forma");
        tag.putString("race", race.toString());
        tag.putString("origin", Origins.EARTH_WARRIOR.toString());
        tag.putString("combatStyle", "balanced");
        tag.putInt("level", level);
        CharacterData data = new CharacterData();
        data.load(tag);
        return data;
    }

    private static ServerPlayer player(GameTestHelper helper, ResourceLocation race, int level) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DBIL-FormTest"));
        Vec3 position = helper.absoluteVec(new Vec3(2, 1, 2));
        player.moveTo(position.x, position.y, position.z, -90, 0);
        player.setGameMode(GameType.SURVIVAL);
        CharacterData data = CharacterCapability.get(player);
        data.copyFrom(fixture(race, level));
        CharacterService.applyAttributes(player, data);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void completeCharge(ServerPlayer player) {
        var state = ServerRuntime.state(player);
        int remaining = state.transformationChargeTicks;
        for (int tick = 0; tick < remaining; tick++) {
            TransformationService.tick(player, CharacterCapability.get(player), state);
        }
    }

    private static void cleanup(ServerPlayer player) {
        ServerEvents.resetSession(player);
        player.discard();
        ServerRuntime.clear(player.getUUID());
    }
}
