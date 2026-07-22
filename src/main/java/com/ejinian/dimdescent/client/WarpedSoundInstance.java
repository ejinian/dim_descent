package com.ejinian.dimdescent.client;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

// A sound played at the wrong pitch and volume, delegating everything else to the sound it wraps.
//
// Wrapping rather than rebuilding matters: a SoundInstance carries its resolved Sound, looping flag,
// attenuation mode, relative-position flag and world position, and reconstructing one from scratch
// would quietly lose whichever of those the original had set. The engine has no pitch-shift hook, so
// substituting the instance is the only way in.
//
// Volume is a MULTIPLIER applied lazily rather than an absolute value captured up front, and that is
// load-bearing. SoundEngine.play fires PlaySoundEvent *before* it calls resolve(), and
// AbstractSoundInstance.getVolume()/getPitch() both dereference the Sound that resolve() populates -
// so reading either one while handling the event is a guaranteed NullPointerException. Deferring the
// read to getVolume() means it happens after the engine has resolved us, when it is safe.
public class WarpedSoundInstance implements SoundInstance {

    private final SoundInstance delegate;
    private final float pitch;
    private final float volumeScale;

    public WarpedSoundInstance(SoundInstance delegate, float pitch, float volumeScale) {
        this.delegate = delegate;
        this.pitch = pitch;
        this.volumeScale = volumeScale;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public float getVolume() {
        return this.delegate.getVolume() * this.volumeScale;
    }

    @Override
    public ResourceLocation getLocation() {
        return this.delegate.getLocation();
    }

    @Nullable
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        return this.delegate.resolve(manager);
    }

    @Override
    public Sound getSound() {
        return this.delegate.getSound();
    }

    @Override
    public SoundSource getSource() {
        return this.delegate.getSource();
    }

    @Override
    public boolean isLooping() {
        return this.delegate.isLooping();
    }

    @Override
    public boolean isRelative() {
        return this.delegate.isRelative();
    }

    @Override
    public int getDelay() {
        return this.delegate.getDelay();
    }

    @Override
    public double getX() {
        return this.delegate.getX();
    }

    @Override
    public double getY() {
        return this.delegate.getY();
    }

    @Override
    public double getZ() {
        return this.delegate.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return this.delegate.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return this.delegate.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return this.delegate.canPlaySound();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return this.delegate.getStream(soundBuffers, sound, looping);
    }
}
