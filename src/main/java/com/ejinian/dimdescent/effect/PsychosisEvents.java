package com.ejinian.dimdescent.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.entity.HallucinationGhost;
import com.ejinian.dimdescent.registry.ModRegistry;
import com.ejinian.dimdescent.sound.PlayerSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Everything Psychosis does on a timer: hearing things, and seeing someone.
//
// The hallucinated figure used to be its own trip stage. It's folded in here because a hallucination
// genuinely is part of this symptom rather than a separate one - and because it lets the figure
// arrive partway through, once the noises have already put the player on edge, rather than
// announcing itself the moment the effect lands.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class PsychosisEvents {

    // 20s between sounds -> 3/min; 10s -> 6/min.
    private static final int MIN_GAP_TICKS = 200;
    private static final int MAX_GAP_TICKS = 400;

    // The FIRST sound comes sooner than the rest. Without this, a short Psychosis could roll a
    // full-length opening gap and end on the very tick its first sound was due - so a 20s dose would
    // be silent about as often as not. Only the opening interval is affected, so the 3-6 per minute
    // rate over any meaningful duration is untouched.
    private static final int FIRST_GAP_MIN_TICKS = 100;
    private static final int FIRST_GAP_MAX_TICKS = 300;

    // Odds that a given Psychosis produces a figure at all.
    private static final float GHOST_CHANCE = 0.85F;

    // Where in the effect's span the figure can appear, as a fraction. Never at the very start (the
    // noises should land first) and never so late that it has no time to be seen.
    private static final float GHOST_EARLIEST = 0.15F;
    private static final float GHOST_LATEST = 0.75F;

    private static final int GHOST_LIFETIME_TICKS = 200;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        int ticksUntilNext;
        // Negative once the figure has been spawned, or if this Psychosis rolled no figure at all.
        int ticksUntilGhost = -1;
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

        MobEffectInstance instance = player.getEffect(ModRegistry.PSYCHOSIS_EFFECT);
        if (instance == null) {
            STATES.remove(id);
            return;
        }

        RandomSource random = player.getRandom();
        State state = STATES.get(id);
        if (state == null) {
            state = newState(instance, random);
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

        if (state.ticksUntilGhost >= 0 && --state.ticksUntilGhost <= 0) {
            state.ticksUntilGhost = -1;
            // Don't let the figure outlive the symptom that conjured it.
            HallucinationGhost.spawnNear(player, Math.min(GHOST_LIFETIME_TICKS, instance.getDuration()));
        }

        if (--state.ticksUntilNext > 0) {
            return;
        }
        // Some entries in the pool run long (vanilla's cave ambiences reach ~10s, and our whispers
        // are 7.5-9s), which could tail-overlap the next sound at the bottom of the gap range. Each
        // entry therefore reports how much room it needs, and the gap is widened to fit. Since
        // every such floor sits below MAX_GAP_TICKS, the 3-6 per minute rate still holds.
        int minSeparation = playRandomHallucinatedSound(player, state, random);
        state.ticksUntilNext = Math.max(nextGap(random), minSeparation);
    }

    private static State newState(MobEffectInstance instance, RandomSource random) {
        State state = new State();
        state.ticksUntilNext = FIRST_GAP_MIN_TICKS
                + random.nextInt(FIRST_GAP_MAX_TICKS - FIRST_GAP_MIN_TICKS + 1);

        if (random.nextFloat() < GHOST_CHANCE) {
            // Whatever is left of the effect right now is effectively its full span, since this runs
            // on the first tick the effect is seen.
            int window = instance.getDuration();
            float fraction = GHOST_EARLIEST + random.nextFloat() * (GHOST_LATEST - GHOST_EARLIEST);
            state.ticksUntilGhost = Mth.clamp((int) (window * fraction), 20, Math.max(20, window - 20));
        }
        return state;
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
                // One of the three takes, picked per occurrence.
                int variant = random.nextInt(ModRegistry.WHISPER_SOUNDS.size());
                PlayerSounds.playPrivately(player, ModRegistry.WHISPER_SOUNDS.get(variant).get(),
                        0.85F, 0.97F + random.nextFloat() * 0.06F);
                return 240;
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

    private PsychosisEvents() {
    }
}
