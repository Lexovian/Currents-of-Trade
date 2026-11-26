package com.lexovian.currentsoftrade.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class TeaBrickItem extends Item {
    public TeaBrickItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.getFoodData().eat(3, 0.5F);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 900, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.9F, 1.1F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getEyeY(), player.getZ(), 6, 0.2, 0.15, 0.2, 0.01);
            }
            player.getCooldowns().addCooldown(this, 40);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tea_brick.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tea_brick.effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tea_brick.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.tea_brick.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
