package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.block.DaemonlightLighting;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

// Phase 6: the Null Domain works like Dimensional Doors' pocket dungeon. There is no visible descent
// and no depth counter any more - instead every door you walk through opens a fresh, randomly-chosen
// room somewhere far off in the same dimension, and you're teleported into it.
//
// How DimDoors does it (and what we copied):
//   - Rooms live on a coarse GRID. DimDoors uses a 32-chunk (512-block) cell; so do we (SPACING).
//     Each new room is handed the next free integer index, which maps to the next free grid cell,
//     so rooms never overlap and land ~512 blocks apart - the "long teleport" you see between rooms.
//   - Rooms are generated LAZILY, the moment a door is entered, rather than all up front. newRoom()
//     is called straight from the door's getPortalDestination.
//   - Rooms persist once stamped (they're just left in the world), which leaves the door open to
//     two-way / backtracking links later without changing any of this.
//
// What we deliberately dropped from DimDoors for this pass: their depth axis (VirtualLocation.depth,
// which biases room selection deeper in) and their authored .schem room pool. Selection here is a
// flat uniform pick over five code-generated room types - a proof of concept to hang authored rooms,
// depth-weighting, enemies and richer loot on later.
//
// The room index is stored in a SavedData on the rift level so the counter survives a restart and a
// re-entering player is never handed a cell that's already occupied.
public final class NullDomainRooms {

    // Floor plane shared by every room. Rooms are spread across the grid, never stacked, so this is a
    // constant - build height (0..256 in the rift dimension type) is never a concern.
    public static final int FLOOR_Y = 100;

    // Grid geometry. SPACING mirrors DimDoors' 32-chunk cell (512 blocks). Rooms are placed on a
    // square SPIRAL out from the origin (see spiralCell): index 0 at the centre, each next index the
    // next cell of an outward-growing square. This fans rooms into BOTH axes and keeps every room as
    // close to the origin as possible - N rooms never stray past ~sqrt(N)/2 cells either way, which is
    // the tightest any collision-free packing can be - so the world stays compact instead of marching
    // off down one axis. Collision-safety does NOT come from the layout: newRoom hands out a single
    // global monotonic index, so no two rooms (two players at once, or the same player across the
    // whole history of the world) can ever be handed the same cell.
    private static final int SPACING = 512;

