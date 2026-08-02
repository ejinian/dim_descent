package com.ejinian.dimdescent.dimension;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.block.NexusBedBlock;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

// The Null Domain's rooms: hand-authored .nbt structures, stamped onto a coarse grid on demand.
//
// This replaced an earlier set of five code-generated room shapes (pillar halls, barred cells and so
// on). Those were scaffolding to prove the traversal loop; the real rooms are built in-game and
// captured with structure blocks, which is both better-looking and how Dimensional Doors does it.
//
// Grid: SPACING mirrors DimDoors' 32-chunk cell (512 blocks), and rooms are laid on a square SPIRAL
// out from the origin (spiralCell - an O(1) closed form, verified bijective against a brute-force
// spiral walk) so they fan into both axes and stay within ~sqrt(N)/2 cells of origin, the tightest
// collision-free packing. Rooms never collide because indices come from one global monotonic counter,
// not because of the layout.
//
// Generation is LAZY - a room is stamped the first time a bed opens it (like DimDoors'
// LazyPocketGenerator) and then persists forever, since travel is keyed on beds (see BedLinkData).
public final class NullDomainRooms {

    // Rooms sit on the very bottom of the dimension. That is load-bearing rather than tidy: the
    // containment box around each room (RoomContainment) has no floor, and its walls run down to this
    // same level, so there is nothing to stand on outside a room and no way to tunnel underneath one.
    // Anchoring both to the build floor is what makes the cage inescapable.
    public static final int FLOOR_Y = 0;
    private static final int SPACING = 512;

    // Rooms are discovered from data/dimdescent/structure/rooms/*.nbt, so dropping a new .nbt in that
    // folder adds it to the pool with no code change. FALLBACK_POOL only matters if discovery finds
    // nothing (e.g. a resource-loading oddity), so the dimension degrades to "still works" rather
    // than "no rooms at all".
    private static final String ROOM_PREFIX = "rooms/";
    private static final List<ResourceLocation> FALLBACK_POOL = List.of(
            room("hallway"), room("hangul"), room("left"), room("t"), room("u"));

    // Where a player arrives: the spot, and the way they face.
    public record Arrival(Vec3 pos, float yRot) {
    }

    // A freshly stamped room: its grid index, and the head half of its pale Nexus - which is both the
    // arrival point and the room's identity for travelling back out.
    public record NewRoom(int index, @Nullable BlockPos entranceBedHead) {
    }

