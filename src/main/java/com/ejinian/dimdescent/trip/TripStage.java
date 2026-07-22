package com.ejinian.dimdescent.trip;

import com.ejinian.dimdescent.registry.ModRegistry;

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

    // Speed II + Haste II. The heartbeat surges are driven by TachycardiaEvents off the effect
    // itself, so they work identically when the effect is handed out by command.
    TACHYCARDIA(1200) {
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

    // Night vision, intermittent noises, a warped soundscape, and - usually - a figure watching you
    // partway through. None of it is applied here: the night vision is a hidden companion effect
    // owned by CompanionEffectManager, and the rest is driven by PsychosisEvents and
    // PsychosisSoundWarp off the effect's presence rather than off this stage, so it all works
    // identically when the effect is handed out by command.
    //
    // The hallucinated figure used to be a separate stage. It was folded in here because it's part
    // of this symptom rather than a distinct one, which also frees up a slot in a potion trip's
    // window - eight symptoms in three minutes left each of them barely twenty seconds.
    //
    // The 20s potion floor exists so the symptom always outlasts its own first noise; below that,
    // a Psychosis could plausibly end before anything was heard.
    PSYCHOSIS(1200, 400) {
        @Override
        void applyTo(ServerPlayer player, int durationTicks) {
            apply(player, ModRegistry.PSYCHOSIS_EFFECT, durationTicks);
        }

        @Override
        boolean isActiveOn(ServerPlayer player) {
            return player.hasEffect(ModRegistry.PSYCHOSIS_EFFECT);
        }
    };

    private static final int DEFAULT_MIN_POTION_TICKS = 200;

    private final int durationTicks;
    private final int minimumPotionTicks;

    TripStage(int durationTicks) {
        this(durationTicks, DEFAULT_MIN_POTION_TICKS);
    }

    TripStage(int durationTicks, int minimumPotionTicks) {
        this.durationTicks = durationTicks;
        this.minimumPotionTicks = minimumPotionTicks;
    }

    // Used by a raw-seed trip, where every symptom runs its natural length.
    public int durationTicks() {
        return this.durationTicks;
    }

    // The smallest slice of a potion's window this symptom may be given when the window is carved
    // up at random.
    public int minimumPotionTicks() {
        return this.minimumPotionTicks;
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
