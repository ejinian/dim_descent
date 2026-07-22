package com.ejinian.dimdescent.dimension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// Tracks, once per world save, whether the Potion of Attunement has ever been brewed - so the
// first-brew thunderclap (see AttunementBrewingEvents) only ever fires once in a world's history,
// not on every subsequent brew. Attached to the overworld's data storage regardless of which
// dimension the brewing stand is actually in, since that's the one canonical "this world" storage
// vanilla itself uses for similar world-wide one-time flags.
public class FirstAttunementBrewData extends SavedData {

    private static final String STORAGE_KEY = "dimdescent_first_attunement_brew";
    private static final String TAG_TRIGGERED = "triggered";

    public static final SavedData.Factory<FirstAttunementBrewData> FACTORY =
            new SavedData.Factory<>(FirstAttunementBrewData::new, FirstAttunementBrewData::load);

    private boolean triggered;

    public static FirstAttunementBrewData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    // Returns true only the very first time this is called for a given world - false every time
    // after, including across server restarts (the flag is persisted to disk).
    public boolean tryMarkFirstBrew() {
        if (triggered) {
            return false;
        }
        triggered = true;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_TRIGGERED, triggered);
        return tag;
    }

    private static FirstAttunementBrewData load(CompoundTag tag, HolderLookup.Provider registries) {
        FirstAttunementBrewData data = new FirstAttunementBrewData();
        data.triggered = tag.getBoolean(TAG_TRIGGERED);
        return data;
    }
}
