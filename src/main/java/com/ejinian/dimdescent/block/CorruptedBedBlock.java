package com.ejinian.dimdescent.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// What your own bed becomes once you have refused the trip and come back out through the pale Nexus.
// It wears the pale bed's tattering, because something followed you home.
//
// Mechanically it is the mildest of the three: it does not travel and it does not explode, it simply
// can no longer be slept in - a permanent, silent mark on the world that you went somewhere and came
// back early. It stays a bed for respawn purposes (it is in the #minecraft:beds tag), so refusing the
// trip costs you the use of the bed, not your spawn point.
//
// Breakable but dropless on purpose: the player can clear it away and place a fresh bed, so the
// punishment is a scar rather than a dead end - but they can never collect or re-place this thing.
public class CorruptedBedBlock extends NexusBedBlock {

    public static final MapCodec<BedBlock> CODEC = simpleCodec(CorruptedBedBlock::new);

    public CorruptedBedBlock(BlockBehaviour.Properties properties) {
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
        player.displayClientMessage(
                Component.translatable("dimdescent.bed.not_comfortable")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                true);
        return InteractionResult.SUCCESS;
    }
}
