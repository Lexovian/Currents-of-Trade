package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Spice Sack — trade commodity and consumable stimulant.
 *
 * <p>Consuming the sack applies Speed II and Haste I for a configurable duration
 * (default 45 seconds / 900 ticks). Plays a consuming sound and can be eaten
 * even when the player's hunger bar is full.
 */
public class SpiceSackItem extends Item {

    public SpiceSackItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof Player player) {
            int duration = Config.SPICE_SACK_DURATION_TICKS.get();
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, duration, 0, false, true, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F,
                    level.getRandom().nextFloat() * 0.1F + 0.9F);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.spice_sack_effect"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.spice_sack_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.spice_sack.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
