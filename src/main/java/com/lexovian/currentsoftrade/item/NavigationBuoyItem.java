package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class NavigationBuoyItem extends PlaceOnWaterBlockItem {
    public NavigationBuoyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.navigation_buoy.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.navigation_buoy.hint"));
        super.appendHoverText(stack, context, tooltipComponents, flag);
    }
}
