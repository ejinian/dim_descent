package com.ejinian.dimdescent;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.ejinian.dimdescent.registry.ModRegistry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// RiftCommands, RiftClientEvents, AttunementBrewingEvents, DaturaTrip, CompanionEffectManager,
// TripClientEvents, and ScreenSaturationEffect register themselves on the event bus via
// @EventBusSubscriber.
@Mod(DimDescent.MODID)
public class DimDescent {
    public static final String MODID = "dimdescent";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DimDescent(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModRegistry.MOB_EFFECTS.register(modEventBus);
        ModRegistry.POTIONS.register(modEventBus);
        ModRegistry.SOUND_EVENTS.register(modEventBus);
        ModRegistry.ENTITY_TYPES.register(modEventBus);
        ModRegistry.PARTICLE_TYPES.register(modEventBus);
        modEventBus.addListener(ModRegistry::addCreativeItems);
        modEventBus.addListener(ModRegistry::registerEntityAttributes);
    }
}
