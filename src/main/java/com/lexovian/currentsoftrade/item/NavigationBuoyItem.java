package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class NavigationBuoyItem extends PlaceOnWaterBlockItem {
    public NavigationBuoyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.currents_of_trade.navigation_buoy.desc"));
        adder.accept(Component.translatable("tooltip.currents_of_trade.navigation_buoy.hint"));
        super.appendHoverText(stack, context, display, adder, flag);
    }
}
