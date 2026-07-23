package com.ejinian.dimdescent.dimension.door.client;

import java.io.IOException;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT)
public class RiftClientEvents {

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "rendertype_rift_portal"),
                        DefaultVertexFormat.POSITION),
                shader -> RiftPortalRenderTypes.riftPortalShader = shader);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.RIFT_DOOR_BLOCK_ENTITY.get(), RiftDoorBlockEntityRenderer::new);
    }

    // Reuses vanilla's own flame behaviour (rise, shrink, flicker out) with our red sprite, so the
    // Daemonlight's flame moves exactly like a real torch flame without reimplementing it.
    // SmallFlameProvider rather than Provider: it calls scale(0.5F), which SingleQuadParticle turns
    // into a halved quadSize - the same modest flame vanilla uses for candles.
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.DAEMON_FLAME, FlameParticle.SmallFlameProvider::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Cutout (not solid) so alpha=0 pixels actually render as transparent instead of showing
        // their raw (black) RGB. New custom blocks default to solid regardless of model/texture -
        // vanilla's own flowers only look right out of the box because vanilla hardcodes them into
        // this same list on its own blocks; ours needs the same registration explicitly.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.RIFT_DOOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.DATURA.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.DARK_IRON_BARS.get(), RenderType.cutout());
            // The Daemonlight's flame billboard is alpha-cutout; without this the transparent
            // region around the flame renders as opaque black.
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.DAEMONLIGHT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.DAEMONLIGHT_WALL.get(), RenderType.cutout());
        });
    }
}