    // The one loot table rooms with a chest point at, reusing the altar's themed pool for now.
    private static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "chests/altar"));

    // The five proof-of-concept room shapes, distinguished by footprint (w x d), ceiling height (h),
    // and the decoration switch below. Interior spans relative coords x in 0..w-1, z in 0..d-1.
    private enum RoomType {
        PILLAR_HALL(13, 13, 6),   // square hall, a grid of full-height pillars
        LONG_GALLERY(25, 7, 6),   // wide, shallow gallery with benches and glowing plinths
        GRAND_CHAMBER(17, 17, 11),// tall chamber built around a raised, caged altar-heart dais (+chest)
        CRAMPED_CELLS(15, 15, 4),  // low, partitioned warren of barred cells
        HALL_OF_BARS(15, 15, 7);   // a weave of dark-iron-bar screens around a caged relic

        final int w;
        final int d;
        final int h;

        RoomType(int w, int d, int h) {
            this.w = w;
            this.d = d;
            this.h = h;
        }
    }

    // Allocate the next room and stamp it. Called on every crossing (door entry, sleep, /rift enter),
    // so each is a brand-new room; returns where the arriving entity should stand.
    public static Vec3 newRoom(ServerLevel rift) {
        int index = GridData.get(rift).takeNextIndex();
        return generateRoom(rift, index);
    }

    // Maps a room index to its grid cell on a square spiral out from (0,0). O(1), verified bijective
    // against a brute-force spiral walk. See the SPACING comment for why a spiral.
    private static int[] spiralCell(int index) {
        if (index == 0) {
            return new int[]{0, 0};
        }
        int ring = (int) Math.floor((Math.sqrt(index) + 1) / 2);
        // Correct any floating-point drift at the exact ring boundaries (perfect squares).
        while ((2 * ring - 1) * (2 * ring - 1) > index) {
            ring--;
        }
        while ((2 * ring + 1) * (2 * ring + 1) - 1 < index) {
            ring++;
        }
        int offset = index - (2 * ring - 1) * (2 * ring - 1);
        if (offset <= 2 * ring - 1) {                       // east edge, heading +Z
            return new int[]{ring, offset - ring + 1};
        } else if (offset <= 4 * ring - 1) {                // north edge, heading -X
            return new int[]{ring - (offset - (2 * ring - 1)), ring};
        } else if (offset <= 6 * ring - 1) {                // west edge, heading -Z
            return new int[]{-ring, ring - (offset - (4 * ring - 1))};
        } else {                                             // south edge, heading +X
            return new int[]{-ring + (offset - (6 * ring - 1)), -ring};
        }
    }

    // Deterministic per index, so re-stamping the same cell rebuilds the identical room. The type is
    // drawn first from the same seed, so a given index always has the same shape too.
    private static Vec3 generateRoom(ServerLevel level, int index) {
        RandomSource rng = RandomSource.create(index * 0x9E3779B97F4A7C15L ^ 0x5EEDCAFEL);
        RoomType type = RoomType.values()[rng.nextInt(RoomType.values().length)];
        int[] cell = spiralCell(index);
        int ox = cell[0] * SPACING;
        int oz = cell[1] * SPACING;

        stampShell(level, ox, oz, type);
        stampFloor(level, ox, oz, type, rng);
        decorate(level, ox, oz, type, rng);
        placeExitDoor(level, ox, oz, type);

        // Arrive at the south-centre, one block in from the wall, facing the exit door on the far side.
        return new Vec3(ox + type.w / 2 + 0.5, FLOOR_Y + 1, oz + 1.5);
    }

    // A pitch-black void box. The interior faces of the walls and the ceiling are lined with Nullstone
    // (dead black), backed by an unbreakable Forsaken Fiber shell one block further out and up, so the
    // room reads as pure black yet still can't be mined out of in survival. There is deliberately
    // NOTHING beneath the floor: the altar-brick walkway (stampFloor) is the only thing over the void,
    // so breaking through it drops you clean out of the world (the flat ground the dimension used to
    // generate far below has been removed to match - see the rift dimension json).
    private static void stampShell(ServerLevel level, int ox, int oz, RoomType t) {
        BlockState fiber = fiber();
        BlockState nullstone = nullstone();
        BlockState air = Blocks.AIR.defaultBlockState();
        int wallTop = FLOOR_Y + t.h;   // highest interior air row
        int innerCeil = wallTop + 1;   // nullstone lid
        int outerCeil = wallTop + 2;   // fiber lid

        // Hollow the interior (the floor row itself is left for stampFloor to fill with brick).
        for (int x = 0; x < t.w; x++) {
            for (int z = 0; z < t.d; z++) {
                for (int y = FLOOR_Y + 1; y <= wallTop; y++) {
                    level.setBlock(new BlockPos(ox + x, y, oz + z), air, 2);
                }
            }
        }

        // Inner shell: nullstone walls (the -1 / w ring) from the floor up, plus a full nullstone lid.
        for (int x = -1; x <= t.w; x++) {
            for (int z = -1; z <= t.d; z++) {
                boolean wall = x == -1 || x == t.w || z == -1 || z == t.d;
                for (int y = FLOOR_Y; y <= innerCeil; y++) {
                    if (wall || y == innerCeil) {
                        level.setBlock(new BlockPos(ox + x, y, oz + z), nullstone, 2);
                    }
                }
            }
        }

        // Outer shell: unbreakable fiber, one block further out and up, sealing the box behind the
        // nullstone so the walls and ceiling can't be breached into the void.
        for (int x = -2; x <= t.w + 1; x++) {
            for (int z = -2; z <= t.d + 1; z++) {
                boolean wall = x == -2 || x == t.w + 1 || z == -2 || z == t.d + 1;
                for (int y = FLOOR_Y; y <= outerCeil; y++) {
                    if (wall || y == outerCeil) {
                        level.setBlock(new BlockPos(ox + x, y, oz + z), fiber, 2);
                    }
                }
            }
        }
    }

    // Altar-brick surface with a scatter of cracked bricks for wear.
    private static void stampFloor(ServerLevel level, int ox, int oz, RoomType t, RandomSource rng) {
        BlockState bricks = bricks();
        BlockState cracked = cracked();
        for (int x = 0; x < t.w; x++) {
            for (int z = 0; z < t.d; z++) {
                BlockState floor = rng.nextInt(6) == 0 ? cracked : bricks;
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z), floor, 2);
            }
        }
    }

    private static void decorate(ServerLevel level, int ox, int oz, RoomType t, RandomSource rng) {
        switch (t) {
            case PILLAR_HALL -> decoratePillarHall(level, ox, oz, t);
            case LONG_GALLERY -> decorateLongGallery(level, ox, oz, t);
            case GRAND_CHAMBER -> decorateGrandChamber(level, ox, oz, t);
            case CRAMPED_CELLS -> decorateCrampedCells(level, ox, oz, t, rng);
            case HALL_OF_BARS -> decorateHallOfBars(level, ox, oz, t, rng);
        }
    }

    // A grid of full-height brick pillars either side of the central corridor, some coursed with
    // cracked brick. The centre column (cx) is left clear so the walk to the door is never blocked.
    private static void decoratePillarHall(ServerLevel level, int ox, int oz, RoomType t) {
        lampCorners(level, ox, oz, t);
        int[] xs = {3, t.w - 4};
        int[] zs = {3, 6, 9};
        for (int px : xs) {
            for (int pz : zs) {
                for (int y = 1; y <= t.h; y++) {
                    BlockState s = (y == 2 || y == 5) ? cracked() : bricks();
                    level.setBlock(new BlockPos(ox + px, FLOOR_Y + y, oz + pz), s, 2);
                }
            }
        }
    }

    // Wide, shallow room: a run of slab "benches" down the middle band (broken for the corridor),
    // wall lamps at intervals, and a glowing altar-heart plinth in each far corner.
    private static void decorateLongGallery(ServerLevel level, int ox, int oz, RoomType t) {
        int cx = t.w / 2;
        BlockState slab = ModRegistry.ALTAR_STONE_BRICK_SLAB.get().defaultBlockState();
        for (int x = 2; x < t.w - 2; x++) {
            if (Math.abs(x - cx) <= 1) {
                continue; // keep the corridor open
            }
            level.setBlock(new BlockPos(ox + x, FLOOR_Y + 1, oz + 3), slab, 2);
        }
        for (int x = 2; x < t.w - 2; x += 5) {
            level.setBlock(new BlockPos(ox + x, FLOOR_Y + 1, oz + t.d - 2), lamp(), 2);
            if (x != cx) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y + 1, oz + 1), lamp(), 2);
            }
        }
        for (int px : new int[]{2, t.w - 3}) {
            level.setBlock(new BlockPos(ox + px, FLOOR_Y + 1, oz + 3), altarStone(), 2);
            level.setBlock(new BlockPos(ox + px, FLOOR_Y + 2, oz + 3), heart(), 2);
        }
    }

    // Tall chamber around a raised 5x5 dais: brick platform, a carved plinth topped with a glowing
    // altar heart, dark-iron-bar posts caging the corners, a stair step up the front, and a
    // guaranteed loot chest to the side.
    private static void decorateGrandChamber(ServerLevel level, int ox, int oz, RoomType t) {
        lampCorners(level, ox, oz, t);
        int cx = t.w / 2;
        int cz = t.d / 2;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(ox + cx + dx, FLOOR_Y + 1, oz + cz + dz), bricks(), 2);
            }
        }
        level.setBlock(new BlockPos(ox + cx, FLOOR_Y + 2, oz + cz), carved(), 2);
        level.setBlock(new BlockPos(ox + cx, FLOOR_Y + 3, oz + cz), heart(), 2);

        // Caged corners: 2-tall bar posts at the four platform corners.
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                bars(level, new BlockPos(ox + cx + dx, FLOOR_Y + 2, oz + cz + dz), true, true);
                bars(level, new BlockPos(ox + cx + dx, FLOOR_Y + 3, oz + cz + dz), true, true);
            }
        }
        // A step up the south (approach) face.
        BlockState stair = ModRegistry.ALTAR_STONE_BRICK_STAIRS.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(new BlockPos(ox + cx + dx, FLOOR_Y + 1, oz + cz - 3), stair, 2);
        }
        placeChest(level, new BlockPos(ox + 3, FLOOR_Y + 1, oz + cz), Direction.EAST);
    }

    // Low warren: two east-west partition walls with a central gap, barred at the gate posts, plus a
    // chance of a chest tucked in a corner cell. The centre column stays open end to end.
    private static void decorateCrampedCells(ServerLevel level, int ox, int oz, RoomType t, RandomSource rng) {
        lampCorners(level, ox, oz, t);
        int cx = t.w / 2;
        for (int wallZ : new int[]{5, 9}) {
            for (int x = 1; x < t.w - 1; x++) {
                if (Math.abs(x - cx) <= 1) {
                    continue; // doorway through the partition
                }
                for (int y = 1; y <= t.h; y++) {
                    level.setBlock(new BlockPos(ox + x, FLOOR_Y + y, oz + wallZ), bricks(), 2);
                }
            }
            // Bar gateposts flanking the gap.
            for (int gx : new int[]{cx - 2, cx + 2}) {
                bars(level, new BlockPos(ox + gx, FLOOR_Y + 1, oz + wallZ), false, true);
                bars(level, new BlockPos(ox + gx, FLOOR_Y + 2, oz + wallZ), false, true);
            }
        }
        level.setBlock(new BlockPos(ox + 1, FLOOR_Y + 1, oz + 7), lamp(), 2);
        level.setBlock(new BlockPos(ox + t.w - 2, FLOOR_Y + 1, oz + 7), lamp(), 2);
        if (rng.nextBoolean()) {
            placeChest(level, new BlockPos(ox + 2, FLOOR_Y + 1, oz + 12), Direction.SOUTH);
        }
    }

    // A pinwheel of 2-tall dark-iron-bar screens to weave through, around a 3x3 cage holding a glowing
    // relic. Sometimes a corner chest. The lanes at x=cx-2 / cx+2 stay clear so the cage is passable.
    private static void decorateHallOfBars(ServerLevel level, int ox, int oz, RoomType t, RandomSource rng) {
        lampCorners(level, ox, oz, t);
        int cx = t.w / 2;
        int cz = t.d / 2;

        // Central cage: bar the ring around the centre, relic on a plinth inside.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                bars(level, new BlockPos(ox + cx + dx, FLOOR_Y + 1, oz + cz + dz), true, true);
                bars(level, new BlockPos(ox + cx + dx, FLOOR_Y + 2, oz + cz + dz), true, true);
            }
        }
        level.setBlock(new BlockPos(ox + cx, FLOOR_Y + 1, oz + cz), carved(), 2);
        level.setBlock(new BlockPos(ox + cx, FLOOR_Y + 2, oz + cz), heart(), 2);

        // Offset screens, none crossing the cage's side lanes.
        barScreenNS(level, ox, oz, 3, 1, 5);
        barScreenNS(level, ox, oz, t.w - 4, t.d - 6, t.d - 2);
        barScreenEW(level, ox, oz, t.w - 6, t.w - 2, 3);
        barScreenEW(level, ox, oz, 1, 5, t.d - 4);

        if (rng.nextBoolean()) {
            placeChest(level, new BlockPos(ox + 2, FLOOR_Y + 1, oz + 2), Direction.EAST);
        }
    }

    // The onward door: a Rift Door set into the far (north) interior wall, centred, flanked by lamps.
    // Walking through it fires getPortalDestination -> newRoom, so it always opens the next room. It
    // sits one block in from the boundary so the fiber shell is never breached.
    private static void placeExitDoor(ServerLevel level, int ox, int oz, RoomType t) {
        int cx = t.w / 2;
        int cz = t.d - 1;
        BlockState lower = ModRegistry.RIFT_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        BlockPos lowerPos = new BlockPos(ox + cx, FLOOR_Y + 1, oz + cz);
        level.setBlock(lowerPos, lower, 3);
        level.setBlock(lowerPos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        if (cx - 1 >= 0) {
            level.setBlock(new BlockPos(ox + cx - 1, FLOOR_Y + 1, oz + cz), lamp(), 2);
        }
        if (cx + 1 < t.w) {
            level.setBlock(new BlockPos(ox + cx + 1, FLOOR_Y + 1, oz + cz), lamp(), 2);
        }
    }

    // ---- small building helpers ----

    private static void lampCorners(ServerLevel level, int ox, int oz, RoomType t) {
        int[][] corners = {{1, 1}, {t.w - 2, 1}, {1, t.d - 2}, {t.w - 2, t.d - 2}};
        for (int[] c : corners) {
            level.setBlock(new BlockPos(ox + c[0], FLOOR_Y + 1, oz + c[1]), lamp(), 2);
        }
    }

    // A 2-tall north-south screen of bars at column x, from z0 to z1 inclusive.
    private static void barScreenNS(ServerLevel level, int ox, int oz, int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            bars(level, new BlockPos(ox + x, FLOOR_Y + 1, oz + z), true, false);
            bars(level, new BlockPos(ox + x, FLOOR_Y + 2, oz + z), true, false);
        }
    }

    // A 2-tall east-west screen of bars at row z, from x0 to x1 inclusive.
    private static void barScreenEW(ServerLevel level, int ox, int oz, int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) {
            bars(level, new BlockPos(ox + x, FLOOR_Y + 1, oz + z), false, true);
            bars(level, new BlockPos(ox + x, FLOOR_Y + 2, oz + z), false, true);
        }
    }

    // Dark iron bars with the connection flags set explicitly (ns = north/south, ew = east/west), so
    // a straight run reads as a connected screen without relying on neighbour block updates.
    private static void bars(ServerLevel level, BlockPos pos, boolean ns, boolean ew) {
        BlockState s = ModRegistry.DARK_IRON_BARS.get().defaultBlockState()
                .setValue(BlockStateProperties.NORTH, ns)
                .setValue(BlockStateProperties.SOUTH, ns)
                .setValue(BlockStateProperties.EAST, ew)
                .setValue(BlockStateProperties.WEST, ew);
        level.setBlock(pos, s, 2);
    }

    private static void placeChest(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(LOOT_TABLE, level.getRandom().nextLong());
        }
    }

    private static BlockState fiber() {
        return ModRegistry.FORSAKEN_FIBER.get().defaultBlockState();
    }

    private static BlockState nullstone() {
        return ModRegistry.NULLSTONE.get().defaultBlockState();
    }

    private static BlockState bricks() {
        return ModRegistry.ALTAR_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState cracked() {
        return ModRegistry.CRACKED_ALTAR_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState carved() {
        return ModRegistry.CARVED_ALTAR_STONE.get().defaultBlockState();
    }

    private static BlockState altarStone() {
        return ModRegistry.ALTAR_STONE.get().defaultBlockState();
    }

    private static BlockState heart() {
        return ModRegistry.ALTAR_HEART.get().defaultBlockState();
    }

    private static BlockState lamp() {
        return ModRegistry.DAEMONLIGHT.get().defaultBlockState()
                .setValue(DaemonlightLighting.LIT, Boolean.TRUE);
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

    private NullDomainRooms() {
    }
}
