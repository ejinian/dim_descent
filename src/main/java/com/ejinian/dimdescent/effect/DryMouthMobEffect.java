package com.ejinian.dimdescent.effect;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// Stage 1 of the datura trip. Physically it IS vanilla Slowness I - same attribute, same -0.15
// ADD_MULTIPLIED_TOTAL modifier vanilla's MobEffects.MOVEMENT_SLOWDOWN uses - but registered as its
// own effect so it reads as "Dry Mouth" in the sidebar rather than borrowing vanilla's name/icon.
// The screen desaturation half of this effect is client-side and lives in ScreenSaturationEffect,
// which just watches for this effect being active on the local player.
public class DryMouthMobEffect extends MobEffect {

    // Dry, bloodless tan - the potion-swirl/particle tint.
    private static final int COLOR = 0xC2B280;

    public DryMouthMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "effect.dry_mouth"),
                -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
