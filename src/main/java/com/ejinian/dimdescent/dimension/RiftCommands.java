package com.ejinian.dimdescent.dimension;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.ejinian.dimdescent.DimDescent;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// Temporary test harness for entering the rift dimension: /rift enter, /rift leave.
// Will be superseded by the rift door once it's wired up, but kept around for quick debugging.
@EventBusSubscriber(modid = DimDescent.MODID)
public class RiftCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rift")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter").executes(RiftCommands::enterRift))
                .then(Commands.literal("leave").executes(RiftCommands::leaveRift)));
    }

    private static int enterRift(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel riftLevel = context.getSource().getServer().getLevel(RiftTeleporter.RIFT_LEVEL);
        if (riftLevel == null) {
            context.getSource().sendFailure(Component.literal("Rift dimension failed to load."));
            return 0;
        }

        DimensionTransition transition = RiftTeleporter.getTransitionFor(player.serverLevel(), player);
        if (transition == null) {
            context.getSource().sendFailure(Component.literal("Rift dimension failed to load."));
            return 0;
        }

        player.changeDimension(transition);
        return 1;
    }

    private static int leaveRift(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        DimensionTransition transition = RiftTeleporter.getTransitionFor(player.serverLevel(), player);
        if (transition == null) {
            context.getSource().sendFailure(Component.literal("Could not find the target dimension."));
            return 0;
        }

        player.changeDimension(transition);
        return 1;
    }
}
