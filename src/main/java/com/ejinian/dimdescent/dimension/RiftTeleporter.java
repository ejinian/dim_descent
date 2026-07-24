package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

// Shared logic for moving an entity into/out of the rift dimension, used by both /rift enter|leave,
// the sleep crossing, and the Rift Door block.
//
// The model is Dimensional Doors' pocket dungeon (see NullDomainRooms): every crossing INTO the Null
// Domain - and every door walked through once inside - opens a fresh, randomly-chosen room somewhere
// far off on the room grid. Doors only ever lead deeper; there is no door-based way back out. Leaving
// happens two ways only: the manual /rift leave, and Attunement expiry (RiftEjectionEvents ejects you
// to your respawn point the tick the effect ends). A voluntary exit door is a separate, future item.
public final class RiftTeleporter {

    public static final ResourceKey<Level> RIFT_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "rift"));

    private RiftTeleporter() {
    }

    public static boolean isInRift(ServerLevel level) {
        return level.dimension() == RIFT_LEVEL;
    }

    // Doorless transition, used by /rift enter|leave and the sleep crossing:
    //   - inside the rift  -> leave to the overworld spawn
    //   - anywhere else    -> enter a fresh room in the Null Domain
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity) {
        if (isInRift(level)) {
            ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return null;
            }
            Vec3 spawn = Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
            return transition(overworld, spawn, entity);
        }
        return enterFreshRoom(level, entity);
    }

    // Door-aware transition. A door always leads deeper, whether you're stepping in from the overworld
    // or from an earlier room - either way it opens the next room. The door position is no longer used
    // for pairing (return trips are gone), but the signature is kept for the Portal contract.
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity, BlockPos doorPos) {
        return enterFreshRoom(level, entity);
    }

    private static DimensionTransition enterFreshRoom(ServerLevel level, Entity entity) {
        ServerLevel rift = level.getServer().getLevel(RIFT_LEVEL);
        if (rift == null) {
            return null;
        }
        Vec3 landing = NullDomainRooms.newRoom(rift);
        return transition(rift, landing, entity);
    }

    private static DimensionTransition transition(ServerLevel target, Vec3 pos, Entity entity) {
        return new DimensionTransition(
                target, pos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }
}
