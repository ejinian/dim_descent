package com.ejinian.dimdescent.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.block.DaemonlightLighting;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Phase 6, first slice: the Null Domain is no longer a flat platform but a descent through
// procedurally-stamped rooms. Each room is an unbreakable Forsaken Fiber shell with an altar-brick
// floor, a little light, some seeded variety, and a hole in the floor. Drop through the hole and you
// fall into the next, deeper room; a per-player depth counter climbs each time.
//
// Rooms are placed imperatively (not via world-gen), spread far apart on the X axis and keyed by
// depth, so depth is a logical counter rather than physical stacking - which sidesteps build-height
// limits and lets room selection become depth-aware later. Expiry-ejection (RiftEjectionEvents)
// still works from any room, since it teleports to the overworld respawn regardless of position.
//
// This is a foundation to hang variety, depth-tiered enemies and loot on - not the finished dungeon.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class NullDomainRooms {

    public static final int FLOOR_Y = 100;
    private static final int SPACING = 256;   // X distance between successive rooms
    private static final int INTERIOR = 13;   // interior footprint, relative coords 0..12
    private static final int HEIGHT = 7;       // interior air rows above the floor

    // Descent hole (relative interior coords), 2x2, opened several blocks deep into the void.
    private static final int HOLE_X = 9;
    private static final int HOLE_Z = 5;
    private static final int HOLE_W = 2;
    private static final int HOLE_DEPTH = 4;

    // Feet below this = the player has dropped through the hole; catch and send them deeper.
    private static final double DESCEND_BELOW_Y = FLOOR_Y - 1.5;

    private static final Map<UUID, Integer> DEPTH = new HashMap<>();

    private static int originX(int depth) {
        return depth * SPACING;
    }

    // Where a player stands when they arrive in a room: an interior spot away from the hole.
    public static Vec3 landingPos(int depth) {
        return new Vec3(originX(depth) + 3.5, FLOOR_Y + 1, 6.5);
    }

    // Entry point (called from RiftTeleporter): reset the player to depth 0 and build the first room.
    public static Vec3 beginDescent(ServerLevel rift, UUID playerId) {
        DEPTH.put(playerId, 0);
        generateRoom(rift, 0);
        return landingPos(0);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!RiftTeleporter.isInRift(level)) {
            return;
        }
        Integer depth = DEPTH.get(player.getUUID());
        if (depth == null) {
            // In the rift but not on the descent track (e.g. entered via the legacy door). Leave be.
            return;
        }
        // Any time a tracked player is below their room's floor, they've dropped through the hole -
        // go deeper. (Doubles as a safety net: any below-floor position recovers into the next room.)
        if (player.getY() < DESCEND_BELOW_Y) {
            descend(player, level, depth + 1);
        }
    }

    private static void descend(ServerPlayer player, ServerLevel level, int newDepth) {
        DEPTH.put(player.getUUID(), newDepth);
        generateRoom(level, newDepth);
        Vec3 land = landingPos(newDepth);
        player.teleportTo(level, land.x, land.y, land.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.displayClientMessage(
                Component.literal("▼  Depth " + newDepth).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true);
    }

    // Stamps a whole room for the given depth. Deterministic per depth, so re-entering regenerates
    // the same room rather than a different one.
    public static void generateRoom(ServerLevel level, int depth) {
        int ox = originX(depth);
        int oz = 0;

        BlockState fiber = ModRegistry.FORSAKEN_FIBER.get().defaultBlockState();
        BlockState floorBlock = ModRegistry.ALTAR_STONE_BRICKS.get().defaultBlockState();
        BlockState carved = ModRegistry.CARVED_ALTAR_STONE.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState lamp = ModRegistry.DAEMONLIGHT.get().defaultBlockState()
                .setValue(DaemonlightLighting.LIT, Boolean.TRUE);

        // Shell (unbreakable boundary) + hollow interior.
        for (int x = -1; x <= INTERIOR; x++) {
            for (int z = -1; z <= INTERIOR; z++) {
                for (int y = FLOOR_Y - 1; y <= FLOOR_Y + HEIGHT + 1; y++) {
                    boolean boundary = x == -1 || x == INTERIOR || z == -1 || z == INTERIOR
                            || y == FLOOR_Y - 1 || y == FLOOR_Y + HEIGHT + 1;
                    level.setBlock(new BlockPos(ox + x, y, oz + z), boundary ? fiber : air, 2);
                }
            }
        }

        // Brick floor.
        for (int x = 0; x < INTERIOR; x++) {
            for (int z = 0; z < INTERIOR; z++) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z), floorBlock, 2);
            }
        }

        // Seeded variety: a few full-height pillars, kept clear of the hole and the landing spot.
        RandomSource rng = RandomSource.create(depth * 0x9E3779B97F4A7C15L ^ 0xD1CE);
        int pillars = 1 + rng.nextInt(3);
        for (int i = 0; i < pillars; i++) {
            int px = 2 + rng.nextInt(INTERIOR - 4);
            int pz = 2 + rng.nextInt(INTERIOR - 4);
            boolean nearHole = Math.abs(px - HOLE_X) < 2 && Math.abs(pz - HOLE_Z) < 2;
            boolean nearLanding = px <= 4 && pz >= 5 && pz <= 8;
            if (nearHole || nearLanding) {
                continue;
            }
            for (int y = 1; y <= HEIGHT; y++) {
                level.setBlock(new BlockPos(ox + px, FLOOR_Y + y, oz + pz), floorBlock, 2);
            }
        }

        // Corner lights.
        int[][] corners = {{1, 1}, {INTERIOR - 2, 1}, {1, INTERIOR - 2}, {INTERIOR - 2, INTERIOR - 2}};
        for (int[] c : corners) {
            level.setBlock(new BlockPos(ox + c[0], FLOOR_Y + 1, oz + c[1]), lamp, 2);
        }

        // The descent hole: open through the floor and several blocks into the void, rimmed with the
        // carved altar stone so it reads as "down here".
        for (int dx = -1; dx <= HOLE_W; dx++) {
            for (int dz = -1; dz <= HOLE_W; dz++) {
                int rx = HOLE_X + dx;
                int rz = HOLE_Z + dz;
                if (rx < 0 || rx >= INTERIOR || rz < 0 || rz >= INTERIOR) {
                    continue;
                }
                boolean insideHole = dx >= 0 && dx < HOLE_W && dz >= 0 && dz < HOLE_W;
                if (insideHole) {
                    for (int dy = 0; dy < HOLE_DEPTH; dy++) {
                        level.setBlock(new BlockPos(ox + rx, FLOOR_Y - dy, oz + rz), air, 2);
                    }
                } else {
                    level.setBlock(new BlockPos(ox + rx, FLOOR_Y, oz + rz), carved, 2);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Leaving the Null Domain resets the descent - a fresh trip starts at depth 0.
        if (event.getFrom() == RiftTeleporter.RIFT_LEVEL) {
            DEPTH.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DEPTH.remove(event.getEntity().getUUID());
    }

    private NullDomainRooms() {
    }
}
