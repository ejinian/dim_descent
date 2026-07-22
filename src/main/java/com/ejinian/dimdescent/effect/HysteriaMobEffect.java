package com.ejinian.dimdescent.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// Seeing in the dark, and hearing things that aren't there.
//
// Neither half lives here. The night vision is a "hidden companion" vanilla effect applied by
// CompanionEffectManager, because the brightening is hardcoded client-side against the literal
// MobEffects.NIGHT_VISION instance. The noises are scheduled by HysteriaSoundScheduler, which needs
// per-player timing state that a MobEffect singleton has nowhere sensible to keep.
//
// So this is a pure marker: a name, a colour, and a category.
public class HysteriaMobEffect extends MobEffect {

    // Washed-out violet.
    private static final int COLOR = 0x8A7FA8;

    public HysteriaMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
