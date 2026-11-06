package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Damascus Steel Ingot (Şam Çeliği Külçesi) — watered crucible steel prized by captains and bladesmiths.
 * Right-clicking with a damaged tool or weapon in the off-hand restores 25% of its maximum durability.
 */
public class DamascusSteelItem extends Item {

    public DamascusSteelItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack offhand = player.getOffhandItem();
        if (hand == InteractionHand.MAIN_HAND && offhand.isDamageableItem() && offhand.isDamaged()) {
            if (!level.isClientSide()) {
                int repair = Math.max(1, offhand.getMaxDamage() / 4);
                offhand.setDamageValue(Math.max(0, offhand.getDamageValue() - repair));
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.25F);
                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.damascus_steel_desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.damascus_steel_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.damascus_steel.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}

