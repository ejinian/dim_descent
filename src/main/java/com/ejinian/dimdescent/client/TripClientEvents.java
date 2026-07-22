package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.entity.client.GhostRenderer;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class TripClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.HALLUCINATION_GHOST.get(), GhostRenderer::new);
    }

    // Attunement and Hysteria each apply a real vanilla effect under the hood to borrow its
    // client-side visual (Darkness' vignette, night vision's brightening) - see
    // CompanionEffectManager. showIcon=false already keeps those off the HUD, but the inventory
    // screen lists every active effect regardless of showIcon, which would give away the trick by
    // showing "Darkness" sitting next to "Attunement". These extensions suppress that, so the player
    // sees exactly one effect with one name, as intended.
    //
    // Deliberately conditional rather than blanket: a Warden's Darkness or a drunk-on-potions night
    // vision still displays normally. The known cost is that if you get Warden'd WHILE attuned, that
    // darkness is hidden too - acceptable, and preferable to leaking the companion.
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(hiddenWhile(ModRegistry.ATTUNEMENT_EFFECT), MobEffects.DARKNESS.value());
        event.registerMobEffect(hiddenWhile(ModRegistry.HYSTERIA_EFFECT), MobEffects.NIGHT_VISION.value());

        // The trip marker is never meant to be seen at all - it's a client-side signal, not a
        // symptom. Without this it would sit in the inventory list for the entire trip, spoiling
        // both the surprise and the illusion that the symptoms are unrelated events.
        event.registerMobEffect(ALWAYS_HIDDEN, ModRegistry.DATURA_TRIP_EFFECT.get());
    }

    private static final IClientMobEffectExtensions ALWAYS_HIDDEN = new IClientMobEffectExtensions() {
        @Override
        public boolean isVisibleInInventory(MobEffectInstance instance) {
            return false;
        }

        @Override
        public boolean isVisibleInGui(MobEffectInstance instance) {
            return false;
        }
    };

    private static IClientMobEffectExtensions hiddenWhile(Holder<MobEffect> owner) {
        return new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) {
                return !localPlayerHas(owner);
            }

            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) {
                return !localPlayerHas(owner);
            }
        };
    }

    private static boolean localPlayerHas(Holder<MobEffect> effect) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(effect);
    }

    private TripClientEvents() {
    }
}
