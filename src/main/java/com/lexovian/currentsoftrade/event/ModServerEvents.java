package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.spawner.WanderingSailorSpawner;
import com.lexovian.currentsoftrade.world.village.VillagePoolInjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (com.lexovian.currentsoftrade.item.AnimalCageItem.tryCaptureEntity(event.getEntity(), event.getTarget(), event.getHand())) {
            event.getEntity().swing(event.getHand(), true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor.clearCache();
        com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity.clearCache();
        VillagePoolInjection.inject(event.getServer());
    }

    /** Equivalent of NeoForge's LevelTickEvent.Post. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            WanderingSailorSpawner.serverTick(serverLevel);
            com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity.tickOceanFlotsamSpawner(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
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

    /** Any block change (place, break, bucket, fluid spread, piston) invalidates the ship nav cache of that chunk. */
    @SubscribeEvent
    public static void onNeighborNotify(net.minecraftforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.entity.TradeBoatEntity.invalidateChunkNavAt(serverLevel, event.getPos());
        }
    }

    /** Salted food bonus (1.21 stored it in the stack's FOOD component; 1.20.1 applies it on eat). */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide) {
            com.lexovian.currentsoftrade.item.SaltPouchItem.applySaltedBonus(event.getEntity(), event.getItem());
        }
    }

    /**
     * Resolves Message-in-a-Bottle Buried Treasure Maps only when actually crafted (avoiding preview lag & map spam).
     */
    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe.resolvePendingTreasureMap(
                    event.getCrafting(), serverLevel, event.getEntity().blockPosition());
        }
    }

    /**
     * Allows players aiming at the visual mast/sails above the hull hitbox (or riding/standing on the ship)
     * to dye sails, mount banners/pennants ("Flama"), lanterns, or copper hull sheathing with Right-Click.
     */
    @SubscribeEvent
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe.resolvePendingTreasureMap(
                    event.getItemStack(), serverLevel, event.getEntity().blockPosition());
        }
        if (com.lexovian.currentsoftrade.entity.SloopEntity.tryCustomizeNearbyShip(event.getEntity(), event.getHand())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            event.setCanceled(true);
        }
    }
}
