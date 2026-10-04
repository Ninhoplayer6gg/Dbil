package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.character.Origins;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.technique.KiWaveEntity;
import dev.dbil.technique.TechniqueService;
import dev.dbil.technique.Techniques;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TechniqueGameTests {
    private TechniqueGameTests() { }

    @GameTest(template = "empty", batch = "dbil_techniques")
    public static void selectionRejectsLockedAndUnequippedTechniques(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CharacterData data = CharacterCapability.get(player);
        try {
            helper.assertTrue(!TechniqueService.select(player, Techniques.KI_BLAST),
                    "Selecting a locked technique must be rejected by the server");
            helper.assertTrue(data.selectedTechnique().equals(Techniques.KI_WAVE),
                    "Rejected selection must retain the persisted active technique");
            data.learn(Techniques.KI_BLAST);
            helper.assertTrue(!TechniqueService.select(player, Techniques.KI_BLAST),
                    "Learning alone cannot bypass equipment validation");
            data.equip(Techniques.KI_BLAST);
            helper.assertTrue(TechniqueService.select(player, Techniques.KI_BLAST),
                    "A learned and equipped technique must be selectable");
            CharacterData restored = new CharacterData();
            restored.load(data.save());
            helper.assertTrue(restored.selectedTechnique().equals(Techniques.KI_BLAST),
                    "The selected equipped technique must survive a persistence round trip");
            helper.assertTrue(!TechniqueService.select(player, DBIL.id("unknown_attack")),
                    "Unknown client resource IDs must not select an executable technique");
            helper.succeed();
        } finally {
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_techniques")
    public static void barragePaysOnceAndSpawnsBoundedProjectiles(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        try {
            data.learn(Techniques.KI_BLAST);
            data.equip(Techniques.KI_BLAST);
            data.learn(Techniques.KI_BARRAGE);
            data.equip(Techniques.KI_BARRAGE);
            helper.assertTrue(TechniqueService.select(player, Techniques.KI_BARRAGE), "Barrage selection must succeed");
            var definition = Techniques.get(Techniques.KI_BARRAGE);
            double beforeKi = data.ki();
            double beforeStamina = data.stamina();
            double cost = TechniqueService.kiCost(definition, data);
            TechniqueService.start(player);
            double paidKi = data.ki();
            helper.assertTrue(Math.abs(beforeKi - paidKi - cost) < 1.0e-6,
                    "The accepted activation must pay its bounded Ki cost exactly once");
            helper.assertTrue(Math.abs(beforeStamina - data.stamina() - definition.staminaCost()) < 1.0e-6,
                    "The accepted activation must pay stamina atomically");
            TechniqueService.start(player);
            helper.assertTrue(data.ki() == paidKi, "Repeated activation while charging must not pay again");
            helper.assertTrue(!TechniqueService.select(player, Techniques.KI_WAVE),
                    "Changing the technique during a paid sequence must be rejected");
            int duration = definition.chargeTime()
                    + (definition.projectiles().count() - 1) * definition.projectiles().intervalTicks();
            for (int tick = 0; tick < duration + 10; tick++) TechniqueService.tick(player, data, state);
            long count = player.level().getEntitiesOfClass(KiWaveEntity.class,
                            player.getBoundingBox().inflate(4), entity -> entity.getOwner() == player).size();
            helper.assertTrue(count == definition.projectiles().count(),
                    "One barrage must create exactly the definition's bounded number of energy entities");
            helper.assertTrue(data.ki() == paidKi && state.barrageRemaining == 0 && !state.techniqueCharging,
                    "The completed pattern must not consume additional Ki or remain active");
            helper.assertTrue(Math.abs(data.mastery().getOrDefault(Techniques.KI_BARRAGE, 0.0) - 0.05) < 1.0e-6,
                    "Barrage mastery belongs to the activation rather than each projectile");
            TechniqueService.start(player);
            helper.assertTrue(data.ki() == paidKi, "Cooldown must also reject reactivation after the sequence");
            helper.succeed();
        } finally {
            cleanup(player);
        }
    }

    @GameTest(template = "empty", batch = "dbil_techniques")
    public static void cancelledBarrageNeverFiresDelayedProjectiles(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CharacterData data = CharacterCapability.get(player);
        PlayerState state = ServerRuntime.state(player);
        try {
            data.learn(Techniques.KI_BLAST);
            data.equip(Techniques.KI_BLAST);
            data.learn(Techniques.KI_BARRAGE);
            data.equip(Techniques.KI_BARRAGE);
            TechniqueService.select(player, Techniques.KI_BARRAGE);
            TechniqueService.start(player);
            double paidKi = data.ki();
            TechniqueService.cancel(player);
            for (int tick = 0; tick < 40; tick++) TechniqueService.tick(player, data, state);
            helper.assertTrue(player.level().getEntitiesOfClass(KiWaveEntity.class,
                            player.getBoundingBox().inflate(4), entity -> entity.getOwner() == player).isEmpty(),
                    "Cancellation before release must remove the scheduled pattern");
            helper.assertTrue(data.ki() == paidKi && state.chargingTechnique == null,
                    "Cancellation must not refund the accepted cost or retain its attack identity");
            helper.succeed();
        } finally {
            cleanup(player);
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "DBILTechnique"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2, 1, 2)));
        player.setYRot(0);
        player.setXRot(0);
        CharacterCapability.get(player).reset();
        boolean created = CharacterService.create(player, "TechniqueTest", Races.HUMAN,
                Origins.EARTH_WARRIOR, "balanced");
        helper.assertTrue(created, "Technique test fixture must create a valid server character");
        return player;
    }

    @GameTest(template = "empty", batch = "dbil_techniques")
    public static void travellingShotStillHitsAfterCasterMovesBehindCover(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TrainingEnemy enemy = helper.spawnWithNoFreeWill(ModEntities.TRAINING_ENEMY.get(), new Vec3(6, 1, 2));
        try {
            float health = enemy.getHealth();
            KiWaveEntity wave = new KiWaveEntity(ModEntities.KI_WAVE.get(), helper.getLevel());
            wave.initialize(player, Techniques.get(Techniques.KI_WAVE), new Vec3(1, 0, 0), 7);
            helper.getLevel().addFreshEntity(wave);
            for (int y = 0; y < 5; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(0, y, z)), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            player.setPos(helper.absoluteVec(new Vec3(-3, 1, 2)));
            helper.assertTrue(!player.hasLineOfSight(enemy), "The caster must have moved behind opaque cover");
            for (int tick = 0; tick < 8 && !wave.isRemoved(); tick++) wave.tick();
            helper.assertTrue(wave.isRemoved() && enemy.getHealth() < health,
                    "A valid travelling shot must use launch geometry instead of the caster's later visibility");
            helper.assertTrue(CharacterCapability.get(player).trainingStats().getOrDefault("technique_hits", 0.0) == 1,
                    "A real projectile collision must record one landed technique hit");
            float afterHit = enemy.getHealth();
            player.setPos(helper.absoluteVec(new Vec3(2, 1, 2)));
            for (int y = 0; y < 5; y++) {
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(4, y, 2)), Blocks.STONE.defaultBlockState(), 3);
            }
            KiWaveEntity intercepted = new KiWaveEntity(ModEntities.KI_WAVE.get(), helper.getLevel());
            intercepted.initialize(player, Techniques.get(Techniques.KI_WAVE), new Vec3(1, 0, 0), 7);
            helper.getLevel().addFreshEntity(intercepted);
            for (int tick = 0; tick < 8 && !intercepted.isRemoved(); tick++) intercepted.tick();
            helper.assertTrue(intercepted.isRemoved() && enemy.getHealth() == afterHit,
                    "A wall in the actual projectile trajectory must still intercept the shot");
            helper.assertTrue(CharacterCapability.get(player).trainingStats().getOrDefault("technique_hits", 0.0) == 1,
                    "A blocked projectile must not add a landed hit");
            helper.succeed();
        } finally {
            enemy.discard();
            cleanup(player);
        }
    }

    private static void cleanup(ServerPlayer player) {
        player.level().getEntitiesOfClass(KiWaveEntity.class, player.getBoundingBox().inflate(5),
                entity -> entity.getOwner() == player).forEach(KiWaveEntity::discard);
        ServerRuntime.clear(player.getUUID());
        player.discard();
    }
}
