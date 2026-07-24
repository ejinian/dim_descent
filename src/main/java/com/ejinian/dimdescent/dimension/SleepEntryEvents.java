package com.ejinian.dimdescent.dimension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The Waking Dream: sleep is how you cross into the Null Domain.
//
//   - Attuned, at night, in any valid bed  -> you actually lie down, the screen fades toward black,
//     and a few seconds in you are pulled into the Null Domain instead of waking to morning.
//   - Under the raw poisoning (seeds / Devil's Trumpet), not attuned -> can't sleep at all.
//   - Sober -> sleeps normally.
//
// Vanilla does the heavy lifting. CanPlayerSleepEvent hands us the result of vanilla's own bed
// checks (night, in range, unobstructed, safe) and lets us override it; and vanilla's startSleepInBed
// already sets the player's respawn to the bed before it decides. So a crosser's respawn IS the bed,
// and Attunement expiry ejects to the respawn point - they wake in the very bed they lay down in,
// hours gone, with no extra code.
//
// The crossing is timed off the sleep fade rather than fired instantly: we let the player sleep and
// wait until getSleepTimer nears full black, then teleport. That gives the "lie down, darken, gone"
// beat. We cross a little BEFORE the fade completes (100) so vanilla's own sleep-through-night skip
// never fires first.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class SleepEntryEvents {

    // sleepTimer runs 0..100; the sleep-through-night skip triggers at 100. Cross at 88: nearly full
    // black, a comfortable margin before the skip.
    private static final int CROSS_AT_SLEEP_TIMER = 88;

    private static final Set<UUID> CROSSING = Collections.synchronizedSet(new HashSet<>());

    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();

        // No crossing from inside the Null Domain (the rift dimension isn't natural anyway, so vanilla
        // already refuses - belt and braces).
        if (RiftTeleporter.isInRift(player.serverLevel())) {
            return;
        }

        boolean attuned = player.hasEffect(ModRegistry.ATTUNEMENT_EFFECT);
        boolean poisoned = player.hasEffect(ModRegistry.DATURA_TRIP_EFFECT);

        if (poisoned && !attuned) {
            // Too far gone to sleep. OTHER_PROBLEM carries no default message, so the narrated line
            // is the only thing shown.
            event.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(
                    Component.translatable("dimdescent.sleep.too_strange")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    true);
            return;
        }

        // Only cross when vanilla would otherwise have allowed the sleep - it's night, the bed is in
        // range and unobstructed and safe, and respawn has just been set to it. We deliberately do
        // NOT set a problem here: the player really lies down and the fade begins; the tick handler
        // pulls them under once it's dark enough.
        if (attuned && event.getVanillaProblem() == null) {
            CROSSING.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!CROSSING.contains(id)) {
            return;
        }

        // Woke up (left the bed) or was interrupted before crossing - cancel.
        if (!player.isSleeping()) {
            CROSSING.remove(id);
            return;
        }

        if (player.getSleepTimer() >= CROSS_AT_SLEEP_TIMER) {
            CROSSING.remove(id);
            ServerLevel level = player.serverLevel();
            // Wake without nudging the level's sleep bookkeeping, then cross.
            player.stopSleepInBed(true, false);
            DimensionTransition transition = RiftTeleporter.getTransitionFor(level, player);
            if (transition != null) {
                player.changeDimension(transition);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CROSSING.remove(event.getEntity().getUUID());
    }

    private SleepEntryEvents() {
    }
}
