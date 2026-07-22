package com.ejinian.dimdescent.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// There is exactly ONE generated exit door in the rift, ever - not one per overworld door. Every
// door walked through from outside the rift lands at that same single door. What makes the return
// trip go to the right place is per-player tracking of "where did THIS player most recently enter
// from" - so walking back through the shared exit door always sends you to whichever door you
// personally came in through, without needing a separate generated door for every entry point.
// A door with no recorded entry (or any door inside the rift that isn't the shared exit door)
// falls back to the default overworld-spawn exit and doesn't touch any of this state.
public class RiftDoorLinkData extends SavedData {

    private static final String STORAGE_KEY = "dimdescent_rift_door_links";
    private static final String TAG_GENERATED_DOOR = "generated_door";
    private static final String TAG_LAST_ENTRY = "last_entry";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_DOOR = "door";

    public static final SavedData.Factory<RiftDoorLinkData> FACTORY =
            new SavedData.Factory<>(RiftDoorLinkData::new, RiftDoorLinkData::load);

    @Nullable
    private DoorLocation generatedExitDoor;
    private final Map<UUID, DoorLocation> lastEntryByPlayer = new HashMap<>();

    public static RiftDoorLinkData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    @Nullable
    public DoorLocation getGeneratedExitDoor() {
        return generatedExitDoor;
    }

    public void setGeneratedExitDoor(DoorLocation location) {
        this.generatedExitDoor = location;
        setDirty();
    }

    public void recordEntry(UUID playerId, DoorLocation enteredFrom) {
        lastEntryByPlayer.put(playerId, enteredFrom);
        setDirty();
    }

    @Nullable
    public DoorLocation getLastEntry(UUID playerId) {
        return lastEntryByPlayer.get(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (generatedExitDoor != null) {
            tag.put(TAG_GENERATED_DOOR, generatedExitDoor.toNbt());
        }
        ListTag entries = new ListTag();
        lastEntryByPlayer.forEach((playerId, door) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_PLAYER, playerId);
            entry.put(TAG_DOOR, door.toNbt());
            entries.add(entry);
        });
        tag.put(TAG_LAST_ENTRY, entries);
        return tag;
    }

    private static RiftDoorLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftDoorLinkData data = new RiftDoorLinkData();
        if (tag.contains(TAG_GENERATED_DOOR)) {
            data.generatedExitDoor = DoorLocation.fromNbt(tag.getCompound(TAG_GENERATED_DOOR));
        }
        ListTag entries = tag.getList(TAG_LAST_ENTRY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID playerId = entry.getUUID(TAG_PLAYER);
            DoorLocation door = DoorLocation.fromNbt(entry.getCompound(TAG_DOOR));
            data.lastEntryByPlayer.put(playerId, door);
        }
        return data;
    }
}
