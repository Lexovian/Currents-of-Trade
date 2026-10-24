package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Spiral Sea Ivory (Sarmal Deniz Fildişi) — gleaming spiraled tusk harvested from frozen northern pack-ice waters.
 */
public class NarwhalIvoryItem extends Item {

    public NarwhalIvoryItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.narwhal_ivory_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.narwhal_ivory_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.narwhal_ivory.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

