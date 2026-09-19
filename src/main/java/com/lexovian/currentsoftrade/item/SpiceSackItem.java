package com.lexovian.currentsoftrade.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Spice Sack - Consumable spice providing a short burst of Speed II and Haste I.
 */
public class SpiceSackItem extends Item {

    private static final int EFFECT_DURATION = 240; // 12 seconds


    public SpiceSackItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            int duration = (com.lexovian.currentsoftrade.Config.SPICE_SACK_DURATION_TICKS != null)
                    ? com.lexovian.currentsoftrade.Config.SPICE_SACK_DURATION_TICKS.get() : EFFECT_DURATION;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, 0, false, true, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.spice_sack_effect"));
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.spice_sack_hint"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
