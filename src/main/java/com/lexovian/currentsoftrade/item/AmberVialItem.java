package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Restorative maritime tonic — grants Regeneration II for a configurable duration,
 * with a momentary Nausea side-effect (seasickness). Returns an empty glass bottle on use.
 * Durations are configurable via {@link Config#AMBER_VIAL_REGEN_DURATION_TICKS} and
 * {@link Config#AMBER_VIAL_NAUSEA_DURATION_TICKS}.
 */
public class AmberVialItem extends Item {

    public AmberVialItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            int regen   = Config.AMBER_VIAL_REGEN_DURATION_TICKS.get();
            int nausea  = Config.AMBER_VIAL_NAUSEA_DURATION_TICKS.get();

            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 1, false, true, true));
            if (nausea > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nausea, 0, false, true, true));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WANDERING_TRADER_DRINK_POTION, SoundSource.PLAYERS, 0.8F, 1.0F);

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) return bottle;
                if (!player.getInventory().add(bottle)) player.drop(bottle, false);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.amber_vial_effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.amber_vial_warning"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
