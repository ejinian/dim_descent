package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.entity.HallucinationGhost;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

// Nothing lives in the Null Domain. Not a mob, not a wandering anything.
//
// The data already discourages it - the generator's biome is minecraft:the_void, which declares no
// spawners at all, and the dimension_type pins monster_spawn_light_level to 0 - but "discouraged" is
// not "impossible", and the design depends on the Domain being empty. Anything that reaches
// Mob.finalizeSpawn in this dimension is refused outright: natural spawns, chunk generation,
// structures, spawners, spawn eggs, /summon, breeding, everything.
//
// The single exception is the Hallucination - it is not an inhabitant, it is a symptom, and it
// belongs to whoever is tripping rather than to the place. It spawns via MobSpawnType.EVENT from
// DeliriumEvents, and only that entity type is let through.
//
// This is deliberately a hard block rather than tuned spawn conditions: emptiness is a design
// guarantee here, so it should not depend on light levels or biome data staying correct forever.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class NullDomainSpawns {

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel().getLevel() instanceof ServerLevel level) || !RiftTeleporter.isInRift(level)) {
            return;
        }
        if (event.getEntity() instanceof HallucinationGhost) {
            return;
        }
        event.setSpawnCancelled(true);
        event.setCanceled(true);
    }

    private NullDomainSpawns() {
    }
}
