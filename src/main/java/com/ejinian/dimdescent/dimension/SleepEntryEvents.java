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
//   - Attuned, at night, in any valid bed  -> pulled into the Null Domain instead of sleeping.
//   - Under the raw poisoning (seeds / Devil's Trumpet), not attuned -> can't sleep at all.
//   - Sober -> sleeps normally.
//
// Vanilla does the heavy lifting. CanPlayerSleepEvent hands us the result of vanilla's own bed
// checks (night, in range, unobstructed, safe) and lets us override it; and vanilla's
// startSleepInBed ALREADY sets the player's respawn to the bed before it decides whether sleep
// succeeds. So crossing means their respawn is the bed - and since Attunement expiry ejects to the
// respawn point, they wake in the very bed they lay down in, hours gone, with no extra code.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class SleepEntryEvents {

    // Crossing is deferred one tick rather than done inside the sleep-attempt call stack, to avoid
    // changing dimension in the middle of the bed interaction that triggered it.
    private static final Set<UUID> PENDING_CROSS = Collections.synchronizedSet(new HashSet<>());

    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        ServerLevel level = player.serverLevel();

        // No crossing from inside the Null Domain (and the rift dimension isn't natural anyway, so
        // vanilla would already refuse - this is belt and braces).
        if (RiftTeleporter.isInRift(level)) {
            return;
        }

        boolean attuned = player.hasEffect(ModRegistry.ATTUNEMENT_EFFECT);
        boolean poisoned = player.hasEffect(ModRegistry.DATURA_TRIP_EFFECT);

        if (poisoned && !attuned) {
            // You're too far gone to sleep. OTHER_PROBLEM carries no default message, so the only
            // thing shown is the narrated line.
            event.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(
                    Component.translatable("dimdescent.sleep.too_strange")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    true);
            return;
        }

        // Only cross when vanilla would otherwise have allowed the sleep - i.e. it is night, the bed
        // is in range and unobstructed, and it's safe. That also means respawn has just been set to
        // this bed. Anything else (daytime, too far, monsters) falls through to vanilla's own reason.
        if (attuned && event.getVanillaProblem() == null) {
            event.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            PENDING_CROSS.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!PENDING_CROSS.remove(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (RiftTeleporter.isInRift(level)) {
            return;
        }
        DimensionTransition transition = RiftTeleporter.getTransitionFor(level, player);
        if (transition != null) {
            player.changeDimension(transition);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CROSS.remove(event.getEntity().getUUID());
    }

    private SleepEntryEvents() {
    }
}
