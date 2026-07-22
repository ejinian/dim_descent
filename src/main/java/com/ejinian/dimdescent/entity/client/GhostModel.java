package com.ejinian.dimdescent.entity.client;

import com.ejinian.dimdescent.entity.HallucinationGhost;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

// Deliberately a plain HumanoidModel rather than ZombieModel, for two reasons:
//
// 1. Arms. AbstractZombieModel.setupAnim unconditionally runs AnimationUtils.animateZombieArms,
//    which is what sticks a zombie's arms straight out in front of it. Plain HumanoidModel gives the
//    normal humanoid rest pose, so the ghost's arms simply hang at its sides - which is what makes
//    it read as a person standing there rather than a monster lunging.
//
// 2. Transparency. The render type comes from the model's constructor, and HumanoidModel is the
//    level of the hierarchy that exposes it - entityTranslucent honours the alpha baked into the
//    texture, where the default entityCutoutNoCull would snap every pixel to fully on or fully off.
//
// The geometry is still literally vanilla's zombie: the renderer bakes ModelLayers.ZOMBIE into it,
// so the silhouette matches a zombie exactly without redefining a single cube.
public class GhostModel extends HumanoidModel<HallucinationGhost> {

    public GhostModel(ModelPart root) {
        super(root, RenderType::entityTranslucent);
    }
}
