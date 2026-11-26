package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Bulk maritime trade crate packing 9 units of a trade commodity into a single compact ship-cargo slot.
 * Can be unpacked back into 9 units in any crafting grid, or sold wholesale at high-demand harbors.
 */
public class TradeCrateItem extends Item {

    private final String id;

    public TradeCrateItem(String id, Properties properties) {
        super(properties.stacksTo(16));
        this.id = id;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade." + this.id + ".desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.trade_crate.unpack_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity." + this.id + ".market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
