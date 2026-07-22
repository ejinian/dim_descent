package com.ejinian.dimdescent.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;
import com.ejinian.dimdescent.sound.PlayerSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Hearing things that aren't there, for as long as Hysteria lasts.
//
// Rate is expressed as a GAP rather than a count, which makes it scale with duration for free:
// a gap drawn uniformly from 10-20s yields between 3 and 6 sounds per minute by construction, so a
// 60s Hysteria gives 3-6 and a 5-minute one given by command gives 15-30, with no special casing.
// The minimum gap is also what guarantees two sounds never land on top of each other.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class HysteriaSoundScheduler {

    // 20s between sounds -> 3/min; 10s -> 6/min.
    private static final int MIN_GAP_TICKS = 200;
    private static final int MAX_GAP_TICKS = 400;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        int ticksUntilNext;
        // Only the note-block cascade needs to emit more than one sound, so pending entries are
        // kept as a tiny queue rather than giving every entry in the pool its own scheduler.
        final List<Pending> pending = new ArrayList<>();
    }

    private static final class Pending {
        int delayTicks;
        final SoundEvent sound;
        final float volume;
        final float pitch;

        Pending(int delayTicks, SoundEvent sound, float volume, float pitch) {
            this.delayTicks = delayTicks;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();

        if (!player.hasEffect(ModRegistry.HYSTERIA_EFFECT)) {
            STATES.remove(id);
            return;
        }

        RandomSource random = player.getRandom();
        State state = STATES.get(id);
        if (state == null) {
            state = new State();
            state.ticksUntilNext = nextGap(random);
            STATES.put(id, state);
        }

        // Drain anything already queued (i.e. the tail of a cascade) before considering a new one.
        for (Iterator<Pending> it = state.pending.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (--pending.delayTicks <= 0) {
                PlayerSounds.playPrivately(player, pending.sound, pending.volume, pending.pitch);
                it.remove();
            }
        }

        if (--state.ticksUntilNext > 0) {
            return;
        }
        // Some entries in the pool run long (vanilla's cave ambiences reach ~10s, and our whispers
        // are 7.5s), which could tail-overlap the next sound at the bottom of the gap range. Each
        // entry therefore reports how much room it needs, and the gap is widened to fit. Since
        // every such floor sits below MAX_GAP_TICKS, the 3-6 per minute rate still holds.
        int minSeparation = playRandomHallucinatedSound(player, state, random);
        state.ticksUntilNext = Math.max(nextGap(random), minSeparation);
    }

    private static int nextGap(RandomSource random) {
        return MIN_GAP_TICKS + random.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1);
    }

    // Returns the minimum number of ticks that should elapse before the next sound.
    private static int playRandomHallucinatedSound(ServerPlayer player, State state, RandomSource random) {
        switch (random.nextInt(7)) {
            case 0 -> {
                PlayerSounds.playPrivately(player, SoundEvents.AMBIENT_CAVE.value(),
                        1.0F, 0.8F + random.nextFloat() * 0.2F);
                return 280;
            }

            // Something is trying to get in.
            case 1 -> {
                PlayerSounds.playPrivately(player, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        0.7F, 0.7F + random.nextFloat() * 0.2F);
                return 0;
            }

            case 2 -> {
                PlayerSounds.playPrivately(player, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(),
                        1.0F, 0.9F + random.nextFloat() * 0.2F);
                return 280;
            }

            case 3 -> {
                PlayerSounds.playPrivately(player, SoundEvents.WITHER_SKELETON_AMBIENT,
                        0.6F, 0.7F + random.nextFloat() * 0.2F);
                return 0;
            }

            // The fuse. Nothing is actually about to explode.
            case 4 -> {
                PlayerSounds.playPrivately(player, SoundEvents.CREEPER_PRIMED,
                        0.5F, 0.8F + random.nextFloat() * 0.2F);
                return 0;
            }

            case 5 -> {
                return queueNoteCascade(player, state, random);
            }

            default -> {
                PlayerSounds.playPrivately(player, ModRegistry.WHISPERS_SOUND.get(),
                        0.8F, 0.95F + random.nextFloat() * 0.1F);
                return 220;
            }
        }
    }

    // A short descending run of low note-block tones. The first note fires immediately and the rest
    // are queued, so the whole figure plays out over well under a second.
    private static int queueNoteCascade(ServerPlayer player, State state, RandomSource random) {
        int notes = 4 + random.nextInt(3);
        int spacing = 3 + random.nextInt(3);
        // Minecraft clamps pitch to [0.5, 2.0]; starting low and stepping down keeps the whole run
        // in the bottom of that range, where it reads as ominous rather than cheerful.
        float pitch = 0.78F - random.nextFloat() * 0.06F;

        for (int i = 0; i < notes; i++) {
            float notePitch = Math.max(0.5F, pitch - i * 0.05F);
            if (i == 0) {
                PlayerSounds.playPrivately(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, notePitch);
            } else {
                state.pending.add(new Pending(i * spacing, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, notePitch));
            }
        }
        // The run itself is short, but leave room for the last note's tail.
        return notes * spacing + 20;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private HysteriaSoundScheduler() {
    }
}
