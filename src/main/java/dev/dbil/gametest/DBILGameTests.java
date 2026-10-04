package dev.dbil.gametest;

import dev.dbil.DBIL;
import dev.dbil.api.DefinitionRegistry;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.character.Origins;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.RaceDefinition;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.combat.CombatService;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.server.Action;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerActions;
import dev.dbil.server.ServerEvents;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import dev.dbil.technique.Techniques;
import dev.dbil.technique.KiWaveEntity;
import dev.dbil.transformation.TransformationDefinition;
import dev.dbil.transformation.TransformationEligibility;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;

/** Invariants and migration cases run in an actual Forge dedicated GameTest server. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DBILGameTests {
    private DBILGameTests() {}

    @GameTest(template = "empty", batch = "dbil_core")
    public static void persistenceKeepsIndependentCharacters(GameTestHelper helper) {
        CharacterData first = fixture();
        ResourceLocation wave = DBIL.id("ki_wave");
        ResourceLocation form = DBIL.id("test_form");
        first.setStat(Stat.STRENGTH, 24);
        first.setKi(38);
        first.setStamina(41);
        first.learn(wave);
        first.equip(wave);
        first.unlockTransformation(form);
        first.setTransformation(form);
        first.setMastery(form, 27.5);
        first.recordTraining("ki_control", 13);
        first.setStoryFlag("first_training_enemy");
        first.setPower(171, 143);
        CompoundTag persisted = first.save();
        CharacterData second = new CharacterData();
        second.load(persisted);
        helper.assertTrue(second.save().equals(persisted), "Canonical save/load must preserve all character channels");
        helper.assertTrue(persisted.getInt("schemaVersion") == CharacterData.SCHEMA_VERSION, "Save must include current schema");
        second.spendKi(7);
        second.setMastery(form, 91);
        helper.assertTrue(first.ki() == 38 && first.mastery().get(form) == 27.5,
                "Changing another character must not alter the original data");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void migratesLegacyDataWithoutChangingSource(GameTestHelper helper) {
        CompoundTag legacy = fixtureTag();
        legacy.remove("origin");
        legacy.remove("combatStyle");
        legacy.putDouble("strength", 29);
        legacy.putDouble("maxKi", 180);
        legacy.putDouble("currentKi", 77);
        CompoundTag original = legacy.copy();
        CharacterData migrated = new CharacterData();
        migrated.load(legacy);
        helper.assertTrue(legacy.equals(original), "Migration must not edit the original save payload");
        helper.assertTrue(migrated.stat(Stat.STRENGTH) == 29 && migrated.maxKi() == 180 && migrated.ki() == 77,
                "Legacy flat attributes and Ki capacity must migrate");
        helper.assertTrue(migrated.originId().equals(Origins.EARTH_WARRIOR) && migrated.styleId().equals("balanced"),
                "Legacy missing origin and style need valid defaults");
        helper.assertTrue(migrated.save().getInt("schemaVersion") == CharacterData.SCHEMA_VERSION,
                "Migrated data must save under the current schema");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void rejectsInvalidEconomyWithoutDeduction(GameTestHelper helper) {
        CharacterData data = fixture();
        data.setKi(12);
        data.setStamina(9);
        double[] invalidCosts = { -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
        for (double cost : invalidCosts) {
            helper.assertTrue(!data.spendKi(cost) && !data.spendStamina(cost), "Invalid costs must be rejected");
        }
        helper.assertTrue(!data.spendKi(13) && !data.spendStamina(10), "Overspend must be rejected");
        helper.assertTrue(data.ki() == 12 && data.stamina() == 9, "Rejected requests must leave balances unchanged");
        helper.assertTrue(data.spendKi(12) && data.spendStamina(9), "Spending the exact balance must succeed");
        helper.assertTrue(data.ki() == 0 && data.stamina() == 0, "Resources must reach zero without becoming negative");
        data.addKi(1_000_000);
        data.addStamina(1_000_000);
        helper.assertTrue(data.ki() == data.maxKi() && data.stamina() == data.maxStamina(), "Regeneration must cap at capacity");
        data.setStat(Stat.MAX_KI, 30);
        helper.assertTrue(data.ki() <= 30, "Lowering resource capacity must clamp the current balance");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void malformedSaveCannotGrantInvalidState(GameTestHelper helper) {
        CompoundTag corrupted = fixtureTag();
        corrupted.putString("race", "invalid race!");
        corrupted.putString("characterName", "A".repeat(80));
        corrupted.putInt("level", Integer.MAX_VALUE);
        corrupted.putLong("experience", Long.MAX_VALUE);
        corrupted.putDouble("currentKi", Double.NaN);
        corrupted.putDouble("currentStamina", -500);
        CompoundTag attributes = new CompoundTag();
        attributes.putDouble("strength", Double.POSITIVE_INFINITY);
        attributes.putDouble("speed", 1_000_000);
        corrupted.put("attributes", attributes);
        ListTag equipped = new ListTag();
        equipped.add(StringTag.valueOf("dbil:unearned"));
        corrupted.put("equippedTechniques", equipped);
        corrupted.putString("currentTransformation", "dbil:locked_form");
        CharacterData data = new CharacterData();
        data.load(corrupted);
        helper.assertTrue(Races.get(data.raceId()) != null, "Unknown race must fall back safely");
        helper.assertTrue(data.name().codePointCount(0, data.name().length()) <= 24, "Save name must be bounded");
        helper.assertTrue(data.level() <= CharacterData.levelLimit(), "Loaded level must respect the server cap");
        helper.assertTrue(Double.isFinite(data.ki()) && data.ki() >= 0 && data.stamina() >= 0, "Resources must remain finite and nonnegative");
        helper.assertTrue(data.stat(Stat.SPEED) <= CharacterData.attributeLimit(), "Loaded attributes must respect the server cap");
        helper.assertTrue(!data.equippedTechniques().contains(DBIL.id("unearned")), "An unlearned technique cannot be equipped by a save");
        helper.assertTrue(data.currentTransformation().equals(CharacterData.BASE_FORM), "An unearned transformation cannot be activated by a save");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void newerSchemasArePreservedWithoutMutation(GameTestHelper helper) {
        CompoundTag future = fixtureTag();
        future.putInt("schemaVersion", CharacterData.SCHEMA_VERSION + 10);
        CompoundTag futureSystem = new CompoundTag();
        futureSystem.putString("unknownAbility", "data_from_a_newer_mod");
        futureSystem.putLong("unknownValue", 123456789L);
        future.put("futureSubsystem", futureSystem);
        CharacterData data = new CharacterData();
        data.load(future);
        helper.assertTrue(!data.compatibleSchema() && !data.created(), "Newer save versions must block incompatible gameplay");
        data.setKi(0);
        data.setStamina(0);
        data.setStat(Stat.STRENGTH, 1);
        data.addExperience(500);
        data.learn(DBIL.id("ki_wave"));
        data.reset();
        helper.assertTrue(data.save().equals(future), "Mutations and reset must never overwrite unknown future save fields");
        CharacterData copy = new CharacterData();
        copy.copyFrom(data);
        helper.assertTrue(copy.save().equals(future) && !copy.compatibleSchema(), "Death cloning must also preserve future save data");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void snapshotUsesAuthoritativeCapsAndSaveIgnoresTransportCaps(GameTestHelper helper) {
        CompoundTag snapshot = fixtureTag();
        snapshot.putInt("schemaVersion", CharacterData.SCHEMA_VERSION);
        snapshot.putInt("snapshotAttributeLimit", 999);
        snapshot.putInt("snapshotLevelLimit", 999);
        CompoundTag attributes = new CompoundTag();
        attributes.putDouble("strength", 700);
        snapshot.put("attributes", attributes);
        CharacterData mirror = new CharacterData();
        mirror.loadSnapshot(snapshot);
        helper.assertTrue(mirror.stat(Stat.STRENGTH) == 700, "Client snapshot must preserve the server's bounded attributes");
        CharacterData server = new CharacterData();
        server.load(snapshot);
        helper.assertTrue(server.stat(Stat.STRENGTH) <= CharacterData.attributeLimit(),
                "Persistent server loads must ignore transport metadata and use actual server limits");
        helper.assertTrue(!server.save().contains("snapshotAttributeLimit"), "Transport cap metadata must not become persistent character state");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void progressionRespectsServerBounds(GameTestHelper helper) {
        CharacterData data = fixture();
        int before = data.level();
        double strength = data.stat(Stat.STRENGTH);
        data.addExperience(data.nextLevelExperience());
        helper.assertTrue(data.level() == before + 1 && data.experience() == 0,
                "An exact threshold must grant one level and consume its XP");
        helper.assertTrue(data.stat(Stat.STRENGTH) > strength, "Level gain must provide actual bounded stat growth");
        data.addExperience(Long.MAX_VALUE);
        helper.assertTrue(data.level() <= CharacterData.levelLimit() && data.experience() >= 0,
                "Extreme rewards must not overflow or exceed the configured level cap");
        for (Stat stat : Stat.values()) {
            helper.assertTrue(Double.isFinite(data.stat(stat)) && data.stat(stat) <= CharacterData.attributeLimit(),
                    "Progression must keep every attribute bounded: " + stat);
        }
        CharacterData uncreated = new CharacterData();
        uncreated.addExperience(1_000_000);
        helper.assertTrue(uncreated.level() == 1 && uncreated.experience() == 0, "Uncreated players cannot gain RPG progression");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void powerRespondsToResourcesAndCondition(GameTestHelper helper) {
        CharacterData data = fixture();
        data.setKi(data.maxKi());
        data.setStamina(data.maxStamina());
        PowerLevelCalculator.update(data, 1, false, false);
        long fullPower = data.currentPower();
        long basePower = data.basePower();
        helper.assertTrue(fullPower > 0 && basePower > 0, "A created character must have measurable power");
        data.setKi(0);
        data.setStamina(0);
        PowerLevelCalculator.update(data, 0.2, false, false);
        helper.assertTrue(data.basePower() == basePower && data.currentPower() < fullPower,
                "Exhaustion lowers current power without changing base attributes");
        data.setStat(Stat.STRENGTH, data.stat(Stat.STRENGTH) + 8);
        PowerLevelCalculator.update(data, 1, false, false);
        helper.assertTrue(data.basePower() > basePower, "Base power must use attributes rather than level alone");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void definitionsAreRegisteredAndDuplicatesRejected(GameTestHelper helper) {
        helper.assertTrue(Races.get(Races.HUMAN) != null && Races.get(Races.SAIYAN) != null, "Both playable races must be registered");
        var wave = Techniques.get(DBIL.id("ki_wave"));
        helper.assertTrue(wave != null && wave.kiCost() > 0 && wave.cooldown() > 0 && wave.range() > 0 && wave.range() <= 128,
                "The starting technique must have finite bounded economy and reach");
        DefinitionRegistry<RaceDefinition> independent = new DefinitionRegistry<>(RaceDefinition::id);
        independent.register(Races.get(Races.HUMAN));
        boolean rejected = false;
        try {
            independent.register(Races.get(Races.HUMAN));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected && independent.values().size() == 1, "Duplicate definitions must fail without replacing existing data");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void capabilitySerializesAndInvalidates(GameTestHelper helper) {
        CharacterCapability.Provider original = new CharacterCapability.Provider();
        var reference = original.getCapability(CharacterCapability.CAPABILITY, null);
        CharacterData data = reference.orElseThrow(() -> new IllegalStateException("Missing provider data"));
        data.load(fixtureTag());
        data.setKi(23);
        data.setStoryFlag("saved_in_capability");
        CharacterCapability.Provider restored = new CharacterCapability.Provider();
        restored.deserializeNBT(original.serializeNBT());
        CharacterData copied = restored.getCapability(CharacterCapability.CAPABILITY, null)
                .orElseThrow(() -> new IllegalStateException("Missing restored data"));
        helper.assertTrue(copied.ki() == 23 && copied.storyFlags().contains("saved_in_capability"),
                "Forge provider serialization must preserve persistent channels");
        original.invalidate();
        helper.assertTrue(!reference.isPresent(), "Invalidated providers must release their capability reference");
        helper.assertTrue(copied.created() && copied.ki() == 23, "Invalidating a previous player cannot invalidate the copied character");
        restored.invalidate();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void networkingRateLimitsDoNotShareCounters(GameTestHelper helper) {
        PlayerState state = new PlayerState();
        int admitted = 0;
        for (int packet = 0; packet < 1000; packet++) {
            if (state.admitAction(200)) admitted++;
        }
        helper.assertTrue(admitted > 0 && admitted <= 60, "Action bursts must have a bounded per-second budget");
        helper.assertTrue(state.admitMovement(200), "An exhausted action budget must not consume the movement budget");
        int movement = 1;
        for (int packet = 0; packet < 1000; packet++) {
            if (state.admitMovement(200)) movement++;
        }
        helper.assertTrue(movement >= 20 && movement <= 30, "Analog input can update each game tick, while bursts remain bounded");
        helper.assertTrue(state.admitAction(220) && state.admitMovement(220), "Budgets must reopen after the clock window");
        PlayerState anotherPlayer = new PlayerState();
        helper.assertTrue(anotherPlayer.admitAction(200), "One player's burst must not throttle another player");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_entities")
    public static void serverActionsRejectUncreatedAndUnaffordableDash(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, new Vec3(2, 1, 2));
        CharacterData data = CharacterCapability.get(player);
        data.reset();
        ServerActions.handle(player, Action.CHARGE_START);
        helper.assertTrue(!ServerRuntime.state(player).charging, "Uncreated clients cannot activate Ki charging");
        data.load(fixtureTag());
        data.setKi(0);
        data.setStamina(data.maxStamina());
        double stamina = data.stamina();
        Vec3 before = player.position();
        ServerActions.handle(player, Action.DASH);
        helper.assertTrue(data.stamina() == stamina && data.ki() == 0 && player.position().equals(before),
                "Dash must reject an unaffordable Ki cost without charging stamina or moving the player");
        data.setKi(data.maxKi());
        data.setStamina(0);
        double ki = data.ki();
        ServerActions.handle(player, Action.DASH);
        helper.assertTrue(data.ki() == ki && player.position().equals(before),
                "Dash must reject an unaffordable stamina cost without charging Ki or moving the player");
        ServerRuntime.clear(player.getUUID());
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_entities")
    public static void characterCreationValidatesAndAppliesStableAttributes(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, new Vec3(2, 1, 2));
        CharacterData data = CharacterCapability.get(player);
        data.reset();
        helper.assertTrue(!CharacterService.create(player, "", Races.HUMAN, Origins.EARTH_WARRIOR, "balanced"),
                "Blank names must be rejected by the server service");
        helper.assertTrue(!CharacterService.create(player, "Teste", DBIL.id("unknown_race"), Origins.EARTH_WARRIOR, "balanced"),
                "An unknown race must be rejected by the server service");
        helper.assertTrue(!CharacterService.create(player, "Teste", Races.HUMAN, DBIL.id("survivor"), "balanced"),
                "Race-specific origins must not be spoofed by another race");
        helper.assertTrue(!CharacterService.create(player, "Teste", Races.HUMAN, Origins.EARTH_WARRIOR, "invalid_style"),
                "An unknown style must be rejected by the server service");
        helper.assertTrue(CharacterService.create(player, "Teste", Races.SAIYAN, DBIL.id("survivor"), "brawler"),
                "A valid race, origin and style must create an actual character");
        helper.assertTrue(data.created() && data.ki() == data.maxKi() && data.stamina() == data.maxStamina()
                        && data.equippedTechniques().contains(Techniques.KI_WAVE),
                "Creation must populate resources and the usable starting technique");
        float maxHealth = player.getMaxHealth();
        CharacterService.applyAttributes(player, data);
        CharacterService.applyAttributes(player, data);
        helper.assertTrue(player.getMaxHealth() == maxHealth && maxHealth > 20,
                "Reapplying RPG vitality must preserve HP without stacking modifiers");
        helper.assertTrue(!CharacterService.create(player, "Outro", Races.HUMAN, Origins.EARTH_WARRIOR, "speed")
                        && data.name().equals("Teste") && data.raceId().equals(Races.SAIYAN),
                "A second creation request must not overwrite a character");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_entities")
    public static void deathCapabilityRevivalAndClonePreserveCharacter(GameTestHelper helper) {
        ServerPlayer original = fakePlayer(helper, new Vec3(2, 1, 2));
        CharacterData originalData = CharacterCapability.get(original);
        originalData.load(fixtureTag());
        originalData.setKi(43);
        originalData.setMastery(Techniques.KI_WAVE, 17);
        originalData.setStoryFlag("death_clone_regression");
        CompoundTag expected = originalData.save();
        var oldReference = original.getCapability(CharacterCapability.CAPABILITY);
        original.invalidateCaps();
        helper.assertTrue(!oldReference.isPresent()
                        && !original.getCapability(CharacterCapability.CAPABILITY).isPresent(),
                "Death invalidation must release old references and block entity capability access");
        original.reviveCaps();
        helper.assertTrue(CharacterCapability.get(original).save().equals(expected),
                "Reviving an invalidated entity must restore access to its original character data");
        helper.assertTrue(!oldReference.isPresent(), "Revival must never reactivate an old invalidated reference");
        original.invalidateCaps();
        ServerPlayer replacement = fakePlayer(helper, new Vec3(3, 1, 2));
        ServerEvents.clonePlayer(new PlayerEvent.Clone(replacement, original, true));
        CharacterData replacementData = CharacterCapability.get(replacement);
        helper.assertTrue(replacementData.save().equals(expected),
                "Forge death cloning must recover invalidated original data and copy every persistent channel");
        helper.assertTrue(!original.getCapability(CharacterCapability.CAPABILITY).isPresent(),
                "Clone lifecycle must invalidate the original entity again after copying");
        replacementData.setKi(1);
        original.reviveCaps();
        helper.assertTrue(CharacterCapability.get(original).ki() == 43,
                "Respawned and original character data must have independent ownership");
        original.discard();
        replacement.discard();
        ServerRuntime.clear(original.getUUID());
        ServerRuntime.clear(replacement.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_entities")
    public static void deadPlayerTickDoesNotReadInvalidatedCapabilities(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, new Vec3(2, 1, 2));
        CharacterCapability.get(player).load(fixtureTag());
        player.setHealth(0);
        player.invalidateCaps();
        helper.assertTrue(!player.isAlive() && !player.getCapability(CharacterCapability.CAPABILITY).isPresent(),
                "Regression fixture must be a dead player with invalidated Forge capabilities");
        ServerEvents.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(!player.getCapability(CharacterCapability.CAPABILITY).isPresent(),
                "Dead-player ticks must skip DBIL simulation until respawn without reviving capabilities");
        player.discard();
        ServerRuntime.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_entities", timeoutTicks = 40)
    public static void kiWaveCollidesWithNpcOnServer(GameTestHelper helper) {
        ServerPlayer owner = fakePlayer(helper, new Vec3(2, 1, 5));
        CharacterCapability.get(owner).load(fixtureTag());
        TrainingEnemy enemy = helper.spawnWithNoFreeWill(ModEntities.TRAINING_ENEMY.get(), new Vec3(5.5, 1, 5));
        float health = enemy.getHealth();
        helper.assertTrue(enemy.getPowerLevel() > 0, "Training NPC must have registered attributes and measurable power");
        KiWaveEntity projectile = ModEntities.KI_WAVE.get().create(helper.getLevel());
        helper.assertTrue(projectile != null, "Ki Wave entity type must create an entity on the server");
        projectile.initialize(owner, Techniques.get(Techniques.KI_WAVE), new Vec3(1, 0, 0), 7);
        helper.getLevel().addFreshEntity(projectile);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(projectile.isRemoved(), "A Ki Wave must be consumed by collision");
            helper.assertTrue(enemy.getHealth() < health, "A Ki Wave collision must inflict DBIL damage on the server");
            enemy.discard();
            owner.discard();
            ServerRuntime.clear(owner.getUUID());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dbil_entities", timeoutTicks = 110)
    public static void kiWaveExpiresWithoutPermanentEntities(GameTestHelper helper) {
        ServerPlayer owner = fakePlayer(helper, new Vec3(2, 10, 5));
        KiWaveEntity projectile = ModEntities.KI_WAVE.get().create(helper.getLevel());
        helper.assertTrue(projectile != null, "Ki Wave entity type must create an entity on the server");
        projectile.initialize(owner, Techniques.get(Techniques.KI_WAVE), new Vec3(0, 1, 0), 7);
        helper.getLevel().addFreshEntity(projectile);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(projectile.isRemoved(), "A missed Ki Wave must expire within its bounded travel range");
            owner.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dbil_entities")
    public static void trainingEnemyAwardsExperienceOnlyOnce(GameTestHelper helper) {
        ServerPlayer owner = fakePlayer(helper, new Vec3(2, 1, 5));
        CharacterData data = CharacterCapability.get(owner);
        data.load(fixtureTag());
        TrainingEnemy enemy = helper.spawnWithNoFreeWill(ModEntities.TRAINING_ENEMY.get(), new Vec3(4, 1, 5));
        var source = owner.damageSources().playerAttack(owner);
        helper.assertTrue(CombatService.damage(owner, enemy, 100_000, 0, 0), "A valid close-range server hit must kill the training NPC");
        helper.assertTrue(enemy.isDeadOrDying() && data.experience() > 0, "A credited kill must grant individual RPG experience");
        long reward = data.experience();
        enemy.die(source);
        helper.assertTrue(data.experience() == reward, "Repeated death callbacks must not duplicate the reward");
        helper.assertTrue(owner.totalExperience == 0, "DBIL reward must remain separate from vanilla XP");
        enemy.discard();
        owner.discard();
        ServerRuntime.clear(owner.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_core")
    public static void transformationFrameworkEnforcesUnlockAndMastery(GameTestHelper helper) {
        ResourceLocation formId = DBIL.id("test_form");
        var mastery = new TransformationDefinition.MasteryRules(100, 0.25, 0.3, 1);
        var definition = new TransformationDefinition(formId, "Forma de teste", Set.of(Races.HUMAN),
                new TransformationDefinition.Requirements(1, Map.of(Stat.STRENGTH, 10.0), Set.of()),
                Map.of(Stat.STRENGTH, 1.5), 0.5, 15, 40,
                new TransformationDefinition.Appearance(DBIL.id("base_aura"), null, null, null, null, Set.of()),
                Set.of(), mastery, new TransformationDefinition.UnlockCondition(DBIL.id("story"), Map.of("flag", "first_training_enemy")),
                DBIL.id("technical"));
        CharacterData data = fixture();
        helper.assertTrue(TransformationEligibility.check(data, definition) == TransformationEligibility.Result.LOCKED,
                "Requirements alone cannot bypass an unlock");
        data.unlockTransformation(formId);
        data.setKi(0);
        helper.assertTrue(TransformationEligibility.check(data, definition) == TransformationEligibility.Result.INSUFFICIENT_KI,
                "An unlocked form must still pay its activation cost");
        data.setKi(data.maxKi());
        helper.assertTrue(TransformationEligibility.check(data, definition) == TransformationEligibility.Result.READY,
                "A compatible, unlocked and funded form must pass eligibility");
        data.setRace(Races.SAIYAN);
        helper.assertTrue(TransformationEligibility.check(data, definition) == TransformationEligibility.Result.WRONG_RACE,
                "Eligibility must follow definition race rules");
        helper.assertTrue(mastery.drain(0.5, 100) < mastery.drain(0.5, 0)
                        && mastery.activationTicks(40, 100) < mastery.activationTicks(40, 0),
                "Mastery improves drain and activation within finite bounds");
        helper.assertTrue(TransformationEligibility.check(data, DBIL.id("unknown")) == TransformationEligibility.Result.UNKNOWN_FORM,
                "Unknown form identifiers must not activate anything");
        helper.succeed();
    }

    private static CharacterData fixture() {
        CharacterData data = new CharacterData();
        data.load(fixtureTag());
        return data;
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, Vec3 relativePosition) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DBIL-GameTest"));
        Vec3 position = helper.absoluteVec(relativePosition);
        player.moveTo(position.x, position.y, position.z, -90, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static CompoundTag fixtureTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Teste DBIL");
        tag.putString("race", Races.HUMAN.toString());
        tag.putString("origin", Origins.EARTH_WARRIOR.toString());
        tag.putString("combatStyle", "balanced");
        tag.putInt("level", 1);
        return tag;
    }
}
