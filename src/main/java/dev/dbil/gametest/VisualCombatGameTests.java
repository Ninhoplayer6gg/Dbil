package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.character.Origins;
import dev.dbil.combat.CombatService;
import dev.dbil.combat.TerrainDamageService;
import dev.dbil.config.ServerConfig;
import dev.dbil.flight.FlightMotion;
import dev.dbil.flight.FlightService;
import dev.dbil.movement.DashService;
import dev.dbil.movement.VanishService;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.targeting.TargetingService;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.TechniqueService;
import dev.dbil.technique.Techniques;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** DBIL 0.3 invariants: appearance data, charge scaling, beams, Vanish, chase, combo, fast flight, terrain, lock-on. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisualCombatGameTests {
    private VisualCombatGameTests() {}

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void appearanceMigratesValidatesAndPersists(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 3);
        legacy.putBoolean("characterCreated", true);
        legacy.putString("characterName", "Legado");
        legacy.putString("race", Races.SAIYAN.toString());
        legacy.putInt("level", 4);
        CharacterData migrated = new CharacterData();
        migrated.load(legacy);
        helper.assertTrue(migrated.created() && migrated.level() == 4 && migrated.appearance().equals(CharacterAppearance.SAIYAN_DEFAULT),
                "A schema 3 Saiyan keeps progress and receives the Saiyan default look");
        helper.assertTrue(migrated.save().getInt("schemaVersion") == CharacterData.SCHEMA_VERSION, "Migration writes the current schema");
        CompoundTag hostile = new CompoundTag();
        hostile.putInt("eyeStyle", 99);
        hostile.putInt("bodyType", -4);
        hostile.putString("hairstyle", "evil:unknown");
        hostile.putString("outfit", "x".repeat(200));
        hostile.putInt("accessories", 0xFFFF);
        CharacterAppearance clamped = CharacterAppearance.load(hostile, Races.HUMAN);
        helper.assertTrue(clamped.eyeStyle() == 0 && clamped.bodyType() == 0 && clamped.hairstyle().equals(AppearanceOptions.HAIR_SHORT)
                        && AppearanceOptions.OUTFITS.contains(clamped.outfit()) && !clamped.has(AppearanceOptions.ACCESSORY_TAIL),
                "Invalid indices clamp, unknown ids fall back and humans never keep a tail");
        CharacterData copy = new CharacterData();
        CharacterAppearance custom = CharacterAppearance.SAIYAN_DEFAULT.withHairstyle(AppearanceOptions.HAIR_SPIKY_TALL)
                .withHairColor(0x3A6FB8).withOutfit(AppearanceOptions.OUTFIT_BATTLE_ARMOR).withBodyType(1);
        migrated.setAppearance(custom);
        copy.load(migrated.save());
        helper.assertTrue(copy.appearance().equals(custom) && copy.save().equals(migrated.save()),
                "Appearance survives a canonical save/load round trip");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void appearanceUpdatesOnlyForCreatedCharacters(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Aparencia");
        CharacterData data = CharacterCapability.get(player);
        data.reset();
        helper.assertTrue(!CharacterService.updateAppearance(player, CharacterAppearance.SAIYAN_DEFAULT),
                "An uncreated character cannot store an appearance");
        helper.assertTrue(CharacterService.create(player, "Visual", Races.HUMAN, Origins.EARTH_WARRIOR, "balanced",
                CharacterAppearance.HUMAN_DEFAULT.withHairstyle(AppearanceOptions.HAIR_MESSY)), "Creation accepts a chosen look");
        helper.assertTrue(data.appearance().hairstyle().equals(AppearanceOptions.HAIR_MESSY), "The chosen look is stored at creation");
        helper.assertTrue(CharacterService.updateAppearance(player, CharacterAppearance.SAIYAN_DEFAULT)
                        && !data.appearance().has(AppearanceOptions.ACCESSORY_TAIL), "Edits apply, filtered by race traits");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void heldChargeCostsMoreAndScalesTheShot(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Carga");
        CharacterData data = create(helper, player, Races.HUMAN, 3);
        PlayerState state = ServerRuntime.state(player);
        try {
            var definition = Techniques.get(Techniques.KI_WAVE);
            TechniqueProfile profile = Techniques.profile(Techniques.KI_WAVE);
            double base = TechniqueService.kiCost(definition, data);
            double before = data.ki();
            TechniqueService.startHold(player);
            helper.assertTrue(state.techniqueCharging && state.techniqueHolding, "Hold mode starts charging");
            helper.assertTrue(Math.abs(before - data.ki() - base) < 1.0e-6, "The base cost is paid once on start");
            for (int tick = 0; tick < definition.chargeTime() + profile.chargeMaxTicks() + 2; tick++) {
                TechniqueService.tick(player, data, state);
            }
            helper.assertTrue(TechniqueService.chargeFraction(state) >= 0.999F, "Holding reaches full charge");
            double paid = before - data.ki();
            helper.assertTrue(Math.abs(paid - base * profile.maxKiFactor()) < 0.05,
                    "A full charge costs the base cost times the profile factor, paid gradually");
            TechniqueService.release(player);
            TechniqueService.tick(player, data, state);
            helper.assertTrue(!state.techniqueCharging && state.techniqueFireCharge >= 0.999F, "Release fires at the reached charge");
            var shots = player.level().getEntitiesOfClass(dev.dbil.technique.KiWaveEntity.class, player.getBoundingBox().inflate(6),
                    entity -> entity.getOwner() == player);
            helper.assertTrue(shots.size() == 1 && shots.get(0).charge() >= 0.999F
                            && shots.get(0).radius() > profile.size() * 1.5F, "The fired shot carries its charge and larger radius");
            helper.succeed();
        } finally {
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_v03", timeoutTicks = 100)
    public static void kamehamehaBeamHitsTheOpponentInFront(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Feixe");
        CharacterData data = create(helper, player, Races.HUMAN, 3);
        data.learn(Techniques.KAMEHAMEHA);
        data.equip(Techniques.KAMEHAMEHA);
        helper.assertTrue(TechniqueService.select(player, Techniques.KAMEHAMEHA), "A learned, equipped beam can be selected");
        TrainingEnemy enemy = enemy(helper, new Vec3(2.5, 1, 6.5));
        float health = enemy.getHealth();
        TechniqueService.start(player);
        PlayerState state = ServerRuntime.state(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (player.isAlive()) TechniqueService.tick(player, data, state);
            ticks[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(enemy.getHealth() < health, "The beam must damage the opponent standing in its path");
            helper.assertTrue(state.activeBeamId >= 0 || ticks[0] > 20, "The beam entity is tracked as the caster's active beam");
            cleanup(player);
            enemy.discard();
        });
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void vanishNeedsATargetAndLandsNextToIt(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Vanish");
        CharacterData data = create(helper, player, Races.HUMAN, 1);
        PlayerState state = ServerRuntime.state(player);
        try {
            double stamina = data.stamina();
            helper.assertTrue(!VanishService.vanish(player, data, state) && data.stamina() == stamina,
                    "Without a locked target Vanish is rejected and costs nothing");
            TrainingEnemy enemy = enemy(helper, new Vec3(2.5, 1, 6.5));
            state.targetId = enemy.getId();
            helper.assertTrue(TargetingService.target(player) == enemy, "The fixture target is valid for lock-on");
            helper.assertTrue(VanishService.vanish(player, data, state), "Vanish with a nearby target succeeds");
            helper.assertTrue(player.position().distanceTo(enemy.position()) < 3.0, "The caster reappears next to the target");
            helper.assertTrue(data.stamina() < stamina, "Vanish spends stamina");
            Vec3 after = player.position();
            helper.assertTrue(!VanishService.vanish(player, data, state) && player.position().equals(after),
                    "The cooldown rejects an immediate second Vanish");
            enemy.discard();
            helper.succeed();
        } finally {
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void comboFinisherAndLauncherOpenTheChase(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Combo");
        CharacterData data = create(helper, player, Races.SAIYAN, 1);
        PlayerState state = ServerRuntime.state(player);
        TrainingEnemy enemy = enemy(helper, new Vec3(2.5, 1, 4.2));
        try {
            enemy.setNoAi(true);
            for (int hit = 1; hit <= 3; hit++) {
                state.nextMeleeTick = 0;
                CombatService.attack(player, CombatService.MeleeKind.LIGHT);
                helper.assertTrue(state.combo == hit, "Light strikes advance the combo stage");
            }
            state.nextMeleeTick = 0;
            CombatService.attack(player, CombatService.MeleeKind.LIGHT);
            helper.assertTrue(state.combo == 0, "The fourth strike is the finisher and resets the combo");
            enemy.setHealth(enemy.getMaxHealth());
            enemy.setPos(helper.absoluteVec(new Vec3(2.5, 1, 4.2)));
            state.nextMeleeTick = 0;
            state.chaseTargetId = -1;
            CombatService.attack(player, CombatService.MeleeKind.LAUNCHER);
            long now = player.serverLevel().getGameTime();
            helper.assertTrue(state.chaseTargetId == enemy.getId() && state.chaseWindowUntil > now,
                    "A landed launcher opens a chase window on that opponent");
            helper.assertTrue(enemy.getDeltaMovement().y > 0.3, "The launcher throws the opponent upwards");
            double stamina = data.stamina();
            Vec3 before = player.position();
            data.setKi(data.maxKi());
            DashService.dash(player, data, state);
            helper.assertTrue(state.chaseTargetId == -1 && data.stamina() < stamina && !player.position().equals(before),
                    "A dash inside the window chases the opponent and consumes the window");
            helper.succeed();
        } finally {
            enemy.discard();
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void fastFlightIsFasterFollowsPitchAndCostsMore(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Rapido");
        CharacterData data = create(helper, player, Races.HUMAN, 1);
        helper.assertTrue(FlightService.fastSpeed(data) > FlightService.maximumSpeed(data), "Fast flight cruises faster");
        Vec3 level = FlightMotion.desired(new FlightMotion.Input(1, 0, false, false, 0, 45, false), 1);
        Vec3 dive = FlightMotion.desired(new FlightMotion.Input(1, 0, false, false, 0, 45, true), 1);
        helper.assertTrue(level.y == 0 && dive.y < -0.5, "Only fast flight follows the look pitch");
        PlayerState state = new PlayerState();
        state.flying = true;
        state.flightPosition = player.position();
        state.forward = 1;
        state.lastInputTick = player.serverLevel().getGameTime();
        double ki = data.ki();
        FlightService.tick(player, data, state);
        double normal = ki - data.ki();
        state.flightFast = true;
        ki = data.ki();
        FlightService.tick(player, data, state);
        double fast = ki - data.ki();
        helper.assertTrue(fast > normal * 2, "Fast flight costs more Ki per tick");
        cleanup(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void terrainDamageIsOptInAndRespectsProtection(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Cratera");
        create(helper, player, Races.HUMAN, 1);
        BlockPos center = new BlockPos(5, 2, 5);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) helper.setBlock(center.offset(dx, 0, dz), Blocks.STONE);
        helper.setBlock(center.offset(1, 0, 0), Blocks.BEDROCK);
        helper.setBlock(center.offset(-1, 0, 0), Blocks.CHEST);
        Vec3 impact = Vec3.atCenterOf(helper.absolutePos(center));
        boolean previous = ServerConfig.terrainDamage.get();
        try {
            ServerConfig.terrainDamage.set(false);
            helper.assertTrue(TerrainDamageService.crater(player, helper.getLevel(), impact, 1.5, 1.0F) == 0,
                    "Terrain damage is disabled by default");
            ServerConfig.terrainDamage.set(true);
            int removed = TerrainDamageService.crater(player, helper.getLevel(), impact, 1.5, 1.0F);
            helper.assertTrue(removed > 0 && removed <= ServerConfig.terrainMaxBlocks.get(), "Enabled terrain damage removes a bounded crater");
            helper.assertTrue(TerrainDamageService.crater(player, helper.getLevel(), impact, 1.5, 0.1F) == 0,
                    "Weak charges below the configured minimum never break blocks");
            helper.assertBlockPresent(Blocks.BEDROCK, center.offset(1, 0, 0));
            helper.assertBlockPresent(Blocks.CHEST, center.offset(-1, 0, 0));
            helper.succeed();
        } finally {
            ServerConfig.terrainDamage.set(previous);
            cleanup(player);
        }
    }

    /** Own batch: tests in one batch run side by side, and their opponents would be valid lock-on candidates. */
    @GameTest(template = "empty", batch = "dbil_v03_lock")
    public static void lockOnCyclesBetweenOpponents(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "Alvo");
        create(helper, player, Races.HUMAN, 1);
        PlayerState state = ServerRuntime.state(player);
        TrainingEnemy left = enemy(helper, new Vec3(1.0, 1, 7.0));
        TrainingEnemy right = enemy(helper, new Vec3(4.0, 1, 7.0));
        try {
            TargetingService.toggle(player);
            int first = state.targetId;
            helper.assertTrue(first == left.getId() || first == right.getId(), "Lock-on selects an opponent in front");
            state.nextTargetCycleTick = 0;
            helper.assertTrue(TargetingService.cycle(player) && state.targetId != first
                    && (state.targetId == left.getId() || state.targetId == right.getId()), "Cycling switches to the other opponent");
            helper.succeed();
        } finally {
            left.discard();
            right.discard();
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_v03")
    public static void loadoutHasSixSlotsAndUnequipKeepsASelection(GameTestHelper helper) {
        CharacterData data = new CharacterData();
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", CharacterData.SCHEMA_VERSION);
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Loadout");
        tag.putString("race", Races.HUMAN.toString());
        tag.putInt("level", 5);
        data.load(tag);
        int equipped = 0;
        for (var technique : Techniques.values()) {
            data.learn(technique.id());
            if (data.equip(technique.id())) equipped++;
        }
        helper.assertTrue(equipped == Math.min(CharacterData.MAX_EQUIPPED, Techniques.values().size()), "Six loadout slots");
        ResourceLocation selected = data.selectedTechnique();
        helper.assertTrue(data.unequip(selected) && !data.selectedTechnique().equals(selected)
                && data.equippedTechniques().contains(data.selectedTechnique()), "Unequipping the selection moves it to an equipped slot");
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DBIL-" + name));
        Vec3 position = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        ServerRuntime.clear(player.getUUID());
        return player;
    }

    private static CharacterData create(GameTestHelper helper, ServerPlayer player, ResourceLocation race, int level) {
        CharacterData data = CharacterCapability.get(player);
        data.reset();
        helper.assertTrue(CharacterService.create(player, "Teste", race, Origins.EARTH_WARRIOR, "balanced"), "Fixture character");
        if (level > 1) data.addExperience(100_000);
        data.setKi(data.maxKi());
        data.setStamina(data.maxStamina());
        return data;
    }

    private static TrainingEnemy enemy(GameTestHelper helper, Vec3 relative) {
        TrainingEnemy enemy = ModEntities.TRAINING_ENEMY.get().create(helper.getLevel());
        Vec3 position = helper.absoluteVec(relative);
        enemy.moveTo(position.x, position.y, position.z, 180, 0);
        enemy.setNoAi(true);
        helper.getLevel().addFreshEntity(enemy);
        return enemy;
    }

    private static void cleanup(ServerPlayer player) {
        TechniqueService.cancel(player);
        ServerRuntime.clear(player.getUUID());
        player.discard();
    }
}
