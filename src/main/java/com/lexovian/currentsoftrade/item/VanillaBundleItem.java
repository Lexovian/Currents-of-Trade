package com.lexovian.currentsoftrade.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class VanillaBundleItem extends Item {
    public VanillaBundleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.getFoodData().eat(3, 0.6F);
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.9F, 1.15F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getEyeY(), player.getZ(), 8, 0.3, 0.25, 0.3, 0.02);
            }
            player.getCooldowns().addCooldown(stack, 40);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.effect"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.vanilla_bundle.lore"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.vanilla_bundle.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
