package com.ejinian.dimdescent.entity.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.entity.HallucinationGhost;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// MobRenderer, not HumanoidMobRenderer - the ghost has no equipment, so the held-item and armour
// layers would be dead weight. Shadow radius is 0: it doesn't cast one.
public class GhostRenderer extends MobRenderer<HallucinationGhost, GhostModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "textures/entity/hallucination_ghost.png");

    public GhostRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HallucinationGhost entity) {
        return TEXTURE;
    }
}
