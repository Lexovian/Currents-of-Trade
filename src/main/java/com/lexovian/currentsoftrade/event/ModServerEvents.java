package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.world.spawner.WanderingSailorSpawner;
import com.lexovian.currentsoftrade.world.village.VillagePoolInjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onItemCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe.resolvePendingTreasureMap(
                    event.getCrafting(), serverLevel, event.getEntity().blockPosition());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe.resolvePendingTreasureMap(
                    event.getItemStack(), serverLevel, event.getEntity().blockPosition());
        }
        if (SloopEntity.tryCustomizeNearbyShip(event.getEntity(), event.getHand())) {
            event.getEntity().swing(event.getHand(), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        // Any block change (place, break, bucket, fluid spread, piston) invalidates the ship nav cache of that chunk
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.entity.TradeBoatEntity.invalidateChunkNavAt(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (com.lexovian.currentsoftrade.item.AnimalCageItem.tryCaptureEntity(event.getEntity(), event.getTarget(), event.getHand())) {
            event.getEntity().swing(event.getHand(), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor.clearCache();
        com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity.clearCache();
        VillagePoolInjection.inject(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WanderingSailorSpawner.serverTick(serverLevel);
            com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity.tickOceanFlotsamSpawner(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("cot")
                        .then(net.minecraft.commands.Commands.literal("rank").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (player.level() instanceof ServerLevel sl) {
                                var data = com.lexovian.currentsoftrade.world.harbor.GuildReputationData.get(sl);
                                int pts = data.getReputation(player.getUUID());
                                var rank = data.getRank(player.getUUID());
                                int discPct = (int) Math.round(rank.feeDiscount * 100.0D);
                                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                                        "message.currents_of_trade.cot_rank_info", rank.getDisplayName(), pts, discPct), false);
                            }
                            return 1;
                        })
                        .then(net.minecraft.commands.Commands.literal("add")
                                .requires(src -> src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && sp.getAbilities().instabuild)
                                .then(net.minecraft.commands.Commands.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> {
                                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            if (player.getAbilities().instabuild && player.level() instanceof ServerLevel sl) {
                                                int amt = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "points");
                                                com.lexovian.currentsoftrade.world.harbor.GuildReputationData.awardReputation(sl, player, amt);
                                                var data = com.lexovian.currentsoftrade.world.harbor.GuildReputationData.get(sl);
                                                int pts = data.getReputation(player.getUUID());
                                                var rank = data.getRank(player.getUUID());
                                                int discPct = (int) Math.round(rank.feeDiscount * 100.0D);
                                                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                                                        "message.currents_of_trade.cot_rank_info", rank.getDisplayName(), pts, discPct), false);
                                            }
                                            return 1;
                                        }))))
                        .then(net.minecraft.commands.Commands.literal("wind").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (player.level() instanceof ServerLevel sl) {
                                String windName = com.lexovian.currentsoftrade.world.market.TradeWinds.getWindCardinal(sl);
                                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                                        "message.currents_of_trade.cot_wind_info", windName), false);
                            }
                            return 1;
                        }))
        );
    }
}
