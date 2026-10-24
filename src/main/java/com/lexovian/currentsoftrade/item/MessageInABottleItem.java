package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * Message in a Bottle (Şişede Mesaj).
 *
 * <p>Uncorking reveals maritime secrets, lost tales, authentic treasure maps,
 * or ancient sea currencies.
 */
public class MessageInABottleItem extends Item {

    public MessageInABottleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            RandomSource rng = level.getRandom();
            int msgIndex = 1 + rng.nextInt(5);
            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.bottle_unsealed_" + msgIndex),
                    false
            );

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.9F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);

            // Determine bonus ocean discovery
            float roll = rng.nextFloat();
            ItemStack discovery = ItemStack.EMPTY;

            if (roll < 0.25F && level instanceof ServerLevel serverLevel) {
                // 25% Buried Treasure Map
                discovery = createTreasureMap(serverLevel, player.blockPosition());
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.bottle_found_map"), true);
            } else if (roll < 0.45F && level instanceof ServerLevel serverLevel) {
                // 20% Ancient Pre-bound Nautical Chart
                discovery = createAncientChart(serverLevel, player.blockPosition(), rng);
                level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 1.2F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.bottle_found_chart"), true);
            } else if (roll < 0.70F) {
                // 25% Sunken Doubloons & Rare Curiosities
                int relicRoll = rng.nextInt(6);
                discovery = switch (relicRoll) {
                    case 0 -> new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2 + rng.nextInt(4));
                    case 1 -> new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1);
                    case 2 -> new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1);
                    case 3 -> new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1);
                    case 4 -> new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1);
                    default -> new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1);
                };
                level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.bottle_found_relic", discovery.getHoverName().getString()), true);
            }

            if (!discovery.isEmpty()) {
                if (!player.getInventory().add(discovery)) {
                    player.drop(discovery, false);
                }
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    return InteractionResultHolder.sidedSuccess(emptyBottle, level.isClientSide());
                }
                if (!player.getInventory().add(emptyBottle)) {
                    player.drop(emptyBottle, false);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private ItemStack createTreasureMap(ServerLevel serverLevel, BlockPos origin) {
        BlockPos treasurePos = serverLevel.findNearestMapStructure(
                StructureTags.ON_TREASURE_MAPS,
                origin,
                250,
                false
        );

        BlockPos target = treasurePos != null ? treasurePos : origin;
        ItemStack mapStack = MapItem.create(serverLevel, target.getX(), target.getZ(), (byte) 1, true, true);
        MapItem.renderBiomePreviewMap(serverLevel, mapStack);
        MapItemSavedData.addTargetDecoration(mapStack, target, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
        mapStack.setHoverName(Component.translatable("filled_map.buried_treasure"));
        return mapStack;
    }

    private ItemStack createAncientChart(ServerLevel serverLevel, BlockPos playerPos, RandomSource rng) {
        ItemStack chart = new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get());

        // Prefer a real generated village dock if any exist in the world
        List<BlockPos> placedDocks = com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor.getPlacedDocks();
        BlockPos chosenPos = null;
        for (BlockPos dockPos : placedDocks) {
            if (dockPos.distSqr(playerPos) > 64 * 64) {
                chosenPos = dockPos;
                break;
            }
        }
        if (chosenPos == null && !placedDocks.isEmpty()) {
            chosenPos = placedDocks.get(rng.nextInt(placedDocks.size()));
        }

        if (chosenPos == null) {
            int offsetX = (rng.nextBoolean() ? 1 : -1) * (300 + rng.nextInt(600));
            int offsetZ = (rng.nextBoolean() ? 1 : -1) * (300 + rng.nextInt(600));
            chosenPos = new BlockPos(playerPos.getX() + offsetX, 64, playerPos.getZ() + offsetZ);
        }

        final BlockPos targetPos = chosenPos;
        final String regionId = com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion.fromBiome(serverLevel.getBiome(targetPos)).getId();
        final String harborName = "Ancient Atoll " + Math.abs(targetPos.getX() % 100);

        CustomData.update(chart, tag -> {
            tag.putInt("TargetX", targetPos.getX());
            tag.putInt("TargetY", targetPos.getY());
            tag.putInt("TargetZ", targetPos.getZ());
            tag.putString("HarborName", harborName);
            tag.putString("RegionId", regionId);
            tag.putBoolean("Bound", true);
        });

        return chart;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.message_in_a_bottle.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.message_in_a_bottle.lore"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.message_in_a_bottle.use_hint"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.message_in_a_bottle.treasure_hint"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

