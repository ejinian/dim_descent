package com.ejinian.dimdescent.dimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// Per-player memory of how they got where they are, which is what makes the pale bed possible.
//
// Travel used to be strictly one-way, so a monotonic room counter was enough. Now that the pale Nexus
// walks players back, each player needs their own ordered chain of the rooms they have passed through
// - the dark bed pushes a new room on, the pale bed pops one off - plus the overworld bed they lay
// down in, so that stepping out of the first room can put them back beside it (and corrupt it).
//
// Stored as SavedData on the rift level, so a chain survives logout, restart and death: a player who
// disconnects three rooms deep is still three rooms deep when they return.
public class RoomChainData extends SavedData {

    private static final String STORAGE_KEY = "dimdescent_room_chains";
    private static final String TAG_CHAINS = "chains";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_ROOMS = "rooms";
    private static final String TAG_BED_DIM = "bed_dimension";
    private static final String TAG_BED_POS = "bed_pos";

    private static final SavedData.Factory<RoomChainData> FACTORY =
            new SavedData.Factory<>(RoomChainData::new, RoomChainData::load);

    // The bed a player lay down in to enter, so a return trip knows where to put them back.
    public record EntryBed(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<UUID, List<Integer>> chains = new HashMap<>();
    private final Map<UUID, EntryBed> entryBeds = new HashMap<>();

    public static RoomChainData get(ServerLevel anyLevel) {
        ServerLevel rift = anyLevel.getServer().getLevel(RiftTeleporter.RIFT_LEVEL);
        ServerLevel host = rift != null ? rift : anyLevel.getServer().overworld();
        return host.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    // Called when a player crosses IN: the chain restarts from nothing, since a new trip is a new
    // descent regardless of where they got to last time.
    public void beginChain(UUID playerId, int roomIndex) {
        List<Integer> chain = new ArrayList<>();
        chain.add(roomIndex);
        chains.put(playerId, chain);
        setDirty();
    }

    public void pushRoom(UUID playerId, int roomIndex) {
        chains.computeIfAbsent(playerId, id -> new ArrayList<>()).add(roomIndex);
        setDirty();
    }

    // Drops the room the player is standing in and hands back the one before it, or -1 if this was
    // the first room (ground zero) and there is nothing behind them but the waking world.
    public int popRoom(UUID playerId) {
        List<Integer> chain = chains.get(playerId);
        if (chain == null || chain.size() <= 1) {
            return -1;
        }
        chain.remove(chain.size() - 1);
        setDirty();
        return chain.get(chain.size() - 1);
    }

    public int depth(UUID playerId) {
        List<Integer> chain = chains.get(playerId);
        return chain == null ? 0 : chain.size();
    }

    public void clearChain(UUID playerId) {
        if (chains.remove(playerId) != null | entryBeds.remove(playerId) != null) {
            setDirty();
        }
    }

    public void setEntryBed(UUID playerId, @Nullable EntryBed bed) {
        if (bed == null) {
            entryBeds.remove(playerId);
        } else {
            entryBeds.put(playerId, bed);
        }
        setDirty();
    }

    @Nullable
    public EntryBed getEntryBed(UUID playerId) {
        return entryBeds.get(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, List<Integer>> entry : chains.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID(TAG_PLAYER, entry.getKey());
            row.putIntArray(TAG_ROOMS, entry.getValue().stream().mapToInt(Integer::intValue).toArray());
            EntryBed bed = entryBeds.get(entry.getKey());
            if (bed != null) {
                row.putString(TAG_BED_DIM, bed.dimension().location().toString());
                row.put(TAG_BED_POS, NbtUtils.writeBlockPos(bed.pos()));
            }
            list.add(row);
        }
        tag.put(TAG_CHAINS, list);
        return tag;
    }

    private static RoomChainData load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomChainData data = new RoomChainData();
        ListTag list = tag.getList(TAG_CHAINS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            UUID playerId = row.getUUID(TAG_PLAYER);
            List<Integer> chain = new ArrayList<>();
            for (int roomIndex : row.getIntArray(TAG_ROOMS)) {
                chain.add(roomIndex);
            }
            data.chains.put(playerId, chain);
            if (row.contains(TAG_BED_DIM)) {
                ResourceKey<Level> dimension = ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.parse(row.getString(TAG_BED_DIM)));
                NbtUtils.readBlockPos(row, TAG_BED_POS).ifPresent(
                        pos -> data.entryBeds.put(playerId, new EntryBed(dimension, pos)));
            }
        }
        return data;
    }
}
