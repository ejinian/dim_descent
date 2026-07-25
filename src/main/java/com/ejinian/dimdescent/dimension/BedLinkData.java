package com.ejinian.dimdescent.dimension;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// The Null Domain's map, and the reason it is a shared place rather than a private one.
//
// Links belong to BEDS, not to players. A given bed opens a given room every single time, for
// everybody: sleep in the bed in your house and you and everyone else on the server arrive in the
// same room, today and next week. Rooms are therefore one fixed graph the whole server explores,
// which is exactly how Dimensional Doors behaves (its links live on the rift, not the traveller).
//
// Two maps, both written once at the moment a room is born:
//   forward - the bed you used     -> the room it opens
//   back    - that room's pale bed -> the bed that opened it
//
// The reverse entry is what makes the pale Nexus work without tracking anybody: it asks "what made
// me?" and sends the player there. If the answer is a bed inside the Domain you step back a room; if
// it's a bed in the waking world, that's the way out (and refusing the trip - see NexusReturn).
//
// A previous design kept a per-player chain of rooms here instead. It made the Domain single-player
// in all but name - two people sleeping in the same bed got two different rooms and could never meet
// - so it was replaced wholesale. Don't reintroduce per-player routing.
public class BedLinkData extends SavedData {

    private static final String STORAGE_KEY = "dimdescent_bed_links";
    private static final String TAG_FORWARD = "forward";
    private static final String TAG_BACK = "back";
    private static final String TAG_BED = "bed";
    private static final String TAG_ROOM = "room";
    private static final String TAG_TARGET = "target";

    private static final SavedData.Factory<BedLinkData> FACTORY =
            new SavedData.Factory<>(BedLinkData::new, BedLinkData::load);

    private final Map<BedKey, Integer> forward = new HashMap<>();
    private final Map<BedKey, BedKey> back = new HashMap<>();

    // Stored on the rift level, so the whole graph lives with the dimension it describes.
    public static BedLinkData get(ServerLevel anyLevel) {
        ServerLevel rift = anyLevel.getServer().getLevel(RiftTeleporter.RIFT_LEVEL);
        ServerLevel host = rift != null ? rift : anyLevel.getServer().overworld();
        return host.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    // The room this bed opens, or null if it has never been used and no room exists for it yet.
    @Nullable
    public Integer roomFor(BedKey bed) {
        return forward.get(bed);
    }

    // Record a freshly-created room: the bed that opens it, and the room's own pale bed pointing back.
    public void link(BedKey sourceBed, int roomIndex, BedKey roomEntranceBed) {
        forward.put(sourceBed, roomIndex);
        back.put(roomEntranceBed, sourceBed);
        setDirty();
    }

    // Where a pale Nexus sends you: the bed that opened the room it stands in.
    @Nullable
    public BedKey returnTargetFor(BedKey paleBed) {
        return back.get(paleBed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag forwardList = new ListTag();
        forward.forEach((bed, room) -> {
            CompoundTag row = new CompoundTag();
            row.put(TAG_BED, bed.toNbt());
            row.putInt(TAG_ROOM, room);
            forwardList.add(row);
        });
        tag.put(TAG_FORWARD, forwardList);

        ListTag backList = new ListTag();
        back.forEach((paleBed, target) -> {
            CompoundTag row = new CompoundTag();
            row.put(TAG_BED, paleBed.toNbt());
            row.put(TAG_TARGET, target.toNbt());
            backList.add(row);
        });
        tag.put(TAG_BACK, backList);
        return tag;
    }

    private static BedLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        BedLinkData data = new BedLinkData();
        ListTag forwardList = tag.getList(TAG_FORWARD, Tag.TAG_COMPOUND);
        for (int i = 0; i < forwardList.size(); i++) {
            CompoundTag row = forwardList.getCompound(i);
            data.forward.put(BedKey.fromNbt(row.getCompound(TAG_BED)), row.getInt(TAG_ROOM));
        }
        ListTag backList = tag.getList(TAG_BACK, Tag.TAG_COMPOUND);
        for (int i = 0; i < backList.size(); i++) {
            CompoundTag row = backList.getCompound(i);
            data.back.put(BedKey.fromNbt(row.getCompound(TAG_BED)),
                    BedKey.fromNbt(row.getCompound(TAG_TARGET)));
        }
        return data;
    }
}
