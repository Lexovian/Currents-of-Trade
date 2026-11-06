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
 * Royal Tyrian Purple Dye — legendary luxury dye extracted from rare marine murex snails.
 *
 * <p>Uses:
 * <ul>
 *   <li>Primary: Consumed directly on right-click to grant Hero of the Village I (60s)
 *       and Glowing (30s) alongside enchanting particle aura.</li>
 *   <li>Secondary: Right-click a Sloop or Cargo Ship to permanently dye its sails
 *       the iconic Royal Tyrian Purple (#66023C).</li>
 * </ul>
 */
public class TyrianPurpleItem extends Item {

    public TyrianPurpleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, true, true));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);

            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 20; i++) {
                    double ox = (sl.getRandom().nextDouble() - 0.5) * 1.0;
                    double oy = sl.getRandom().nextDouble() * 1.8;
                    double oz = (sl.getRandom().nextDouble() - 0.5) * 1.0;
                    sl.sendParticles(ParticleTypes.ENCHANT, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 1, 0, 0.1, 0, 0.1);
                }
            }

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(stack, 100);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.tyrian_purple_desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.tyrian_purple_hint"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.tyrian_purple.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
