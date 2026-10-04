package dev.dbil.flight;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Deterministic 20 Hz motion shared by server authority and the local presentation predictor. */
public final class FlightMotion {
    public static final double ACCELERATION = 0.18;
    public static final double BRAKING = 0.30;
    public static final int INPUT_TIMEOUT_TICKS = 20;
    public static final int ACK_INTERVAL_TICKS = 4;
    public static final double HARD_CORRECTION_DISTANCE = 5.0;
    private static final double STOP_EPSILON_SQUARED = 0.00001;

    public record Input(float forward, float strafe, boolean ascend, boolean descend, float yaw) {
        public static final Input NONE = new Input(0, 0, false, false, 0);
        public Input {
            forward = Float.isFinite(forward) ? Math.max(-1, Math.min(1, forward)) : 0;
            strafe = Float.isFinite(strafe) ? Math.max(-1, Math.min(1, strafe)) : 0;
            yaw = Float.isFinite(yaw) ? yaw : 0;
        }
    }

    private FlightMotion() {}

    public static double maximumSpeed(double speedAttribute, double multiplier, double serverCap) {
        double attribute = Double.isFinite(speedAttribute) ? Math.max(1, speedAttribute) : 10;
        double scale = Double.isFinite(multiplier) ? Math.max(0.1, Math.min(10, multiplier)) : 1;
        double cap = Double.isFinite(serverCap) ? Math.max(0.1, Math.min(1.5, serverCap)) : 0.75;
        return Math.min(cap, (0.26 + attribute * 0.006) * scale);
    }

    public static Vec3 desired(Input input, double maximumSpeed) {
        double yaw = Math.toRadians(input.yaw());
        Vec3 direction = new Vec3(-Math.sin(yaw) * input.forward() + Math.cos(yaw) * input.strafe(),
                (input.ascend() ? 1 : 0) - (input.descend() ? 1 : 0),
                Math.cos(yaw) * input.forward() + Math.sin(yaw) * input.strafe());
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        double speed = Double.isFinite(maximumSpeed) ? Math.max(0, Math.min(1.5, maximumSpeed)) : 0;
        return direction.scale(speed);
    }

    public static Vec3 nextVelocity(Vec3 velocity, Input input, double maximumSpeed) {
        if (!finite(velocity)) velocity = Vec3.ZERO;
        // Only server-created dash/launch impulses can exceed cruising speed. They decay naturally.
        if (velocity.lengthSqr() > 16) velocity = velocity.normalize().scale(4);
        Vec3 target = desired(input, maximumSpeed);
        Vec3 result = velocity.lerp(target, target.lengthSqr() < 0.001 ? BRAKING : ACCELERATION);
        return result.lengthSqr() < STOP_EPSILON_SQUARED ? Vec3.ZERO : result;
    }

    /** Drop only components that the normal Minecraft collision solver actually blocked. */
    public static Vec3 afterCollision(Vec3 requested, Vec3 actual) {
        if (!finite(requested) || !finite(actual)) return Vec3.ZERO;
        return new Vec3(Math.abs(requested.x - actual.x) > 1.0e-6 ? 0 : requested.x,
                Math.abs(requested.y - actual.y) > 1.0e-6 ? 0 : requested.y,
                Math.abs(requested.z - actual.z) > 1.0e-6 ? 0 : requested.z);
    }

    /** Bounded collision replay for a local ACK; it never mutates the player or ticks world entities. */
    public static Vec3 replayMovement(Entity entity, Vec3 position, Vec3 requested) {
        AABB box = entity.getBoundingBox().move(position.subtract(entity.position()));
        return Entity.collideBoundingBox(entity, requested, box, entity.level(), List.of());
    }

    public static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
