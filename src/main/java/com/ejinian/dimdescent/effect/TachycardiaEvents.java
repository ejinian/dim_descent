package com.ejinian.dimdescent.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;
import com.ejinian.dimdescent.sound.PlayerSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The heart doesn't race once and settle - it keeps surging. One burst the moment Tachycardia
// lands, then more at irregular intervals for as long as it holds.
//
// Driven off the effect rather than the trip stage (where the original single burst lived) so that
// /effect give behaves like the real thing, and so a potion trip's randomly-sized Tachycardia gets
// a proportionate number of surges without anything having to calculate that.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class TachycardiaEvents {

    // The clip is 5s long, so the floor here also guarantees surges never overlap each other.
    private static final int MIN_GAP_TICKS = 200;
    private static final int MAX_GAP_TICKS = 500;

    private static final Map<UUID, Integer> NEXT_BEAT = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();

        if (!player.hasEffect(ModRegistry.TACHYCARDIA_EFFECT)) {
            NEXT_BEAT.remove(id);
            return;
        }

        RandomSource random = player.getRandom();
        Integer remaining = NEXT_BEAT.get(id);
        if (remaining == null) {
            // First tick of the effect: surge immediately, then schedule the next.
            playHeartbeat(player);
            NEXT_BEAT.put(id, nextGap(random));
            return;
        }

        if (remaining > 1) {
            NEXT_BEAT.put(id, remaining - 1);
            return;
        }
        playHeartbeat(player);
        NEXT_BEAT.put(id, nextGap(random));
    }

    private static void playHeartbeat(ServerPlayer player) {
        PlayerSounds.playPrivately(player, ModRegistry.HEARTBEAT_SOUND.get(), 0.9F, 1.0F);
    }

    private static int nextGap(RandomSource random) {
        return MIN_GAP_TICKS + random.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_BEAT.remove(event.getEntity().getUUID());
    }

    private TachycardiaEvents() {
    }
}
