package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

// While Psychosis holds, everything the world sounds like stops being trustworthy.
//
// On the original ask - literally inverting sounds: phase-inverting a mono signal (multiplying the
// waveform by -1) is *inaudible*, so the literal reading produces no perceptible change at all. True
// reverse playback isn't reachable either: the engine exposes only setPitch and setVolume per
// channel (com.mojang.blaze3d.audio.Channel), with no reverse, reverb, filter or DSP hook of any
// kind, so reversing arbitrary sounds would mean shipping pre-reversed copies of every sound file in
// the game.
//
// What IS reachable is total interception. PlaySoundEvent fires for every sound the client is about
// to play and lets the instance be swapped or dropped, which covers literally everything - water,
// footsteps, eating, mobs, blocks - and that's what the four distortions below are built on.
//
// CAREFUL: the event fires BEFORE SoundEngine.play calls resolve() on the instance, and
// AbstractSoundInstance.getVolume()/getPitch() both dereference the Sound that resolve() populates.
// Calling either one in this handler is an instant NullPointerException (it crashed on water
// ambience the first time round). Only the plain fields - getSource, getLocation, getX/Y/Z - are
// safe to read here; anything volume- or pitch-derived has to be deferred into the replacement
// instance, which is why WarpedSoundInstance takes a volume MULTIPLIER rather than a value.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class PsychosisSoundWarp {

    private static final RandomSource RANDOM = RandomSource.create();

    // Every surviving sound is dragged down and then knocked off-key by a random amount, so nothing
    // is ever consistent between two plays of the same sound.
    private static final float PITCH_BASE = 0.62F;
    private static final float PITCH_SPREAD = 0.30F;
    private static final float VOLUME_SPREAD = 0.25F;

    private static final int DROPOUT_PERCENT = 12;
    private static final int SUBSTITUTE_PERCENT = 10;

    // A substituted sound is a different sound entirely, so there is no original loudness to scale
    // from - and the original's own volume cannot be read here anyway (see below).
    private static final float SUBSTITUTE_VOLUME = 0.7F;

    // Short, wrong sounds. The point isn't that these are scary in themselves - it's that hearing
    // one where a footstep should have been makes the player doubt what they heard.
    private static final SoundEvent[] SUBSTITUTES = {
        SoundEvents.WOODEN_DOOR_OPEN,
        SoundEvents.CHEST_OPEN,
        SoundEvents.ZOMBIE_AMBIENT,
        SoundEvents.SKELETON_AMBIENT,
        SoundEvents.BAT_AMBIENT,
        SoundEvents.NOTE_BLOCK_BASS.value(),
    };

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasEffect(ModRegistry.PSYCHOSIS_EFFECT)) {
            return;
        }

        // Leave music alone - it's a long stream and warping it reads as a bug rather than a symptom.
        if (sound.getSource() == SoundSource.MUSIC) {
            return;
        }

        // Tickable instances recompute their own position and volume every tick (entity-bound
        // sounds, minecart loops, boss music); wrapping one would strip that behaviour. This also
        // conveniently protects our own hallucinated sounds, which are all entity-bound - dropping
        // or re-pitching those would undermine the very symptom being simulated.
        if (sound instanceof TickableSoundInstance) {
            return;
        }

        if (RANDOM.nextInt(100) < DROPOUT_PERCENT) {
            // Silence. You swing at a block and nothing confirms it happened.
            event.setSound(null);
            return;
        }

        float pitch = Mth.clamp(PITCH_BASE + (RANDOM.nextFloat() - 0.5F) * PITCH_SPREAD, 0.5F, 2.0F);
        float volumeScale = 1.0F - RANDOM.nextFloat() * VOLUME_SPREAD;

        if (RANDOM.nextInt(100) < SUBSTITUTE_PERCENT) {
            SoundEvent replacement = SUBSTITUTES[RANDOM.nextInt(SUBSTITUTES.length)];
            event.setSound(new SimpleSoundInstance(
                    replacement, sound.getSource(), SUBSTITUTE_VOLUME * volumeScale, pitch, RANDOM,
                    sound.getX(), sound.getY(), sound.getZ()));
            return;
        }

        event.setSound(new WarpedSoundInstance(sound, pitch, volumeScale));
    }

    private PsychosisSoundWarp() {
    }
}
