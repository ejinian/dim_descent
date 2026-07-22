package com.ejinian.dimdescent.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// Seeing in the dark, hearing things that aren't there, seeing someone who isn't there, and the
// whole world sounding wrong.
//
// (Called Hysteria at first. Renamed because hysteria is an obsolete, discredited diagnosis which
// in modern usage describes emotional excess rather than hallucination.)
//
// None of the behaviour lives here:
//   - night vision is a hidden companion vanilla effect applied by CompanionEffectManager, because
//     the brightening is hardcoded client-side against the literal MobEffects.NIGHT_VISION instance
//   - the noises and the hallucinated figure are scheduled by PsychosisEvents, which needs
//     per-player timing state a MobEffect singleton has nowhere sensible to keep
//   - the soundscape distortion is client-side, in PsychosisSoundWarp
//
// All of them key off this effect's presence rather than off the trip stage, so handing it out with
// /effect behaves exactly like the real thing.
public class PsychosisMobEffect extends MobEffect {

    // Washed-out violet.
    private static final int COLOR = 0x8A7FA8;

    public PsychosisMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
