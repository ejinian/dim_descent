package com.ejinian.dimdescent.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

// Stage 7 of the datura trip: seeing in the dark, and hearing things that aren't there.
//
// The night-vision half is NOT implemented here - it's a "hidden companion" effect applied by
// CompanionEffectManager, because the night-vision brightening is hardcoded client-side against the
// literal MobEffects.NIGHT_VISION instance (GameRenderer.getNightVisionScale), so the only way to
// get the real visual is to apply the real vanilla effect and then hide it from the HUD/inventory.
//
// The noises are here, though, since they're self-contained: vanilla's own cave ambience, fired at
// random. Playing it via ServerPlayer.playNotifySound (rather than level.playSound) means it
// arrives non-positional and only for the afflicted player - nobody standing next to them hears it.
public class HysteriaMobEffect extends MobEffect {

    // Washed-out violet.
    private static final int COLOR = 0x8A7FA8;

    // Roughly one noise every ~3.5s on average. Deliberately irregular rather than on a timer.
    private static final int SOUND_CHANCE_PER_TICK = 70;

    public HysteriaMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Default only ticks on an interval; we want a chance to roll every tick.
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity instanceof ServerPlayer player
                && player.getRandom().nextInt(SOUND_CHANCE_PER_TICK) == 0) {
            player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F,
                    0.8F + player.getRandom().nextFloat() * 0.4F);
        }
        return true;
    }
}
