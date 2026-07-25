package com.ejinian.dimdescent.block;

import com.mojang.serialization.MapCodec;

import com.ejinian.dimdescent.dimension.RiftTeleporter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// The dark Nexus of Eternal Slumber - the way DOWN.
//
// Lore: sleep is how you fall into the trip (the Waking Dream). Inside it, the only way deeper is to
// lie back down and try to rest again, so this bed is how you travel. It is the tattered, gray,
// zero-saturation one; its pale twin (PaleDreamBedBlock) is the way back.
//
// Outside the Null Domain the dream can't hold you and the bed detonates, exactly as a vanilla bed
// does in the Nether or the End - except we do it in EVERY dimension that isn't the Domain, because
// nowhere in the waking world is a safe place to lie down in this thing.
public class DreamBedBlock extends NexusBedBlock {

    // TEMPORARY: off while rooms are being hand-authored in the overworld, so an accidental
    // right-click can't blow up a build. Flip back to true to restore the wrong-dimension detonation.
    private static final boolean EXPLODE_OUTSIDE_DOMAIN = false;

    // Typed as MapCodec<BedBlock> because BedBlock.codec() is invariant on BedBlock; the factory still
    // builds DreamBedBlock instances, so decoding produces our block.
    public static final MapCodec<BedBlock> CODEC = simpleCodec(DreamBedBlock::new);

    public DreamBedBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
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
        if (EXPLODE_OUTSIDE_DOMAIN) {
            return detonate(state, level, pos);
        }
        return InteractionResult.CONSUME;
    }

    // Vanilla's wrong-dimension bed explosion, reused verbatim except that we reach it in every
    // non-Domain dimension rather than only where bedWorks() is false.
    private InteractionResult detonate(BlockState state, Level level, BlockPos pos) {
        BlockPos headPos = resolveHead(level, pos, state);
        if (headPos == null) {
            return InteractionResult.CONSUME;
        }
        BlockState headState = level.getBlockState(headPos);
        level.removeBlock(headPos, false);
        BlockPos footPos = headPos.relative(headState.getValue(FACING).getOpposite());
        if (level.getBlockState(footPos).is(this)) {
            level.removeBlock(footPos, false);
        }
        Vec3 center = headPos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(center), null, center,
                5.0F, true, Level.ExplosionInteraction.BLOCK);
        return InteractionResult.SUCCESS;
    }
}
