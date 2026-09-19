package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TradeLedgerItem extends Item {

    public TradeLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean("Bound");
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
        if (!level.isClientSide) {
            MerchantOffers offers = villager.getOffers();
            if (offers.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.currents_of_trade.villager_no_trades"), true);
                return InteractionResult.SUCCESS;
            }

            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag currentTag = customData.copyTag();
            int selectedIndex = currentTag.getInt("SelectedIndex");

            if (player.isShiftKeyDown() && offers.size() > 1) {
                // Shift + Right-Click cycles through villager's available offers
                selectedIndex = (selectedIndex + 1) % offers.size();
                final int finalIdx = selectedIndex;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putInt("SelectedIndex", finalIdx);
                });

                MerchantOffer offer = offers.get(selectedIndex);
                String info = offer.getBaseCostA().getCount() + "x " + offer.getBaseCostA().getHoverName().getString();
                if (!offer.getCostB().isEmpty()) {
                    info += " + " + offer.getCostB().getCount() + "x " + offer.getCostB().getHoverName().getString();
                }
                info += " \u2794 " + offer.getResult().getCount() + "x " + offer.getResult().getHoverName().getString();

                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.ledger_selected_preview", selectedIndex + 1, offers.size(), info),
                        true
                );
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
                return InteractionResult.SUCCESS;
            }

            if (selectedIndex >= offers.size()) {
                selectedIndex = 0;
            }

            MerchantOffer chosenOffer = offers.get(selectedIndex);
            HarborTradeOffer harborOffer = new HarborTradeOffer(chosenOffer);

            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.put("RecordedTrade", harborOffer.toTag(level.registryAccess()));
                tag.putBoolean("Bound", true);
                tag.putString("VillagerName", villager.getDisplayName().getString());
            });

            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);

            String tradeSummary = chosenOffer.getBaseCostA().getCount() + "x " + chosenOffer.getBaseCostA().getHoverName().getString();
            if (!chosenOffer.getCostB().isEmpty()) {
                tradeSummary += " + " + chosenOffer.getCostB().getCount() + "x " + chosenOffer.getCostB().getHoverName().getString();
            }
            tradeSummary += " \u2794 " + chosenOffer.getResult().getCount() + "x " + chosenOffer.getResult().getHoverName().getString();

            // Show in action bar (true) for immediate feedback, and in chat (false) so it persists in scroll history
            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.ledger_bound_success", tradeSummary),
                    true
            );
            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.ledger_bound_success", tradeSummary),
                    false
            );

            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = customData.copyTag();

                if (!tag.getBoolean("Bound") || !tag.contains("RecordedTrade")) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().displayClientMessage(Component.translatable("message.currents_of_trade.ledger_empty"), true);
                    }
                    return InteractionResult.SUCCESS;
                }

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AnchorPointBlockEntity anchorEntity) {
                    HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompound("RecordedTrade"), level.registryAccess());
                    if (anchorEntity.addHarborTrade(offer)) {
                        level.playSound(null, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (context.getPlayer() != null) {
                            context.getPlayer().displayClientMessage(
                                    Component.translatable("message.currents_of_trade.trade_added_to_harbor", anchorEntity.getHarborTrades().size(), AnchorPointBlockEntity.MAX_HARBOR_TRADES),
                                    false
                            );
                        }

                        // Clear the ledger so it can record another trade
                        stack.remove(DataComponents.CUSTOM_DATA);
                        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            serverPlayer.inventoryMenu.broadcastChanges();
                        }
                        return InteractionResult.SUCCESS;
                    } else {
                        if (context.getPlayer() != null) {
                            context.getPlayer().displayClientMessage(Component.translatable("message.currents_of_trade.harbor_trades_full", AnchorPointBlockEntity.MAX_HARBOR_TRADES), true);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = CurrentsofTrade.MODID)
    public static class Events {
        @net.neoforged.bus.api.SubscribeEvent
        public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
            if (event.getTarget() instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (tag.getBoolean("Bound") && tag.contains("RecordedTrade")) {
            if (context.registries() != null) {
                HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompound("RecordedTrade"), context.registries());
                String costA = offer.getCostA().getCount() + "x " + offer.getCostA().getHoverName().getString();
                String costB = !offer.getCostB().isEmpty() ? (" + " + offer.getCostB().getCount() + "x " + offer.getCostB().getHoverName().getString()) : "";
                String res = offer.getResult().getCount() + "x " + offer.getResult().getHoverName().getString();

                tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.ledger_bound"));
                tooltipComponents.add(Component.literal("§e" + costA + costB + " §7\u2794 §a" + res));
                tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.ledger_how_to_submit"));
            }
        } else {
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.ledger_unbound"));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.ledger_how_to_record"));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.ledger_sneak_hint", Component.keybind("key.sneak")));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
