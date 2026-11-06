package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RouteJournalItem extends Item {

    // --- Constants & Records ---

    public static final int MAX_ROUTES = 10;
    public static final String TAG_ROUTES = "Routes";
    public static final String TAG_SELECTED_INDEX = "SelectedIndex";

    public record RouteEntry(String name, BlockPos pos, int level, String regionId) {
        public RouteEntry(String name, BlockPos pos, int level) {
            this(name, pos, level, "temperate");
        }
    }

    public RouteJournalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // --- Interaction ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof AnchorPointBlock)) {
            return super.useOn(context);
        }

        Player player = context.getPlayer();
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AnchorPointBlockEntity anchor) {
                AnchorPointBlockEntity.HarborStatus status = anchor.getHarborStatus();
                if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                    if (player != null) {
                        Component warnMsg = switch (status) {
                            case TOO_SMALL -> Component.translatable("message.currents_of_trade.harbor_too_small");
                            case UNDERGROUND -> Component.translatable("message.currents_of_trade.harbor_underground");
                            case TOO_SHALLOW -> Component.translatable("message.currents_of_trade.harbor_too_shallow");
                            case WRONG_DIMENSION -> Component.translatable("message.currents_of_trade.overworld_only");
                            default -> Component.translatable("message.currents_of_trade.anchor_placed_no_water");
                        };
                        player.sendOverlayMessage(warnMsg);
                    }
                    return InteractionResult.FAIL;
                }

                String harborName = anchor.getHarborName();
                ItemStack stack = context.getItemInHand();
                List<RouteEntry> routes = getRoutes(stack);

                for (RouteEntry entry : routes) {
                    if (entry.pos().equals(pos)) {
                        if (player != null) {
                            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.journal_already_recorded", harborName));
                        }
                        return InteractionResult.CONSUME;
                    }
                }

                if (routes.size() >= MAX_ROUTES) {
                    if (player != null) {
                        player.sendOverlayMessage(Component.translatable("message.currents_of_trade.journal_full", MAX_ROUTES));
                    }
                    return InteractionResult.FAIL;
                }

                addRoute(stack, new RouteEntry(harborName, pos, anchor.getTradeLevel(), anchor.getTradeRegion().getId()));
                if (routes.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    syncActiveHarborTrades(stack, pos, serverLevel);
                }

                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);

                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(
                            "message.currents_of_trade.journal_recorded",
                            harborName,
                            routes.size() + 1,
                            MAX_ROUTES
                    ));
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        List<String> pruned = pruneBrokenHarbors(stack, level);
        if (!pruned.isEmpty() && !level.isClientSide()) {
            for (String name : pruned) {
                player.sendSystemMessage(Component.translatable("message.currents_of_trade.journal_harbor_removed", name));
            }
        }
        List<RouteEntry> routes = getRoutes(stack);

        // Shift + Right-Click quickly cycles the active harbor in the action bar
        if (player.isShiftKeyDown()) {
            if (routes.isEmpty()) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.journal_empty"));
                }
                return InteractionResult.PASS;
            }

            int currentIdx = getSelectedIndex(stack);
            int nextIdx = (currentIdx + 1) % routes.size();
            setSelectedIndex(stack, nextIdx);

            if (level.isClientSide()) {
                level.playSound(player, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
            } else {
                RouteEntry selected = routes.get(nextIdx);
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    syncActiveHarborTrades(stack, selected.pos(), serverLevel);
                }
                player.sendOverlayMessage(Component.translatable(
                        "message.currents_of_trade.journal_switched",
                        selected.name(),
                        selected.pos().getX(),
                        selected.pos().getZ(),
                        nextIdx + 1,
                        routes.size()
                ));
            }
            return InteractionResult.SUCCESS;
        }

        // Normal Right-Click opens the Interactive Nautical Atlas & Maritime Compendium GUI!
        if (level.isClientSide()) {
            level.playSound(player, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.85F, 1.05F);
            ClientScreenOpener.open(hand);
        } else if (!routes.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            int idx = Math.max(0, Math.min(routes.size() - 1, getSelectedIndex(stack)));
            syncActiveHarborTrades(stack, routes.get(idx).pos(), serverLevel);
        }

        return InteractionResult.SUCCESS;
    }

    private static final class ClientScreenOpener {
        static void open(InteractionHand hand) {
            com.lexovian.currentsoftrade.client.gui.NauticalAtlasScreen.open(hand);
        }
    }

    public static void syncActiveHarborTrades(ItemStack stack, BlockPos targetPos, net.minecraft.server.level.ServerLevel serverLevel) {
        List<com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer> liveTrades =
                com.lexovian.currentsoftrade.world.harbor.HarborSavedData.get(serverLevel).getTrades(targetPos);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag list = new ListTag();
            for (com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer offer : liveTrades) {
                list.add(offer.toTag(serverLevel.registryAccess()));
            }
            tag.put("HarborTrades", list);
        });
    }

    // --- Tooltip ---

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        List<RouteEntry> routes = getRoutes(stack);
        if (routes.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.currents_of_trade.journal_empty_desc"));
            tooltip.accept(Component.translatable("tooltip.currents_of_trade.journal_hint"));
        } else {
            int selectedIdx = getSelectedIndex(stack);
            if (selectedIdx >= 0 && selectedIdx < routes.size()) {
                RouteEntry active = routes.get(selectedIdx);
                tooltip.accept(Component.translatable("tooltip.currents_of_trade.journal_active", active.name(), active.pos().getX(), active.pos().getZ()));
                MaritimeTradeRegion region = MaritimeTradeRegion.byId(active.regionId());
                tooltip.accept(Component.translatable("tooltip.currents_of_trade.chart_region", region.getDisplayName()));
            }
            tooltip.accept(Component.translatable("tooltip.currents_of_trade.journal_count", routes.size(), MAX_ROUTES));
            tooltip.accept(Component.translatable("tooltip.currents_of_trade.journal_cycle_hint"));
        }
        super.appendHoverText(stack, context, display, tooltip, tooltipFlag);
    }

    // --- NBT & Data Helpers ---

    public static List<RouteEntry> getRoutes(ItemStack stack) {
        List<RouteEntry> result = new ArrayList<>();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return result;

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_ROUTES)) return result;

        ListTag list = tag.getListOrEmpty(TAG_ROUTES);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompoundOrEmpty(i);
            String name = entryTag.getStringOr("Name", "Unknown Harbor");
            int x = entryTag.getIntOr("X", 0);
            int y = entryTag.getIntOr("Y", 64);
            int z = entryTag.getIntOr("Z", 0);
            BlockPos pos = new BlockPos(x, y, z);
            int lvl = entryTag.getIntOr("Level", 1);
            String regionId = entryTag.getStringOr("Region", "temperate");
            result.add(new RouteEntry(name, pos, lvl, regionId));
        }
        return result;
    }

    public static void addRoute(ItemStack stack, RouteEntry entry) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        ListTag list = tag.getListOrEmpty(TAG_ROUTES);
        CompoundTag entryTag = new CompoundTag();
        entryTag.putString("Name", entry.name());
        entryTag.putInt("X", entry.pos().getX());
        entryTag.putInt("Y", entry.pos().getY());
        entryTag.putInt("Z", entry.pos().getZ());
        entryTag.putInt("Level", entry.level());
        entryTag.putString("Region", entry.regionId());
        list.add(entryTag);

        tag.put(TAG_ROUTES, list);
        if (!tag.contains(TAG_SELECTED_INDEX)) {
            tag.putInt(TAG_SELECTED_INDEX, 0);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean removeRoute(ItemStack stack, BlockPos pos) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_ROUTES)) return false;

        ListTag list = tag.getListOrEmpty(TAG_ROUTES);
        int removeIdx = -1;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompoundOrEmpty(i);
            BlockPos entryPos = new BlockPos(entryTag.getIntOr("X", 0), entryTag.getIntOr("Y", 64), entryTag.getIntOr("Z", 0));
            if (entryPos.equals(pos)) {
                removeIdx = i;
                break;
            }
        }
        if (removeIdx == -1) return false;

        list.remove(removeIdx);
        tag.put(TAG_ROUTES, list);

        int currentIdx = tag.getIntOr(TAG_SELECTED_INDEX, 0);
        if (list.isEmpty()) {
            tag.putInt(TAG_SELECTED_INDEX, 0);
        } else if (currentIdx >= list.size()) {
            tag.putInt(TAG_SELECTED_INDEX, list.size() - 1);
        } else if (currentIdx > removeIdx) {
            tag.putInt(TAG_SELECTED_INDEX, currentIdx - 1);
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    public static boolean removeRouteByIndex(ItemStack stack, int index) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_ROUTES)) return false;

        ListTag list = tag.getListOrEmpty(TAG_ROUTES);
        if (index < 0 || index >= list.size()) return false;

        list.remove(index);
        tag.put(TAG_ROUTES, list);

        int currentIdx = tag.getIntOr(TAG_SELECTED_INDEX, 0);
        if (list.isEmpty()) {
            tag.putInt(TAG_SELECTED_INDEX, 0);
        } else if (currentIdx >= list.size()) {
            tag.putInt(TAG_SELECTED_INDEX, list.size() - 1);
        } else if (currentIdx > index) {
            tag.putInt(TAG_SELECTED_INDEX, currentIdx - 1);
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    public static List<String> pruneBrokenHarbors(ItemStack stack, Level level) {
        List<RouteEntry> routes = getRoutes(stack);
        if (routes.isEmpty()) return List.of();

        List<String> removedNames = new ArrayList<>();
        for (RouteEntry entry : routes) {
            boolean broken = false;
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                if (serverLevel.isLoaded(entry.pos())) {
                    if (!(serverLevel.getBlockState(entry.pos()).getBlock() instanceof AnchorPointBlock)) {
                        broken = true;
                    }
                } else {
                    if (!com.lexovian.currentsoftrade.world.harbor.HarborSavedData.get(serverLevel).hasHarbor(entry.pos())) {
                        broken = true;
                    }
                }
            } else {
                if (level.hasChunkAt(entry.pos())) {
                    if (!(level.getBlockState(entry.pos()).getBlock() instanceof AnchorPointBlock)) {
                        broken = true;
                    }
                }
            }
            if (broken) {
                if (removeRoute(stack, entry.pos())) {
                    removedNames.add(entry.name());
                }
            }
        }
        return removedNames;
    }

    public static int getSelectedIndex(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getIntOr(TAG_SELECTED_INDEX, 0);
    }

    public static void setSelectedIndex(ItemStack stack, int index) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(TAG_SELECTED_INDEX, index);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static BlockPos getSelectedTargetPos(ItemStack stack) {
        List<RouteEntry> routes = getRoutes(stack);
        if (routes.isEmpty()) return null;
        int idx = getSelectedIndex(stack);
        if (idx < 0 || idx >= routes.size()) idx = 0;
        return routes.get(idx).pos();
    }

    @Nullable
    public static String getSelectedTargetName(ItemStack stack) {
        List<RouteEntry> routes = getRoutes(stack);
        if (routes.isEmpty()) return null;
        int idx = getSelectedIndex(stack);
        if (idx < 0 || idx >= routes.size()) idx = 0;
        return routes.get(idx).name();
    }
}
