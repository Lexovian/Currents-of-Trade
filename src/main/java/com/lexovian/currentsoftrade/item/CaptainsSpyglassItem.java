package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

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
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
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
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        livingEntity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    public static boolean isScoping(Player player) {
        return player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof CaptainsSpyglassItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_spyglass.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.captains_spyglass.hud"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
