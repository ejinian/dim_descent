package com.ejinian.dimdescent.dimension.door.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;

// Reuses vanilla's end-portal-style rendering trick (a POSITION-only render type whose
// fragment shader derives the swirl entirely from screen-space projection, no UV needed)
// with our own re-tinted shader in place of RenderType.endPortal().
public final class RiftPortalRenderTypes {

    // Set once, when RegisterShadersEvent's load callback fires.
    static ShaderInstance riftPortalShader;

    public static final RenderType RIFT_PORTAL = RenderType.create(
            "rift_portal",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> riftPortalShader))
                    .setTextureState(
                            RenderStateShard.MultiTextureStateShard.builder()
                                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                                    .build())
                    .createCompositeState(false));

    private RiftPortalRenderTypes() {
    }
}
