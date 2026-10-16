package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class VanillaBundleItem extends Item {
    public VanillaBundleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.vanilla_bundle.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
