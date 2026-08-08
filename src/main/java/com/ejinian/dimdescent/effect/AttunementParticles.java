package com.ejinian.dimdescent.effect;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;

// Attunement emits no potion particles, ever.
//
// The swirling motes are the one part of the effect that is visible to OTHER players, and the mod's
// whole premise is that a bystander sees somebody lie down in a bed and then behave as though they
// had gone somewhere - not somebody visibly enchanted. A grey haze around the player narrates the
// drug from the outside, which is exactly the confirmation the allegory refuses to give.
//
// This is done at LivingEntity.updateSynchronizedMobEffectParticles' NeoForge hook rather than by
// passing visible=false when the potion's MobEffectInstance is built, because the instance is
// constructed in several places that are easy to miss and impossible to reach: the two Potion
// registrations, their splash and lingering variants derived from those, and `/effect give`, which
// is how this gets tested. Suppressing at the point of USE covers every source at once and cannot
// be forgotten by a future caller - "by default" in the real sense.
//
// Only the particles go. The HUD icon and the inventory entry stay: the player still needs to see
// how long they have left, since the last 10 seconds of darkness are the warning that the trip is
// about to end and eject them.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class AttunementParticles {

    private AttunementParticles() {
    }

    @SubscribeEvent
    public static void suppressAttunementParticles(EffectParticleModificationEvent event) {
        if (event.getEffect().is(ModRegistry.ATTUNEMENT_EFFECT)) {
            event.setVisible(false);
        }
    }
}
