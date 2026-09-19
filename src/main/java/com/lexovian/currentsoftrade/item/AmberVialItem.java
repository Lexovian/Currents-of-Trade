package com.lexovian.currentsoftrade.item;

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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Amber Vial - Restorative maritime tonic granting Regeneration II and momentary Nausea.
 */
public class AmberVialItem extends Item {

    private static final int REGEN_DURATION  = 300; // 15 seconds
    private static final int NAUSEA_DURATION = 160; // 8 seconds (seasickness effect)

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
            int regen = (com.lexovian.currentsoftrade.Config.AMBER_VIAL_REGEN_DURATION_TICKS != null)
                    ? com.lexovian.currentsoftrade.Config.AMBER_VIAL_REGEN_DURATION_TICKS.get() : REGEN_DURATION;
            int nausea = (com.lexovian.currentsoftrade.Config.AMBER_VIAL_NAUSEA_DURATION_TICKS != null)
                    ? com.lexovian.currentsoftrade.Config.AMBER_VIAL_NAUSEA_DURATION_TICKS.get() : NAUSEA_DURATION;

            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 1, false, true, true));
            if (nausea > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nausea, 0, false, true, true));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WANDERING_TRADER_DRINK_POTION, SoundSource.PLAYERS, 0.8F, 1.0F);

            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    return emptyBottle;
                }
                if (!player.getInventory().add(emptyBottle)) {
                    player.drop(emptyBottle, false);
                }
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.amber_vial_effect"));
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.amber_vial_warning"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
