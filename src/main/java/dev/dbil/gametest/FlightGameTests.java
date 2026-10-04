package dev.dbil.gametest;

import com.mojang.authlib.GameProfile;
import dev.dbil.DBIL;
import dev.dbil.character.CharacterData;
import dev.dbil.flight.FlightMotion;
import dev.dbil.flight.FlightService;
import dev.dbil.race.Races;
import dev.dbil.server.PlayerState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Regression coverage for continuous flight motion and intent recovery; Android rendering is a separate test. */
@GameTestHolder(DBIL.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    private FlightGameTests() {}

    @GameTest(template = "empty", batch = "dbil_flight")
    public static void directionAndSpeedAreFiniteAndBounded(GameTestHelper helper) {
        double cap = FlightMotion.maximumSpeed(10_000, 10, 0.5);
        helper.assertTrue(cap == 0.5, "Attribute and transformation growth must preserve server speed cap");
        Vec3 diagonal = FlightMotion.desired(new FlightMotion.Input(1, 1, true, false, 45), cap);
        helper.assertTrue(Math.abs(diagonal.length() - cap) < 1.0e-9,
                "Diagonal horizontal/vertical input cannot exceed straight-line speed");
        var invalid = new FlightMotion.Input(Float.NaN, Float.POSITIVE_INFINITY, false, false, Float.NaN);
        helper.assertTrue(FlightMotion.desired(invalid, cap).equals(Vec3.ZERO), "Malformed input cannot create non-finite motion");
        helper.assertTrue(FlightMotion.desired(new FlightMotion.Input(0, 0, true, true, 0), cap).equals(Vec3.ZERO),
                "Opposite vertical controls cancel out");
        helper.assertTrue(FlightMotion.desired(new FlightMotion.Input(1, 0, false, false, 0), Double.NaN).equals(Vec3.ZERO),
                "An invalid speed cannot create NaN motion");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_flight")
    public static void accelerationAndBrakingDoNotSnapOrDrift(GameTestHelper helper) {
        var forward = new FlightMotion.Input(1, 0, false, false, 0);
        Vec3 velocity = FlightMotion.nextVelocity(Vec3.ZERO, forward, 0.4);
        double firstSpeed = velocity.length();
        helper.assertTrue(firstSpeed > 0 && firstSpeed < 0.4, "Flight must accelerate gradually from rest");
        for (int tick = 0; tick < 40; tick++) {
            double previous = velocity.length();
            velocity = FlightMotion.nextVelocity(velocity, forward, 0.4);
            helper.assertTrue(velocity.length() >= previous && velocity.length() <= 0.4 + 1.0e-9,
                    "Acceleration must approach the cruise cap without overshooting");
        }
        helper.assertTrue(velocity.length() > firstSpeed, "Held input must provide continuous acceleration");
        for (int tick = 0; tick < 40; tick++) {
            double previous = velocity.length();
            velocity = FlightMotion.nextVelocity(velocity, FlightMotion.Input.NONE, 0.4);
            helper.assertTrue(velocity.length() <= previous + 1.0e-9, "Released input must brake monotonically");
        }
        helper.assertTrue(velocity.equals(Vec3.ZERO), "Braking must converge to stationary hover without endless drift");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_flight")
    public static void collisionBlocksOnlyCollidedAxes(GameTestHelper helper) {
        Vec3 requested = new Vec3(0.5, 0.2, -0.4);
        Vec3 result = FlightMotion.afterCollision(requested, new Vec3(0.1, 0.2, -0.4));
        helper.assertTrue(result.equals(new Vec3(0, 0.2, -0.4)),
                "A horizontal wall must stop its blocked axis while retaining vertical/tangential flight");
        helper.assertTrue(FlightMotion.afterCollision(requested, new Vec3(0.5, 0, -0.4)).y == 0,
                "A vertical collision must clear vertical momentum");
        helper.assertTrue(FlightMotion.afterCollision(requested, new Vec3(Double.NaN, 0, 0)).equals(Vec3.ZERO),
                "An invalid collision result cannot preserve corrupt motion");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_flight")
    public static void inputSequenceRecoversAfterDroppedPackets(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerState state = new PlayerState();
        helper.assertTrue(FlightService.acceptInput(player, state, 16, 1, 0, false, false, 0, 0),
                "A valid initial input must be accepted");
        state.lastInputTick = player.serverLevel().getGameTime() - 100;
        helper.assertTrue(FlightService.acceptInput(player, state, 101, 0, 1, true, false, 90, 0),
                "A dropped/rate-limited burst must not permanently block later increasing input sequences");
        helper.assertTrue(state.flightInputSequence == 101 && state.flightInputTicks == 0 && state.ascend,
                "A recovered input must replace the held command and reset its tick count");
        helper.assertTrue(!FlightService.acceptInput(player, state, 100, 1, 0, false, false, 0, 0)
                        && !FlightService.acceptInput(player, state, 101, 1, 0, false, false, 0, 0),
                "Older or repeated inputs cannot replace the accepted command");
        helper.assertTrue(!FlightService.acceptInput(player, state, 102, Float.NaN, 0, false, false, 0, 0)
                        && !FlightService.acceptInput(player, state, 102, 1, 0, false, false, Float.POSITIVE_INFINITY, 0),
                "Increasing sequence does not bypass finite input checks");
        helper.assertTrue(state.flightInputSequence == 101 && state.strafe == 1 && state.ascend,
                "Rejected packets leave the prior authoritative input unchanged");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dbil_flight")
    public static void clientPositionCannotChooseFlightDestination(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("characterCreated", true);
        tag.putString("characterName", "Flight authority");
        tag.putString("race", Races.HUMAN.toString());
        tag.putInt("level", 1);
        CharacterData data = new CharacterData();
        data.load(tag);
        PlayerState state = new PlayerState();
        state.flying = true;
        state.flightPosition = player.position();
        state.forward = 1;
        state.lastInputTick = player.serverLevel().getGameTime();
        Vec3 authoritative = state.flightPosition;
        player.setPos(authoritative.add(2, 0, 0));
        FlightService.beforeTick(player, data, state);
        helper.assertTrue(player.position().equals(authoritative), "START must restore server authority after a client position change");
        double ki = data.ki();
        FlightService.tick(player, data, state);
        Vec3 movement = player.position().subtract(authoritative);
        helper.assertTrue(movement.x == 0 && movement.z > 0 && movement.length() <= state.flightMaximumSpeed,
                "Destination must follow validated intent rather than the forged position");
        helper.assertTrue(state.flightPosition.equals(player.position()) && data.ki() < ki,
                "Server position and Ki payment must both advance through the authoritative integrator");
        player.discard();
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Flight-GameTest"));
        Vec3 position = helper.absoluteVec(new Vec3(2, 10, 2));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        return player;
    }
}
