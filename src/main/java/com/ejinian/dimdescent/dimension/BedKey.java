package com.ejinian.dimdescent.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

// The identity of a bed, for link purposes: which dimension it's in and where it is.
//
// Identity is the PLACE, not the block object - break a bed and put a new one on the same spot and it
// leads where it always led. That's deliberate: the location is what's haunted, and it means a
// corrupted bed is recoverable (clear it, lay a fresh one, your route is back).
//
// Always normalised to the HEAD half, because a bed is two blocks but one thing; keying on whichever
// half happened to be clicked would split a single bed into two different destinations.
public record BedKey(ResourceKey<Level> dimension, BlockPos pos) {

    public static BedKey of(BlockGetter level, ResourceKey<Level> dimension, BlockPos anyHalf) {
        return new BedKey(dimension, headOf(level, anyHalf));
    }

    public static BlockPos headOf(BlockGetter level, BlockPos anyHalf) {
        BlockState state = level.getBlockState(anyHalf);
        if (!(state.getBlock() instanceof BedBlock)) {
            return anyHalf.immutable();
        }
        return state.getValue(BedBlock.PART) == BedPart.HEAD
                ? anyHalf.immutable()
                : anyHalf.relative(state.getValue(BedBlock.FACING)).immutable();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        return tag;
    }

    public static BedKey fromNbt(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        return new BedKey(dimension, NbtUtils.readBlockPos(tag, "pos").orElseThrow());
    }
}
