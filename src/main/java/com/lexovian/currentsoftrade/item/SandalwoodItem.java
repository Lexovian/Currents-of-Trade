package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SandalwoodItem extends Item {
    public SandalwoodItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.sandalwood.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.sandalwood.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.sandalwood.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
