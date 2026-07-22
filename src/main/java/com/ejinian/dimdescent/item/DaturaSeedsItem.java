package com.ejinian.dimdescent.item;

import com.ejinian.dimdescent.trip.DaturaTrip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

// Carries the "maybe this brews into something" tooltip nudge, and - for anyone who ignores that
// and eats them instead - kicks off the datura trip. The tooltip deliberately doesn't warn that
// they're edible or poisonous; finding that out is the point.
public class DaturaSeedsItem extends Item {

    public DaturaSeedsItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        // super handles the actual eating (hunger/saturation, shrinking the stack).
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (livingEntity instanceof ServerPlayer player) {
            // Eating again mid-trip deliberately restarts the sequence from the top.
            DaturaTrip.start(player);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.dimdescent.datura_seeds.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
