package com.ejinian.dimdescent.dimension;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The rift kills anyone in it who isn't attuned.
//
// This is the survival gate described in CLAUDE.md: the Potion of Attunement isn't a convenience,
// it's the only thing keeping you alive in there. Deliberately unexplained in-game for now - the
// first playtesters are meant to find out the hard way.
//
// Implemented as a per-tick presence check rather than a hook on the teleport itself, because that
// single check covers BOTH halves of the rule for free: walking in with no potion dies on the first
// tick after arrival, and a potion running out while you're still inside dies on the tick it
// expires. A teleport-time hook would only ever catch the first.
@EventBusSubscriber(modid = DimDescent.MODID)
public final class RiftLethalityEvents {

    public static final ResourceKey<DamageType> RIFT_UNATTUNED = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "rift_unattuned"));

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!RiftTeleporter.isInRift(level)) {
            return;
        }

        // Building and exploring the rift in creative has to stay possible, and a spectator isn't
        // really there. (Creative players are damage-immune anyway, so this is belt-and-braces -
        // but it also stops us spamming a doomed hurt() call every tick.)
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            return;
        }

        if (player.hasEffect(ModRegistry.ATTUNEMENT_EFFECT) || player.isDeadOrDying()) {
            return;
        }

        // Float.MAX_VALUE plus the bypasses_armor/effects/enchantments tags on the damage type:
        // nothing - not netherite, not Resistance V, not Protection IV - survives being unmade.
        player.hurt(level.damageSources().source(RIFT_UNATTUNED), Float.MAX_VALUE);
    }

    private RiftLethalityEvents() {
    }
}
