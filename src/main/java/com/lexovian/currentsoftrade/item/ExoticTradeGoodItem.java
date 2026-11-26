package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Exotic regional maritime trade commodity with localized description, lore, and regional market tooltip.
 */
public class ExoticTradeGoodItem extends Item {

    private final String id;

    public ExoticTradeGoodItem(String id, Properties properties) {
        super(properties.stacksTo(16));
        this.id = id;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade." + this.id + ".desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade." + this.id + ".lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity." + this.id + ".market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
