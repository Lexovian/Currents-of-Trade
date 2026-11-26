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

public class LuminousPearlItem extends Item {
    public LuminousPearlItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.35F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.GLOW, player.getX(), player.getEyeY(), player.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
            }
            player.getCooldowns().addCooldown(this, 60);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.luminous_pearl.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.luminous_pearl.effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.luminous_pearl.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.luminous_pearl.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
