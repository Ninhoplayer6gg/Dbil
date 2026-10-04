package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.character.Origins;
import dev.dbil.combat.CombatService;
import dev.dbil.combat.GuardService;
import dev.dbil.config.ServerConfig;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.stats.Stat;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Guard economy/direction tests plus actual Minecraft HP damage in a dedicated Forge world. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardGameTests {
    private GuardGameTests() { }

    @GameTest(template = "empty", batch = "dbil_guard")
    public static void frontalGuardConsumesStaminaAndBlocksVanillaImpulse(GameTestHelper helper) {
        ServerPlayer defender = fakePlayer(helper, new Vec3(2, 1, 2));
        ServerPlayer attacker = fakePlayer(helper, new Vec3(4, 1, 2));
        try {
            CharacterData data = CharacterCapability.get(defender);
            PlayerState state = ServerRuntime.state(defender);
            GuardService.start(defender, data, state);
            helper.assertTrue(state.guarding, "A created character with stamina must start guarding");
            double stamina = data.stamina();
            float defended = defendedDamage(data, 8);
            LivingHurtEvent hurt = postHurt(defender, attacker.damageSources().playerAttack(attacker), 8);
            double expectedCost = Math.max(3, defended * ServerConfig.guardStaminaPerDamage.get());
            assertClose(helper, hurt.getAmount(), defended * (1 - ServerConfig.guardDamageReduction.get()),
                    "A frontal guard must apply the configured reduction after one defense reduction");
            assertClose(helper, data.stamina(), stamina - expectedCost,
                    "A successful guard must spend the complete configured stamina cost once");
            LivingKnockBackEvent impulse = new LivingKnockBackEvent(defender, 0.4F, 1, 0);
            MinecraftForge.EVENT_BUS.post(impulse);
            helper.assertTrue(impulse.isCanceled(), "A real block must cancel vanilla's additional knockback");
            helper.assertTrue(GuardService.consumeBlocked(defender) && !GuardService.consumeBlocked(defender),
                    "A blocked-hit result must be consumed once rather than leaking into another hit");
            helper.succeed();
        } finally {
            cleanup(defender, attacker);
        }
    }

    @GameTest(template = "empty", batch = "dbil_guard")
    public static void rearHitCannotUseGuardOrReuseFrontalHitMarker(GameTestHelper helper) {
        ServerPlayer defender = fakePlayer(helper, new Vec3(2, 1, 2));
        ServerPlayer attacker = fakePlayer(helper, new Vec3(4, 1, 2));
        try {
            CharacterData data = CharacterCapability.get(defender);
            PlayerState state = ServerRuntime.state(defender);
            GuardService.start(defender, data, state);
            postHurt(defender, attacker.damageSources().playerAttack(attacker), 8);
            Vec3 behind = defender.position().subtract(defender.getLookAngle().scale(2));
            attacker.moveTo(behind.x, behind.y, behind.z, 0, 0);
            double stamina = data.stamina();
            LivingHurtEvent hurt = postHurt(defender, attacker.damageSources().playerAttack(attacker), 8);
            assertClose(helper, hurt.getAmount(), defendedDamage(data, 8),
                    "A rear hit must receive passive defense but no guard reduction");
            assertClose(helper, data.stamina(), stamina, "A rear hit must not spend guard stamina");
            LivingKnockBackEvent impulse = new LivingKnockBackEvent(defender, 0.4F, 1, 0);
            MinecraftForge.EVENT_BUS.post(impulse);
            helper.assertTrue(!impulse.isCanceled() && !GuardService.consumeBlocked(defender),
                    "A prior frontal block must not suppress a later rear hit's knockback");
            state.guardHeartbeatTick = helper.getLevel().getGameTime() - 41;
            GuardService.tick(defender, data, state);
            helper.assertTrue(!state.guarding, "A missing hold heartbeat must stop guarding after forty ticks");
            helper.succeed();
        } finally {
            cleanup(defender, attacker);
        }
    }

    @GameTest(template = "empty", batch = "dbil_guard")
    public static void heavyStrikeBreaksGuardWithoutLeavingPartialStamina(GameTestHelper helper) {
        ServerPlayer defender = fakePlayer(helper, new Vec3(2, 1, 2));
        ServerPlayer attacker = fakePlayer(helper, new Vec3(4, 1, 2));
        try {
            CharacterData data = CharacterCapability.get(defender);
            PlayerState state = ServerRuntime.state(defender);
            float defended = defendedDamage(data, 8);
            double lightCost = Math.max(3, defended * ServerConfig.guardStaminaPerDamage.get());
            data.setStamina(lightCost + 6);
            GuardService.start(defender, data, state);
            helper.assertTrue(state.guarding && data.canSpendStamina(lightCost),
                    "The fixture must afford a normal block while lacking heavy-strike stamina");
            LivingHurtEvent hurt;
            GuardService.markHeavy(attacker, defender);
            try {
                hurt = postHurt(defender, attacker.damageSources().playerAttack(attacker), 8);
            } finally {
                GuardService.clearHeavy();
            }
            helper.assertTrue(!state.guarding && state.guardBroken && data.stamina() == 0,
                    "An unaffordable heavy block must exhaust stamina and break guard atomically");
            helper.assertTrue(state.guardBreakUntil == helper.getLevel().getGameTime() + ServerConfig.guardBreakTicks.get(),
                    "Guard break must use the server's configured recovery duration");
            assertClose(helper, hurt.getAmount(), defended, "A broken guard must take the full passively defended hit");
            helper.assertTrue(!GuardService.consumeBlocked(defender), "Guard break must not report a successful block");
            data.setStamina(data.maxStamina());
            GuardService.start(defender, data, state);
            helper.assertTrue(!state.guarding, "Refilling stamina cannot bypass the guard-break recovery time");
            helper.succeed();
        } finally {
            cleanup(defender, attacker);
        }
    }

    @GameTest(template = "empty", batch = "dbil_guard")
    public static void environmentBypassesGuardAndStopSurvivesInvalidatedCapability(GameTestHelper helper) {
        ServerPlayer defender = fakePlayer(helper, new Vec3(2, 1, 2));
        try {
            CharacterData data = CharacterCapability.get(defender);
            PlayerState state = ServerRuntime.state(defender);
            GuardService.start(defender, data, state);
            double stamina = data.stamina();
            DamageSource[] environmentalSources = { defender.damageSources().inFire(), defender.damageSources().starve(),
                    defender.damageSources().fellOutOfWorld(), defender.damageSources().fall() };
            for (DamageSource source : environmentalSources) {
                LivingHurtEvent hurt = postHurt(defender, source, 8);
                assertClose(helper, hurt.getAmount(), 8, "Guard and combat defense cannot block environmental damage: " + source.getMsgId());
                assertClose(helper, data.stamina(), stamina, "Environmental damage cannot charge guard stamina");
                helper.assertTrue(!GuardService.consumeBlocked(defender), "Environmental damage cannot create a blocked-hit result");
            }
            defender.invalidateCaps();
            GuardService.stop(defender);
            helper.assertTrue(!state.guarding && !defender.getCapability(CharacterCapability.CAPABILITY).isPresent(),
                    "Logout/reset must stop guard without reading or reviving an invalidated capability");
            helper.succeed();
        } finally {
            cleanup(defender);
        }
    }

    @GameTest(template = "empty", batch = "dbil_guard")
    public static void realMinecraftDamageAppliesDefenseExactlyOnce(GameTestHelper helper) {
        ServerPlayer handler = fakePlayer(helper, new Vec3(2, 1, 2));
        ServerPlayer attacker = fakePlayer(helper, new Vec3(4, 1, 2));
        TestPlayer defender = new TestPlayer(helper.getLevel());
        TrainingEnemy enemy = helper.spawnWithNoFreeWill(ModEntities.TRAINING_ENEMY.get(), new Vec3(4, 1, 2));
        try {
            // FakePlayer itself is always invulnerable. Use its no-op connection with a real ServerPlayer,
            // then expire Minecraft's initial sixty-tick protection using the normal ServerPlayer tick.
            defender.connection = handler.connection;
            configure(defender, helper.absoluteVec(new Vec3(2, 1, 2)));
            for (int tick = 0; tick <= 60; tick++) defender.tick();
            CharacterData data = CharacterCapability.get(defender);
            data.setStat(Stat.DEFENSE, 80);
            float health = defender.getHealth();
            helper.assertTrue(defender.hurt(attacker.damageSources().playerAttack(attacker), 8),
                    "The integration fixture must receive real Minecraft damage after spawn protection");
            assertClose(helper, health - defender.getHealth(), defendedDamage(data, 8),
                    "The actual Minecraft damage pipeline must apply passive defense exactly once");

            defender.setHealth(defender.getMaxHealth());
            defender.invulnerableTime = 0;
            health = defender.getHealth();
            float raw = (float) Math.max(0.25, Math.min(100_000,
                    (float) (4 * ServerConfig.npcDifficulty.get()) * ServerConfig.damageMultiplier.get()));
            float difficultyDamage = switch (helper.getLevel().getDifficulty()) {
                case PEACEFUL -> 0;
                case EASY -> Math.min(raw / 2 + 1, raw);
                case NORMAL -> raw;
                case HARD -> raw * 1.5F;
            };
            boolean hit = CombatService.npcAttack(enemy, defender, 4);
            helper.assertTrue(hit == (difficultyDamage > 0), "NPC attacks must preserve Minecraft difficulty's damage policy");
            assertClose(helper, health - defender.getHealth(), defendedDamage(data, difficultyDamage),
                    "NPC combat must not apply a second defense reduction before the Forge hurt event");
            helper.succeed();
        } finally {
            enemy.discard();
            cleanup(defender, handler, attacker);
        }
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, Vec3 relative) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DBIL-GuardTest"));
        configure(player, helper.absoluteVec(relative));
        return player;
    }

    private static void configure(ServerPlayer player, Vec3 position) {
        player.moveTo(position.x, position.y, position.z, -90, 0);
        player.setGameMode(GameType.SURVIVAL);
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", CharacterData.SCHEMA_VERSION);
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Teste guarda");
        tag.putString("race", Races.HUMAN.toString());
        tag.putString("origin", Origins.EARTH_WARRIOR.toString());
        tag.putString("combatStyle", "balanced");
        tag.putInt("level", 1);
        CharacterData data = CharacterCapability.get(player);
        data.load(tag);
        data.setStat(Stat.DEFENSE, 20);
        data.setStat(Stat.MAX_STAMINA, 120);
        data.setStat(Stat.VITALITY, 120);
        data.setStamina(data.maxStamina());
        CharacterService.applyAttributes(player, data);
        player.setHealth(player.getMaxHealth());
    }

    private static LivingHurtEvent postHurt(ServerPlayer target, DamageSource source, float amount) {
        LivingHurtEvent hurt = new LivingHurtEvent(target, source, amount);
        MinecraftForge.EVENT_BUS.post(hurt);
        return hurt;
    }

    private static float defendedDamage(CharacterData data, float raw) {
        double defense = data.stat(Stat.DEFENSE);
        return (float) (raw * (1 - Math.min(0.65, defense / (defense + 80))));
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001, message + " (expected " + expected + ", actual " + actual + ")");
    }

    private static void cleanup(ServerPlayer... players) {
        GuardService.clearHeavy();
        for (ServerPlayer player : players) {
            GuardService.consumeBlocked(player);
            ServerRuntime.clear(player.getUUID());
            player.discard();
        }
    }

    /** Only this test fixture permits direct player damage, independently of server PvP configuration. */
    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "DBIL-GuardHP"));
        }

        @Override
        public boolean canHarmPlayer(Player other) {
            return true;
        }
    }
}
