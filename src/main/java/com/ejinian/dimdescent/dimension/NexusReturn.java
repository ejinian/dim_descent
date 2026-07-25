package com.ejinian.dimdescent.dimension;

import javax.annotation.Nullable;

import com.ejinian.dimdescent.block.NexusBedBlock;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

// Refusing the trip: what happens when a player uses the pale Nexus in the FIRST room, where there is
// no room behind them to step back into.
//
// They get out - the mod never traps anyone - but the trip does not simply end tidily, because the
// point of the whole mod is that the drug is not something you can decide your way out of. Backing
// out costs four things:
//
//   1. You do not wake where you lay down. You come to a few blocks away from your bed.
//   2. That bed is corrupted, permanently. It wears the pale bed's tattering now and can never be
//      slept in again. Something came back with you.
//   3. The comedown: nausea, then a long dry mouth and weakness.
//   4. Attunement is gone. You left the dream early, so the dream is over - and with it the only
//      thing that would have let you back in without brewing again.
//
// The intended reading is that going deeper is rewarded and going back is merely survivable. Later
// gear is planned to soften the comedown, which is why the effects live behind one method here.
public final class NexusReturn {

    // Comedown, in ticks.
    private static final int NAUSEA_TICKS = 200;      // 10s
    private static final int DRY_MOUTH_TICKS = 1200;  // 60s
    private static final int WEAKNESS_TICKS = 1200;   // 60s

    // How far from the bed the player comes to. Far enough to read as "you moved in the night".
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 6;

    private NexusReturn() {
    }

    // Pull the player out of the Domain entirely. Safe to call from a block interaction. originBed is
    // the waking-world bed that opened this room - null only for an unlinked debug room, or if the
    // link somehow predates the bed being destroyed.
    public static void refuseTrip(ServerLevel riftLevel, ServerPlayer player, @Nullable BedKey originBed) {
        ServerLevel targetLevel = null;
        Vec3 wakingSpot = null;
        if (originBed != null) {
            targetLevel = riftLevel.getServer().getLevel(originBed.dimension());
            if (targetLevel != null) {
                corruptBed(targetLevel, originBed.pos());
                wakingSpot = findWakingSpot(targetLevel, originBed.pos());
            }
        }

        // No usable origin bed: fall back to wherever they would respawn, which is what
        // expiry-ejection already does. They still pay the comedown - backing out always costs.
        if (targetLevel == null || wakingSpot == null) {
            player.changeDimension(player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING));
            applyComedown(player);
            return;
        }

        player.changeDimension(new DimensionTransition(
                targetLevel, wakingSpot, Vec3.ZERO, player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
        applyComedown(player);
    }

    // Attunement ends and the body notices. Kept as one method so planned gear can bypass or shorten
    // it in one place rather than being threaded through the travel code.
    private static void applyComedown(ServerPlayer player) {
        player.removeEffect(ModRegistry.ATTUNEMENT_EFFECT);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(ModRegistry.DRY_MOUTH_EFFECT, DRY_MOUTH_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, false, true, true));
    }

    // Turn the bed the player lay down in into its corrupted form, preserving orientation so it does
    // not visibly jump. Both halves are written with flag 2 (no neighbour updates) or vanilla's bed
    // updateShape would delete the half whose partner hasn't been converted yet.
    private static void corruptBed(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return; // they broke it, or something else stands there now
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BedPart part = state.getValue(BedBlock.PART);
        BlockPos footPos = part == BedPart.FOOT ? pos : pos.relative(facing.getOpposite());
        BlockPos headPos = footPos.relative(facing);
        if (!(level.getBlockState(headPos).getBlock() instanceof BedBlock)) {
            return; // half a bed; leave it alone rather than leaving a broken pair behind
        }
        BlockState corrupted = ModRegistry.CORRUPTED_BED.get().defaultBlockState()
                .setValue(BedBlock.FACING, facing);
        level.setBlock(footPos, corrupted.setValue(BedBlock.PART, BedPart.FOOT), NexusBedBlock.BED_WRITE_FLAGS);
        level.setBlock(headPos, corrupted.setValue(BedBlock.PART, BedPart.HEAD), NexusBedBlock.BED_WRITE_FLAGS);
    }

    // A standable spot a few blocks from the bed. Walks outward in rings and takes the first place
    // with two blocks of air on solid ground, so the player never wakes inside a wall or on a roof.
    @Nullable
    private static Vec3 findWakingSpot(ServerLevel level, BlockPos bed) {
        for (int radius = MIN_RADIUS; radius <= MAX_RADIUS; radius++) {
            for (int step = 0; step < 8; step++) {
                double angle = step * Math.PI / 4.0;
                int dx = (int) Math.round(Math.cos(angle) * radius);
                int dz = (int) Math.round(Math.sin(angle) * radius);
                BlockPos candidate = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bed.offset(dx, 0, dz));
                if (isStandable(level, candidate)) {
                    return Vec3.atBottomCenterOf(candidate);
                }
            }
        }
        return null;
    }

    private static boolean isStandable(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isSolidRender(level, pos.below());
    }
}
