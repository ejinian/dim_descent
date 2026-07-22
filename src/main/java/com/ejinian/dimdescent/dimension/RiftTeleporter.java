package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    // There is only ever ONE generated exit door, placed once on the same default platform -
    // not one per overworld door (see RiftDoorLinkData for how the single door still returns
    // each player to wherever they personally entered from).
    private static final BlockPos GENERATED_DOOR_POS = new BlockPos(0, PLATFORM_Y + 1, -4);

    private RiftTeleporter() {
    }

    public static boolean isInRift(ServerLevel level) {
        return level.dimension() == RIFT_LEVEL;
    }

    // Default transition with no specific door involved (used by /rift enter|leave, and as the
    // fallback whenever door-pairing doesn't apply): rift -> overworld spawn, anywhere else -> the
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
            ensureSpawnPlatform(targetLevel);
            targetPos = RIFT_SPAWN_POS;
        }

        return new DimensionTransition(
                targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    // Door-aware transition: walking through a specific door.
    //
    // Entering (from anywhere outside the rift): always lands at the single shared generated exit
    // door, creating it the first time ever it's needed. Remembers, per player, which door they
    // personally just entered from.
    //
    // Leaving (from inside the rift): if the door is that one shared generated door AND this
    // player has a recorded entry point, sends them back to exactly that door. Any other door
    // inside the rift (e.g. one placed by hand) - or a player with no recorded entry - just gets
    // the default overworld-spawn exit, same as before, with no pairing created.
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity, BlockPos doorPos) {
        RiftDoorLinkData linkData = RiftDoorLinkData.get(level);

        if (isInRift(level)) {
            DoorLocation generatedDoor = linkData.getGeneratedExitDoor();
            boolean isGeneratedDoor = generatedDoor != null && generatedDoor.pos().equals(doorPos);
            if (isGeneratedDoor && entity instanceof ServerPlayer player) {
                DoorLocation lastEntry = linkData.getLastEntry(player.getUUID());
                if (lastEntry != null) {
                    ServerLevel targetLevel = level.getServer().getLevel(lastEntry.dimension());
                    if (targetLevel != null) {
                        Vec3 targetPos = Vec3.atBottomCenterOf(lastEntry.pos());
                        return new DimensionTransition(
                                targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                                DimensionTransition.DO_NOTHING);
                    }
                }
            }
            return getTransitionFor(level, entity);
        }

        ServerLevel riftLevel = level.getServer().getLevel(RIFT_LEVEL);
        if (riftLevel == null) {
            return null;
        }

        DoorLocation generatedDoor = linkData.getGeneratedExitDoor();
        if (generatedDoor == null) {
            generateExitDoor(riftLevel);
            generatedDoor = new DoorLocation(RIFT_LEVEL, GENERATED_DOOR_POS);
            linkData.setGeneratedExitDoor(generatedDoor);
        }
        if (entity instanceof ServerPlayer player) {
            linkData.recordEntry(player.getUUID(), new DoorLocation(level.dimension(), doorPos.immutable()));
        }

        Vec3 targetPos = Vec3.atBottomCenterOf(generatedDoor.pos());
        return new DimensionTransition(
                riftLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    // Places the single generated door (both halves, closed, facing south) on the default
    // platform, ensuring that platform exists first.
    private static void generateExitDoor(ServerLevel riftLevel) {
        ensureSpawnPlatform(riftLevel);

        BlockState lowerState = ModRegistry.RIFT_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        riftLevel.setBlock(GENERATED_DOOR_POS, lowerState, 3);
        riftLevel.setBlock(GENERATED_DOOR_POS.above(), lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
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
