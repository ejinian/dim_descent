package com.ejinian.dimdescent.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

// Plays a sound that exists only for one player and travels with them.
//
// The obvious call, ServerPlayer.playNotifySound, sends a ClientboundSoundPacket with fixed world
// coordinates baked in - so the sound stays pinned to wherever the player was standing and falls
// behind as they walk away. ClientboundSoundEntityPacket instead makes the client build an
// EntityBoundSoundInstance that re-reads the entity's position every tick. Bound to the player
// themselves, that puts the sound permanently at the listener's own position: centred, effectively
// non-directional, and inescapable. Sending it to that single connection is what keeps it private -
// nobody standing next to them hears anything.
//
// This is the delivery mechanism for every sound in the datura trip: they're all meant to be inside
// the player's head, not events in the world.
public final class PlayerSounds {

    public static void playPrivately(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundEntityPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS,
                player,
                volume,
                pitch,
                player.getRandom().nextLong()));
    }

    private PlayerSounds() {
    }
}
