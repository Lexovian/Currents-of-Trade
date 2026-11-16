package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Merchant Guild Strongbox — awarded for completing Sealed Trade Charters.
 * Right-click to unseal a curated cache of Doubloons, exotic luxury commodities, and rare nautical gear.
 */
public class GuildStrongboxItem extends Item {

    public GuildStrongboxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            RandomSource rng = level.getRandom();
            List<ItemStack> drops = rollStrongboxRewards(rng);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            StringBuilder summary = new StringBuilder();
            for (ItemStack reward : drops) {
                if (reward.isEmpty()) continue;
                if (summary.length() > 0) summary.append(", ");
                summary.append(reward.getCount()).append("x ").append(reward.getHoverName().getString());
                ItemStack toGive = reward.copy();
                if (!player.getInventory().add(toGive)) {
                    player.drop(toGive, false);
                }
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();

            level.playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.9F, 1.15F);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.1F);

            serverLevel.sendParticles(ParticleTypes.WAX_ON,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    16, 0.35, 0.3, 0.35, 0.08);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.35, 0.3, 0.35, 0.05);

            player.sendOverlayMessage(
                    Component.translatable("message.currents_of_trade.strongbox_opened", summary.toString())
            );
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    public static List<ItemStack> rollStrongboxRewards(RandomSource rng) {
        List<ItemStack> rewards = new ArrayList<>();

        // 1. Guaranteed Guild Doubloon stipend (8 - 16 Doubloons)
        int doubloons = 8 + rng.nextInt(9);
        rewards.add(new ItemStack(CurrentsofTrade.DOUBLOON.get(), doubloons));

        // 2. High-tier luxury commodity roll
        Item[] luxuryPool = new Item[]{
                CurrentsofTrade.TYRIAN_PURPLE.get(),
                CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(),
                CurrentsofTrade.FRANKINCENSE_TEARS.get(),
                CurrentsofTrade.DAMASCUS_STEEL.get(),
                CurrentsofTrade.JADE_FIGURINE.get(),
                CurrentsofTrade.NARWHAL_IVORY.get(),
                CurrentsofTrade.LUMINOUS_PEARL.get(),
                CurrentsofTrade.SAFFRON_BUNDLE.get(),
                CurrentsofTrade.SILK_BALE.get(),
                CurrentsofTrade.FINE_PORCELAIN.get()
        };
        Item luxury = luxuryPool[rng.nextInt(luxuryPool.length)];
        int luxuryCount = 1 + rng.nextInt(2);
        rewards.add(new ItemStack(luxury, luxuryCount));

        // 3. Bonus nautical curiosity / rare equipment roll (45% chance)
        float bonusRoll = rng.nextFloat();
        if (bonusRoll < 0.10F) {
            rewards.add(new ItemStack(CurrentsofTrade.SWIVEL_HARPOON.get(), 1));
        } else if (bonusRoll < 0.18F) {
            rewards.add(new ItemStack(CurrentsofTrade.BOSUNS_WHISTLE.get(), 1));
        } else if (bonusRoll < 0.26F) {
            rewards.add(new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1));
        } else if (bonusRoll < 0.34F) {
            rewards.add(new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1));
        } else if (bonusRoll < 0.45F) {
            rewards.add(new ItemStack(Items.EMERALD, 4 + rng.nextInt(5)));
        }

        return rewards;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.guild_strongbox.desc"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.guild_strongbox.use"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
