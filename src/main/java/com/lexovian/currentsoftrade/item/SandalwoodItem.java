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
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sandalwood.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sandalwood.lore"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.sandalwood.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
