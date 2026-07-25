package com.ejinian.dimdescent.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

// Shared skeleton for every bed in this mod. All of them extend vanilla BedBlock purely to inherit
// the two-part shape, placement and collision - and all of them throw away the two things that make
// a vanilla bed a bed:
//
//   - rendering: RenderShape.MODEL + a null block entity, so they draw from ordinary JSON block
//     models with our own textures rather than through vanilla's hardcoded BedBlockEntity renderer
//     (which would force a vanilla bed texture and a DyeColor).
//   - sleeping: subclasses never call startSleepInBed, so no bed here can ever be slept in.
//
// What each subclass does with a right-click is the only thing that differs, and that difference IS
// the mechanic: the dark one takes you deeper, the pale one takes you back, and the corrupted one -
// your own overworld bed after you've refused the trip once - does nothing but tell you so.
public abstract class NexusBedBlock extends BedBlock {

    // Flags for writing a bed half from code. Both extras are load-bearing, not caution:
    //
    // UPDATE_KNOWN_SHAPE suppresses the neighbour-shape cascade. Without it, writing the first half
    // makes the second half's BedBlock.updateShape see a partner that is no longer "the same block",
    // which returns AIR - so it deletes itself AND drops a bed item, and that deletion cascades back
    // into the half just written, deleting that too. The result is a lone half-bed plus a stray bed
    // item on the ground. (Level.setBlock only skips the cascade when (flags & 16) != 0.)
    //
    // UPDATE_SUPPRESS_DROPS then guarantees nothing can pop out of a bed we are converting, whatever
    // the block being replaced was.
    public static final int BED_WRITE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    protected NexusBedBlock(BlockBehaviour.Properties properties) {
        // The colour only ever feeds vanilla's bed-entity renderer, which none of these use.
        super(DyeColor.GRAY, properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    // A bed is two blocks but one logical object, so any interaction has to be normalised to a single
    // half first or the same click behaves differently depending on which end was hit. Returns null if
    // the other half isn't actually part of the same bed (a half-destroyed bed mid-update).
    @Nullable
    protected BlockPos resolveHead(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) == BedPart.HEAD) {
            return pos;
        }
        BlockPos headPos = pos.relative(state.getValue(FACING));
        return level.getBlockState(headPos).is(this) ? headPos : null;
    }
}
