package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.lexovian.currentsoftrade.compat.CustomData;
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
        if (!level.isClientSide) {
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
                        player.displayClientMessage(warnMsg, true);
                    }
                    return InteractionResult.FAIL;
                }

                String harborName = anchor.getHarborName();
                ItemStack stack = context.getItemInHand();
                List<RouteEntry> routes = getRoutes(stack);

                for (RouteEntry entry : routes) {
                    if (entry.pos().equals(pos)) {
                        if (player != null) {
                            player.displayClientMessage(Component.translatable("message.currents_of_trade.journal_already_recorded", harborName), true);
                        }
                        return InteractionResult.CONSUME;
                    }
                }

                if (routes.size() >= MAX_ROUTES) {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.currents_of_trade.journal_full", MAX_ROUTES), true);
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
                    player.displayClientMessage(Component.translatable(
                            "message.currents_of_trade.journal_recorded",
                            harborName,
                            routes.size() + 1,
                            MAX_ROUTES
                    ), true);
                }
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        List<RouteEntry> routes = getRoutes(stack);

        if (routes.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.currents_of_trade.journal_empty"), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        int currentIdx = getSelectedIndex(stack);
        int nextIdx = (currentIdx + 1) % routes.size();
        setSelectedIndex(stack, nextIdx);

        if (level.isClientSide) {
            level.playSound(player, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
        } else {
            RouteEntry selected = routes.get(nextIdx);
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                syncActiveHarborTrades(stack, selected.pos(), serverLevel);
            }
            player.displayClientMessage(Component.translatable(
                    "message.currents_of_trade.journal_switched",
                    selected.name(),
                    selected.pos().getX(),
                    selected.pos().getZ(),
                    nextIdx + 1,
                    routes.size()
            ), true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void syncActiveHarborTrades(ItemStack stack, BlockPos targetPos, net.minecraft.server.level.ServerLevel serverLevel) {
        List<com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer> liveTrades =
                com.lexovian.currentsoftrade.world.harbor.HarborSavedData.get(serverLevel).getTrades(targetPos);
        CustomData.update(stack, tag -> {
            ListTag list = new ListTag();
            for (com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer offer : liveTrades) {
                list.add(offer.toTag(serverLevel.registryAccess()));
            }
            tag.put("HarborTrades", list);
        });
    }

    // --- Tooltip ---

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        List<RouteEntry> routes = getRoutes(stack);
        if (routes.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.currents_of_trade.journal_empty_desc"));
            tooltip.add(Component.translatable("tooltip.currents_of_trade.journal_hint"));
        } else {
            int selectedIdx = getSelectedIndex(stack);
            if (selectedIdx >= 0 && selectedIdx < routes.size()) {
                RouteEntry active = routes.get(selectedIdx);
                tooltip.add(Component.translatable("tooltip.currents_of_trade.journal_active", active.name(), active.pos().getX(), active.pos().getZ()));
                MaritimeTradeRegion region = MaritimeTradeRegion.byId(active.regionId());
                tooltip.add(Component.translatable("tooltip.currents_of_trade.chart_region", region.getDisplayName()));
            }
            tooltip.add(Component.translatable("tooltip.currents_of_trade.journal_count", routes.size(), MAX_ROUTES));
            tooltip.add(Component.translatable("tooltip.currents_of_trade.journal_cycle_hint"));
        }
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }

    // --- NBT & Data Helpers ---

    public static List<RouteEntry> getRoutes(ItemStack stack) {
        List<RouteEntry> result = new ArrayList<>();
        CustomData customData = CustomData.getOrNull(stack);
        if (customData == null) return result;

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_ROUTES, Tag.TAG_LIST)) return result;

        ListTag list = tag.getList(TAG_ROUTES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            String name = entryTag.getString("Name");
            BlockPos pos = new BlockPos(entryTag.getInt("X"), entryTag.getInt("Y"), entryTag.getInt("Z"));
            int lvl = entryTag.contains("Level") ? entryTag.getInt("Level") : 1;
            String regionId = entryTag.contains("Region") ? entryTag.getString("Region") : "temperate";
            result.add(new RouteEntry(name, pos, lvl, regionId));
        }
        return result;
    }

    public static void addRoute(ItemStack stack, RouteEntry entry) {
        CustomData customData = CustomData.get(stack);
        CompoundTag tag = customData.copyTag();

        ListTag list = tag.contains(TAG_ROUTES, Tag.TAG_LIST) ? tag.getList(TAG_ROUTES, Tag.TAG_COMPOUND) : new ListTag();
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
        CustomData.set(stack, CustomData.of(tag));
    }

    public static int getSelectedIndex(ItemStack stack) {
        CustomData customData = CustomData.getOrNull(stack);
        if (customData == null) return 0;
        return customData.copyTag().getInt(TAG_SELECTED_INDEX);
    }

    public static void setSelectedIndex(ItemStack stack, int index) {
        CustomData customData = CustomData.get(stack);
        CompoundTag tag = customData.copyTag();
        tag.putInt(TAG_SELECTED_INDEX, index);
        CustomData.set(stack, CustomData.of(tag));
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
