package dev.dbil.fx;

import dev.dbil.network.Network;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-side helpers. Every event is small, bounded and only describes something that already happened. */
public final class FxService {
    private static final double POSITIONAL_RANGE = 64;

    private FxService() {}

    public static void entity(Entity entity, int type, int variant, int otherId, float magnitude, int color) {
        if (entity.level().isClientSide) return;
        Network.sendFx(entity, new Network.FxEvent(type, variant, entity.getId(), otherId, magnitude,
                (float) entity.getX(), (float) entity.getY(), (float) entity.getZ(), color));
    }

    public static void entityAt(Entity entity, int type, int variant, int otherId, float magnitude, Vec3 position, int color) {
        if (entity.level().isClientSide) return;
        Network.sendFx(entity, new Network.FxEvent(type, variant, entity.getId(), otherId, magnitude,
                (float) position.x, (float) position.y, (float) position.z, color));
    }

    public static void at(ServerLevel level, int type, int variant, Vec3 position, float magnitude, int color) {
        Network.sendFxNear(level, position, POSITIONAL_RANGE, new Network.FxEvent(type, variant, -1, -1, magnitude,
                (float) position.x, (float) position.y, (float) position.z, color));
    }
}
