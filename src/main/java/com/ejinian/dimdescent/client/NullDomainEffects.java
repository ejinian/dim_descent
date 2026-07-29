package com.ejinian.dimdescent.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

// How the Null Domain renders: no sky at all, and nothing but black behind the geometry.
//
// The dimension_type's `effects` field points here (`dimdescent:rift`) instead of at
// `minecraft:the_end`, which was drawing the End's purple starfield through any gap in a room - far
// too pretty, and it read as "somewhere", when the whole point is that there is nowhere outside the
// room you are standing in.
//
// SkyType.NONE means the sky dome is never drawn, so what shows through is the clear/fog colour -
// which getBrightnessDependentFogColor pins to pure black. The result is a void that does not
// resolve into anything no matter how long you look at it. (DeliriumSkyEffect still overrides this
// while Delirium is active; that's deliberate - the red sky is the symptom, black is the baseline.)
//
// constantAmbientLight + forceBrightLightmap, together with "ambient_light": 1.0 in the
// dimension_type, are what make light meaningless here: every block renders at full brightness
// whether or not anything is lighting it, exactly as Dimensional Doors' pockets do. Torches become
// decoration rather than a tool, which is the intent - you can never light your way out.
public class NullDomainEffects extends DimensionSpecialEffects {

    public NullDomainEffects() {
        // cloudLevel NaN = no clouds; hasGround false = no horizon haze; SkyType.NONE = draw no sky;
        // forceBrightLightmap + constantAmbientLight = a flat, maximum, unchanging light level.
        super(Float.NaN, false, SkyType.NONE, true, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
