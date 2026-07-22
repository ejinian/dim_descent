package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

// Shared logic for moving an entity into/out of the rift dimension, used by both
// /rift enter|leave and the rift door block.
public final class RiftTeleporter {

    public static final ResourceKey<Level> RIFT_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "rift"));

    public static final Vec3 RIFT_SPAWN_POS = new Vec3(0.5, 7.0, 0.5);

    // A 10x10x1 safe patch of stone brick under the spawn point, stamped over whatever the flat
    // generator put there (Nullstone everywhere else) so arriving players don't land straight on
    // the insta-break void floor. Not real worldgen - just placed imperatively the first time
    // someone arrives. Revisit once room placement/generation is actually being designed.
    private static final int PLATFORM_MIN = -5;
    private static final int PLATFORM_MAX = 4;
    private static final int PLATFORM_Y = 6;

    private RiftTeleporter() {
    }

    public static boolean isInRift(ServerLevel level) {
        return level.dimension() == RIFT_LEVEL;
    }

    // Returns the DimensionTransition an entity standing in `level` should take: rift -> overworld,
    // or anywhere else -> rift. Returns null if the target dimension can't be found.
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity) {
        boolean leavingRift = isInRift(level);
        ResourceKey<Level> targetKey = leavingRift ? Level.OVERWORLD : RIFT_LEVEL;
        ServerLevel targetLevel = level.getServer().getLevel(targetKey);
        if (targetLevel == null) {
            return null;
        }

        Vec3 targetPos;
        if (leavingRift) {
            targetPos = Vec3.atBottomCenterOf(targetLevel.getSharedSpawnPos());
        } else {
            ensureSpawnPlatform(targetLevel);
            targetPos = RIFT_SPAWN_POS;
        }

        return new DimensionTransition(
                targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    private static void ensureSpawnPlatform(ServerLevel riftLevel) {
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = PLATFORM_MIN; x <= PLATFORM_MAX; x++) {
            for (int z = PLATFORM_MIN; z <= PLATFORM_MAX; z++) {
                BlockPos pos = new BlockPos(x, PLATFORM_Y, z);
                if (riftLevel.getBlockState(pos).getBlock() != Blocks.STONE_BRICKS) {
                    riftLevel.setBlock(pos, stoneBricks, 3);
                }
            }
        }
    }
}
