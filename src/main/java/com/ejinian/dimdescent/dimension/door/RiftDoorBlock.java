package com.ejinian.dimdescent.dimension.door;

import javax.annotation.Nullable;

import com.ejinian.dimdescent.dimension.RiftTeleporter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;

// A door that looks like a normal (if ornate) door, but when open, entities that physically touch
// the glowing rift-portal pane filling the doorway get pulled into the rift dimension (or back to
// the overworld, if already inside the rift).
public class RiftDoorBlock extends DoorBlock implements EntityBlock, Portal {

    // Vanilla iron doors can only be toggled by redstone; ours should open by hand like a wooden door.
    public static final BlockSetType RIFT_DOOR_SET = new BlockSetType(
            "dimdescent_rift_door",
            true,
            true,
            false,
            BlockSetType.PressurePlateSensitivity.EVERYTHING,
            SoundType.STONE,
            SoundEvents.IRON_DOOR_CLOSE,
            SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundEvents.STONE_BUTTON_CLICK_ON);

    public RiftDoorBlock(BlockBehaviour.Properties properties) {
        super(RIFT_DOOR_SET, properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Vanilla picks left vs. right hinge based on neighboring blocks/facing (same heuristic
        // every door uses) - this is a special door, not a real one, so skip that entirely and
        // pin it to the left every time, regardless of where or how it's placed.
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(HINGE, DoorHingeSide.LEFT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the lower half needs a block entity; its renderer draws boxes spanning both halves.
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new RiftDoorBlockEntity(pos, state) : null;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!isOpen(state) || !entity.canUsePortal(false)) {
            return;
        }

        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        AABB glowBounds = getOpenPortalBoundsLocal(level, state, pos)
                .move(lowerPos.getX(), lowerPos.getY(), lowerPos.getZ());

        if (glowBounds.intersects(entity.getBoundingBox())) {
            setOpen(entity, level, state, pos, false);
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        // Normalize to the door's lower half regardless of which half the entity actually
        // triggered the portal from - a door is one logical unit for pairing purposes, and using
        // whichever half happened to fire would split a single physical door into two different
        // link keys.
        BlockState triggerState = level.getBlockState(pos);
        BlockPos lowerPos = triggerState.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return RiftTeleporter.getTransitionFor(level, entity, lowerPos);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    // Local-space (relative to the door's LOWER-half position) bounds of the glow pane shown
    // when the door is open - the same box RiftDoorBlockEntityRenderer draws, so entityInside only
    // teleports an entity that's actually touching the visible glow, not just standing anywhere
    // in the door's block cell.
    private static AABB getOpenPortalBoundsLocal(BlockGetter level, BlockState state, BlockPos triggerPos) {
        BlockState closedState = state.setValue(OPEN, false);
        AABB slab = closedState.getShape(level, triggerPos).bounds();
        boolean fixedIsX = (slab.maxX - slab.minX) < (slab.maxZ - slab.minZ);
        double near = fixedIsX ? slab.minX : slab.minZ;
        double far = fixedIsX ? slab.maxX : slab.maxZ;
        double otherMin = 0.06, otherMax = 0.94, yMin = 0.06, yMax = 1.94;

        return fixedIsX
                ? new AABB(near, yMin, otherMin, far, yMax, otherMax)
                : new AABB(otherMin, yMin, near, otherMax, yMax, far);
    }
}
