package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Captain's Tricorne Hat (Kaptan Şapkası).
 *
 * <p>Equipable naval headwear for seasoned sea captains.
 * When worn:
 * <ul>
 *   <li>Grants +20% sailing velocity when piloting a Sloop.</li>
 *   <li>Grants a 10% Doubloon discount on Harbor travel and cargo dispatch fees.</li>
 * </ul>
 */
public class CaptainsTricorneItem extends Item {

    public CaptainsTricorneItem(Properties properties) {
        super(properties.stacksTo(1).durability(240).equippable(EquipmentSlot.HEAD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.captains_tricorne.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.captains_tricorne.speed_bonus"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.captains_tricorne.discount_bonus"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
