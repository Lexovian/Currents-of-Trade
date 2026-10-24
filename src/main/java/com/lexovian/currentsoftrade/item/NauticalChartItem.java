package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

        if (!level.isClientSide) {
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
                        context.getPlayer().displayClientMessage(errorMsg, true);
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
            final List<HarborTradeOffer> trades =
                    be instanceof AnchorPointBlockEntity aBe ? aBe.getHarborTrades() : List.of();

            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putInt("TargetX", pos.getX());
                tag.putInt("TargetY", pos.getY());
                tag.putInt("TargetZ", pos.getZ());
                tag.putString("HarborName", finalName);
                tag.putString("RegionId", region.getId());
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
            }

            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);

            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.currents_of_trade.chart_bound",
                                finalName, pos.getX(), pos.getY(), pos.getZ()),
                        true
                );
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static final int MAX_WAYPOINTS = 8;

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBound(stack)) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            if (!applyChartUse(level, player, stack)) {
                return net.minecraft.world.InteractionResultHolder.fail(stack);
            }
        }

        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
        if (!player.level().isClientSide) {
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
            player.displayClientMessage(Component.translatable("message.currents_of_trade.chart_waypoints_cleared"), true);
            return true;
        }

        // Plot intermediate sea waypoint at player's current position
        List<BlockPos> waypoints = getWaypoints(stack);
        if (waypoints.size() >= MAX_WAYPOINTS) {
            player.displayClientMessage(Component.translatable("message.currents_of_trade.chart_waypoints_full", MAX_WAYPOINTS), true);
            return false;
        }

        BlockPos raw = new BlockPos(player.getBlockX(), Math.max(62, player.getBlockY()), player.getBlockZ());
        // Snap onto water wide enough for the hull; refuse waypoints on land / in tiny puddles
        BlockPos wp = com.lexovian.currentsoftrade.entity.TradeBoatEntity.findNearestNavigableWater(level, raw, 6);
        if (wp == null) {
            player.displayClientMessage(Component.translatable("message.currents_of_trade.chart_waypoint_not_water"), true);
            return false;
        }
        if (!waypoints.isEmpty() && waypoints.get(waypoints.size() - 1).distManhattan(wp) < 8) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag wpList = tag.contains("Waypoints", Tag.TAG_LIST)
                    ? tag.getList("Waypoints", Tag.TAG_COMPOUND)
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
        player.displayClientMessage(Component.translatable("message.currents_of_trade.chart_waypoint_added",
                waypoints.size() + 1, MAX_WAYPOINTS, wp.getX(), wp.getZ()), true);
        return true;
    }

    // Waypoint markers: while the chart is held, plotted waypoints glow and are joined by a dotted line

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide || !(entity instanceof net.minecraft.world.entity.player.Player player)) return;
        if (!isSelected && player.getOffhandItem() != stack) return;
        if (level.getGameTime() % 5L != 0L) return;
        List<BlockPos> waypoints = getWaypoints(stack);
        if (waypoints.isEmpty()) return;

        double prevX = 0.0;
        double prevZ = 0.0;
        double prevY = 0.0;
        for (int i = 0; i < waypoints.size(); i++) {
            BlockPos wp = waypoints.get(i);
            double x = wp.getX() + 1.0;
            double z = wp.getZ() + 1.0;
            double y = wp.getY() + 0.3;
            if (player.distanceToSqr(x, y, z) < 160.0 * 160.0) {
                for (int k = 0; k < 4; k++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            x + (level.random.nextDouble() - 0.5) * 0.3, y + k * 1.1 + level.random.nextDouble() * 0.4,
                            z + (level.random.nextDouble() - 0.5) * 0.3, 0.0, 0.015, 0.0);
                }
            }
            if (i > 0) {
                double dx = x - prevX;
                double dz = z - prevZ;
                double len = Math.hypot(dx, dz);
                int dots = (int) Math.min(80, len / 3.0);
                for (int k = 1; k < dots; k++) {
                    double t = (double) k / dots;
                    double px = prevX + dx * t;
                    double pz = prevZ + dz * t;
                    double py = prevY + (y - prevY) * t;
                    if (player.distanceToSqr(px, py, pz) < 64.0 * 64.0) {
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, px, py, pz, 0.0, 0.0, 0.0);
                    }
                }
            }
            prevX = x;
            prevZ = z;
            prevY = y;
        }
    }

    // Tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (tag.getBoolean("Bound")) {
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_destination", tag.getString("HarborName")));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_coordinates",
                    tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ")));

            List<BlockPos> waypoints = getWaypoints(stack);
            if (!waypoints.isEmpty()) {
                tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_waypoints_count", waypoints.size(), MAX_WAYPOINTS));
                for (int i = 0; i < waypoints.size(); i++) {
                    BlockPos wp = waypoints.get(i);
                    tooltipComponents.add(Component.literal("  §8#" + (i + 1) + ": §7(" + wp.getX() + ", " + wp.getZ() + ")"));
                }
            }
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_waypoint_hint"));

            String regionId = tag.getString("RegionId");
            MaritimeTradeRegion region = MaritimeTradeRegion.byId(regionId);
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_region", region.getDisplayName()));
            tooltipComponents.add(region.getExportDescription());
            tooltipComponents.add(region.getImportDescription());
        } else {
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_unbound"));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_how_to_bind"));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // Static helpers

    public static List<BlockPos> getWaypoints(ItemStack stack) {
        List<BlockPos> result = new ArrayList<>();
        if (stack.isEmpty() || stack.getItem() instanceof RouteJournalItem) {
            return result;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("Waypoints", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag wp = list.getCompound(i);
                result.add(new BlockPos(wp.getInt("X"), wp.getInt("Y"), wp.getInt("Z")));
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
        if (tag.getBoolean("Bound")) {
            return new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        return null;
    }

    @Nullable
    public static String getHarborName(ItemStack stack) {
        if (stack.getItem() instanceof RouteJournalItem) {
            return RouteJournalItem.getSelectedTargetName(stack);
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean("Bound") ? tag.getString("HarborName") : null;
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
        if (targetPos != null && level instanceof ServerLevel serverLevel) {
            List<HarborTradeOffer> liveTrades = HarborSavedData.get(serverLevel).getTrades(targetPos);
            if (!liveTrades.isEmpty()) return liveTrades;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("HarborTrades", Tag.TAG_LIST)) {
            ListTag tradesList = tag.getList("HarborTrades", Tag.TAG_COMPOUND);
            List<HarborTradeOffer> list = new ArrayList<>();
            for (int i = 0; i < tradesList.size(); i++) {
                list.add(HarborTradeOffer.fromTag(tradesList.getCompound(i), registries));
            }
            return list;
        }
        return List.of();
    }
}
