package com.ejinian.dimdescent.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// An invisible marker that runs for exactly as long as a datura trip does, including the quiet
// gaps between symptoms.
//
// It exists purely so the CLIENT can know a trip is in progress. The trip sequencer is entirely
// server-side, and during a cooldown there is no symptom effect active at all - so without this,
// the client would have no way to tell "between symptoms" apart from "not tripping", and the screen
// vignette would flicker off every 20 seconds. Riding on the effect system rather than a custom
// packet means the sync is free and already handled.
//
// Never shown: applied with showIcon=false, and hidden from the inventory list by TripClientEvents.
public class DaturaTripMobEffect extends MobEffect {

    // Near-pure black. This is also what makes the Potion of the Devil's Trumpet read as pitch
    // black in the bottle - a potion's liquid colour is derived from the effects it contains.
    private static final int COLOR = 0x0A0810;

    public DaturaTripMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
