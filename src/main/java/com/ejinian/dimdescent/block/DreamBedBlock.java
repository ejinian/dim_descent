package com.ejinian.dimdescent.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import com.ejinian.dimdescent.dimension.RiftTeleporter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// The Dream Bed - the Null Domain's way "onward", and the retirement of the Rift Door.
//
// Lore: sleep is how you first fall into the trip (the Waking Dream). Inside it, the only way deeper
// is to lie back down and try to rest again - so this cursed bed is the door. You never actually
// sleep in it; right-clicking it in the Null Domain simply pulls you into the next room, exactly as
// walking through the old door did. Doors only ever lead deeper, and so does this.
//
// Outside the Null Domain the dream won't hold you, so the bed detonates - the same explosion a
// vanilla bed makes when you try to use it in the Nether or the End, except we do it in EVERY
// dimension that isn't the Domain (the overworld included), because nowhere in the waking world is a
// safe place to lie down in this thing.
//
// It extends BedBlock purely to inherit the two-part bed shape, placement and collision; rendering is
// switched to a normal block model (no BedBlockEntity), and every sleep path is overridden away.
public class DreamBedBlock extends BedBlock {

    // Typed as MapCodec<BedBlock> because BedBlock.codec() is invariant on BedBlock; the factory still
    // builds DreamBedBlock instances, so decoding produces our block.
    public static final MapCodec<BedBlock> CODEC = simpleCodec(DreamBedBlock::new);

    public DreamBedBlock(BlockBehaviour.Properties properties) {
        // Colour only feeds vanilla's bed-entity renderer, which we don't use; GRAY just keeps map
        // colour sane.
        super(DyeColor.GRAY, properties);
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }

    // Rendered by the dream_bed blockstate/model, not the vanilla bed block-entity renderer.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        if (RiftTeleporter.isInRift(serverLevel)) {
            if (player instanceof ServerPlayer serverPlayer) {
                DimensionTransition transition = RiftTeleporter.toNextRoom(serverLevel, serverPlayer);
                if (transition != null) {
                    serverPlayer.changeDimension(transition);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return detonate(state, level, pos);
    }

    // Vanilla's wrong-dimension bed explosion, reused verbatim except that we reach it in every
    // non-Domain dimension rather than only where bedWorks() is false.
    private InteractionResult detonate(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(PART) != BedPart.HEAD) {
            pos = pos.relative(state.getValue(FACING));
            state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DreamBedBlock)) {
                return InteractionResult.CONSUME;
            }
        }
        level.removeBlock(pos, false);
        BlockPos otherHalf = pos.relative(state.getValue(FACING).getOpposite());
        if (level.getBlockState(otherHalf).getBlock() instanceof DreamBedBlock) {
            level.removeBlock(otherHalf, false);
        }
        Vec3 center = pos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(center), null, center,
                5.0F, true, Level.ExplosionInteraction.BLOCK);
        return InteractionResult.SUCCESS;
    }
}
