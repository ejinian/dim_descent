package com.ejinian.dimdescent.block;

import static com.ejinian.dimdescent.block.DaemonlightLighting.LIT;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

// A wall-mounted Daemonlight. Same LIT / flint-and-steel behaviour as the standing one.
//
// WallTorchBlock puts its flame at y + 0.92, 0.27 out from the wall - tuned for vanilla's taller
// wall torch. The Daemonlight's ember bed lands lower and closer in once the -22.5 degree wall
// transform is applied, so these offsets are derived from where the bowl actually ends up:
// rotating the bed's centre (x=8, y=9.5) about [0, 3.5] gives roughly y 12.3/16 and 3.6/16 out.
public class DaemonlightWallBlock extends WallTorchBlock {

    private static final double FLAME_Y = 0.76;
    private static final double FLAME_OUT = 0.23;

    public DaemonlightWallBlock(SimpleParticleType flameParticle, Properties properties) {
        super(flameParticle, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
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
        // FACING points into the wall the torch hangs on, so the flame sits the other way.
        Direction outward = state.getValue(FACING).getOpposite();
        double x = pos.getX() + 0.5 + FLAME_OUT * outward.getStepX();
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + 0.5 + FLAME_OUT * outward.getStepZ();
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticle, x, y, z, 0.0, 0.0, 0.0);
    }
}
