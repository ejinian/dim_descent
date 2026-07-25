package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.client.particle.FlameParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// Client-side registrations that are not tied to any one feature.
//
// This exists because these registrations previously lived in the Rift Door's client class, and were
// deleted along with it when the door was retired - silently breaking the Daemonlight's flame and the
// cutout rendering of every transparent block in the mod. Anything registered here must be safe from
// that: it belongs to the mod, not to a feature that might later be removed.
//
// Note that render layers are deliberately NOT set here any more. They are declared as
// "render_type": "minecraft:cutout" in the block models themselves, so the declaration lives beside
// the texture it describes, cannot be deleted with unrelated Java, and is checked by
// AssetInvariantsTest.
@EventBusSubscriber(modid = DimDescent.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetupEvents {

    // Reuses vanilla's own flame behaviour (rise, shrink, flicker out) with our red sprite, so the
    // Daemonlight's flame moves exactly like a real torch flame without reimplementing it.
    // SmallFlameProvider rather than Provider: it calls scale(0.5F), which SingleQuadParticle turns
    // into a halved quadSize - the same modest flame vanilla uses for candles.
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.DAEMON_FLAME, FlameParticle.SmallFlameProvider::new);
    }

    private ClientSetupEvents() {
    }
}
