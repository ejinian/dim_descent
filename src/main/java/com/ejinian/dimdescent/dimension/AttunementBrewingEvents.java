package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = DimDescent.MODID)
public class AttunementBrewingEvents {

    // Two steps, not one. Datura Seeds get you the weak poisoning; corrupting THAT is what gets you
    // Attunement. Fermented Spider Eye is vanilla's established "invert/corrupt this potion" reagent,
    // which is exactly what the step is narratively.
    //
    // Splash and Lingering variants need no registration here - vanilla's container recipes
    // (POTION + gunpowder -> SPLASH_POTION, SPLASH + dragon's breath -> LINGERING) are generic over
    // every registered potion.
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(
                net.minecraft.world.item.alchemy.Potions.AWKWARD,
                ModRegistry.DATURA_SEEDS.get(),
                ModRegistry.POTION_OF_DEVILS_TRUMPET);
        event.getBuilder().addMix(
                ModRegistry.POTION_OF_DEVILS_TRUMPET,
                Items.REDSTONE,
                ModRegistry.LONG_POTION_OF_DEVILS_TRUMPET);

        event.getBuilder().addMix(
                ModRegistry.POTION_OF_DEVILS_TRUMPET,
                Items.FERMENTED_SPIDER_EYE,
                ModRegistry.POTION_OF_ATTUNEMENT);
        // Corrupting an already-extended dose keeps the extension, so the two upgrade paths commute.
        event.getBuilder().addMix(
                ModRegistry.LONG_POTION_OF_DEVILS_TRUMPET,
                Items.FERMENTED_SPIDER_EYE,
                ModRegistry.LONG_POTION_OF_ATTUNEMENT);
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

        // A real (visual-only) LightningBolt entity spawned right above each player, rather than
        // a manually-sent sound packet - vanilla's own LightningBolt already bundles the sky flash
        // (Level.setSkyFlashTime, client-side) with the thunder + impact sound (also client-side,
        // played once the entity is synced), so spawning one per player gets both for free and
        // better-synced than faking either separately. visualOnly=true skips fire-starting and
        // entity damage (LightningBolt.tick) - it still powers nearby lightning rods/cleans
        // weathered copper as a server-side side effect, but that's harmless out in open air.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel playerLevel = player.serverLevel();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(playerLevel);
            if (bolt == null) {
                continue;
            }
            BlockPos abovePlayer = player.blockPosition().above(40);
            int y = Math.min(abovePlayer.getY(), playerLevel.getMaxBuildHeight() - 1);
            bolt.moveTo(Vec3.atBottomCenterOf(new BlockPos(abovePlayer.getX(), y, abovePlayer.getZ())));
            bolt.setVisualOnly(true);
            playerLevel.addFreshEntity(bolt);
        }
    }

    private AttunementBrewingEvents() {
    }
}
