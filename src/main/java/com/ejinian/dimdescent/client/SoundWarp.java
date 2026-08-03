package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.RiftTeleporter;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

// Everything the world sounds like, made untrustworthy. Two situations warp it, and this is a single
// handler for both ON PURPOSE: PlaySoundEvent fires once per sound, so two subscribers would each
// wrap the other's replacement and a player deliriating inside the Null Domain would hear everything
// pitched down twice. One handler picks one profile and there is no ordering to get wrong.
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
// makes - water, footsteps, eating, chests, blocks, mobs.
//
// CAREFUL: the event fires BEFORE SoundEngine.play calls resolve() on the instance, and
// AbstractSoundInstance.getVolume()/getPitch() both dereference the Sound that resolve() populates.
// Calling either one in this handler is an instant NullPointerException (it crashed on water
// ambience the first time round). Only the plain fields - getSource, getLocation, getX/Y/Z - are
// safe to read here; anything volume- or pitch-derived has to be deferred into the replacement
// instance, which is why WarpedSoundInstance takes a volume MULTIPLIER rather than a value.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class SoundWarp {

    private static final RandomSource RANDOM = RandomSource.create();

    // 0.5 is a HARD FLOOR, not a taste decision: SoundEngine.calculatePitch does
    // Mth.clamp(getPitch(), 0.5F, 2.0F) before the value ever reaches the audio channel, so one
    // octave down is the deepest the engine can play anything. Nothing below this is reachable
    // without shipping pre-pitched copies of every sound file, which is impossible for vanilla's.
    private static final float FLOOR = 0.5F;

    // Expressed as a LOW BOUND plus an upward range rather than a centre plus a symmetric spread.
    // The clamp is why: a centred spread that reaches under 0.5 gets flattened against the floor, so
    // half the intended variation collapses into a single pitch and the detune quietly stops working.
    private record Warp(float pitchLow, float pitchRange, float volumeSpread, int dropoutPercent) {
    }

    // Delirium is an acute symptom lasting seconds, so it can afford to be violent: pinned to the
    // floor itself, knocked off-key hard enough that no two plays of the same sound match, quieter,
    // and one sound in eight simply never arrives - you swing at a block and nothing confirms it
    // happened. There is no deeper setting available; this one is against the stop.
    private static final Warp DELIRIUM = new Warp(FLOOR, 0.10F, 0.25F, 12);

    // The Null Domain is where the player LIVES for the length of a trip, so its warp is the same
    // idea held steadier: everything sits well down and slightly detuned, and nothing is ever
    // dropped. Losing footsteps and chest lids at random for ten minutes stops reading as dread and
    // starts reading as a broken game - which is exactly why the dropout stays at zero here even
    // though Delirium keeps it. Deliberate, not an oversight.
    private static final Warp DOMAIN = new Warp(0.58F, 0.12F, 0.0F, 0);

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        // Precedence, not addition. Delirium is strictly the stronger of the two, so when both apply
        // it simply wins - the alternative is stacking one warp on the other, which lands the pitch
        // somewhere neither profile was tuned for.
        Warp warp;
        if (player.hasEffect(ModRegistry.DELIRIUM_EFFECT)) {
            warp = DELIRIUM;
        } else if (level.dimension() == RiftTeleporter.RIFT_LEVEL) {
            warp = DOMAIN;
        } else {
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

        if (warp.dropoutPercent() > 0 && RANDOM.nextInt(100) < warp.dropoutPercent()) {
            event.setSound(null);
            return;
        }

        float pitch = Mth.clamp(
                warp.pitchLow() + RANDOM.nextFloat() * warp.pitchRange(), FLOOR, 2.0F);
        float volumeScale = 1.0F - RANDOM.nextFloat() * warp.volumeSpread();
        event.setSound(new WarpedSoundInstance(sound, pitch, volumeScale));
    }

    private SoundWarp() {
    }
}
