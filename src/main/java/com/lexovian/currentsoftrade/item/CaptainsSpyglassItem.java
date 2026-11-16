package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Captain's Brass Spyglass — naval reconnaissance telescope.
 *
 * <p>Zooms in further than a standard spyglass and projects a live Maritime Recon HUD
 * showing distant ship manifests, hull upgrades, destination ports, harbor specializations,
 * and coastal {@code MaritimeTradeRegion} market profiles.
 */
public class CaptainsSpyglassItem extends Item {

    public static final int USE_DURATION = 1200;
    public static final double RECON_RANGE = 96.0D;

    public CaptainsSpyglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        livingEntity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        return stack;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        livingEntity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        return false;
    }

    public static boolean isScoping(Player player) {
        return player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof CaptainsSpyglassItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.captains_spyglass.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.captains_spyglass.hud"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
