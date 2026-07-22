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

// Persistent one-to-one pairing between a door that was walked through and the door generated on
// the other side of the rift for it, so walking back through that specific generated door always
// returns to the exact door that created it - the same way a Nether portal remembers its partner.
// A door with no entry here (e.g. one the player placed by hand inside the rift) isn't "linked" to
// anything and falls back to the default overworld-spawn exit.
public class RiftDoorLinkData extends SavedData {

    private static final String STORAGE_KEY = "dimdescent_rift_door_links";
    private static final String TAG_LINKS = "links";
    private static final String TAG_FROM = "from";
    private static final String TAG_TO = "to";
    private static final String TAG_NEXT_INDEX = "next_generated_index";

    public static final SavedData.Factory<RiftDoorLinkData> FACTORY =
            new SavedData.Factory<>(RiftDoorLinkData::new, RiftDoorLinkData::load);

    private final Map<DoorLocation, DoorLocation> links = new HashMap<>();
    private int nextGeneratedIndex;

    public static RiftDoorLinkData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    @Nullable
    public DoorLocation getLinkedDoor(DoorLocation from) {
        return links.get(from);
    }

    public void link(DoorLocation a, DoorLocation b) {
        links.put(a, b);
        links.put(b, a);
        setDirty();
    }

    // Each generated rift-side door gets its own index so placement never collides with an
    // earlier one - see RiftTeleporter's door generation.
    public int allocateGeneratedDoorIndex() {
        int index = nextGeneratedIndex++;
        setDirty();
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag linkList = new ListTag();
        links.forEach((from, to) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_FROM, from.toNbt());
            entry.put(TAG_TO, to.toNbt());
            linkList.add(entry);
        });
        tag.put(TAG_LINKS, linkList);
        tag.putInt(TAG_NEXT_INDEX, nextGeneratedIndex);
        return tag;
    }

    private static RiftDoorLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftDoorLinkData data = new RiftDoorLinkData();
        ListTag linkList = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < linkList.size(); i++) {
            CompoundTag entry = linkList.getCompound(i);
            DoorLocation from = DoorLocation.fromNbt(entry.getCompound(TAG_FROM));
            DoorLocation to = DoorLocation.fromNbt(entry.getCompound(TAG_TO));
            data.links.put(from, to);
        }
        data.nextGeneratedIndex = tag.getInt(TAG_NEXT_INDEX);
        return data;
    }
}
