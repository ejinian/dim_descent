package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// Black fractal cracks creeping in from the corners of the screen for the length of a datura trip -
// bloodshot eyes, in the wrong colour.
//
// Mechanically this is the same trick vanilla uses for the powder-snow frost overlay: one
// full-screen texture blitted at a varying alpha (Gui.renderTextureOverlay). The GL state dance
// below is copied from that method's shape - depth test off and depth writes masked, so the overlay
// can't be occluded by anything, then restored so we don't leak state into the rest of the HUD.
//
// Drawn on RenderGuiEvent.Pre so the hotbar, hearts and chat all sit on top of it, exactly as the
// frost overlay does.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public final class TripVignetteRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "textures/misc/trip_vignette.png");

    // 8 seconds to creep in, and the same 8 to recede. Stepped linearly rather than lerped, so the
    // two directions are literally the same speed rather than merely similar.
    private static final int FADE_TICKS = 160;

    // Held below 1 so the corners never go fully opaque - it should crowd the edges of vision, not
    // black them out.
    private static final float MAX_ALPHA = 0.85F;

    private static float intensity;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        float target = player != null && player.hasEffect(ModRegistry.DATURA_TRIP_EFFECT) ? 1.0F : 0.0F;
        float step = 1.0F / FADE_TICKS;

        if (intensity < target) {
            intensity = Math.min(target, intensity + step);
        } else if (intensity > target) {
            intensity = Math.max(target, intensity - step);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (intensity <= 0.001F) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, intensity * MAX_ALPHA);
        guiGraphics.blit(TEXTURE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private TripVignetteRenderer() {
    }
}
