package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class LuminousPearlItem extends Item {
    public LuminousPearlItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.luminous_pearl_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.luminous_pearl_hint"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}


