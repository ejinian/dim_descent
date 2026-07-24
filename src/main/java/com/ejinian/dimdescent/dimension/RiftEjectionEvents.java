package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// You cannot be in the Null Domain unless you're attuned. The moment you aren't, it spits you back
// out to where you sleep.
//
// This replaced an earlier version that killed the unattuned instead. Ejection fits the mod's
// push-your-luck loop far better: death deletes a loot run and teaches "don't go in", whereas a hard
// timer teaches "how much further dare I push before I have to leave" - which is the whole point of
// the depth axis. Attunement's final-10s Darkness is the warning that the timer is nearly up.
//
// Still a per-tick presence check rather than a hook on the teleport in, because that one check
// covers both halves of the rule for free: walking in without the effect is ejected on the first
// tick after arrival (so a door leads nowhere for the unattuned), and the effect running out while
// you're inside ejects on the tick it expires. A teleport-time hook would only ever catch the first.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class RiftEjectionEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!RiftTeleporter.isInRift(level)) {
            return;
        }

        // Gamemode deliberately does NOT matter (standing instruction): the moment a player is in the
        // Null Domain without active Attunement they go, creative and spectator included. The trade
        // is that building the interior in creative means keeping Attunement topped up - accepted.
        if (player.hasEffect(ModRegistry.ATTUNEMENT_EFFECT)) {
            return;
        }

        // Send them to their actual respawn point (bed / charged anchor), falling back to world
        // spawn - findRespawnPositionAndUseSpawnBlock resolves all of that and returns a ready
        // transition in the correct dimension, exactly as a real respawn would. keepInventory=true
        // is what stops it burning an anchor charge: this is an eviction, not a death.
        DimensionTransition transition = player.findRespawnPositionAndUseSpawnBlock(
                true, DimensionTransition.DO_NOTHING);
        player.changeDimension(transition);
    }

    private RiftEjectionEvents() {
    }
}
