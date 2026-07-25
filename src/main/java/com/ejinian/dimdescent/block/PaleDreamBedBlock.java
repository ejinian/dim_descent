package com.ejinian.dimdescent.block;

import com.mojang.serialization.MapCodec;

import com.ejinian.dimdescent.dimension.NexusReturn;
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
import net.minecraft.world.phys.BlockHitResult;

// The pale Nexus of Eternal Slumber - the way BACK. Same name as its dark twin, deliberately: they
// are the same object, and telling them apart is the whole decision. The pale one is cleaner and
// whiter, the dark one tattered and gray, so "deeper or back?" is readable at a glance with no UI.
//
// It is also the room's ENTRANCE. You always arrive beside the pale bed, which means a hand-built
// room needs no separate spawn marker: the bed's own position and FACING are the arrival point and
// the arrival direction. (This is how Dimensional Doors does it too - their entrance is a real door
// baked into the room, and arrival facing is read off that door's blockstate.)
//
// Right-clicked one room deep or more, it walks the player's room chain back one step. Right-clicked
// in the first room - ground zero, the one you arrive in from the overworld - there is no room behind
// you, so it puts you out of the trip entirely (see NexusReturn for what that costs).
public class PaleDreamBedBlock extends NexusBedBlock {

    public static final MapCodec<BedBlock> CODEC = simpleCodec(PaleDreamBedBlock::new);

    public PaleDreamBedBlock(BlockBehaviour.Properties properties) {
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        // Only meaningful inside the Domain. In the waking world it is inert rather than explosive -
        // this is the bed that REFUSES the trip, so it would be perverse for it to punish a touch.
        if (!RiftTeleporter.isInRift(serverLevel)) {
            return InteractionResult.CONSUME;
        }

        RiftTeleporter.toPreviousRoom(serverLevel, serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
}
