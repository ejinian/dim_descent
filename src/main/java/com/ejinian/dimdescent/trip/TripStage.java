package com.ejinian.dimdescent.trip;

import com.ejinian.dimdescent.entity.HallucinationGhost;
import com.ejinian.dimdescent.registry.ModRegistry;
import com.ejinian.dimdescent.sound.PlayerSounds;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

// The pool of symptoms a datura trip draws from - a rough simulation of real datura (jimsonweed)
// poisoning, which is genuinely anticholinergic-deliriant: dry mouth, racing heart, photophobia,
// and vivid hallucinations of people who aren't there.
//
// DaturaTrip owns the ordering: Dry Mouth always leads, then four of the rest at random. Each
// constant carries its own duration, so a trip's length varies with what it rolled.
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

    // Speed II + Haste II. The heartbeat is a 5s swell at onset, not a minute-long loop - its
    // fade in and out are baked into the audio file rather than driven from here.
    TACHYCARDIA(1200) {
        @Override
        void onStart(ServerPlayer player, int durationTicks) {
            PlayerSounds.playPrivately(player, ModRegistry.HEARTBEAT_SOUND.get(), 0.9F, 1.0F);
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

    // Night vision plus intermittent noises. Neither is applied here: the night vision is a hidden
    // companion effect owned by CompanionEffectManager, and the noises are scheduled by
    // HysteriaSoundScheduler, which keys off the effect's presence rather than off this stage - so
    // they work identically when the effect is handed out by command.
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

    // Something is standing near you, and it is looking at you.
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

    private final int durationTicks;

    TripStage(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public int durationTicks() {
        return this.durationTicks;
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
