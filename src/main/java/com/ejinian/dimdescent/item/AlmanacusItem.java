package com.ejinian.dimdescent.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// The Almanacus Inferni Abditi - "almanac of the hidden hell". The mod's only piece of written
// lore, found in a chest in the room of eight beds beside every altar.
//
// It never states the mechanic. Per the design rule, it foreshadows and never explains: the pages
// circle sleep, beds, and dreams that don't end, so an attentive reader arrives at the connection
// themselves. Nowhere does it say "drink the draught, then lie down".
public class AlmanacusItem extends Item {

    public AlmanacusItem(Properties properties) {
        super(properties);
    }

    // Written as a plain list rather than lang keys because each page is a whole paragraph of prose;
    // it would be a dozen keys of run-on text with no benefit until the mod is actually localised.
    public static List<Component> pages() {
        return List.of(
                lines(
                        "§4§lALMANACUS",
                        "§4§lINFERNI ABDITI§r",
                        "",
                        "§8an accounting of the",
                        "§8hidden hell, and of",
                        "§8those who went down",
                        "§8into it willingly.§r",
                        "",
                        "§8§oThe last hand to write",
                        "§8§oin this book did not",
                        "§8§oclose it."),
                lines(
                        "§0They came to the black",
                        "§0stone to be shown a way",
                        "§0down.",
                        "",
                        "§0There is no way down.",
                        "§0There is only a way",
                        "§0§lunder§r§0, and it opens",
                        "§0where every man already",
                        "§0lies each night."),
                lines(
                        "§0We laid eight beds in",
                        "§0the far room.",
                        "",
                        "§0Not for rest. Rest was",
                        "§0never the difficulty.",
                        "",
                        "§0Eight went down. The",
                        "§0beds are still made.",
                        "§0Count them yourself."),
                lines(
                        "§0The flower does the",
                        "§0opening. That much any",
                        "§0fool learns, and most",
                        "§0learn it the once.",
                        "",
                        "§0Swallow it raw and you",
                        "§0will sweat and see and",
                        "§0hear, and you will lie",
                        "§0down and §lnot go§r§0."),
                lines(
                        "§0Only the corrupted",
                        "§0draught carries a man",
                        "§0across, and only if he",
                        "§0is lying down when it",
                        "§0takes him.",
                        "",
                        "§0Standing, he merely",
                        "§0suffers. Sleeping, he",
                        "§0§larrives§r§0."),
                lines(
                        "§0Mark this, if you mark",
                        "§0nothing else:",
                        "",
                        "§0the dark is not the",
                        "§0danger. The dark is the",
                        "§0§lclock§r§0.",
                        "",
                        "§0When it closes in, the",
                        "§0draught is nearly out."),
                lines(
                        "§0Brother Ansel asked",
                        "§0what becomes of a man",
                        "§0whose draught runs dry",
                        "§0down there.",
                        "",
                        "§0Nothing becomes of him.",
                        "§0He wakes in his own bed",
                        "§0with the hours gone and",
                        "§0no mark upon him."),
                lines(
                        "§0That is the whole cruelty",
                        "§0of it.",
                        "",
                        "§0No wound. No corpse. No",
                        "§0proof.",
                        "",
                        "§0Only a man who goes to",
                        "§0bed earlier each night,",
                        "§0and is harder to wake."),
                lines(
                        "§4§oI have taken it nine",
                        "§4§otimes.",
                        "",
                        "§4§oI no longer believe the",
                        "§4§oroom with the beds is",
                        "§4§osomething we built.",
                        "",
                        "§4§oI believe we woke up in",
                        "§4§oit."));
    }

    private static Component lines(String... lines) {
        return Component.literal(String.join("\n", lines));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Split into a client-only class so the screen classes are never touched on a dedicated
            // server - this branch simply never runs there, so it never loads.
            com.ejinian.dimdescent.client.AlmanacusReader.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dimdescent.almanacus_inferni_abditi.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
