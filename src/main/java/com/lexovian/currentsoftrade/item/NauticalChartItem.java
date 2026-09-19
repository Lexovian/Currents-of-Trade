package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
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

    // --- Right-click on Anchor Point  ---

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
            final List<HarborTradeOffer> trades =
                    be instanceof AnchorPointBlockEntity aBe ? aBe.getHarborTrades() : List.of();

            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putInt("TargetX", pos.getX());
                tag.putInt("TargetY", pos.getY());
                tag.putInt("TargetZ", pos.getZ());
                tag.putString("HarborName", finalName);
                tag.putBoolean("Bound", true);

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

    // --- Tooltip  ---

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (tag.getBoolean("Bound")) {
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_destination", tag.getString("HarborName")));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_coordinates",
                    tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ")));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_unbound"));
            tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.chart_how_to_bind"));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // --- Static helpers  ---

    @Nullable
    public static BlockPos getTargetPos(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("Bound")) {
            return new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        return null;
    }

    @Nullable
    public static String getHarborName(ItemStack stack) {
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
