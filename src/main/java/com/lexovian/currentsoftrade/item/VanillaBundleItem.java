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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VanillaBundleItem extends Item {
    public VanillaBundleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.getFoodData().eat(3, 0.6F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.9F, 1.15F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getEyeY(), player.getZ(), 8, 0.3, 0.25, 0.3, 0.02);
            }
            player.getCooldowns().addCooldown(this, 40);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.vanilla_bundle.market"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}
