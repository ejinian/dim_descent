package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = DimDescent.MODID)
public class AttunementBrewingEvents {

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(
                net.minecraft.world.item.alchemy.Potions.AWKWARD,
                ModRegistry.DATURA_SEEDS.get(),
                ModRegistry.POTION_OF_ATTUNEMENT);
        event.getBuilder().addMix(
                ModRegistry.POTION_OF_ATTUNEMENT,
                Items.REDSTONE,
                ModRegistry.LONG_POTION_OF_ATTUNEMENT);
    }

    // The very first time a Potion of Attunement is ever completed in a world, crack a
    // server-wide thunderclap the instant it finishes - a one-off jump-scare tied to the exact
    // moment players cross the "we can survive the rift now" threshold. Never fires again after
    // (see FirstAttunementBrewData).
    @SubscribeEvent
    public static void onPotionBrewed(PotionBrewEvent.Post event) {
        boolean brewedAttunement = false;
        for (int i = 0; i < event.getLength(); i++) {
            ItemStack stack = event.getItem(i);
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()
                    && (contents.potion().get().value() == ModRegistry.POTION_OF_ATTUNEMENT.get()
                            || contents.potion().get().value() == ModRegistry.LONG_POTION_OF_ATTUNEMENT.get())) {
                brewedAttunement = true;
                break;
            }
        }
        if (!brewedAttunement) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (!FirstAttunementBrewData.get(overworld).tryMarkFirstBrew()) {
            return;
        }

        // clearTime=0, weatherTime=6000 (5 min), raining+thundering - an immediate storm.
        overworld.setWeatherParameters(0, 6000, true, true);

        // Volume/pitch match vanilla's own LightningBolt thunderclap exactly (LightningBolt.java).
        var thunderSound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.LIGHTNING_BOLT_THUNDER);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            float pitch = 0.8F + player.level().getRandom().nextFloat() * 0.2F;
            player.connection.send(new ClientboundSoundPacket(
                    thunderSound, SoundSource.WEATHER,
                    player.getX(), player.getY(), player.getZ(),
                    10000.0F, pitch, player.level().getRandom().nextLong()));
        }
    }

    private AttunementBrewingEvents() {
    }
}
