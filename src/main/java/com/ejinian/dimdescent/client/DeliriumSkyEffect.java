package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Delirium turns the world blood-dark: the sky goes a deep, lightless red and the air closes in.
//
// The sky dome's own colour comes from ClientLevel.getSkyColor, which there is no event for - but the
// dome is geometry drawn at radius 100 and it IS fog-shaded, so pulling the fog plane inside that
// radius paints the entire sky with the fog colour. That gives a solid red sky with no mixin.
//
// The two fog passes are treated differently on purpose. FOG_SKY is pulled right in, so the sky is
// fully and evenly red rather than a gradient. FOG_TERRAIN is only brought partway, so the player can
// still see far enough to move around - a red haze that closes the horizon, not blindness. Turning
// both down equally reads as "someone put a red bucket on my head" and you lose the sky entirely.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class DeliriumSkyEffect {

    // Deep arterial red, kept dark so it reads as dread rather than a lava-level glare.
    private static final float RED = 0.30F;
    private static final float GREEN = 0.02F;
    private static final float BLUE = 0.03F;

    // Inside the sky dome's radius of 100, so the dome is entirely fogged out.
    private static final float SKY_FOG_FAR = 48.0F;

    // How far the terrain haze closes in, in blocks. Deliberately still playable.
    private static final float TERRAIN_FOG_FAR = 96.0F;

    // Per-tick lerp; ~1.5s to come on or fade off, so it creeps rather than snaps.
    private static final float FADE_SPEED = 0.035F;

    private static final float EPSILON = 0.002F;

    private static float intensity;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        float target = player != null && player.hasEffect(ModRegistry.DELIRIUM_EFFECT) ? 1.0F : 0.0F;
        intensity = Mth.lerp(FADE_SPEED, intensity, target);
        if (intensity < EPSILON) {
            intensity = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (intensity <= 0.0F) {
            return;
        }
        event.setRed(Mth.lerp(intensity, event.getRed(), RED));
        event.setGreen(Mth.lerp(intensity, event.getGreen(), GREEN));
        event.setBlue(Mth.lerp(intensity, event.getBlue(), BLUE));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (intensity <= 0.0F) {
            return;
        }
        boolean sky = event.getMode() == FogRenderer.FogMode.FOG_SKY;
        float target = sky ? SKY_FOG_FAR : TERRAIN_FOG_FAR;
        // Never push the fog OUT, only in - otherwise this would fight underwater/lava/blindness fog,
        // which is already much tighter than either target.
        float far = Math.min(event.getFarPlaneDistance(), Mth.lerp(intensity, event.getFarPlaneDistance(), target));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.15F));
        // Required: RenderFog only applies changed plane distances if the event is cancelled.
        event.setCanceled(true);
    }

    private DeliriumSkyEffect() {
    }
}
