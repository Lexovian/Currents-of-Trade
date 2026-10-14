package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.Config;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Captain's Pipe — a carved briar pipe carried by seasoned sea captains.
 * Right-clicking puffs aromatic smoke rings, cures Nausea (seasickness),
 * and grants Luck I for a configurable duration.
 */
public class CaptainsPipeItem extends Item {

    private static final int COOLDOWN_TICKS = 200; // 10 seconds

    public CaptainsPipeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        Vec3 look = player.getLookAngle();
        double mouthX = player.getX() + look.x * 0.45;
        double mouthY = player.getEyeY() - 0.12 + look.y * 0.45;
        double mouthZ = player.getZ() + look.z * 0.45;

        if (level.isClientSide) {
            for (int i = 0; i < 6; i++) {
                double vx = look.x * 0.06 + (level.random.nextDouble() - 0.5) * 0.02;
                double vy = 0.03 + (level.random.nextDouble() * 0.02);
                double vz = look.z * 0.06 + (level.random.nextDouble() - 0.5) * 0.02;
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, mouthX, mouthY, mouthZ, vx, vy, vz);
                level.addParticle(ParticleTypes.SMOKE, mouthX, mouthY, mouthZ, vx * 1.2, vy, vz * 1.2);
            }
        } else if (level instanceof ServerLevel serverLevel) {
            // Puff sound
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.8F, 1.3F);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 0.85F);

            // Server-side smoke particles visible to nearby players
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    mouthX, mouthY, mouthZ, 5, 0.08, 0.05, 0.08, 0.015);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    mouthX, mouthY, mouthZ, 8, 0.1, 0.06, 0.1, 0.02);

            // Cure seasickness (Nausea) and grant Captain's Luck I
            player.removeEffect(MobEffects.CONFUSION);
            int luckDuration = Config.CAPTAINS_PIPE_LUCK_DURATION_TICKS != null
                    ? Config.CAPTAINS_PIPE_LUCK_DURATION_TICKS.get() : 1200;
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, luckDuration, 0, false, true, true));

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_pipe.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_pipe.effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_pipe.lore"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}


