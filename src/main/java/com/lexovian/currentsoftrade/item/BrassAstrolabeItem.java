package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class BrassAstrolabeItem extends Item {
    public BrassAstrolabeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.brass_astrolabe_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.brass_astrolabe_hint"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

