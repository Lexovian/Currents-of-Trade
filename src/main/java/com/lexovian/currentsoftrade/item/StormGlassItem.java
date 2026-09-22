package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class StormGlassItem extends Item {

    public StormGlassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        level.playSound(
                player,
                player.blockPosition(),
                SoundEvents.GLASS_HIT,
                SoundSource.PLAYERS,
                0.8F,
                1.3F
        );

        if (!level.isClientSide) {
            Component forecast;
            if (level.isThundering()) {
                forecast = Component.translatable("message.currents_of_trade.storm_glass_thunder");
            } else if (level.isRaining()) {
                forecast = Component.translatable("message.currents_of_trade.storm_glass_rain");
            } else {
                forecast = Component.translatable("message.currents_of_trade.storm_glass_clear");
            }
            player.displayClientMessage(forecast, true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("tooltip.currents_of_trade.storm_glass_use_hint"));
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }
}
