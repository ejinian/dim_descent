package com.ejinian.dimdescent.trip;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Drives a player through TripStage in order after they eat Datura Seeds.
//
// This is a hand-rolled sequencer rather than a chain of vanilla effects because vanilla's own
// chaining mechanism can't do it: MobEffectInstance supports a `hiddenEffect` that surfaces when the
// outer one expires, but its `effect` Holder field is final and setDetailsFrom() only copies
// duration/amplifier/visibility - so a hidden effect can only ever be MORE of the same effect, never
// a different one (vanilla even logs a warning if the types mismatch). Chaining Dry Mouth into
// Nausea into Tachycardia therefore has to be driven externally, tick by tick.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class DaturaTrip {

    // Debug switch: collapse every stage to the same short duration so the full eight-stage chain
    // can be observed end to end quickly. Turn this off to get the real per-symptom durations
    // declared on TripStage, at which point the order should also become random rather than fixed.
    public static final boolean DEBUG_UNIFORM = true;
    public static final int DEBUG_STAGE_TICKS = 200;

    private static final TripStage[] STAGES = TripStage.values();
    private static final Map<UUID, Progress> ACTIVE = new HashMap<>();

    private static final class Progress {
        int stageIndex;
        int ticksLeft;
    }

    // Eating again mid-trip restarts from the top rather than stacking or being ignored.
    public static void start(ServerPlayer player) {
        Progress progress = new Progress();
        progress.stageIndex = 0;
        progress.ticksLeft = STAGES[0].durationTicks();
        ACTIVE.put(player.getUUID(), progress);

        STAGES[0].onStart(player, progress.ticksLeft);
        STAGES[0].applyTo(player, progress.ticksLeft);
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

        TripStage stage = STAGES[progress.stageIndex];

        // The trip is immune to milk (and to /effect clear, and to anything else that strips
        // effects): if the current stage's effect has gone missing, put it straight back with
        // whatever time was left. You don't get to opt out of being poisoned.
        if (!stage.isActiveOn(player)) {
            stage.applyTo(player, progress.ticksLeft);
        }

        if (--progress.ticksLeft > 0) {
            return;
        }

        if (++progress.stageIndex >= STAGES.length) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        TripStage next = STAGES[progress.stageIndex];
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
