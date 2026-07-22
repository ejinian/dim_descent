package com.ejinian.dimdescent.trip;

import com.ejinian.dimdescent.entity.HallucinationGhost;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

// The ordered "trip" a player goes through after eating Datura Seeds - a rough simulation of real
// datura (jimsonweed) poisoning, which is genuinely anticholinergic-deliriant: dry mouth, racing
// heart, photophobia, and vivid hallucinations of people who aren't there.
//
// Each constant carries the duration it should EVENTUALLY have. While DaturaTrip.DEBUG_UNIFORM
// is on, every stage is clamped to the same short length so the whole chain can be watched end to
// end in under two minutes; flip that flag off (and later randomise the order) for real play.
public enum TripStage {

    // Slowness I, but named for the symptom rather than borrowing vanilla's effect.
    DRY_MOUTH(1200) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, ModRegistry.DRY_MOUTH_EFFECT, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(ModRegistry.DRY_MOUTH_EFFECT);
        }
    },

    NAUSEA(200) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, MobEffects.CONFUSION, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(MobEffects.CONFUSION);
        }
    },

    // Speed II + Haste II, plus the heartbeat that fades up and back down once at stage start.
    TACHYCARDIA(1200) {
        @Override
        void onStart(ServerPlayer player, int durationTicks) {
            player.playNotifySound(ModRegistry.HEARTBEAT_SOUND.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        }

        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, ModRegistry.TACHYCARDIA_EFFECT, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(ModRegistry.TACHYCARDIA_EFFECT);
        }
    },

    DARKNESS(200) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, MobEffects.DARKNESS, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(MobEffects.DARKNESS);
        }
    },

    POISON(200) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, MobEffects.POISON, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(MobEffects.POISON);
        }
    },

    WEAKNESS(2400) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, MobEffects.WEAKNESS, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(MobEffects.WEAKNESS);
        }
    },

    // Night vision + intermittent cave noises. The night vision itself is applied as a hidden
    // companion effect by CompanionEffectManager, not here.
    HYSTERIA(1200) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, ModRegistry.HYSTERIA_EFFECT, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(ModRegistry.HYSTERIA_EFFECT);
        }
    },

    // The finale: something is standing near you, and it is looking at you.
    HALLUCINATION(200) {
        @Override
        void onStart(ServerPlayer player, int durationTicks) {
            HallucinationGhost.spawnNear(player, durationTicks);
        }

        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            // No status effect - the ghost IS the stage.
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            // Nothing to keep topped up; the ghost manages its own lifetime.
            return true;
        }
    };

    private final int realDurationTicks;

    TripStage(int realDurationTicks) {
        this.realDurationTicks = realDurationTicks;
    }

    public int durationTicks() {
        return DaturaTrip.DEBUG_UNIFORM ? DaturaTrip.DEBUG_STAGE_TICKS : this.realDurationTicks;
    }

    // Fires once, the moment the stage begins - for one-shot things like a sound or a spawn.
    void onStart(ServerPlayer player, int durationTicks) {
    }

    // Fires at stage start AND any time isActiveOn goes false mid-stage (see DaturaTrip: the trip
    // is deliberately immune to milk, so anything that strips the effect gets it straight back).
    abstract void applyTo(ServerPlayer player, int durationTicks);

    abstract boolean isActiveOn(ServerPlayer player);

    // ambient=false, visible=false, showIcon=true: no particle swirl cluttering the screen during a
    // sequence that's already visually busy, but the effect still lists in the HUD/inventory so the
    // stage you're on is legible at a glance.
    private static void apply(ServerPlayer player, Holder<MobEffect> effect, int durationTicks) {
        player.addEffect(new MobEffectInstance(effect, durationTicks, 0, false, false, true));
    }
}
