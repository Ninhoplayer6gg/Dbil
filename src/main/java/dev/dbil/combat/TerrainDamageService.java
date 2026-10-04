package dev.dbil.combat;

import dev.dbil.DBIL;
import dev.dbil.config.ServerConfig;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optional, server-configured crater creation. Disabled by default. Every block passes the same protections a
 * player break would (spawn protection, game mode, Forge BreakEvent for claim mods) plus DBIL's own rules:
 * unbreakable blocks, block entities, high blast resistance and the {@code dbil:terrain_immune} tag are kept.
 * A per-tick budget bounds the total work even when many beams land at once.
 */
public final class TerrainDamageService {
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, DBIL.id("terrain_immune"));
    private static final int TICK_BUDGET = 96;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    private TerrainDamageService() {}

    public static boolean enabled() {
        return ServerConfig.SPEC.isLoaded() && ServerConfig.terrainDamage.get();
    }

    /** @return number of blocks removed */
    public static int crater(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, float charge) {
        if (!enabled() || owner == null || !owner.isAlive() || charge < ServerConfig.terrainMinCharge.get()) return 0;
        long now = level.getGameTime();
        if (now != budgetTick) { budgetTick = now; budgetUsed = 0; }
        int limit = Math.min(ServerConfig.terrainMaxBlocks.get(), TICK_BUDGET - budgetUsed);
        if (limit <= 0) return 0;
        double r = Math.max(0.5, Math.min(3.0, radius));
        int ir = (int) Math.ceil(r);
        BlockPos origin = BlockPos.containing(center);
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -ir; dx <= ir; dx++) for (int dy = -ir; dy <= ir; dy++) for (int dz = -ir; dz <= ir; dz++) {
            // Slightly flattened sphere reads as a crater rather than a ball-shaped hole.
            double d = dx * dx + dy * dy * 1.6 + dz * dz;
            if (d <= r * r) candidates.add(origin.offset(dx, dy, dz));
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(center)));
        boolean drops = ServerConfig.terrainDropBlocks.get();
        int removed = 0;
        for (BlockPos pos : candidates) {
            if (removed >= limit) break;
            if (!breakable(owner, level, pos)) continue;
            if (removed < 4) level.destroyBlock(pos, drops, owner);
            else if (drops) level.destroyBlock(pos, true, owner);
            else level.removeBlock(pos, false);
            removed++;
        }
        budgetUsed += removed;
        if (removed > 0) FxService.at(level, FxType.TERRAIN_DEBRIS, 0, center, (float) r, 0x8A7A66);
        return removed;
    }

    private static boolean breakable(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0 || state.is(IMMUNE)) return false;
        if (ServerConfig.terrainProtectBlockEntities.get() && state.hasBlockEntity()) return false;
        if (state.getBlock().getExplosionResistance() > ServerConfig.terrainMaxResistance.get()) return false;
        if (!level.mayInteract(owner, pos) || owner.blockActionRestricted(level, pos, owner.gameMode.getGameModeForPlayer())) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, owner));
    }
}
