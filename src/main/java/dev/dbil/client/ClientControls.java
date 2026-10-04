package dev.dbil.client;

import dev.dbil.flight.FlightMotion;
import dev.dbil.network.Network;
import dev.dbil.server.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;

/** Touch-menu and launcher-injected controls share the same bounded server intents. */
public final class ClientControls {
    public static boolean touchCharging, touchGuarding, touchFast;
    public static boolean forward, backward, left, right, ascend, descend;
    private static boolean sentCharging, sentGuarding;
    private static int inputTicks, chargeHeartbeat, guardHeartbeat, sequence;
    private static float lastForward, lastStrafe;
    private static boolean lastAscend, lastDescend, lastFast;
    private static float lastPitch;
    private static float capturedForward, capturedStrafe;
    private static boolean capturedAscend, capturedDescend;
    private static int capturedTick = -1;

    private ClientControls() {}

    public static void captureInput(Minecraft minecraft, Input input) {
        if (minecraft.player == null) return;
        // Digital direction flags bypass vanilla sneak slowdown. Analog launchers keep their impulses.
        capturedForward = input.up || input.down ? (input.up ? 1 : 0) - (input.down ? 1 : 0) : input.forwardImpulse;
        capturedStrafe = input.left || input.right ? (input.left ? 1 : 0) - (input.right ? 1 : 0) : input.leftImpulse;
        capturedAscend = input.jumping;
        capturedDescend = input.shiftKeyDown;
        capturedTick = minecraft.player.tickCount;
    }

    public static FlightMotion.Input flightInput(Minecraft minecraft) {
        boolean keyboard = minecraft.screen == null && minecraft.player != null;
        boolean fresh = keyboard && capturedTick >= minecraft.player.tickCount - 1;
        float keyboardForward = fresh ? capturedForward
                : keyboard ? (minecraft.options.keyUp.isDown() ? 1 : 0) - (minecraft.options.keyDown.isDown() ? 1 : 0) : 0;
        float keyboardStrafe = fresh ? capturedStrafe
                : keyboard ? (minecraft.options.keyLeft.isDown() ? 1 : 0) - (minecraft.options.keyRight.isDown() ? 1 : 0) : 0;
        float f = keyboardForward + (forward ? 1 : 0) - (backward ? 1 : 0);
        float s = keyboardStrafe + (left ? 1 : 0) - (right ? 1 : 0);
        boolean up = ascend || keyboard && (fresh ? capturedAscend : minecraft.options.keyJump.isDown());
        boolean down = descend || keyboard && (fresh ? capturedDescend : minecraft.options.keyShift.isDown());
        boolean fast = touchFast || keyboard && minecraft.options.keySprint.isDown();
        return new FlightMotion.Input(f, s, up, down, minecraft.player == null ? 0 : minecraft.player.getYRot(),
                minecraft.player == null ? 0 : minecraft.player.getXRot(), fast);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null) return;
        boolean charging = touchCharging || minecraft.screen == null && ClientEvents.CHARGE.isDown();
        if (charging != sentCharging) {
            Network.sendAction(charging ? Action.CHARGE_START : Action.CHARGE_STOP);
            sentCharging = charging;
            chargeHeartbeat = 0;
        } else if (charging && ++chargeHeartbeat >= 20) {
            Network.sendAction(Action.CHARGE_START);
            chargeHeartbeat = 0;
        }
        boolean physicalGuard = minecraft.screen == null && minecraft.player.getMainHandItem().isEmpty()
                && ClientState.visual(minecraft.player.getId()).targetId() >= 0 && minecraft.options.keyUse.isDown();
        boolean guarding = touchGuarding || physicalGuard;
        if (guarding != sentGuarding) {
            Network.sendAction(guarding ? Action.GUARD_START : Action.GUARD_STOP);
            sentGuarding = guarding;
            guardHeartbeat = 0;
        } else if (guarding && ++guardHeartbeat >= 20) {
            Network.sendAction(Action.GUARD_START);
            guardHeartbeat = 0;
        }
        if (!ClientFlightController.active() && !ClientState.visual(minecraft.player.getId()).flying()) {
            inputTicks = 4;
            return;
        }
        FlightMotion.Input input = flightInput(minecraft);
        float pitch = minecraft.player.getXRot();
        boolean changed = input.forward() != lastForward || input.strafe() != lastStrafe
                || input.ascend() != lastAscend || input.descend() != lastDescend || input.fast() != lastFast
                || input.fast() && Math.abs(pitch - lastPitch) > 3;
        if (changed || ++inputTicks >= 4) {
            inputTicks = 0;
            int next = ++sequence;
            if (next <= 0) { sequence = next = 1; }
            Network.sendFlightInput(next, input.forward(), input.strafe(), input.ascend(), input.descend(), input.yaw(), pitch, input.fast());
            ClientFlightController.inputSent(next);
            lastForward = input.forward(); lastStrafe = input.strafe();
            lastAscend = input.ascend(); lastDescend = input.descend();
            lastFast = input.fast(); lastPitch = pitch;
        }
    }

    public static void clearTouchMovement() {
        forward = backward = left = right = ascend = descend = touchFast = false;
    }

    public static void stopAll() {
        touchCharging = touchGuarding = false;
        clearTouchMovement();
        sentCharging = sentGuarding = false;
        Network.sendAction(Action.STOP_ALL);
    }

    public static void reset() {
        touchCharging = touchGuarding = false;
        clearTouchMovement();
        sentCharging = sentGuarding = false;
        inputTicks = chargeHeartbeat = guardHeartbeat = sequence = 0;
        lastForward = lastStrafe = capturedForward = capturedStrafe = 0;
        lastAscend = lastDescend = capturedAscend = capturedDescend = lastFast = false;
        lastPitch = 0;
        capturedTick = -1;
    }
}
