package com.ejinian.dimdescent.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

// Datura, exactly a vanilla FlowerBlock except in what ground it will grow on.
//
// A plain flower may only sit on BlockTags.DIRT (grass, dirt, podzol...) or farmland - so of the
// arid biomes Datura is meant to inhabit, only savanna (grass) would take it, and world-gen would
// silently place nothing in desert or badlands (SimpleBlockFeature checks canSurvive before
// placing). Broadening the valid ground to sand and terracotta fixes that, and it's true to life:
// real datura (jimsonweed) is a weed of dry, sandy, disturbed waste ground, not meadow.
public class DaturaBlock extends FlowerBlock {

    public DaturaBlock(SuspiciousStewEffects suspiciousStewEffects, Properties properties) {
        super(suspiciousStewEffects, properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        // Dirt/farmland (the vanilla set, for savanna and anywhere a player plants it) plus the
        // arid surfaces: SAND covers desert sand and badlands red sand, TERRACOTTA covers the
        // badlands terracotta bands.
        return super.mayPlaceOn(state, level, pos)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.getBlock() instanceof FarmBlock;
    }
}
