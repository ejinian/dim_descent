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

    // Eight pages, written in the register of a working grimoire (the Ars Goetia and its kin):
    // numbered clauses, flat declarative voice, no feeling and no warning. The author is a
    // practitioner setting down a procedure he considers legitimate, which is what makes it
    // unsettling - the horror is in what he records without comment, not in anything he says.
    //
    // Unlike the first draft, this IS a working instruction manual: every substance, its exact
    // effect, and the crossing itself are stated plainly. The lore carries the documentation.
    //
    // Kept to short lines (under ~20 characters) because the book page wraps at roughly that width,
    // and a wrapped line silently pushes the rest of the page out of view.
    public static List<Component> pages() {
        return List.of(
                lines(
                        "§4§lALMANACUS",
                        "§4§lINFERNI ABDITI§r",
                        "",
                        "§8Being a true",
                        "§8account of the",
                        "§8hidden hell, and",
                        "§8of its entering.",
                        "",
                        "§8Set down plainly,",
                        "§8that the work be",
                        "§8not lost."),
                lines(
                        "§4I. OF THE FLOWER§r",
                        "",
                        "§0The trumpet of the",
                        "§0devil grows in dry",
                        "§0and wasted ground:",
                        "§0savanna, desert,",
                        "§0the red lands.",
                        "",
                        "§0It roots in sand",
                        "§0as well as soil.",
                        "",
                        "§0Its seed is the",
                        "§0whole of the work."),
                lines(
                        "§4II. OF THE SEED,",
                        "§4TAKEN RAW§r",
                        "",
                        "§0Swallowed, it lies",
                        "§0quiet ten seconds.",
                        "",
                        "§0Then the drying of",
                        "§0mouth, always first,",
                        "§0and after it four",
                        "§0of the seven, each",
                        "§0in its own time.",
                        "",
                        "§0It opens nothing."),
                lines(
                        "§4III. THE SEVEN§r",
                        "",
                        "§0Dryness of mouth.",
                        "§0Turning of the gut.",
                        "§0The heart races.",
                        "§0Blindness.",
                        "§0Sickness of blood.",
                        "§0Weakness of arm.",
                        "",
                        "§0And the seventh:",
                        "§0voices, and shapes",
                        "§0that stand and look."),
                lines(
                        "§4IV. THE FIRST",
                        "§4DRAUGHT§r",
                        "",
                        "§0Set the seed in a",
                        "§0draught made awkward",
                        "§0by wart of nether.",
                        "",
                        "§0It gives all seven",
                        "§0together, in no",
                        "§0fixed order, until",
                        "§0it is spent.",
                        "",
                        "§0It opens nothing."),
                lines(
                        "§4V. THE SECOND",
                        "§4DRAUGHT§r",
                        "",
                        "§0Corrupt the first",
                        "§0with fermented eye.",
                        "",
                        "§0It puts out the",
                        "§0seven at once, and",
                        "§0sets a single dark",
                        "§0of ten seconds.",
                        "",
                        "§0This draught alone",
                        "§0carries a man."),
                lines(
                        "§4VI. OF THE CROSSING§r",
                        "",
                        "§0Wait upon night.",
                        "§0Take the second",
                        "§0draught.",
                        "§0Lie down in any bed.",
                        "",
                        "§0Sleep does not come.",
                        "§0Something else does.",
                        "",
                        "§0Under the first",
                        "§0draught he may not",
                        "§0lie down at all."),
                lines(
                        "§4VII. OF THE TERM§r",
                        "",
                        "§0The dark that comes",
                        "§0in is no danger. It",
                        "§0is the measure",
                        "§0running out.",
                        "",
                        "§0When it is spent he",
                        "§0wakes in the bed he",
                        "§0left, whole, short",
                        "§0some hours.",
                        "",
                        "§0Drink again below",
                        "§0to remain below."));
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
