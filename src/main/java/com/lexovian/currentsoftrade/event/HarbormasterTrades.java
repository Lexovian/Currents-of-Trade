package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Trade tables for the Harbormaster villager profession.
 *
 * <p>Integrates dynamic regional pricing across maritime trade regions (Arid, Cold, Tropical, Temperate).
 * Merchants offer local surplus exports at heavy discounts and pay 1.5x - 2.0x Doubloons for high-demand
 * foreign commodities, establishing the Maritime Silk Road arbitrage gameplay.
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class HarbormasterTrades {

    private static final Map<Integer, List<BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer>>> TRADES_BY_LEVEL = new HashMap<>();

    private static MerchantOffer offer(ItemStack cost, ItemStack result, int maxUses, int xp, float mult) {
        return new MerchantOffer(new ItemCost(cost.getItem(), cost.getCount()), result, maxUses, xp, mult);
    }

    private static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp, float mult) {
        return new MerchantOffer(new ItemCost(costA.getItem(), costA.getCount()), Optional.of(new ItemCost(costB.getItem(), costB.getCount())), result, maxUses, xp, mult);
    }

    private static void addTrade(int level, BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer> trade) {
        TRADES_BY_LEVEL.computeIfAbsent(level, k -> new ArrayList<>()).add(trade);
    }

    private static void addTrade(int level, MerchantOffer offer) {
        addTrade(level, (region, rng) -> offer);
    }

    private static boolean tradesInitialized = false;

    private static synchronized void ensureTradesInitialized() {
        if (tradesInitialized) return;
        tradesInitialized = true;

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 1 — Novice: Salt, local fish & currency exchanges
        // ══════════════════════════════════════════════════════════════════════════════

        // Regional Fish Demand:
        // Arid pays 2 Doubloons for fewer fish (water scarcity, fish is a luxury!).
        // Cold pays 1 Doubloon for 16 Cod / 14 Salmon (abundant coastal waters).
        // Temperate/Tropical: baseline.
        addTrade(1, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(Items.COD, 8), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), 16, 2, 0.05F);
            case COLD -> offer(new ItemStack(Items.COD, 16), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F);
            default   -> offer(new ItemStack(Items.COD, 12), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F);
        });

        addTrade(1, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(Items.SALMON, 6), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), 16, 2, 0.05F);
            case COLD -> offer(new ItemStack(Items.SALMON, 14), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F);
            default   -> offer(new ItemStack(Items.SALMON, 10), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F);
        });

        addTrade(1, offer(new ItemStack(Items.COOKED_COD, 8), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 2, 0.05F));

        // Salt provision: Arid ports sell 4 pouches for 1 Doubloon (surplus salt flats)
        addTrade(1, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 4), 16, 2, 0.05F);
            default   -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2), 16, 2, 0.05F);
        });

        // Note: Level 1 fixed 0-XP currency swaps (Emerald <-> Doubloon) are always prepended
        // as the first 2 trades via ensureCurrencySwaps(villager), followed by 2 normal trades from this pool.

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 2 — Apprentice: Spices, aromatics, elixirs & navigation equipment
        // ══════════════════════════════════════════════════════════════════════════════

        // Regional Spices & Aromatics:
        // Arid: sells Spices cheap (1 dbl).
        // Cold: buys Tea Bricks & Vanilla for 6 Doubloons (2x high demand!).
        // Tropical: sells Vanilla cheap (1 dbl).
        // Temperate: sells Tea Bricks cheap (2 dbl).
        addTrade(2, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2), 12, 10, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 12, 10, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), 12, 10, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), 12, 10, 0.05F);
        });

        addTrade(2, (region, rng) -> switch (region) {
            case COLD -> offer(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 12, 10, 0.05F);
            default   -> offer(new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 12, 10, 0.05F);
        });

        // Regional Frankincense Tears & Spiced Cacao Elixir:
        addTrade(2, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), 12, 10, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), 12, 10, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 12, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 12, 0.05F);
        });

        addTrade(2, offer(new ItemStack(Items.COPPER_INGOT, 4), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 5, 0.05F));
        addTrade(2, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(Items.COMPASS, 1), new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get(), 1), 12, 10, 0.05F));
        addTrade(2, offer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.SPYGLASS, 1), 8, 10, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 3 — Journeyman: Trade commodities & harbor tools
        // ══════════════════════════════════════════════════════════════════════════════

        // Regional Luxury Goods:
        // Arid: sells Silk Bale cheap (4 dbl).
        // Tropical: sells Sandalwood cheap (2 dbl); buys Porcelain for 12 dbl (2x!).
        // Temperate: sells Porcelain cheap (4 dbl); buys Sandalwood for 8 dbl.
        // Cold: sells Amber cheap (3 dbl); buys Silk for 10 dbl.
        addTrade(3, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), 8, 20, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), 10, 15, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), 8, 20, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), 8, 20, 0.05F);
        });

        addTrade(3, (region, rng) -> switch (region) {
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 8, 20, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 15, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F);
            default -> offer(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 8, 20, 0.05F);
        });

        // Regional Saffron, Dragon's Blood Resin & Tyrian Purple:
        addTrade(3, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), 10, 15, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), 8, 20, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), 8, 20, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 15, 0.05F);
        });

        addTrade(3, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8), 10, 15, 0.05F);
        });

        addTrade(3, offer(new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 10, 15, 0.05F));
        addTrade(3, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(Items.BOOK, 1), new ItemStack(CurrentsofTrade.TRADE_LEDGER.get(), 1), 8, 20, 0.05F));
        addTrade(3, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get(), 1), 6, 20, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 4 — Expert: Relics, artisan crafts & nautical instruments
        // ══════════════════════════════════════════════════════════════════════════════

        // Arid pays 10 Doubloons for Amber Vial (2x payout!)
        addTrade(4, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), 8, 20, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), 8, 20, 0.05F);
            default   -> offer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F);
        });

        // Regional Damascus Steel, Sea Ivory & Carved Jade Figurine exports:
        addTrade(4, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), 6, 25, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), 6, 25, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), 6, 25, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), 6, 25, 0.05F);
        });

        addTrade(4, offer(new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), 8, 20, 0.05F));
        addTrade(4, offer(new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6), 6, 25, 0.05F));
        addTrade(4, offer(new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 7), 6, 25, 0.05F));
        addTrade(4, offer(new ItemStack(Items.NAUTILUS_SHELL, 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), 8, 20, 0.05F));
        addTrade(4, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.CAPTAINS_PIPE.get(), 1), 4, 25, 0.05F));
        addTrade(4, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.SALVAGE_DREDGE.get(), 1), 4, 20, 0.05F));
        addTrade(4, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(Items.EMERALD, 4), new ItemStack(Items.CLOCK, 1), 8, 15, 0.05F));

        // ══════════════════════════════════════════════════════════════════════════════
        // Level 5 — Master: Royal Dockyard imports, rare relics & ship commissioning
        // ══════════════════════════════════════════════════════════════════════════════
        addTrade(5, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
        });

        addTrade(5, (region, rng) -> switch (region) {
            case ARID -> offer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
            case COLD -> offer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 6, 30, 0.05F);
            case TROPICAL -> offer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
            case TEMPERATE -> offer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14), 6, 30, 0.05F);
        });

        addTrade(5, offer(new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), 6, 30, 0.05F));
        addTrade(5, offer(new ItemStack(Items.HEART_OF_THE_SEA, 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), 4, 30, 0.05F));
        addTrade(5, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), new ItemStack(Items.OAK_BOAT, 1), new ItemStack(CurrentsofTrade.SLOOP_ITEM.get(), 1), 4, 30, 0.05F));
        addTrade(5, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 36), new ItemStack(Items.OAK_CHEST_BOAT, 1), new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get(), 1), 4, 30, 0.05F));
        addTrade(5, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 32), new ItemStack(Items.EMERALD, 16), new ItemStack(Items.CONDUIT, 1), 3, 30, 0.05F));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getTarget() instanceof Villager villager) {
            if (villager.getVillagerData().profession().is(CurrentsofTrade.HARBORMASTER.getKey())) {
                ensureTrades(villager);
            }
        }
    }

    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager().level().isClientSide()) return;
        if (event.getAbstractVillager() instanceof Villager villager) {
            if (villager.getVillagerData().profession().is(CurrentsofTrade.HARBORMASTER.getKey())) {
                ensureTrades(villager);
            }
        }
    }

    private static void ensureCurrencySwaps(MerchantOffers offers) {
        boolean hasEmeraldToDoubloon = false;
        boolean hasDoubloonToEmerald = false;
        for (MerchantOffer existing : offers) {
            if (existing.getBaseCostA().is(Items.EMERALD) && existing.getResult().is(CurrentsofTrade.DOUBLOON.get())) {
                hasEmeraldToDoubloon = true;
            } else if (existing.getBaseCostA().is(CurrentsofTrade.DOUBLOON.get()) && existing.getResult().is(Items.EMERALD)) {
                hasDoubloonToEmerald = true;
            }
        }
        if (!hasDoubloonToEmerald) {
            offers.add(0, offer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), new ItemStack(Items.EMERALD, 2), 16, 0, 0.0F));
        }
        if (!hasEmeraldToDoubloon) {
            offers.add(0, offer(new ItemStack(Items.EMERALD, 2), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 16, 0, 0.0F));
        }
    }

    public static void ensureTrades(Villager villager) {
        if (villager.level().isClientSide()) return;
        if (!villager.getVillagerData().profession().is(CurrentsofTrade.HARBORMASTER.getKey())) return;
        ensureTradesInitialized();
        int level = villager.getVillagerData().level();
        CompoundTag persistent = villager.getPersistentData();
        int unlockedLevel = persistent.getIntOr("HarbormasterUnlockedLevel", 0);
        MerchantOffers offers = villager.getOffers();

        if (offers.isEmpty()) {
            unlockedLevel = 0;
        }

        // Always guarantee the first 2 trades are the 0-XP currency swaps
        ensureCurrencySwaps(offers);

        // If a pre-existing Level 1 Harbormaster has fewer than 2 XP-granting trades, top up Level 1
        int xpGrantingCount = 0;
        for (MerchantOffer existing : offers) {
            if (existing.getXp() > 0) {
                xpGrantingCount++;
            }
        }
        if (unlockedLevel >= 1 && xpGrantingCount < 2) {
            MaritimeTradeRegion region = MaritimeTradeRegion.fromEntity(villager);
            List<BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer>> lvl1Pool = TRADES_BY_LEVEL.get(1);
            if (lvl1Pool != null && !lvl1Pool.isEmpty()) {
                List<BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer>> copy = new ArrayList<>(lvl1Pool);
                java.util.Random rng = new java.util.Random(villager.getId() * 31L + 101L);
                Collections.shuffle(copy, rng);
                RandomSource mcRng = villager.getRandom();
                for (int i = 0; i < copy.size() && xpGrantingCount < 2; i++) {
                    MerchantOffer candidate = copy.get(i).apply(region, mcRng);
                    if (candidate != null) {
                        offers.add(candidate);
                        xpGrantingCount++;
                    }
                }
            }
        }

        if (unlockedLevel < level) {
            MaritimeTradeRegion region = MaritimeTradeRegion.fromEntity(villager);
            for (int lvl = unlockedLevel + 1; lvl <= Math.min(level, 5); lvl++) {
                List<BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer>> available = TRADES_BY_LEVEL.get(lvl);
                if (available != null && !available.isEmpty()) {
                    List<BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer>> copy = new ArrayList<>(available);
                    // Deterministic shuffle per villager and level
                    java.util.Random rng = new java.util.Random(villager.getId() * 31L + lvl * 101L);
                    Collections.shuffle(copy, rng);
                    RandomSource mcRng = villager.getRandom();
                    int countToAdd = Math.min(2, copy.size());
                    for (int i = 0; i < countToAdd; i++) {
                        MerchantOffer off = copy.get(i).apply(region, mcRng);
                        if (off != null) {
                            offers.add(off);
                        }
                    }
                }
            }
            persistent.putInt("HarbormasterUnlockedLevel", level);
        }
    }
}
