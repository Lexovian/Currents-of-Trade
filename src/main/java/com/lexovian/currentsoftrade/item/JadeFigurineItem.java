package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
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

/**
 * Carved Jade Figurine (İşlemeli Yeşim Heykelcik) — imperial green nephrite sea-serpent talisman.
 * Right-clicking invokes the Sea Dragon's auspicious blessing (Luck I & Dolphin's Grace I for 30s, 90s cooldown).
 */
public class JadeFigurineItem extends Item {

    public JadeFigurineItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 600, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 600, 0, false, true, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.15F);
            player.getCooldowns().addCooldown(this, 1800);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.jade_figurine_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.jade_figurine_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.jade_figurine.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

