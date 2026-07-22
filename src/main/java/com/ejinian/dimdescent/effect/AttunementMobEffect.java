package com.ejinian.dimdescent.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// MobEffect's own constructor is protected (package-private-ish) - vanilla's MobEffects registry
// can call it directly because it lives in the same package; a mod needs this thin subclass to
// get a public constructor.
public class AttunementMobEffect extends MobEffect {

    // Grey, matching the Potion of Attunement's colored liquid (a potion's bottle tint is derived
    // from its contained effects' colors).
    private static final int COLOR = 0x808080;

    public AttunementMobEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
