package dev.dbil.client;

import dev.dbil.DBIL;
import dev.dbil.flight.FlightMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Movement presentation only: the server ACK supplies authority, speed, collisions and action outcomes. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class ClientFlightController {
    private static final int HISTORY_SIZE = 64;
    private static final Frame[] HISTORY = new Frame[HISTORY_SIZE];
    private static int head, count, commandSequence, commandTicks;
    private static long lastAckTick = Long.MIN_VALUE;
    private static LocalPlayer trackedPlayer;
    private static boolean serverFlying;
    private static double maximumSpeed = 0.32;
    private static Vec3 velocity = Vec3.ZERO;
    private static Vec3 correction = Vec3.ZERO;

    private record Frame(int sequence, int inputTicks, FlightMotion.Input input) {}
    private ClientFlightController() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.player instanceof LocalPlayer player) || player != minecraft.player) return;
        if (event.phase == TickEvent.Phase.START) beforeTick(player);
        else afterTick(player);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void inputUpdated(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player) return;
        ClientControls.captureInput(minecraft, event.getInput());
        if (!active()) return;
        // Capture before neutralizing: launcher-injected input is preserved alongside touch-menu controls.
        var input = event.getInput();
        input.forwardImpulse = input.leftImpulse = 0;
        input.jumping = input.shiftKeyDown = false;
        input.up = input.down = input.left = input.right = false;
        minecraft.player.setSprinting(false);
    }

    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return serverFlying && minecraft.player != null && minecraft.player == trackedPlayer
                && minecraft.player.isAlive() && !minecraft.player.isSleeping() && !minecraft.player.isPassenger() && !minecraft.player.isSpectator()
                && !minecraft.player.isInWaterOrBubble() && ClientState.received() && ClientState.data().created();
    }

    public static void beforeTick(LocalPlayer player) {
        if (trackedPlayer != null && trackedPlayer != player) reset();
        if (!active()) return;
        // Vanilla travel still runs, but consumes zero velocity/input. Our END step is the only translation.
        player.setNoGravity(true);
        player.setOnGround(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    public static void afterTick(LocalPlayer player) {
        if (!active()) return;
        FlightMotion.Input input = ClientControls.flightInput(Minecraft.getInstance());
        Vec3 requested = FlightMotion.nextVelocity(velocity, input, maximumSpeed);
        Vec3 start = player.position();
        player.setNoGravity(true);
        player.setOnGround(false);
        player.move(MoverType.SELF, requested);
        velocity = FlightMotion.afterCollision(requested, player.position().subtract(start));
        if (correction.lengthSqr() > 1.0e-6) {
            Vec3 adjustment = correction.scale(0.35);
            if (adjustment.lengthSqr() > 0.0225) adjustment = adjustment.normalize().scale(0.15);
            Vec3 beforeCorrection = player.position();
            player.move(MoverType.SELF, adjustment);
            correction = correction.subtract(player.position().subtract(beforeCorrection));
            // A server/client block mismatch cannot accumulate an unbounded correction against a wall.
            if (player.position().distanceToSqr(beforeCorrection) < 1.0e-8) correction = Vec3.ZERO;
        }
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
        Frame frame = new Frame(commandSequence, ++commandTicks, input);
        HISTORY[(head + count) % HISTORY_SIZE] = frame;
        if (count < HISTORY_SIZE) count++;
        else head = (head + 1) % HISTORY_SIZE;
    }

    public static void inputSent(int sequence) {
        commandSequence = sequence;
        commandTicks = 0;
    }

    /** Called by the owner-only S2C packet, on the client game thread. No character resource is modified. */
    public static void acceptAck(long serverTick, int sequence, int inputTicks, Vec3 position, Vec3 motion,
                                 double speed, boolean flying, boolean hardReset) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (trackedPlayer != null && trackedPlayer != player) reset();
        if (player == null || !FlightMotion.finite(position) || !FlightMotion.finite(motion)
                || !Double.isFinite(speed) || speed < 0.1 || speed > 1.5 || sequence < 0 || inputTicks < 0
                || serverTick < lastAckTick) return;
        lastAckTick = serverTick;
        trackedPlayer = player;
        maximumSpeed = speed;
        serverFlying = flying;
        if (!flying) {
            clearHistory();
            velocity = correction = Vec3.ZERO;
            player.setNoGravity(false);
            if (hardReset) setPosition(player, position);
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        player.setNoGravity(true);
        Vec3 targetPosition = position;
        Vec3 targetVelocity = motion;
        // Drop acknowledged local frames; replay at most 64 remaining 20 Hz movement frames.
        while (count > 0) {
            Frame frame = HISTORY[head];
            if (frame.sequence() > sequence || frame.sequence() == sequence && frame.inputTicks() > inputTicks) break;
            HISTORY[head] = null;
            head = (head + 1) % HISTORY_SIZE;
            count--;
        }
        if (!hardReset) {
            for (int i = 0; i < count; i++) {
                Frame frame = HISTORY[(head + i) % HISTORY_SIZE];
                Vec3 requested = FlightMotion.nextVelocity(targetVelocity, frame.input(), maximumSpeed);
                Vec3 actual = FlightMotion.replayMovement(player, targetPosition, requested);
                targetPosition = targetPosition.add(actual);
                targetVelocity = FlightMotion.afterCollision(requested, actual);
            }
        }
        Vec3 error = targetPosition.subtract(player.position());
        velocity = targetVelocity;
        if (hardReset || error.lengthSqr() > FlightMotion.HARD_CORRECTION_DISTANCE * FlightMotion.HARD_CORRECTION_DISTANCE) {
            setPosition(player, targetPosition);
            clearHistory();
            boolean sameCommand = commandSequence <= sequence;
            commandSequence = Math.max(commandSequence, sequence);
            commandTicks = sameCommand ? inputTicks : 0;
            correction = Vec3.ZERO;
        } else {
            correction = error;
        }
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
    }

    private static void setPosition(LocalPlayer player, Vec3 position) {
        player.setPos(position);
        player.xo = player.xOld = position.x;
        player.yo = player.yOld = position.y;
        player.zo = player.zOld = position.z;
    }

    private static void clearHistory() {
        java.util.Arrays.fill(HISTORY, null);
        head = count = 0;
    }

    public static void reset() {
        if (trackedPlayer != null && serverFlying) trackedPlayer.setNoGravity(false);
        clearHistory();
        commandSequence = commandTicks = 0;
        lastAckTick = Long.MIN_VALUE;
        trackedPlayer = null;
        serverFlying = false;
        maximumSpeed = 0.32;
        velocity = correction = Vec3.ZERO;
    }
}
