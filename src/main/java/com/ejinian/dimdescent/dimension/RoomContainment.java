package com.ejinian.dimdescent.dimension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// The lie that keeps players in.
//
// Every block a room is built from is breakable, so digging out looks like a real option. It isn't.
// Two things go up around each room the moment it is stamped:
//
//   1. A SHRINK-WRAP of Nullstone, one block thick, hugging the room's outer surface exactly - so
//      breaking any wall block reveals dead black behind it, whatever shape the room is. Only the
//      OUTSIDE is wrapped; interior faces are left alone, which is what makes it read as "the world
//      ends here" rather than "someone built a second wall".
//   2. A CONTAINMENT BOX five blocks further out: an inner shell of Nullstone backed by an outer
//      shell of unbreakable Forsaken Essence. Players can chew through the Nullstone and reach the
//      Essence, and that is the point - the escape is allowed to feel possible right up until it isn't.
//
// The box has walls and a ceiling but deliberately NO floor, and its walls run all the way down to
// the dimension's minimum build height. There is therefore nothing to stand on outside the room and
// nothing to tunnel beneath: leaving the room downward means falling out of the world. That is also
// why rooms are stamped at the very bottom of the dimension - anchoring the box to the build floor
// is what makes it impossible to go under.
public final class RoomContainment {

    // Empty space between the room's outer surface and the inside face of the box.
    private static final int BUFFER = 5;

    // Shell offsets from the room's bounding box: Nullstone first, then Forsaken Essence behind it.
    private static final int INNER_SHELL = BUFFER + 1;
    private static final int OUTER_SHELL = BUFFER + 2;

    // Plain cubes need no neighbour-shape updates, and suppressing them keeps a large stamp cheap.
    private static final int SHELL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // Safety valve for the flood fill. A room can be at most 48^3, so its outside-surface search
    // space is bounded; this only trips if a room is unsealed and the fill escapes into the buffer.
    private static final int MAX_FLOOD = 400_000;

    private RoomContainment() {
    }

    // Wrap a freshly stamped room and cage it. `room` is the volume the template occupies.
    public static void encase(ServerLevel level, BoundingBox room) {
        shrinkWrap(level, room);
        buildBox(level, room);
    }

