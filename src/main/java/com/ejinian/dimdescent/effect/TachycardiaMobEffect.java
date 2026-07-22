package com.ejinian.dimdescent.effect;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// Stage 3 of the datura trip: racing heart. Mechanically equivalent to Speed II + Haste II, but as
// its own named effect.
//
// The Speed II half is a straight copy of vanilla's modifier (MOVEMENT_SPEED, +0.2 per level,
// ADD_MULTIPLIED_TOTAL) doubled for level II.
//
// The Haste II half needs care: vanilla's Haste does NOT do its mining-speed boost through an
// attribute. Player.getDigSpeed hardcodes a check for the literal MobEffects.DIG_SPEED effect
// (`MobEffectUtil.hasDigSpeed`) and multiplies by 1.0 + (amplifier + 1) * 0.2 - so a custom effect
// can never trigger it, no matter what attributes it carries. What DOES work is
// Attributes.BLOCK_BREAK_SPEED, which the very next line of getDigSpeed multiplies in. Haste II is
// x1.4, so +0.4 ADD_MULTIPLIED_TOTAL on BLOCK_BREAK_SPEED reproduces it exactly. ATTACK_SPEED is
// included too because that's the one attribute vanilla Haste actually does use (+0.1/level).
public class TachycardiaMobEffect extends MobEffect {

    // Arterial red.
    private static final int COLOR = 0xB03030;

    public TachycardiaMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "effect.tachycardia.speed"),
                0.4,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(
                Attributes.BLOCK_BREAK_SPEED,
                ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "effect.tachycardia.dig"),
                0.4,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "effect.tachycardia.attack"),
                0.2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
