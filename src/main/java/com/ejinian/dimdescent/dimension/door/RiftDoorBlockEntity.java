package com.ejinian.dimdescent.dimension.door;

import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Exists purely to give RiftDoorBlock a hook for RiftDoorBlockEntityRenderer.
// All actual state (open/closed, facing) is read straight from the block's own BlockState.
public class RiftDoorBlockEntity extends BlockEntity {

    public RiftDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RIFT_DOOR_BLOCK_ENTITY.get(), pos, state);
    }
}
