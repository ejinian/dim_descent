package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
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
// to play and lets the instance be swapped or dropped, which covers literally everything the world
// makes - water, footsteps, eating, mobs, blocks. Two distortions are built on it: everything is
// dragged down in pitch and knocked off-key, and some of it simply never arrives.
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

        // Only the outside world gets warped. The voices in the player's head are supposed to be the
        // one thing coming through clearly, so everything PlayerSounds emits has to pass untouched.
        //
        // The discriminator is the INSTANCE TYPE, not the sound's name, and it has to be: half the
        // hallucination pool is borrowed vanilla sounds (a zombie forcing a door, wither skeleton
        // ambience, a creeper fuse), so by resource location a hallucinated one is indistinguishable
        // from the real thing happening nearby. What separates them is delivery -
        // ClientboundSoundEntityPacket resolves to an EntityBoundSoundInstance, which is tickable,
        // whereas ordinary world sounds arrive as plain SimpleSoundInstances.
        //
        // Skipping tickables is required for its own sake anyway: they recompute position and volume
        // every tick (entity-bound sounds, minecart loops, boss music) and a static wrapper would
        // strip that. The two requirements happen to coincide exactly.
        if (sound instanceof TickableSoundInstance) {
            return;
        }

        // Belt and braces for our own sounds specifically, so this still holds if PlayerSounds ever
        // switches away from entity-bound delivery. Doesn't help the borrowed vanilla ones above -
        // nothing name-based could - which is why the tickable check is the real guarantee.
        if (sound.getLocation().getNamespace().equals(DimDescent.MODID)) {
            return;
        }

        if (RANDOM.nextInt(100) < DROPOUT_PERCENT) {
            // Silence. You swing at a block and nothing confirms it happened.
            event.setSound(null);
            return;
        }

        float pitch = Mth.clamp(PITCH_BASE + (RANDOM.nextFloat() - 0.5F) * PITCH_SPREAD, 0.5F, 2.0F);
        float volumeScale = 1.0F - RANDOM.nextFloat() * VOLUME_SPREAD;
        event.setSound(new WarpedSoundInstance(sound, pitch, volumeScale));
    }

    private PsychosisSoundWarp() {
    }
}
