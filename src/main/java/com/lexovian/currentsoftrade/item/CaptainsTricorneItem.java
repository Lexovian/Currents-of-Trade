package com.lexovian.currentsoftrade.item;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

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
public class CaptainsTricorneItem extends Item implements Equipable {

    public CaptainsTricorneItem(Properties properties) {
        super(properties.stacksTo(1).durability(240));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_tricorne.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_tricorne.speed_bonus"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_tricorne.discount_bonus"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
