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

/**
 * Exotic Dragon's Blood Resin (Ejderha Kanı Reçinesi) — mystical crimson resin tears tapped from
 * ancient island trees along tropical archipelago trade routes.
 * Prized across maritime empires by alchemists, shipwrights, and kings.
 * Right-click on a Sloop or Cargo Ship to dye its sails into Dragon's Blood Crimson.
 * Right-click in hand to consume/burn: grants Fire Resistance I (60s) + Strength I (30s).
 */
public class DragonsBloodResinItem extends Item {

    public DragonsBloodResinItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0, false, true, true));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.4F);

            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 18; i++) {
                    double ox = (sl.random.nextDouble() - 0.5) * 0.9;
                    double oy = sl.random.nextDouble() * 1.6;
                    double oz = (sl.random.nextDouble() - 0.5) * 0.9;
                    sl.sendParticles(ParticleTypes.FLAME, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0.04, 0, 0.02);
                    if (sl.random.nextBoolean()) {
                        sl.sendParticles(ParticleTypes.LAVA, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0, 0, 0);
                    }
                }
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 80);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.dragons_blood_resin_desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.dragons_blood_resin_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.dragons_blood_resin.market"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}

