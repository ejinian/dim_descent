package com.ejinian.dimdescent.effect;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.RiftTeleporter;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Some of the visuals we want are welded to a specific vanilla effect instance on the CLIENT and
// can't be reproduced by a custom effect at all:
//
//   - Darkness' closing-in vignette is driven by MobEffects.DARKNESS' blend factor.
//   - Night vision's brightening is GameRenderer.getNightVisionScale, which reads
//     MobEffects.NIGHT_VISION directly.
//
// So the only way to get the genuine article is to apply the genuine vanilla effect, hidden from the
// HUD (showIcon=false) and the inventory list (TripClientEvents). This class owns applying and,
// crucially, retracting those companions.
//
// Attunement -> Darkness is DIMENSION-AWARE:
//   - Outside the Null Domain (overworld or anywhere else): permanent darkness for as long as
//     Attunement lasts. You are poisoned; the world is dark to you until you cross.
//   - Inside the Null Domain: darkness only during the arrival window (first 10s after entering) and
//     the departure window (last 10s of Attunement, the warning before ejection). In between you see.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class CompanionEffectManager {

    // Both the arrival darkening and the last-10s ejection warning, inside the Null Domain.
    private static final int DOMAIN_WINDOW_TICKS = 200;

    // Kept comfortably above 200 because vanilla flickers night vision during its final 200 ticks
    // (GameRenderer.getNightVisionScale). Refreshed while Psychosis lasts, so the player never sees
    // the flicker - Psychosis's night vision should be flat and total.
    private static final int NIGHT_VISION_REFRESH_TICKS = 400;

    // Darkness is topped up while wanted. Well above DARKNESS's 22-tick blend, so keeping it above
    // this floor means the vignette never starts fading out mid-window.
    private static final int DARKNESS_APPLY_TICKS = 60;
    private static final int DARKNESS_REFRESH_BELOW = 40;

    private static final Set<UUID> OUR_DARKNESS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> OUR_NIGHT_VISION = Collections.synchronizedSet(new HashSet<>());
    // Game time at which each player last entered the Null Domain - drives the arrival window.
    private static final Map<UUID, Long> ENTERED_DOMAIN_AT = Collections.synchronizedMap(new HashMap<>());

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo() == RiftTeleporter.RIFT_LEVEL) {
            ENTERED_DOMAIN_AT.put(player.getUUID(), player.serverLevel().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();

        // --- Attunement -> Darkness (dimension-aware) ---
        MobEffectInstance attunement = player.getEffect(ModRegistry.ATTUNEMENT_EFFECT);
        if (attunement != null) {
            if (wantsDarkness(player, level, attunement)) {
                MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
                if (darkness == null || darkness.getDuration() < DARKNESS_REFRESH_BELOW) {
                    applyDarkness(player);
                }
            } else if (OUR_DARKNESS.contains(id)) {
                // In the seeing part of the Null Domain - lift our darkness.
                player.removeEffect(MobEffects.DARKNESS);
                OUR_DARKNESS.remove(id);
            }
        } else if (OUR_DARKNESS.remove(id)) {
            // Attunement ended while our darkness was still running - retract it.
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

    // Attuned and OUTSIDE the Null Domain -> always dark. Inside -> only the arrival window (first
    // 10s after entering) and the departure window (last 10s of Attunement).
    private static boolean wantsDarkness(ServerPlayer player, ServerLevel level, MobEffectInstance attunement) {
        if (!RiftTeleporter.isInRift(level)) {
            return true;
        }
        if (attunement.getDuration() <= DOMAIN_WINDOW_TICKS) {
            return true;
        }
        Long enteredAt = ENTERED_DOMAIN_AT.get(player.getUUID());
        return enteredAt != null && level.getGameTime() - enteredAt < DOMAIN_WINDOW_TICKS;
    }

    private static void applyDarkness(ServerPlayer player) {
        // showIcon=false keeps it off the HUD; TripClientEvents keeps it out of the inventory list.
        // visible=false only suppresses particles and has no bearing on the vignette itself.
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_APPLY_TICKS, 0, false, false, false));
        OUR_DARKNESS.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        OUR_DARKNESS.remove(id);
        OUR_NIGHT_VISION.remove(id);
        ENTERED_DOMAIN_AT.remove(id);
    }

    private CompanionEffectManager() {
    }
}
