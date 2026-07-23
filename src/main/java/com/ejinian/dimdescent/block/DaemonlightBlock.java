package com.ejinian.dimdescent.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

// A floor-standing Daemonlight. Identical to a vanilla torch apart from where its flame sits.
//
// TorchBlock hardcodes the particle origin at y + 0.7, which is tuned for vanilla's 10-tall torch.
// The Daemonlight is deliberately shorter - its ember bed tops out at 10/16 - so the inherited
// height left the flame floating above the bowl. This drops it onto the embers.
public class DaemonlightBlock extends TorchBlock {

    // Just under the top of the ember bed (10/16 = 0.625), so the flame sits IN the bowl.
    public static final double FLAME_Y = 0.60;

    public DaemonlightBlock(SimpleParticleType flameParticle, Properties properties) {
        super(flameParticle, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticle, x, y, z, 0.0, 0.0, 0.0);
    }
}
