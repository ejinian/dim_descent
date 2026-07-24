package com.ejinian.dimdescent.block;

import static com.ejinian.dimdescent.block.DaemonlightLighting.LIT;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

// A floor-standing Daemonlight. A vanilla torch, except: it carries a LIT state (placed unlit, lit
// by flint and steel), and its flame sits lower to match the shorter model.
//
// TorchBlock hardcodes the particle origin at y + 0.7, tuned for vanilla's 10-tall torch. The
// Daemonlight's ember bed tops out at 10/16, so the inherited height left the flame floating above
// the bowl; FLAME_Y drops it onto the embers.
public class DaemonlightBlock extends TorchBlock {

    // Just under the top of the ember bed (10/16 = 0.625), so the flame sits IN the bowl.
    public static final double FLAME_Y = 0.60;

    public DaemonlightBlock(SimpleParticleType flameParticle, Properties properties) {
        super(flameParticle, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return DaemonlightLighting.strike(stack, state, level, pos, player, hand);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticle, x, y, z, 0.0, 0.0, 0.0);
    }
}
