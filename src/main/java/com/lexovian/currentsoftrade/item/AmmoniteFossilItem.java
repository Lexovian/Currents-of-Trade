package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class AmmoniteFossilItem extends Item {
    public AmmoniteFossilItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ammonite_fossil.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ammonite_fossil.lore"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.ammonite_fossil.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
