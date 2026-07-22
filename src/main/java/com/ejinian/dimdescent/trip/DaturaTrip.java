package com.ejinian.dimdescent.trip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
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

    // No symptom in a potion trip is allowed to be shorter than this, however the window divides.
    private static final int MIN_POTION_STAGE_TICKS = 200;

    private static final Map<UUID, Progress> ACTIVE = new HashMap<>();

    private static final class Progress {
        List<TripStage> plan;
        // Parallel to `plan`. Held separately rather than read off the stage because a potion trip
        // divides its window up at random, so the same symptom runs for different lengths each time.
        int[] durations;
        // Raw seeds leave 20s gaps between symptoms; a potion packs them back to back.
        boolean useCooldowns;
        // -1 while still in the pre-onset delay; otherwise the index into `plan` of the current
        // (or, when cooling down, the just-finished) stage.
        int index = -1;
        int ticksLeft;
        boolean cooling;
        // Whole-trip countdown, used to keep the client-facing marker effect topped up.
        int totalTicksLeft;
    }

    // Eating raw seeds: Dry Mouth then four of the rest, at their own natural durations, with dead
    // air between them.
    public static void start(ServerPlayer player) {
        List<TripStage> plan = rollSeedPlan(player.getRandom());
        int[] durations = new int[plan.size()];
        for (int i = 0; i < plan.size(); i++) {
            durations[i] = plan.get(i).durationTicks();
        }

        Progress progress = begin(player, plan, durations, true, totalTicks(plan));
        applyTripMarker(player, progress.totalTicksLeft);
    }

    // Drinking Devil's Trumpet: every symptom, in random order, carved out of the potion's own
    // duration. Stronger than raw seeds not by lasting longer but by leaving no gaps and skipping
    // nothing - the whole spectrum, back to back, for as long as the dose holds.
    //
    // The marker effect is NOT applied here: the potion itself carries it, which is both what
    // colours the bottle black and what triggers this method in the first place (see
    // onTripMarkerApplied).
    public static void startFromPotion(ServerPlayer player, int windowTicks) {
        RandomSource random = player.getRandom();

        List<TripStage> plan = new ArrayList<>(List.of(TripStage.values()));
        shuffle(plan, plan.size(), random);
        int[] durations = partition(windowTicks - ONSET_TICKS, plan.size(), random);

        begin(player, plan, durations, false, windowTicks);
    }

    private static Progress begin(ServerPlayer player, List<TripStage> plan, int[] durations,
            boolean useCooldowns, int totalTicksLeft) {
        Progress progress = new Progress();
        progress.plan = plan;
        progress.durations = durations;
        progress.useCooldowns = useCooldowns;
        progress.index = -1;
        progress.ticksLeft = ONSET_TICKS;
        progress.cooling = false;
        progress.totalTicksLeft = totalTicksLeft;
        // Dosing again mid-trip restarts from the top with a freshly rolled plan.
        ACTIVE.put(player.getUUID(), progress);
        return progress;
    }

    // Splits `total` ticks into `count` parts, each at least MIN_POTION_STAGE_TICKS, by handing every
    // part the floor and then sharing the slack out on random weights. If the window is too short to
    // give everyone the floor, it degrades to an even split rather than dropping symptoms - the
    // promise is that all eight happen.
    private static int[] partition(int total, int count, RandomSource random) {
        int[] parts = new int[count];
        if (total < count * MIN_POTION_STAGE_TICKS) {
            int even = Math.max(1, total / count);
            java.util.Arrays.fill(parts, even);
            return parts;
        }

        int slack = total - count * MIN_POTION_STAGE_TICKS;
        float[] weights = new float[count];
        float weightSum = 0.0F;
        for (int i = 0; i < count; i++) {
            // The floor keeps any one symptom from being handed essentially none of the slack.
            weights[i] = random.nextFloat() + 0.2F;
            weightSum += weights[i];
        }

        int handedOut = 0;
        for (int i = 0; i < count; i++) {
            int extra = i == count - 1 ? slack - handedOut : Math.round(slack * weights[i] / weightSum);
            handedOut += extra;
            parts[i] = MIN_POTION_STAGE_TICKS + extra;
        }
        return parts;
    }

    // Because the whole plan is rolled up front, the exact length of this trip is known now - which
    // is what lets the marker effect be applied once with a real duration instead of being refreshed
    // blindly forever.
    private static int totalTicks(List<TripStage> plan) {
        int total = ONSET_TICKS + COOLDOWN_TICKS * (plan.size() - 1);
        for (TripStage stage : plan) {
            total += stage.durationTicks();
        }
        return total;
    }

    // Set while we apply the marker ourselves, so onTripMarkerApplied can tell our own bookkeeping
    // apart from a genuine fresh dose. Without it, either the keep-alive would restart the trip on
    // a loop, or (if guarded by "is a trip already running") drinking a potion mid-trip would be
    // silently swallowed. Server logic is single-threaded, so a plain static flag is sufficient.
    private static boolean applyingOwnMarker;

    // Invisible on both the HUD (showIcon=false) and the inventory list (TripClientEvents). Its only
    // job is to let the client know a trip is running so it can draw the vignette.
    private static void applyTripMarker(ServerPlayer player, int durationTicks) {
        applyingOwnMarker = true;
        try {
            player.addEffect(new MobEffectInstance(
                    ModRegistry.DATURA_TRIP_EFFECT, durationTicks, 0, false, false, false));
        } finally {
            applyingOwnMarker = false;
        }
    }

    // Dry Mouth first, then a partial Fisher-Yates shuffle over the rest to draw the others without
    // repeats. Order among the random four is itself random.
    private static List<TripStage> rollSeedPlan(RandomSource random) {
        List<TripStage> pool = new ArrayList<>();
        for (TripStage stage : TripStage.values()) {
            if (stage != TripStage.DRY_MOUTH) {
                pool.add(stage);
            }
        }
        shuffle(pool, RANDOM_STAGE_COUNT, random);

        List<TripStage> plan = new ArrayList<>();
        plan.add(TripStage.DRY_MOUTH);
        plan.addAll(pool.subList(0, RANDOM_STAGE_COUNT));
        return plan;
    }

    // Partial Fisher-Yates: shuffles enough of `list` that the first `count` entries are a fair
    // draw. Passing list.size() shuffles the lot.
    private static <T> void shuffle(List<T> list, int count, RandomSource random) {
        for (int i = 0; i < Math.min(count, list.size() - 1); i++) {
            int swap = i + random.nextInt(list.size() - i);
            T tmp = list.get(i);
            list.set(i, list.get(swap));
            list.set(swap, tmp);
        }
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

        // The marker is subject to the same milk immunity as the symptoms themselves - otherwise a
        // bucket of milk would clear the vignette while the trip carried on underneath it.
        progress.totalTicksLeft--;
        if (progress.totalTicksLeft > 0 && !player.hasEffect(ModRegistry.DATURA_TRIP_EFFECT)) {
            applyTripMarker(player, progress.totalTicksLeft);
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

        // A stage just ended: rest before the next one, unless that was the last, or unless this
        // is a potion trip that packs them back to back.
        if (progress.index >= 0 && !progress.cooling) {
            if (progress.index >= progress.plan.size() - 1) {
                ACTIVE.remove(player.getUUID());
                return;
            }
            if (progress.useCooldowns) {
                progress.cooling = true;
                progress.ticksLeft = COOLDOWN_TICKS;
                return;
            }
        }

        // The onset delay, a cooldown, or a back-to-back handover just elapsed - begin the next stage.
        progress.cooling = false;
        progress.index++;
        TripStage next = progress.plan.get(progress.index);
        progress.ticksLeft = progress.durations[progress.index];
        next.onStart(player, progress.ticksLeft);
        next.applyTo(player, progress.ticksLeft);
    }

    // Starting point for a potion trip.
    //
    // Hooking the marker effect being applied - rather than the act of drinking - means splash and
    // lingering Devil's Trumpet work for free: they apply the effect to everyone they touch, and
    // each of those players starts their own independently rolled trip. Anything that isn't our own
    // bookkeeping is by definition a fresh dose, including a second potion drunk mid-trip, which
    // restarts with a newly rolled plan exactly as eating more seeds does.
    @SubscribeEvent
    public static void onTripMarkerApplied(MobEffectEvent.Added event) {
        if (applyingOwnMarker || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getEffectInstance().is(ModRegistry.DATURA_TRIP_EFFECT)) {
            return;
        }
        startFromPotion(player, event.getEffectInstance().getDuration());
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
        if (ACTIVE.remove(event.getEntity().getUUID()) != null
                && event.getEntity() instanceof ServerPlayer player) {
            // The marker outlives the sequencer otherwise, and the player would log back in to a
            // vignette with no trip behind it.
            player.removeEffect(ModRegistry.DATURA_TRIP_EFFECT);
        }
    }

    private DaturaTrip() {
    }
}
