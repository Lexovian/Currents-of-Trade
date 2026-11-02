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

/**
 * Royal Tyrian Dye (Kraliyet Moru) — legendary marine snail dye worth more than gold.
 * Prized across maritime empires by emperors, admirals, and kings.
 * Right-click on a Sloop or Cargo Ship to dye its sails into imperial royal purple.
 * Right-click in hand to proclaim commercial prestige: grants Hero of the Village I (60s)
 * and Glowing (30s).
 */
public class TyrianPurpleItem extends Item {

    public TyrianPurpleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, true, true));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);

            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 20; i++) {
                    double ox = (sl.random.nextDouble() - 0.5) * 1.0;
                    double oy = sl.random.nextDouble() * 1.8;
                    double oz = (sl.random.nextDouble() - 0.5) * 1.0;
                    sl.sendParticles(ParticleTypes.ENCHANT, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0.1, 0, 0.1);
                }
            }

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 100);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tyrian_purple_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.tyrian_purple_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.tyrian_purple.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

