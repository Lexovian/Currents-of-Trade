package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * Trade tables for the Harbormaster villager profession.
 *
 * <p>Direction conventions used throughout:
 * <ul>
 *   <li><b>BUY</b>  — player gives items → receives Doubloons/Emeralds.</li>
 *   <li><b>SELL</b> — player spends Doubloons/Emeralds → receives items.</li>
 * </ul>
 *
 * <p>Currency exchange (Lvl 1) intentionally carries {@code villagerXp = 0} to prevent
 * players from power-levelling the Harbormaster purely through arbitrage loops.
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class HarbormasterTrades {

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != CurrentsofTrade.HARBORMASTER.get()) return;

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Level 1 — Novice: Harbor intake, provisions & currency exchange
        trades.get(1).add(new BasicItemListing(new ItemStack(Items.COD,      12), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2,  0.05F));
        trades.get(1).add(new BasicItemListing(new ItemStack(Items.SALMON,   10), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2,  0.05F));
        trades.get(1).add(new BasicItemListing(new ItemStack(Items.COOKED_COD, 8), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2,  0.05F));
        trades.get(1).add(new BasicItemListing(new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F));
        // Currency swaps — xp=0 prevents XP-farming via arbitrage
        trades.get(1).add(new BasicItemListing(new ItemStack(Items.EMERALD, 2),                     new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 0, 0.0F));
        trades.get(1).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1),    new ItemStack(Items.EMERALD, 2),                  16, 0, 0.0F));
        trades.get(1).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1),    new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2), 16, 2, 0.05F));

        // Level 2 — Apprentice: Spices, navigation equipment
        trades.get(2).add(new BasicItemListing(new ItemStack(CurrentsofTrade.SPICE_SACK.get(),    2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(CurrentsofTrade.TEA_BRICK.get(),     2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(),2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(Items.COPPER_INGOT, 4),                  new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 5,  0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(Items.COMPASS, 1), new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get(), 1), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.SPYGLASS, 1), 8, 10, 0.05F));

        // Level 3 — Journeyman: Trade commodities & harbor tools
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.SILK_BALE.get(),          1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 8,  20, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(),     1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 8,  20, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.SANDALWOOD.get(),         2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 10, 15, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(),2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 10, 15, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(Items.BOOK, 1), new ItemStack(CurrentsofTrade.TRADE_LEDGER.get(), 1),    8, 20, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4),                                new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get(), 1), 6, 20, 0.05F));

        // Level 4 — Expert: Relics & nautical instruments
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(),    1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(),1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.STORM_GLASS.get(),   1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 6, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(),1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 6, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(Items.NAUTILUS_SHELL, 1),                 new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 8, 20, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5),                                new ItemStack(CurrentsofTrade.CAPTAINS_PIPE.get(), 1),  4, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(Items.EMERALD, 4), new ItemStack(Items.CLOCK, 1),                         8, 15, 0.05F));

        // Level 5 — Master: Rare relics & ship commissioning
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 6, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(Items.HEART_OF_THE_SEA, 1),               new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), 4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), new ItemStack(Items.OAK_BOAT, 1),       new ItemStack(CurrentsofTrade.SLOOP_ITEM.get(), 1),      4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 36), new ItemStack(Items.OAK_CHEST_BOAT, 1), new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get(), 1), 4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 32), new ItemStack(Items.EMERALD, 16),        new ItemStack(Items.CONDUIT, 1),                         3, 30, 0.05F));
    }
}
