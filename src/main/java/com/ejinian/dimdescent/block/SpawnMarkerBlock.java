package com.ejinian.dimdescent.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// Authoring-only marker for the Null Domain room pool. Place ONE in a hand-built room on the tile
// where the arriving player should stand, facing the way they should look (the arrow on top points
// the way you're facing when you place it). The room loader will read its position + facing as the
// spawn and then delete it, so it never appears in play. Not obtainable in survival; creative tab
// only. This replaces the structure-block "Data mode" marker, which vanilla does not expose in the
// GUI (the mode button filters DATA out).
public class SpawnMarkerBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SpawnMarkerBlock> CODEC = simpleCodec(SpawnMarkerBlock::new);

    public SpawnMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the way the placer is looking, so the top arrow points where the arriving player faces.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
}
