package com.ejinian.dimdescent.trip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Drives a player through a randomised course of symptoms after they eat Datura Seeds.
//
// Shape of a trip: nothing at all for 10 seconds, then Dry Mouth (always first - it's the tell that
// something is wrong), then four more symptoms drawn at random from the remaining seven, each
// separated by 20 seconds of deceptive calm. Five events total, each running for its own real
// duration rather than a debug-uniform one.
//
// This is a hand-rolled sequencer rather than a chain of vanilla effects because vanilla's own
// chaining mechanism can't express it: MobEffectInstance supports a `hiddenEffect` that surfaces
// when the outer one expires, but its `effect` Holder field is final and setDetailsFrom() only
// copies duration/amplifier/visibility - so a hidden effect can only ever be MORE of the same
// effect, never a different one (vanilla even logs a warning if the types mismatch).
@EventBusSubscriber(modid = DimDescent.MODID)
public final class DaturaTrip {

    // Nothing happens for the first 10s. Long enough that eating the seeds feels like it did
    // nothing, short enough that the connection is still obvious once it starts.
    private static final int ONSET_TICKS = 200;

    // Dead air between symptoms.
    private static final int COOLDOWN_TICKS = 400;

    // Dry Mouth, plus this many drawn at random from everything else.
    private static final int RANDOM_STAGE_COUNT = 4;

    private static final Map<UUID, Progress> ACTIVE = new HashMap<>();

    private static final class Progress {
        List<TripStage> plan;
        // -1 while still in the pre-onset delay; otherwise the index into `plan` of the current
        // (or, when cooling down, the just-finished) stage.
        int index = -1;
        int ticksLeft;
        boolean cooling;
    }

    // Eating again mid-trip restarts from the top with a freshly rolled plan.
    public static void start(ServerPlayer player) {
        Progress progress = new Progress();
        progress.plan = rollPlan(player.getRandom());
        progress.index = -1;
        progress.ticksLeft = ONSET_TICKS;
        progress.cooling = false;
        ACTIVE.put(player.getUUID(), progress);
    }

    // Dry Mouth first, then a partial Fisher-Yates shuffle over the rest to draw the others without
    // repeats. Order among the random four is itself random.
    private static List<TripStage> rollPlan(RandomSource random) {
        List<TripStage> pool = new ArrayList<>();
        for (TripStage stage : TripStage.values()) {
            if (stage != TripStage.DRY_MOUTH) {
                pool.add(stage);
            }
        }
        for (int i = 0; i < RANDOM_STAGE_COUNT; i++) {
            int swap = i + random.nextInt(pool.size() - i);
            TripStage tmp = pool.get(i);
            pool.set(i, pool.get(swap));
            pool.set(swap, tmp);
        }

        List<TripStage> plan = new ArrayList<>();
        plan.add(TripStage.DRY_MOUTH);
        plan.addAll(pool.subList(0, RANDOM_STAGE_COUNT));
        return plan;
    }

    public static boolean isTripping(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Progress progress = ACTIVE.get(player.getUUID());
        if (progress == null) {
            return;
        }

        // Mid-stage: hold the symptom in place. The trip is deliberately immune to milk (and to
        // /effect clear, and anything else that strips effects) - if the current stage's effect has
        // gone missing, it goes straight back with whatever time was left. You don't get to opt out
        // of being poisoned.
        if (progress.index >= 0 && !progress.cooling) {
            TripStage stage = progress.plan.get(progress.index);
            if (!stage.isActiveOn(player)) {
                stage.applyTo(player, progress.ticksLeft);
            }
        }

        if (--progress.ticksLeft > 0) {
            return;
        }

        // A stage just ended: rest before the next one, unless that was the last.
        if (progress.index >= 0 && !progress.cooling) {
            if (progress.index >= progress.plan.size() - 1) {
                ACTIVE.remove(player.getUUID());
                return;
            }
            progress.cooling = true;
            progress.ticksLeft = COOLDOWN_TICKS;
            return;
        }

        // Either the onset delay or a cooldown just elapsed - begin the next stage.
        progress.cooling = false;
        progress.index++;
        TripStage next = progress.plan.get(progress.index);
        progress.ticksLeft = next.durationTicks();
        next.onStart(player, progress.ticksLeft);
        next.applyTo(player, progress.ticksLeft);
    }

    // Dying ends the trip - the effects themselves are cleared by vanilla on respawn anyway, so
    // leaving the sequencer running would re-apply them to a freshly respawned player.
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACTIVE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private DaturaTrip() {
    }
}
