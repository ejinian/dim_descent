package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

// Shared logic for moving an entity into, through and out of the Null Domain. Used by /rift
// enter|leave, the sleep crossing, and both Nexus beds.
//
// Travel is keyed on BEDS, not players (see BedLinkData): a bed opens the same room forever, for
// everyone, so the Domain is one shared graph that a whole server can explore together. The dark
// Nexus goes forward along that graph, the pale Nexus goes back along it, and the pale Nexus in a
// room whose origin lies outside the Domain is the way out.
//
// Leaving happens three ways: the pale Nexus at an outermost room (NexusReturn - the costly, chosen
// exit), Attunement expiry (RiftEjectionEvents - the timer running out), and the /rift leave debug
// command.
public final class RiftTeleporter {

    public static final ResourceKey<Level> RIFT_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "rift"));

    private RiftTeleporter() {
    }

    public static boolean isInRift(ServerLevel level) {
        return level.dimension() == RIFT_LEVEL;
    }

    // Doorless transition, used by /rift enter|leave only: inside -> overworld spawn, outside -> a
    // fresh room with no bed behind it. Real entry goes through toRoomFor with the bed that was used.
    public static DimensionTransition getTransitionFor(ServerLevel level, Entity entity) {
        if (isInRift(level)) {
            ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return null;
            }
            return transition(overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), entity);
        }
        ServerLevel rift = level.getServer().getLevel(RIFT_LEVEL);
        if (rift == null) {
            return null;
        }
        // Unlinked: nothing opened it, so its pale Nexus has no recorded way back and falls through to
        // the respawn-point exit. Debug convenience, not a real entrance.
        NullDomainRooms.NewRoom room = NullDomainRooms.allocateAndStamp(rift);
        return arrivalTransition(rift, room.entranceBedHead(), entity);
    }

    // The real traversal: "the room this bed opens". The FIRST time a given bed is used the room is
    // minted and the link recorded permanently; every use after that - by anyone, at any time - lands
    // in that same room. This is what makes beds one-to-one and the Domain shared.
    public static DimensionTransition toRoomFor(ServerLevel level, Entity entity, BedKey sourceBed) {
        ServerLevel rift = level.getServer().getLevel(RIFT_LEVEL);
        if (rift == null) {
            return null;
        }
        BedLinkData links = BedLinkData.get(rift);
        Integer index = links.roomFor(sourceBed);
        if (index == null) {
            NullDomainRooms.NewRoom created = NullDomainRooms.allocateAndStamp(rift);
            links.link(sourceBed, created.index(), created.entranceBedHead());
            index = created.index();
        } else if (links.entranceFor(index) == null) {
            // Linked before rooms were authored structures (or the stamp failed): put a real room in
            // that cell now, keeping the bed pointing where it always pointed.
            NullDomainRooms.NewRoom restamped = NullDomainRooms.stampAt(rift, index);
            links.link(sourceBed, index, restamped.entranceBedHead());
        }
        return arrivalTransition(rift, links.entranceFor(index), entity);
    }

    // Land beside the room's pale Nexus, facing into the room. A room whose entrance bed is unknown
    // (an author forgot one) still gets a usable landing so nobody is stranded in the void.
    private static DimensionTransition arrivalTransition(ServerLevel rift, BlockPos paleBedHead, Entity entity) {
        if (paleBedHead == null) {
            return transition(rift, new Vec3(0.5, NullDomainRooms.FLOOR_Y + 1, 0.5), entity);
        }
        NullDomainRooms.Arrival arrival = NullDomainRooms.arrivalAt(rift, paleBedHead);
        return new DimensionTransition(
                rift, arrival.pos(), entity.getDeltaMovement(), arrival.yRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }

    // The pale Nexus: go back to whatever opened this room. A bed inside the Domain means step back a
    // room; a bed outside it means the trip is over (and refusing it costs - see NexusReturn).
    public static void toPreviousRoom(ServerLevel riftLevel, ServerPlayer player, BlockPos paleBedPos) {
        BedKey paleBed = BedKey.of(riftLevel, RIFT_LEVEL, paleBedPos);
        BedKey target = BedLinkData.get(riftLevel).returnTargetFor(paleBed);

        if (target == null || target.dimension() != RIFT_LEVEL) {
            // Outermost room (or an unlinked debug room): this is the way out.
            NexusReturn.refuseTrip(riftLevel, player, target);
            return;
        }
        Vec3 landing = standingSpotBeside(riftLevel, target.pos());
        player.changeDimension(new DimensionTransition(
                riftLevel, landing, Vec3.ZERO, player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
    }

    // Somewhere to stand next to a bed you've just arrived back at, rather than inside it. Checks the
    // squares around both halves and takes the first with room to stand.
    private static Vec3 standingSpotBeside(ServerLevel level, BlockPos bedHead) {
        BlockState headState = level.getBlockState(bedHead);
        BlockPos bedFoot = headState.getBlock() instanceof BedBlock
                ? bedHead.relative(headState.getValue(BedBlock.FACING).getOpposite())
                : bedHead;
        for (BlockPos half : new BlockPos[]{bedFoot, bedHead}) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = half.relative(dir);
                if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                    return Vec3.atBottomCenterOf(candidate);
                }
            }
        }
        return Vec3.atBottomCenterOf(bedHead);
    }

    private static DimensionTransition transition(ServerLevel target, Vec3 pos, Entity entity) {
        return new DimensionTransition(
                target, pos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING);
    }
}
