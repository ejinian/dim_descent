package com.ejinian.dimdescent.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

// Shared "light this with flint and steel" behaviour for both the standing and wall Daemonlight.
//
// The Daemonlight is crafted unlit and lit by hand, so lighting is a block interaction rather than
// anything vanilla's FlintAndSteelItem does for us: the item only knows how to light candles,
// campfires and TNT, and otherwise just places fire. Handling it in the block's useItemOn also means
// striking the torch never spawns a stray fire block on it.
public final class DaemonlightLighting {

    // Reused by both blocks; both add this exact property to their state definition.
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static ItemInteractionResult strike(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand) {
        if (state.getValue(LIT) || !stack.is(Items.FLINT_AND_STEEL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(LIT, Boolean.TRUE), 11);
            if (player != null) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private DaemonlightLighting() {
    }
}