    private static ResourceLocation room(String name) {
        return ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, ROOM_PREFIX + name);
    }

    private NullDomainRooms() {
    }

    // Allocate the next grid cell, pick a room at random and stamp it there. Returns the index and
    // the pale Nexus position, which the caller records against the bed that opened it.
    public static NewRoom allocateAndStamp(ServerLevel rift) {
        return stampAt(rift, GridData.get(rift).takeNextIndex());
    }

    // Stamp the room for a specific index. Also used to heal a link made before rooms were authored
    // structures: the bed still points at its grid cell, so re-stamping puts a real room there rather
    // than leaving the player in whatever the old code-generated version left behind.
    public static NewRoom stampAt(ServerLevel rift, int index) {
        int[] cell = spiralCell(index);
        BlockPos origin = new BlockPos(cell[0] * SPACING, FLOOR_Y, cell[1] * SPACING);

        StructureTemplateManager manager = rift.getStructureManager();
        List<ResourceLocation> pool = pool(manager);
        RandomSource rng = RandomSource.create(seedFor(index));
        ResourceLocation choice = pool.get(rng.nextInt(pool.size()));

        StructureTemplate template = manager.get(choice).orElse(null);
        if (template == null) {
            DimDescent.LOGGER.error("Null Domain room template {} is missing; room {} will be empty", choice, index);
            return new NewRoom(index, null);
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        // The same flags used for writing beds by hand, and for the same reason: without
        // UPDATE_KNOWN_SHAPE the first half of a bed placed by the template makes the other half's
        // updateShape see a missing partner, and the pair deletes itself on the way in.
        template.placeInWorld(rift, origin, origin, settings, rng, NexusBedBlock.BED_WRITE_FLAGS);

        // Shrink-wrap the build in Nullstone and cage it in Forsaken Essence. Every block of the room
        // itself is breakable, so this is the only thing stopping a player digging out - see
        // RoomContainment for why the cage has no floor.
        Vec3i size = template.getSize();
        RoomContainment.encase(rift, new BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1,
                origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1));

        BlockPos paleHead = findEntranceBed(template, origin, settings);
        if (paleHead == null) {
            DimDescent.LOGGER.error(
                    "Null Domain room {} has no pale Nexus - players will arrive at its corner and cannot travel back",
                    choice);
        }
        return new NewRoom(index, paleHead);
    }

    // The pale Nexus baked into the room, read straight off the template rather than by scanning the
    // world. filterBlocks hands back world positions once offset, so this is exact and cheap.
    @Nullable
    private static BlockPos findEntranceBed(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        for (StructureTemplate.StructureBlockInfo info :
                template.filterBlocks(origin, settings, ModRegistry.PALE_DREAM_BED.get())) {
            if (info.state().getValue(BedBlock.PART) == BedPart.HEAD) {
                return info.pos();
            }
        }
        return null;
    }

    // Standing beside the pale Nexus, facing into the room.
    //
    // A bed's FACING runs foot -> head, and authors put the pale bed against the wall they want
    // players to arrive at, so "into the room" is simply the opposite of that. The arrival square is
    // one step past the foot; if the author left that blocked, fall back to any free square around
    // either half rather than dropping the player inside a wall.
    public static Arrival arrivalAt(ServerLevel level, BlockPos paleBedHead) {
        BlockState state = level.getBlockState(paleBedHead);
        if (!(state.getBlock() instanceof BedBlock)) {
            return new Arrival(Vec3.atBottomCenterOf(paleBedHead), 0.0F);
        }
        Direction facing = state.getValue(BedBlock.FACING);
        Direction intoRoom = facing.getOpposite();
        float yRot = intoRoom.toYRot();

        BlockPos foot = paleBedHead.relative(intoRoom);
        BlockPos preferred = foot.relative(intoRoom);
        if (isStandable(level, preferred)) {
            return new Arrival(Vec3.atBottomCenterOf(preferred), yRot);
        }
        for (BlockPos half : new BlockPos[]{foot, paleBedHead}) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = half.relative(dir);
                if (isStandable(level, candidate)) {
                    return new Arrival(Vec3.atBottomCenterOf(candidate), yRot);
                }
            }
        }
        return new Arrival(Vec3.atBottomCenterOf(foot), yRot);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }

    private static List<ResourceLocation> pool(StructureTemplateManager manager) {
        List<ResourceLocation> found = new ArrayList<>();
        manager.listTemplates()
                .filter(id -> id.getNamespace().equals(DimDescent.MODID) && id.getPath().startsWith(ROOM_PREFIX))
                .forEach(found::add);
        if (found.isEmpty()) {
            return FALLBACK_POOL;
        }
        // listTemplates has no defined order; sort so a given room index always picks the same room.
        found.sort(null);
        return found;
    }

    private static long seedFor(int index) {
        return index * 0x9E3779B97F4A7C15L ^ 0x5EEDCAFEL;
    }

    // Maps a room index to its grid cell on a square spiral out from (0,0). O(1), verified bijective
    // against a brute-force spiral walk.
    private static int[] spiralCell(int index) {
        if (index == 0) {
            return new int[]{0, 0};
        }
        int ring = (int) Math.floor((Math.sqrt(index) + 1) / 2);
        while ((2 * ring - 1) * (2 * ring - 1) > index) {
            ring--;
        }
        while ((2 * ring + 1) * (2 * ring + 1) - 1 < index) {
            ring++;
        }
        int offset = index - (2 * ring - 1) * (2 * ring - 1);
        if (offset <= 2 * ring - 1) {
            return new int[]{ring, offset - ring + 1};
        } else if (offset <= 4 * ring - 1) {
            return new int[]{ring - (offset - (2 * ring - 1)), ring};
        } else if (offset <= 6 * ring - 1) {
            return new int[]{-ring, ring - (offset - (4 * ring - 1))};
        } else {
            return new int[]{-ring + (offset - (6 * ring - 1)), -ring};
        }
    }

    // Persists the next free room index on the rift level, so the grid keeps growing across restarts
    // and no cell is ever handed out twice.
    public static final class GridData extends SavedData {

        private static final String STORAGE_KEY = "dimdescent_null_domain_grid";
        private static final String TAG_NEXT = "next_index";

        private static final SavedData.Factory<GridData> FACTORY =
                new SavedData.Factory<>(GridData::new, GridData::load);

        private int nextIndex;

        private static GridData get(ServerLevel rift) {
            return rift.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
        }

        private int takeNextIndex() {
            int index = nextIndex++;
            setDirty();
            return index;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt(TAG_NEXT, nextIndex);
            return tag;
        }

        private static GridData load(CompoundTag tag, HolderLookup.Provider registries) {
            GridData data = new GridData();
            data.nextIndex = tag.getInt(TAG_NEXT);
            return data;
        }
    }
}
