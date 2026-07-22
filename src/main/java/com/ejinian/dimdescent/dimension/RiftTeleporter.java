package com.ejinian.dimdescent.dimension;

import javax.annotation.Nullable;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

    // Each auto-generated exit door (one per distinct door ever walked through from outside the
    // rift) gets its own little platform, laid out in a simple line so they never collide. Same
    // "not real worldgen yet" caveat as the main spawn platform above.
    private static final int GENERATED_DOOR_SPACING = 6;
    private static final int GENERATED_DOOR_BASE_Z = 12;
    private static final int GENERATED_DOOR_PLATFORM_RADIUS = 2;

    private RiftTeleporter() {
    }

    public static boolean isInRift(ServerLevel level) {
        return level.dimension() == RIFT_LEVEL;
    }

    // Default transition with no specific door involved (used by /rift enter|leave, and as the
    // fallback when a door has no linked partner): rift -> overworld spawn, anywhere else -> the
    // rift's default platform.
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
            ensurePlatformAt(targetLevel, BlockPos.ZERO, PLATFORM_MIN, PLATFORM_MAX, PLATFORM_MIN, PLATFORM_MAX, PLATFORM_Y);
            targetPos = RIFT_SPAWN_POS;
        }

        return new DimensionTransition(
                targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    // Door-aware transition: walking through a specific door. Each door that's ever used from
    // outside the rift gets its own paired exit door generated on the other side the first time,
    // and walking through either door of a pair always sends you back to the other one - a stable
    // one-to-one link, the same way a Nether portal remembers its partner. A door with no link
    // (e.g. one placed by hand inside the rift) falls back to the default rift -> overworld-spawn
    // exit and does NOT get linked to anything.
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity, BlockPos doorPos) {
        RiftDoorLinkData linkData = RiftDoorLinkData.get(level);
        DoorLocation from = new DoorLocation(level.dimension(), doorPos.immutable());
        DoorLocation linked = linkData.getLinkedDoor(from);

        if (linked != null) {
            ServerLevel targetLevel = level.getServer().getLevel(linked.dimension());
            if (targetLevel == null) {
                return null;
            }
            Vec3 targetPos = Vec3.atBottomCenterOf(linked.pos());
            return new DimensionTransition(
                    targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                    DimensionTransition.DO_NOTHING);
        }

        if (isInRift(level)) {
            // An unlinked door inside the rift - just the default exit, no pairing created.
            return getTransitionFor(level, entity);
        }

        // First time this door has been used from outside the rift - generate its paired exit.
        ServerLevel riftLevel = level.getServer().getLevel(RIFT_LEVEL);
        if (riftLevel == null) {
            return null;
        }
        BlockPos generatedDoorPos = generateExitDoor(riftLevel, linkData.allocateGeneratedDoorIndex());
        linkData.link(from, new DoorLocation(RIFT_LEVEL, generatedDoorPos));

        Vec3 targetPos = Vec3.atBottomCenterOf(generatedDoorPos);
        return new DimensionTransition(
                riftLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    // Places a new rift door (both halves, closed, facing south) on its own small platform, at a
    // position derived from `index` so it never overlaps an earlier generated door.
    private static BlockPos generateExitDoor(ServerLevel riftLevel, int index) {
        int x = index * GENERATED_DOOR_SPACING;
        int z = GENERATED_DOOR_BASE_Z;
        BlockPos platformCenter = new BlockPos(x, PLATFORM_Y, z);
        ensurePlatformAt(riftLevel, platformCenter,
                -GENERATED_DOOR_PLATFORM_RADIUS, GENERATED_DOOR_PLATFORM_RADIUS,
                -GENERATED_DOOR_PLATFORM_RADIUS, GENERATED_DOOR_PLATFORM_RADIUS, PLATFORM_Y);

        BlockPos doorPos = new BlockPos(x, PLATFORM_Y + 1, z);
        BlockState lowerState = ModRegistry.RIFT_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        riftLevel.setBlock(doorPos, lowerState, 3);
        riftLevel.setBlock(doorPos.above(), lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        return doorPos;
    }

    // Stamps a stone-brick platform in the X/Z ranges (relative to `center`) at `platformY`,
    // shared by both the fixed default-spawn platform and each generated door's own platform.
    private static void ensurePlatformAt(ServerLevel riftLevel, BlockPos center,
                                          int minX, int maxX, int minZ, int maxZ, int platformY) {
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(center.getX() + x, platformY, center.getZ() + z);
                if (riftLevel.getBlockState(pos).getBlock() != Blocks.STONE_BRICKS) {
                    riftLevel.setBlock(pos, stoneBricks, 3);
                }
            }
        }
    }
}
