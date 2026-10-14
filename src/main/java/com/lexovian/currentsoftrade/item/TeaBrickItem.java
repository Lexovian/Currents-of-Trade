package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class TeaBrickItem extends Item {
    public TeaBrickItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tea_brick.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tea_brick.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.tea_brick.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
