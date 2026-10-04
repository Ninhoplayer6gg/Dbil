package dev.dbil.training;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.config.ServerConfig;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.registry.ModEntities;
import dev.dbil.server.ServerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, temporary sparring sessions requested through the normal training menu. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID)
public final class TrainingSessionService {
    private static final String EXPIRY = "DBILSparringExpiry";
    private static final String OWNER = "DBILSparringOwner";
    private static final int LIFETIME_TICKS = 6000;
    private static final int[][] OFFSETS = {{0, 4}, {4, 0}, {0, -4}, {-4, 0},
            {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};

    private TrainingSessionService() {}

    public static boolean start(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()
                || !CharacterCapability.get(player).created()) return false;
        var state = ServerRuntime.state(player);
        long now = player.serverLevel().getGameTime();
        if (state.flying || state.transformationChargeTicks > 0 || player.isPassenger()) {
            return rejected(player, "message.dbil.sparring_land");
        }
        if (state.sparringEnemy != null && player.serverLevel().getEntity(state.sparringEnemy) instanceof TrainingEnemy active
                && active.isAlive()) return rejected(player, "message.dbil.sparring_active");
        if (now < state.nextSparringTick) return rejected(player, "message.dbil.sparring_cooldown");
        var nearby = player.serverLevel().getEntitiesOfClass(TrainingEnemy.class, player.getBoundingBox().inflate(24));
        if (nearby.size() >= ServerConfig.maxNearbyTrainingEnemies.get()) {
            return rejected(player, "message.dbil.sparring_crowded");
        }
        TrainingEnemy enemy = ModEntities.TRAINING_ENEMY.get().create(player.serverLevel());
        if (enemy == null) return false;
        BlockPos origin = player.blockPosition();
        for (int[] offset : OFFSETS) {
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos position = origin.offset(offset[0], dy, offset[1]);
                if (position.getY() <= player.level().getMinBuildHeight() || position.getY() + 2 >= player.level().getMaxBuildHeight()
                        || !player.serverLevel().hasChunkAt(position)
                        || !player.serverLevel().getWorldBorder().isWithinBounds(position)
                        || !player.level().getBlockState(position.below()).isFaceSturdy(player.level(), position.below(), Direction.UP)) continue;
                enemy.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, player.getYRot() + 180, 0);
                if (!player.level().noCollision(enemy) || player.level().containsAnyLiquid(enemy.getBoundingBox())) continue;
                enemy.getPersistentData().putLong(EXPIRY, now + LIFETIME_TICKS);
                enemy.getPersistentData().putUUID(OWNER, player.getUUID());
                enemy.setPersistenceRequired();
                enemy.setTarget(player);
                if (!player.serverLevel().addFreshEntity(enemy)) return false;
                state.sparringEnemy = enemy.getUUID();
                state.nextSparringTick = now + ServerConfig.sparringCooldownTicks.get();
                player.displayClientMessage(Component.translatable("message.dbil.sparring_started"), true);
                return true;
            }
        }
        return rejected(player, "message.dbil.sparring_space");
    }

    /** Only menu-created rivals expire. Spawn-egg and administrative rivals retain their normal lifecycle. */
    @SubscribeEvent
    public static void expire(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof TrainingEnemy enemy) || enemy.level().isClientSide || enemy.tickCount % 20 != 0) return;
        var tag = enemy.getPersistentData();
        if (!tag.contains(EXPIRY, Tag.TAG_LONG) || !tag.hasUUID(OWNER)) return;
        var owner = enemy.getServer().getPlayerList().getPlayer(tag.getUUID(OWNER));
        if (enemy.level().getGameTime() >= tag.getLong(EXPIRY) || owner == null || owner.level() != enemy.level()) enemy.discard();
    }

    private static boolean rejected(ServerPlayer player, String message) {
        player.displayClientMessage(Component.translatable(message), true);
        return false;
    }
}
