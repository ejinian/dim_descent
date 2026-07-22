package com.ejinian.dimdescent.dimension.door.client;

import java.io.IOException;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Cutout (not solid) so the window pixels' alpha=0 actually renders as transparent
        // instead of showing their raw (black) RGB.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModRegistry.RIFT_DOOR.get(), RenderType.cutout()));
    }
}
