package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.BasicItemListing;
import net.minecraftforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * Trade tables for the Harbormaster villager profession.
 *
 * <p>Integrates dynamic regional pricing across maritime trade regions (Arid, Cold, Tropical, Temperate).
 * Merchants offer local surplus exports at heavy discounts and pay 1.5x - 2.0x Doubloons for high-demand
 * foreign commodities, establishing the Maritime Silk Road arbitrage gameplay.
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class HarbormasterTrades {

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != CurrentsofTrade.HARBORMASTER.get()) return;

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 1 — Novice: Harbor intake, provisions & currency exchange
        // ══════════════════════════════════════════════════════════════════════════════

        // Dynamic fish intake: Arid ports pay 2x for cold sea catch (8 cod -> 2 dbl), Cold ports require more (16 cod -> 1 dbl)
        trades.get(1).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(Items.COD, 8), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), 16, 2, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(Items.COD, 16), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F).getOffer(null, rng);
            default   -> new BasicItemListing(new ItemStack(Items.COD, 12), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F).getOffer(null, rng);
        }));

        trades.get(1).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(Items.SALMON, 6), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), 16, 2, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(Items.SALMON, 14), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F).getOffer(null, rng);
            default   -> new BasicItemListing(new ItemStack(Items.SALMON, 10), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F).getOffer(null, rng);
        }));

        trades.get(1).add(new BasicItemListing(new ItemStack(Items.COOKED_COD, 8), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F));

        // Salt provision: Arid ports sell 4 pouches for 1 Doubloon (surplus salt flats)
        trades.get(1).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 4), 16, 2, 0.05F).getOffer(null, rng);
            default   -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2), 16, 2, 0.05F).getOffer(null, rng);
        }));

        // Currency swaps — xp=0 prevents XP-farming via arbitrage
        trades.get(1).add(new BasicItemListing(new ItemStack(Items.EMERALD, 2),                  new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 0, 0.0F));
        trades.get(1).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(Items.EMERALD, 2),                  16, 0, 0.0F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 2 — Apprentice: Spices, aromatics & navigation equipment
        // ══════════════════════════════════════════════════════════════════════════════

        // Regional Spices & Aromatics:
        // Arid: sells Spices cheap (1 dbl).
        // Cold: buys Tea Bricks & Vanilla for 6 Doubloons (2x high demand!).
        // Tropical: sells Vanilla cheap (1 dbl).
        // Temperate: sells Tea Bricks cheap (2 dbl).
        trades.get(2).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2), 12, 10, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 12, 10, 0.05F).getOffer(null, rng);
            case TROPICAL -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), 12, 10, 0.05F).getOffer(null, rng);
            case TEMPERATE -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), 12, 10, 0.05F).getOffer(null, rng);
        }));

        trades.get(2).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case COLD -> new BasicItemListing(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 12, 10, 0.05F).getOffer(null, rng);
            default   -> new BasicItemListing(new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 12, 10, 0.05F).getOffer(null, rng);
        }));

        trades.get(2).add(new BasicItemListing(new ItemStack(Items.COPPER_INGOT, 4), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 5, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(Items.COMPASS, 1), new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get(), 1), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.SPYGLASS, 1), 8, 10, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 3 — Journeyman: Trade commodities & harbor tools
        // ══════════════════════════════════════════════════════════════════════════════

        // Regional Luxury Goods:
        // Arid: sells Silk Bale cheap (4 dbl).
        // Tropical: sells Sandalwood cheap (2 dbl); buys Porcelain for 12 dbl (2x!).
        // Temperate: sells Porcelain cheap (4 dbl); buys Sandalwood for 8 dbl.
        // Cold: sells Amber cheap (3 dbl); buys Silk for 10 dbl.
        trades.get(3).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), 8, 20, 0.05F).getOffer(null, rng);
            case TROPICAL -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), 10, 15, 0.05F).getOffer(null, rng);
            case TEMPERATE -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), 8, 20, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), 8, 20, 0.05F).getOffer(null, rng);
        }));

        trades.get(3).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case TROPICAL -> new BasicItemListing(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 8, 20, 0.05F).getOffer(null, rng);
            case TEMPERATE -> new BasicItemListing(new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 15, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F).getOffer(null, rng);
            default -> new BasicItemListing(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 8, 20, 0.05F).getOffer(null, rng);
        }));

        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 10, 15, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(Items.BOOK, 1), new ItemStack(CurrentsofTrade.TRADE_LEDGER.get(), 1), 8, 20, 0.05F));
        trades.get(3).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get(), 1), 6, 20, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 4 — Expert: Relics & nautical instruments
        // ══════════════════════════════════════════════════════════════════════════════

        // Arid pays 10 Doubloons for Amber Vial (2x payout!)
        trades.get(4).add(new RegionalTradeListing((region, rng) -> switch (region) {
            case ARID -> new BasicItemListing(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F).getOffer(null, rng);
            case COLD -> new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), 8, 20, 0.05F).getOffer(null, rng);
            default   -> new BasicItemListing(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F).getOffer(null, rng);
        }));

        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 6, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 6, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(Items.NAUTILUS_SHELL, 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 8, 20, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.CAPTAINS_PIPE.get(), 1), 4, 25, 0.05F));
        trades.get(4).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(Items.EMERALD, 4), new ItemStack(Items.CLOCK, 1), 8, 15, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 5 — Master: Rare relics & ship commissioning
        // ══════════════════════════════════════════════════════════════════════════════
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 6, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(Items.HEART_OF_THE_SEA, 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), 4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), new ItemStack(Items.OAK_BOAT, 1), new ItemStack(CurrentsofTrade.SLOOP_ITEM.get(), 1), 4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 36), new ItemStack(Items.OAK_CHEST_BOAT, 1), new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get(), 1), 4, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 32), new ItemStack(Items.EMERALD, 16), new ItemStack(Items.CONDUIT, 1), 3, 30, 0.05F));
    }
}
