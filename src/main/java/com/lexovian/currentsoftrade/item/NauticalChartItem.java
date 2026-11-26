package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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

public class NauticalChartItem extends Item {

    public NauticalChartItem(Properties properties) {
        super(properties);
    }

    // Right-click on Anchor Point

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof AnchorPointBlock)) {
            return super.useOn(context);
        }

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            String harborName = "Unknown Port";

            if (be instanceof AnchorPointBlockEntity anchorEntity) {
                AnchorPointBlockEntity.HarborStatus status = anchorEntity.getHarborStatus();
                if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                    if (context.getPlayer() != null) {
                        Component errorMsg = switch (status) {
                            case TOO_SMALL     -> Component.translatable("message.currents_of_trade.harbor_too_small");
                            case UNDERGROUND   -> Component.translatable("message.currents_of_trade.harbor_underground");
                            case TOO_SHALLOW   -> Component.translatable("message.currents_of_trade.harbor_too_shallow");
                            case WRONG_DIMENSION -> Component.translatable("message.currents_of_trade.overworld_only");
                            default            -> Component.translatable("message.currents_of_trade.anchor_not_near_water");
                        };
                        context.getPlayer().sendOverlayMessage(errorMsg);
                    }
                    return InteractionResult.FAIL;
                }
                harborName = anchorEntity.getHarborName();
            }

            // Write harbor coordinates, name and trade snapshot into the chart's NBT
            ItemStack stack = context.getItemInHand();
            final String finalName = harborName;
            final MaritimeTradeRegion region = be instanceof AnchorPointBlockEntity aBe
                    ? aBe.getTradeRegion()
                    : MaritimeTradeRegion.fromLevelAndPos(level, pos);
            final HarborSpecialization spec = be instanceof AnchorPointBlockEntity aBe
                    ? aBe.getSpecialization()
                    : HarborSpecialization.NONE;
            final List<HarborTradeOffer> trades =
                    be instanceof AnchorPointBlockEntity aBe ? aBe.getHarborTrades() : List.of();

            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putInt("TargetX", pos.getX());
                tag.putInt("TargetY", pos.getY());
                tag.putInt("TargetZ", pos.getZ());
                tag.putString("HarborName", finalName);
                tag.putString("RegionId", region.getId());
                tag.putString("Specialization", spec.getId());
                tag.putBoolean("Bound", true);
                tag.remove("Waypoints");

                ListTag tradesList = new ListTag();
                for (HarborTradeOffer offer : trades) {
                    tradesList.add(offer.toTag(level.registryAccess()));
                }
                tag.put("HarborTrades", tradesList);
            });

            // Immediately sync the chart data to the client inventory
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
                if (serverPlayer.level().getServer() != null) {
                    net.minecraft.advancements.AdvancementHolder adv = serverPlayer.level().getServer().getAdvancements()
                            .get(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.lexovian.currentsoftrade.CurrentsofTrade.MODID, "first_chart"));
                    if (adv != null) {
                        for (String criterion : adv.value().criteria().keySet()) {
                            serverPlayer.getAdvancements().award(adv, criterion);
                        }
                    }
                }
            }

            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);

            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(
                        Component.translatable("message.currents_of_trade.chart_bound",
                                finalName, pos.getX(), pos.getY(), pos.getZ())
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    public static final int MAX_WAYPOINTS = 8;

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBound(stack)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            if (!applyChartUse(level, player, stack)) {
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Lets a captain plot/clear waypoints while aboard a ship: the crosshair then usually hits the own hull,
     * which would otherwise swallow the right-click before the chart's {@link #use} is reached.
     */
    public static boolean tryUseAboard(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                       net.minecraft.world.entity.Entity ship) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof NauticalChartItem) || !isBound(stack)) return false;
        if (player.getRootVehicle() != ship.getRootVehicle()) return false;
        if (!player.level().isClientSide()) {
            applyChartUse(player.level(), player, stack);
        }
        return true;
    }

    /** Server side: Sneak = clear all waypoints, otherwise plot a waypoint on the nearest navigable water. */
    private static boolean applyChartUse(Level level, net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            if (getWaypoints(stack).isEmpty()) return false;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("Waypoints"));
            if (player instanceof ServerPlayer sp) sp.inventoryMenu.broadcastChanges();
            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 0.8F);
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoints_cleared"));
            return true;
        }

        // Plot intermediate sea waypoint at player's current position
        List<BlockPos> waypoints = getWaypoints(stack);
        if (waypoints.size() >= MAX_WAYPOINTS) {
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoints_full", MAX_WAYPOINTS));
            return false;
        }

        BlockPos raw = new BlockPos(player.getBlockX(), Math.max(62, player.getBlockY()), player.getBlockZ());
        // Snap onto water wide enough for the hull; refuse waypoints on land / in tiny puddles
        BlockPos wp = com.lexovian.currentsoftrade.entity.TradeBoatEntity.findNearestNavigableWater(level, raw, 6);
        if (wp == null) {
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoint_not_water"));
            return false;
        }
        if (!waypoints.isEmpty() && waypoints.get(waypoints.size() - 1).distManhattan(wp) < 8) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag wpList = tag.contains("Waypoints")
                    ? tag.getListOrEmpty("Waypoints")
                    : new ListTag();
            CompoundTag wpTag = new CompoundTag();
            wpTag.putInt("X", wp.getX());
            wpTag.putInt("Y", wp.getY());
            wpTag.putInt("Z", wp.getZ());
            wpList.add(wpTag);
            tag.put("Waypoints", wpList);
        });

        if (player instanceof ServerPlayer sp) sp.inventoryMenu.broadcastChanges();
        level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.9F, 1.2F);
        player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoint_added",
                waypoints.size() + 1, MAX_WAYPOINTS, wp.getX(), wp.getZ()));
        return true;
    }

    /** Server side: appends an explicit water coordinate (such as a Navigation Buoy) as a waypoint on a bound chart. */
    public static boolean addWaypointAt(Level level, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockPos wp) {
        if (!isBound(stack) || wp == null) return false;
        List<BlockPos> waypoints = getWaypoints(stack);
        if (waypoints.size() >= MAX_WAYPOINTS) {
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoints_full", MAX_WAYPOINTS));
            return false;
        }
        if (!waypoints.isEmpty() && waypoints.get(waypoints.size() - 1).distManhattan(wp) < 4) {
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag wpList = tag.contains("Waypoints")
                    ? tag.getListOrEmpty("Waypoints")
                    : new ListTag();
            CompoundTag wpTag = new CompoundTag();
            wpTag.putInt("X", wp.getX());
            wpTag.putInt("Y", wp.getY());
            wpTag.putInt("Z", wp.getZ());
            wpList.add(wpTag);
            tag.put("Waypoints", wpList);
        });
        if (player instanceof ServerPlayer sp) sp.inventoryMenu.broadcastChanges();
        level.playSound(null, wp, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 1.4F);
        player.sendOverlayMessage(Component.translatable("message.currents_of_trade.chart_waypoint_added",
                waypoints.size() + 1, MAX_WAYPOINTS, wp.getX(), wp.getZ()));
        return true;
    }

    // Waypoint markers are rendered client-side in ModClientEvents to avoid server particle packet spam

    // Tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (tag.getBooleanOr("Bound", false)) {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_destination", tag.getStringOr("HarborName", "")));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_coordinates",
                    tag.getIntOr("TargetX", 0), tag.getIntOr("TargetY", 0), tag.getIntOr("TargetZ", 0)));

            List<BlockPos> waypoints = getWaypoints(stack);
            if (!waypoints.isEmpty()) {
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_waypoints_count", waypoints.size(), MAX_WAYPOINTS));
                for (int i = 0; i < waypoints.size(); i++) {
                    BlockPos wp = waypoints.get(i);
                    tooltipComponents.accept(Component.literal("  §8#" + (i + 1) + ": §7(" + wp.getX() + ", " + wp.getZ() + ")"));
                }
            }
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_waypoint_hint"));

            String regionId = tag.getStringOr("RegionId", "");
            MaritimeTradeRegion region = MaritimeTradeRegion.byId(regionId);
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_region", region.getDisplayName()));
            HarborSpecialization spec = HarborSpecialization.byId(tag.getStringOr("Specialization", ""));
            if (spec != HarborSpecialization.NONE) {
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.spec_active", spec.getShortBadge()));
            }
            tooltipComponents.accept(region.getExportDescription());
            tooltipComponents.accept(region.getImportDescription());
        } else {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_unbound"));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.chart_how_to_bind"));
        }

        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }

    // Static helpers

    public static List<BlockPos> getWaypoints(ItemStack stack) {
        List<BlockPos> result = new ArrayList<>();
        if (stack.isEmpty() || stack.getItem() instanceof RouteJournalItem) {
            return result;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("Waypoints")) {
            ListTag list = tag.getListOrEmpty("Waypoints");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag wp = list.getCompoundOrEmpty(i);
                result.add(new BlockPos(wp.getIntOr("X", 0), wp.getIntOr("Y", 64), wp.getIntOr("Z", 0)));
            }
        }
        return result;
    }

    @Nullable
    public static BlockPos getTargetPos(ItemStack stack) {
        if (stack.getItem() instanceof RouteJournalItem) {
            return RouteJournalItem.getSelectedTargetPos(stack);
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBooleanOr("Bound", false)) {
            return new BlockPos(tag.getIntOr("TargetX", 0), tag.getIntOr("TargetY", 0), tag.getIntOr("TargetZ", 0));
        }
        return null;
    }

    @Nullable
    public static String getHarborName(ItemStack stack) {
        if (stack.getItem() instanceof RouteJournalItem) {
            return RouteJournalItem.getSelectedTargetName(stack);
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBooleanOr("Bound", false) ? tag.getStringOr("HarborName", "") : null;
    }

    public static boolean isBound(ItemStack stack) {
        return getTargetPos(stack) != null;
    }

    public static String getTargetHarbor(ItemStack stack) {
        return getTargetHarbor(stack, null);
    }

    /**
     * Returns the most up-to-date harbor name, preferring the live server-side
     * HarborSavedData over the snapshot stored in the chart's NBT.
     */
    public static String getTargetHarbor(ItemStack stack, @Nullable Level level) {
        BlockPos targetPos = getTargetPos(stack);
        if (targetPos != null && level instanceof ServerLevel serverLevel) {
            String liveName = HarborSavedData.get(serverLevel).getHarborName(targetPos);
            if (liveName != null && !liveName.trim().isEmpty()) {
                return liveName.trim();
            }
        }
        String name = getHarborName(stack);
        return name != null && !name.trim().isEmpty() ? name.trim() : "Unknown Port";
    }

    /**
     * Returns the trade list for the chart's target harbor.
     * Prefers live server data; falls back to the snapshot baked into the chart.
     */
    public static List<HarborTradeOffer> getHarborTrades(ItemStack stack, HolderLookup.Provider registries, @Nullable Level level) {
        BlockPos targetPos = getTargetPos(stack);
        List<HarborTradeOffer> baseList = new ArrayList<>();

        if (targetPos != null && level instanceof ServerLevel serverLevel) {
            List<HarborTradeOffer> liveTrades = HarborSavedData.get(serverLevel).getTrades(targetPos);
            if (!liveTrades.isEmpty()) {
                baseList.addAll(liveTrades);
            }
        }

        if (baseList.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("HarborTrades")) {
                ListTag tradesList = tag.getListOrEmpty("HarborTrades");
                for (int i = 0; i < tradesList.size(); i++) {
                    baseList.add(HarborTradeOffer.fromTag(tradesList.getCompoundOrEmpty(i), registries));
                }
            }
        }

        if (targetPos != null && !baseList.isEmpty()) {
            boolean alreadyHasBounty = false;
            for (HarborTradeOffer offer : baseList) {
                if (offer.isUrgentBounty()) {
                    alreadyHasBounty = true;
                    break;
                }
            }
            if (!alreadyHasBounty) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                MaritimeTradeRegion region;
                if (stack.getItem() instanceof RouteJournalItem) {
                    List<RouteJournalItem.RouteEntry> routes = RouteJournalItem.getRoutes(stack);
                    int idx = RouteJournalItem.getSelectedIndex(stack);
                    if (idx >= 0 && idx < routes.size()) {
                        region = MaritimeTradeRegion.byId(routes.get(idx).regionId());
                    } else {
                        region = MaritimeTradeRegion.fromLevelAndPos(level, targetPos);
                    }
                } else if (tag.contains("RegionId")) {
                    region = MaritimeTradeRegion.byId(tag.getStringOr("RegionId", ""));
                } else {
                    region = MaritimeTradeRegion.fromLevelAndPos(level, targetPos);
                }
                HarborSpecialization destSpec = HarborSpecialization.NONE;
                if (level instanceof ServerLevel serverLevel) {
                    destSpec = HarborSavedData.get(serverLevel).getHarborSpecialization(targetPos);
                } else if (stack.getItem() instanceof RouteJournalItem) {
                    List<RouteJournalItem.RouteEntry> routes = RouteJournalItem.getRoutes(stack);
                    int idx = RouteJournalItem.getSelectedIndex(stack);
                    if (idx >= 0 && idx < routes.size()) {
                        destSpec = routes.get(idx).specialization();
                    }
                } else if (tag.contains("Specialization")) {
                    destSpec = HarborSpecialization.byId(tag.getStringOr("Specialization", ""));
                }

                if (destSpec == HarborSpecialization.GUILD_DEPOT) {
                    HarborTradeOffer secondBounty = region.getSecondaryUrgentBountyOffer(level, targetPos);
                    if (secondBounty != null) {
                        baseList.add(0, secondBounty);
                    }
                }
                HarborTradeOffer bounty = region.getUrgentBountyOffer(level, targetPos);
                if (bounty != null) {
                    baseList.add(0, bounty);
                }
            }
        }

        return baseList;
    }
}
