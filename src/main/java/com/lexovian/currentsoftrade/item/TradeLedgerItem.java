package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class TradeLedgerItem extends Item {

    public TradeLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBooleanOr("Bound", false);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof AbstractVillager villager) {
            return handleVillagerInteract(stack, player, villager, hand);
        }
        return super.interactLivingEntity(stack, player, entity, hand);
    }

    public InteractionResult handleVillagerInteract(ItemStack stack, Player player, AbstractVillager villager, InteractionHand hand) {
        Level level = player.level();
        player.swing(hand, true);
        if (!level.isClientSide()) {
            MerchantOffers offers = villager.getOffers();
            if (offers.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.villager_no_trades"));
                return InteractionResult.SUCCESS;
            }

            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag currentTag = customData.copyTag();
            int selectedIndex = currentTag.getIntOr("SelectedIndex", 0);

            if (player.isShiftKeyDown() && offers.size() > 1) {
                // Shift + Right-Click cycles through villager's available offers
                selectedIndex = (selectedIndex + 1) % offers.size();
                final int finalIdx = selectedIndex;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putInt("SelectedIndex", finalIdx);
                });
                MerchantOffer offer = offers.get(selectedIndex);
                String costA = offer.getBaseCostA().getCount() + "x " + offer.getBaseCostA().getHoverName().getString();
                String costB = !offer.getCostB().isEmpty() ? (" + " + offer.getCostB().getCount() + "x " + offer.getCostB().getHoverName().getString()) : "";
                String res = offer.getResult().getCount() + "x " + offer.getResult().getHoverName().getString();
                String info = costA + costB + " \u2794 " + res;
                player.sendOverlayMessage(
                        Component.translatable("message.currents_of_trade.ledger_selected_preview",
                                selectedIndex + 1, offers.size(), info)
                );
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
                return InteractionResult.SUCCESS;
            }

            if (selectedIndex >= offers.size()) {
                selectedIndex = 0;
            }

            MerchantOffer chosenOffer = offers.get(selectedIndex);

            // Record trade offer into the ledger
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putBoolean("Bound", true);
                tag.put("RecordedTrade", new HarborTradeOffer(chosenOffer).toTag(level.registryAccess()));
            });

            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);

            String tradeSummary = chosenOffer.getBaseCostA().getCount() + "x " + chosenOffer.getBaseCostA().getHoverName().getString();
            if (!chosenOffer.getCostB().isEmpty()) {
                tradeSummary += " + " + chosenOffer.getCostB().getCount() + "x " + chosenOffer.getCostB().getHoverName().getString();
            }
            tradeSummary += " \u2794 " + chosenOffer.getResult().getCount() + "x " + chosenOffer.getResult().getHoverName().getString();

            // Show in action bar for immediacy, and also in chat scroll history
            player.sendOverlayMessage(
                    Component.translatable("message.currents_of_trade.ledger_bound_success", tradeSummary)
            );
            player.sendSystemMessage(
                    Component.translatable("message.currents_of_trade.ledger_bound_success", tradeSummary)
            );

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof AnchorPointBlock) {
            if (context.getPlayer() != null) {
                context.getPlayer().swing(context.getHand(), true);
            }
            if (!level.isClientSide()) {
                ItemStack stack = context.getItemInHand();
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = customData.copyTag();

                if (!tag.getBooleanOr("Bound", false) || !tag.contains("RecordedTrade")) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().sendOverlayMessage(Component.translatable("message.currents_of_trade.ledger_empty"));
                    }
                    return InteractionResult.SUCCESS;
                }

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AnchorPointBlockEntity anchorEntity) {
                    HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompoundOrEmpty("RecordedTrade"), level.registryAccess());
                    if (anchorEntity.addHarborTrade(offer)) {
                        level.playSound(null, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (context.getPlayer() != null) {
                            context.getPlayer().sendSystemMessage(
                                    Component.translatable("message.currents_of_trade.trade_added_to_harbor", anchorEntity.getHarborTrades().size(), anchorEntity.getMaxTradeOffers())
                            );
                        }

                        // Clear the ledger so it can record another trade
                        stack.remove(DataComponents.CUSTOM_DATA);
                        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                            serverPlayer.inventoryMenu.broadcastChanges();
                        }
                        return InteractionResult.SUCCESS;
                    } else {
                        if (context.getPlayer() != null) {
                            context.getPlayer().sendOverlayMessage(Component.translatable("message.currents_of_trade.harbor_trades_full", anchorEntity.getMaxTradeOffers()));
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = CurrentsofTrade.MODID)
    public static class Events {
        @net.neoforged.bus.api.SubscribeEvent
        public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
            if (event.getTarget() instanceof AbstractVillager villager) {
                ItemStack stack = event.getItemStack();
                if (stack.getItem() instanceof TradeLedgerItem ledger) {
                    InteractionResult result = ledger.handleVillagerInteract(stack, event.getEntity(), villager, event.getHand());
                    event.setCancellationResult(result);
                    event.setCanceled(true);
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (tag.getBooleanOr("Bound", false) && tag.contains("RecordedTrade")) {
            if (context.registries() != null) {
                HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompoundOrEmpty("RecordedTrade"), context.registries());
                String costA = offer.getCostA().getCount() + "x " + offer.getCostA().getHoverName().getString();
                String costB = !offer.getCostB().isEmpty() ? (" + " + offer.getCostB().getCount() + "x " + offer.getCostB().getHoverName().getString()) : "";
                String res = offer.getResult().getCount() + "x " + offer.getResult().getHoverName().getString();

                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ledger_bound"));
                tooltipComponents.accept(Component.literal("§e" + costA + costB + " §7\u2794 §a" + res));
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ledger_how_to_submit"));
            }
        } else {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ledger_unbound"));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ledger_how_to_record"));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.ledger_sneak_hint", Component.keybind("key.sneak")));
        }

        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
