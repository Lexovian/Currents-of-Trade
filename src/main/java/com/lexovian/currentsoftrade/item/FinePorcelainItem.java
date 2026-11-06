package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FinePorcelainItem extends Item {
    public FinePorcelainItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.fine_porcelain_desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.fine_porcelain_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.fine_porcelain.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
