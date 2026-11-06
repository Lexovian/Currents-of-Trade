package com.lexovian.currentsoftrade.item;

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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Crimson Saffron (Kızıl Safran Tutamı) — precious desert crocus threads worth their weight in gold.
 * Consuming a pinch warms the blood, granting Fire Resistance I (45s) and Absorption I (30s).
 */
public class SaffronBundleItem extends Item {

    public SaffronBundleItem(Properties properties) {
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
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 900, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0, false, true, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F,
                    level.getRandom().nextFloat() * 0.1F + 0.95F);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.saffron_bundle_effect"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.saffron_bundle_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.saffron_bundle.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}

