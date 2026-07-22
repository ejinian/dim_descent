package com.ejinian.dimdescent.effect;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Some of the visuals we want are welded to a specific vanilla effect instance on the CLIENT and
// can't be reproduced by a custom effect at all:
//
//   - Darkness' closing-in vignette is driven by MobEffects.DARKNESS' blend factor.
//   - Night vision's brightening is GameRenderer.getNightVisionScale, which reads
//     MobEffects.NIGHT_VISION directly.
//
// So the only way to get the genuine article is to apply the genuine vanilla effect. To keep the
// player's status list reading as ONE effect (Attunement / Psychosis) rather than two, the companion
// is applied with showIcon=false (hides it from the HUD) and hidden from the inventory list by
// ModClientExtensions. This class owns applying and, crucially, retracting those companions.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class CompanionEffectManager {

    // Attunement dims the world for its first 10s and its last 10s - arriving and wearing off.
    private static final int ATTUNEMENT_WINDOW_TICKS = 200;

    // Kept comfortably above 200 because vanilla flickers night vision during its final 200 ticks
    // (GameRenderer.getNightVisionScale). Refreshed every tick while Psychosis lasts, so the player
    // never sees the flicker - Psychosis's night vision should be flat and total.
    private static final int NIGHT_VISION_REFRESH_TICKS = 400;

    private static final Set<UUID> OUR_DARKNESS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> OUR_NIGHT_VISION = Collections.synchronizedSet(new HashSet<>());

    // The "first 10 seconds" window. Handled on Added rather than by tracking elapsed time per tick,
    // because the effect's total duration isn't recoverable later - only what's left of it.
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getEffectInstance().is(ModRegistry.ATTUNEMENT_EFFECT)) {
            return;
        }
        int duration = Math.min(ATTUNEMENT_WINDOW_TICKS, event.getEffectInstance().getDuration());
        applyDarkness(player, duration);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();

        // --- Attunement -> Darkness (the trailing window) ---
        MobEffectInstance attunement = player.getEffect(ModRegistry.ATTUNEMENT_EFFECT);
        if (attunement != null) {
            if (attunement.getDuration() <= ATTUNEMENT_WINDOW_TICKS && !player.hasEffect(MobEffects.DARKNESS)) {
                applyDarkness(player, attunement.getDuration());
            }
        } else if (OUR_DARKNESS.remove(id)) {
            // Attunement ended while our companion darkness was still running - retract it so it
            // doesn't outlive the effect it belongs to.
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (!player.hasEffect(MobEffects.DARKNESS)) {
            OUR_DARKNESS.remove(id);
        }

        // --- Psychosis -> Night Vision ---
        if (player.hasEffect(ModRegistry.PSYCHOSIS_EFFECT)) {
            MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
            if (nightVision == null || nightVision.getDuration() < NIGHT_VISION_REFRESH_TICKS / 2) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, NIGHT_VISION_REFRESH_TICKS, 0, false, false, false));
                OUR_NIGHT_VISION.add(id);
            }
        } else if (OUR_NIGHT_VISION.remove(id)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        OUR_DARKNESS.remove(id);
        OUR_NIGHT_VISION.remove(id);
    }

    private static void applyDarkness(ServerPlayer player, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        // showIcon=false keeps it off the HUD; ModClientExtensions keeps it out of the inventory
        // list. visible=false only suppresses particles and has no bearing on the vignette itself.
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, durationTicks, 0, false, false, false));
        OUR_DARKNESS.add(player.getUUID());
    }

    private CompanionEffectManager() {
    }
}
