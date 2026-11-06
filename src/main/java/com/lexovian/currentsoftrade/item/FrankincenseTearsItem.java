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
 * Frankincense Tears — fragrant holy gum resin of desert origin.
 *
 * <p>Uses:
 * <ul>
 *   <li>Consuming cures poison, hunger, slowness, mining fatigue, nausea, and weakness.</li>
 *   <li>Grants Resistance I (30s) and Regeneration I (20s).</li>
 *   <li>Emits aromatic incense campfire smoke particles.</li>
 * </ul>
 */
public class FrankincenseTearsItem extends Item {

    public FrankincenseTearsItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            // Clear harmful debuffs
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.WEAKNESS);
            player.removeEffect(MobEffects.MINING_FATIGUE);
            player.removeEffect(MobEffects.HUNGER);
            player.removeEffect(MobEffects.NAUSEA);

            // Grant soothing blessings
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0, false, true, true));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.4F);

            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 15; i++) {
                    double ox = (sl.getRandom().nextDouble() - 0.5) * 0.8;
                    double oy = sl.getRandom().nextDouble() * 1.5;
                    double oz = (sl.getRandom().nextDouble() - 0.5) * 0.8;
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0.05, 0, 0.02);
                }
            }

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(stack, 60);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.frankincense_tears_desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.frankincense_tears_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.frankincense_tears.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
