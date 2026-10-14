package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SilkBaleItem extends Item {
    public SilkBaleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.silk_bale_value"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.silk_bale_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.silk_bale.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