    // One layer of Nullstone against every outward-facing surface of the build.
    //
    // "Outward-facing" is resolved by flooding air inward from a shell that is guaranteed to be
    // outside the room, and stopping at anything solid. Every empty cell the flood reaches that
    // touches a solid room block becomes Nullstone. Interior cavities are never reached, so interior
    // walls are never wrapped - provided the room is sealed. An unsealed room lets the flood leak in
    // through the gap and wrap the inside too, which is the one authoring rule this relies on.
    private static void shrinkWrap(ServerLevel level, BoundingBox room) {
        // Clamped to the world, which also means the room's UNDERSIDE is never wrapped: rooms sit on
        // the build floor, so there is no cell beneath them to put Nullstone in. Breaking the floor
        // therefore drops you straight out of the world, which is exactly the intent.
        BoundingBox search = clampToWorld(level, expand(room, 2));
        Set<BlockPos> outside = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        // Seed from the search volume's surface, MINUS its bottom plane: by construction the side and
        // top cells sit two blocks clear of the room, so they are genuinely outside it. The bottom is
        // different. clampToWorld pins search.minY() to the world floor, which is also the room's own
        // floor layer - so seeding that plane would treat any hole in a room's FLOOR as a way in, and
        // the flood would climb through it and Nullstone-coat the whole interior.
        //
        // Excluding it is what actually delivers the intent described above: nothing is below a room,
        // so a hole in the floor is a hole into the void and nothing more. That makes a deliberate
        // drop-to-your-death gap a supported thing to author, which it should be - it is one of the
        // few genuinely lethal features available in a dimension where nothing spawns.
        forEachSurfaceCell(search, pos -> {
            if (pos.getY() > search.minY() && isEmpty(level, pos) && outside.add(pos)) {
                queue.add(pos);
            }
        });

        Set<BlockPos> wrap = new HashSet<>();
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_FLOOD) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!search.isInside(next)) {
                    continue;
                }
                if (isEmpty(level, next)) {
                    if (outside.add(next)) {
                        queue.add(next);
                    }
                } else if (room.isInside(next)) {
                    // Solid, part of the build, and touched from outside - so `pos` is the cell a
                    // player breaking through would step into. That is where the black goes.
                    wrap.add(pos);
                }
            }
        }

        BlockState nullstone = ModRegistry.NULLSTONE.get().defaultBlockState();
        for (BlockPos pos : wrap) {
            level.setBlock(pos, nullstone, SHELL_FLAGS);
        }
    }

    // Walls + ceiling, two shells thick, anchored to the bottom of the world. No floor, on purpose.
    private static void buildBox(ServerLevel level, BoundingBox room) {
        BlockState nullstone = ModRegistry.NULLSTONE.get().defaultBlockState();
        BlockState essence = ModRegistry.FORSAKEN_ESSENCE.get().defaultBlockState();
        int floorY = level.getMinBuildHeight();

        placeShell(level, room, INNER_SHELL, floorY, nullstone);
        placeShell(level, room, OUTER_SHELL, floorY, essence);
    }

    private static void placeShell(ServerLevel level, BoundingBox room, int offset, int floorY, BlockState block) {
        int minX = room.minX() - offset;
        int maxX = room.maxX() + offset;
        int minZ = room.minZ() - offset;
        int maxZ = room.maxZ() + offset;
        int ceilY = Math.min(room.maxY() + offset, level.getMaxBuildHeight() - 1);

        // Walls: the perimeter ring, from the world floor up to the ceiling.
        for (int y = floorY; y <= ceilY; y++) {
            for (int x = minX; x <= maxX; x++) {
                setIfEmpty(level, new BlockPos(x, y, minZ), block);
                setIfEmpty(level, new BlockPos(x, y, maxZ), block);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                setIfEmpty(level, new BlockPos(minX, y, z), block);
                setIfEmpty(level, new BlockPos(maxX, y, z), block);
            }
        }
        // Ceiling: the full lid.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setIfEmpty(level, new BlockPos(x, ceilY, z), block);
            }
        }
    }

    // Never overwrite the room itself or an already-placed shell - the inner Nullstone shell is laid
    // first, and the Essence shell must not punch through it if a room is close to the buffer edge.
    private static void setIfEmpty(ServerLevel level, BlockPos pos, BlockState block) {
        if (isEmpty(level, pos)) {
            level.setBlock(pos, block, SHELL_FLAGS);
        }
    }

    private static boolean isEmpty(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    private static BoundingBox expand(BoundingBox box, int by) {
        return new BoundingBox(
                box.minX() - by, box.minY() - by, box.minZ() - by,
                box.maxX() + by, box.maxY() + by, box.maxZ() + by);
    }

    private static BoundingBox clampToWorld(ServerLevel level, BoundingBox box) {
        return new BoundingBox(
                box.minX(), Math.max(box.minY(), level.getMinBuildHeight()), box.minZ(),
                box.maxX(), Math.min(box.maxY(), level.getMaxBuildHeight() - 1), box.maxZ());
    }

    private static void forEachSurfaceCell(BoundingBox box, java.util.function.Consumer<BlockPos> action) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                action.accept(new BlockPos(x, box.minY(), z));
                action.accept(new BlockPos(x, box.maxY(), z));
            }
        }
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                action.accept(new BlockPos(x, y, box.minZ()));
                action.accept(new BlockPos(x, y, box.maxZ()));
            }
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                action.accept(new BlockPos(box.minX(), y, z));
                action.accept(new BlockPos(box.maxX(), y, z));
            }
        }
    }
}
