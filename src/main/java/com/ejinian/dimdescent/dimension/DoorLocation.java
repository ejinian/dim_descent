package com.ejinian.dimdescent.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// A door's identity for pairing purposes: which dimension it's in, and its (lower-half) position.
public record DoorLocation(ResourceKey<Level> dimension, BlockPos pos) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        return tag;
    }

    public static DoorLocation fromNbt(CompoundTag tag) {
        ResourceLocation dimensionLocation = ResourceLocation.parse(tag.getString("dimension"));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElseThrow();
        return new DoorLocation(dimension, pos);
    }
}
