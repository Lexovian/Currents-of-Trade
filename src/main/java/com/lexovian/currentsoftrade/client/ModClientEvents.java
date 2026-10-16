package com.lexovian.currentsoftrade.client;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.item.SaltPouchItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Client-side event listeners: appends salted status and bonus details to food tooltips.
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (SaltPouchItem.isSalted(stack)) {
            List<Component> tooltip = event.getToolTip();
            int insertIndex = Math.min(1, tooltip.size());
            tooltip.add(insertIndex, Component.translatable("tooltip.currents_of_trade.salted_food"));
            tooltip.add(insertIndex + 1, Component.translatable("tooltip.currents_of_trade.salted_food_bonus"));
        }
    }
}
