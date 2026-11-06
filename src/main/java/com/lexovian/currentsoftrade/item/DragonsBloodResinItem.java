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

/**
 * Dragon's Blood Resin — rare crimson tree resin tapped along tropical trade routes.
 *
 * <p>Uses:
 * <ul>
 *   <li>Primary: Consumed directly on right-click to grant Fire Resistance I (60s)
 *       and Strength I (30s) alongside a warm crimson flame particle burst.</li>
 *   <li>Secondary: Right-click a Sloop or Cargo Ship to permanently dye its sails
 *       a vibrant, iconic Dragon's Blood Crimson (#B22222).</li>
 * </ul>
 */
public class DragonsBloodResinItem extends Item {

    public DragonsBloodResinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 0, false, true, true));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.4F);

            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 18; i++) {
                    double ox = (sl.getRandom().nextDouble() - 0.5) * 0.9;
                    double oy = sl.getRandom().nextDouble() * 1.6;
                    double oz = (sl.getRandom().nextDouble() - 0.5) * 0.9;
                    sl.sendParticles(ParticleTypes.FLAME, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0.04, 0, 0.02);
                    if (sl.getRandom().nextBoolean()) {
                        sl.sendParticles(ParticleTypes.LAVA, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0, 0, 0);
                    }
                }
            }

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(stack, 80);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.dragons_blood_resin_desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.dragons_blood_resin_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.dragons_blood_resin.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
