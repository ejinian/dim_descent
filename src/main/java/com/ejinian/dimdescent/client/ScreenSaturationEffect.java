package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.RiftTeleporter;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Drains the colour out of the world while certain effects are active - the "everything looks washed
// out" half of Dry Mouth, Tachycardia, and Attunement's arriving/wearing-off windows.
//
// Implemented as a full-screen post-processing chain. Worth knowing why it looks the way it does:
// Minecraft 1.21.1 no longer ships a `shaders/post/desaturate.json` chain (the old "super secret
// settings" chains were culled), but it DOES still ship the underlying `color_convolve` PROGRAM,
// whose fragment shader ends with exactly the saturation maths we want:
//
//     float Luma = dot(OutColor, Gray);
//     vec3 Chroma = OutColor - Luma;
//     OutColor = (Chroma * Saturation) + Luma;
//
// So our own chain JSON just references that program by name and drives its `Saturation` uniform -
// no shader source of Mojang's is copied into this mod, only referenced. 1.0 is untouched, 0.0 is
// fully greyscale.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class ScreenSaturationEffect {

    private static final ResourceLocation DESATURATE_CHAIN =
            ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "shaders/post/desaturate.json");

    // How washed out things get. Not 0 (fully grey reads as a bug, not a symptom) - just enough
    // that the world looks drained and sickly.
    private static final float AFFLICTED_SATURATION = 0.25F;

    // Per-tick lerp factor; ~0.5s to fade in or out at 20 ticks/sec.
    private static final float FADE_SPEED = 0.12F;

    // Close enough to 1.0 that the chain can be torn down entirely rather than run for nothing.
    private static final float NEUTRAL_EPSILON = 0.005F;

    private static float saturation = 1.0F;
    private static boolean chainLoaded;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        float target = 1.0F;
        if (player != null && isAfflicted(player)) {
            target = AFFLICTED_SATURATION;
        }
        saturation = Mth.lerp(FADE_SPEED, saturation, target);

        if (saturation >= 1.0F - NEUTRAL_EPSILON) {
            saturation = 1.0F;
            unload(minecraft);
            return;
        }

        if (!chainLoaded) {
            minecraft.gameRenderer.loadEffect(DESATURATE_CHAIN);
            chainLoaded = true;
        }

        PostChain chain = minecraft.gameRenderer.currentEffect();
        if (chain != null) {
            // Harmless if some other chain somehow ended up loaded - safeGetUniform no-ops on a
            // uniform the chain doesn't declare.
            chain.setUniform("Saturation", saturation);
        }
    }

    private static boolean isAfflicted(LocalPlayer player) {
        if (player.hasEffect(ModRegistry.DRY_MOUTH_EFFECT) || player.hasEffect(ModRegistry.TACHYCARDIA_EFFECT)) {
            return true;
        }
        // Attunement desaturates only during the Null Domain's darkness windows (arrival + the
        // departure warning), NOT during the permanent overworld darkness - draining the overworld
        // of colour for minutes on end would be miserable. Gated on being in the rift dimension.
        var level = Minecraft.getInstance().level;
        boolean inDomain = level != null && level.dimension() == RiftTeleporter.RIFT_LEVEL;
        return inDomain && player.hasEffect(ModRegistry.ATTUNEMENT_EFFECT) && player.hasEffect(MobEffects.DARKNESS);
    }

    private static void unload(Minecraft minecraft) {
        if (!chainLoaded) {
            return;
        }
        chainLoaded = false;
        // Only tear down a chain that's actually ours - vanilla uses this same single post-effect
        // slot for spectating a creeper/spider/enderman, and stomping that would be rude.
        PostChain chain = minecraft.gameRenderer.currentEffect();
        if (chain != null && chain.getName().equals(DESATURATE_CHAIN.toString())) {
            minecraft.gameRenderer.shutdownEffect();
        }
    }

    private ScreenSaturationEffect() {
    }
}
