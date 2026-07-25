package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

// Shared logic for moving an entity into/out of the rift dimension, used by both /rift enter|leave,
// the sleep crossing, and the Rift Door block.
//
// The model is Dimensional Doors' pocket dungeon (see NullDomainRooms): every crossing INTO the Null
// Domain - and every Dream Bed used once inside - opens a fresh, randomly-chosen room somewhere far
// off on the room grid. Traversal only ever leads deeper; there is no way back out through a bed.
// Leaving happens two ways only: the manual /rift leave, and Attunement expiry (RiftEjectionEvents
// ejects you to your respawn point the tick the effect ends). A voluntary exit is a future item.
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
        return toNextRoom(level, entity);
    }

    // Allocate and stamp a fresh Null Domain room and return the transition into it. Used both to
    // ENTER from outside (sleep / /rift enter, which arrive here via getTransitionFor above) and to go
    // DEEPER from inside (the dark Nexus). Either way it's just "the next room" - the difference is
    // only whether the player's room chain restarts or grows.
    public static DimensionTransition toNextRoom(ServerLevel level, Entity entity) {
        ServerLevel rift = level.getServer().getLevel(RIFT_LEVEL);
        if (rift == null) {
            return null;
        }
        boolean goingDeeper = isInRift(level);
        int index = NullDomainRooms.allocateAndStamp(rift);
        if (entity instanceof ServerPlayer player) {
            RoomChainData chains = RoomChainData.get(rift);
            if (goingDeeper) {
                chains.pushRoom(player.getUUID(), index);
            } else {
                chains.beginChain(player.getUUID(), index);
            }
        }
        return transition(rift, NullDomainRooms.landingFor(index), entity);
    }

    // The pale Nexus: step back to the room you came from. In the FIRST room there is nothing behind
    // you, so this refuses the trip outright and puts you back in the waking world (NexusReturn).
    public static void toPreviousRoom(ServerLevel riftLevel, ServerPlayer player) {
        int previousIndex = RoomChainData.get(riftLevel).popRoom(player.getUUID());
        if (previousIndex < 0) {
            NexusReturn.refuseTrip(riftLevel, player);
            return;
        }
        Vec3 landing = NullDomainRooms.landingFor(previousIndex);
        player.changeDimension(new DimensionTransition(
                riftLevel, landing, Vec3.ZERO, player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
    }

    private static DimensionTransition transition(ServerLevel target, Vec3 pos, Entity entity) {
        return new DimensionTransition(
                target, pos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }
}
