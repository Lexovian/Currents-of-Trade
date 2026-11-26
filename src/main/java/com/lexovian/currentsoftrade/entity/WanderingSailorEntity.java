package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WanderingSailorEntity extends WanderingTrader {

    public static final int LEAGUE_COUNT = 6;
    private static final EntityDataAccessor<Integer> DATA_TRADE_LEAGUE =
            SynchedEntityData.defineId(WanderingSailorEntity.class, EntityDataSerializers.INT);

    // Stay time: min 7 minutes (8,400 ticks), max 15 minutes (18,000 ticks)
    public static final int MIN_DESPAWN_DELAY = 8400;
    public static final int MAX_DESPAWN_DELAY = 18000;
    public static final int DEFAULT_DESPAWN_DELAY = 12000; // ~10 minutes
    private int sailorDespawnDelay = DEFAULT_DESPAWN_DELAY;

    @Nullable
    private UUID mooredBoatUUID = null;

    public @Nullable UUID getMooredBoatUUID() {
        return this.mooredBoatUUID;
    }

    public void setMooredBoatUUID(@Nullable UUID uuid) {
        this.mooredBoatUUID = uuid;
    }

    public int getTradeLeague() {
        return Mth.clamp(this.entityData.get(DATA_TRADE_LEAGUE), 0, LEAGUE_COUNT - 1);
    }

    public void setTradeLeague(int leagueId) {
        int clamped = Mth.clamp(leagueId, 0, LEAGUE_COUNT - 1);
        this.entityData.set(DATA_TRADE_LEAGUE, clamped);
        this.setCustomName(Component.translatable("entity.currents_of_trade.wandering_sailor.league_" + clamped));
        if (this.level() instanceof ServerLevel serverLevel) {
            this.updateTrades(serverLevel);
        }
    }

    public WanderingSailorEntity(EntityType<? extends WanderingTrader> entityType, Level level) {
        super(entityType, level);
        int initLeague = level != null ? level.getRandom().nextInt(LEAGUE_COUNT) : 0;
        this.entityData.set(DATA_TRADE_LEAGUE, initLeague);
        this.setCustomName(Component.translatable("entity.currents_of_trade.wandering_sailor.league_" + initLeague));
        this.setCustomNameVisible(true);
        if (level != null && !level.isClientSide()) {
            this.sailorDespawnDelay = level.getRandom().nextIntBetweenInclusive(MIN_DESPAWN_DELAY, MAX_DESPAWN_DELAY);
            this.setDespawnDelay(this.sailorDespawnDelay);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TRADE_LEAGUE, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D);
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        MerchantOffers offers = this.getOffers();
        offers.clear();

        // ── Fixed trades: always shown, every sailor, every visit ────────────────
        // Currency exchange — xp=0 prevents XP-exploit loop, trades still fully functional
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2),                      new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 10, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),     new ItemStack(Items.EMERALD, 2),                  10, 0, 0.0F));

        // ── Maritime Trade League Signature Deals (2 guaranteed specialty offers per league) ──
        switch (this.getTradeLeague()) {
            case 0 -> { // Golden Levant Company
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), 8, 8, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5), new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1),      6, 12, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), 6, 8, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2), 6, 8, 0.05F));
            }
            case 1 -> { // Boreal Amber Hanse
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1),    6, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), 5, 14, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1),   5, 12, 0.05F));
            }
            case 2 -> { // Emerald Archipelago Guild
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 1),   6, 8, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), 6, 8, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), 6, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2), 6, 8, 0.05F));
            }
            case 3 -> { // Imperial Jade Flotilla
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), 6, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1),  5, 14, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3), new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1),    5, 12, 0.05F));
            }
            case 4 -> { // Crimson Privateer Syndicate
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1),      5, 14, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2), new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1), 6, 8, 0.05F));
            }
            default -> { // Azure Thalassocracy
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1), 4, 14, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1),     4, 14, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4), new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1),    5, 12, 0.05F));
            }
        }

        // Small vanilla food provision (secondary role — keeps sailor flavour)
        offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),     new ItemStack(Items.COOKED_SALMON, 2),             8,  1, 0.05F));
        // Reverse: sailor buys cooked fish from the player
        offers.add(new MerchantOffer(new ItemCost(Items.COOKED_COD, 8),                   new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1),  8,  3, 0.05F));
        // Exotic Livestock Trade: Wandering Sailors are the sole supplier of caged animals (#currents_of_trade:cageable_animals)
        ItemStack primaryCage = com.lexovian.currentsoftrade.item.AnimalCageItem.createDeterministicCage((long) this.getId() * 7919L + 17L);
        offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),     primaryCage,                                       2, 12, 0.05F));

        // ── Pool 1: Maritime Provisions & Historical Vanilla Trade Goods (picks 4) ──
        List<MerchantOffer> pool1 = new ArrayList<>();
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2),      8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2),      6, 5, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2),       6, 5, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2),  6, 5, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.SUGAR, 12),                          8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.COCOA_BEANS, 8),                     8, 3, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(Items.HONEY_BOTTLE, 3),                    6, 4, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.WHITE_WOOL, 8),                      8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.LEATHER, 6),                         8, 3, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.GLASS, 12),                          8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.PAPER, 12),                          8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.COPPER_INGOT, 8),                    8, 3, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(Items.IRON_INGOT, 4),                      6, 4, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(Items.LAPIS_LAZULI, 6),                    6, 5, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.DRIED_KELP_BLOCK, 4),                8, 2, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(Items.NAUTILUS_SHELL, 1),            new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3),        4, 10, 0.05F));
        pool1.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(Items.INK_SAC, 8),                         8, 2, 0.05F));

        // ── Pool 2: Regional Exotic Commodities, Bulk Crates & Navigation (picks 4) ─
        List<MerchantOffer> pool2 = new ArrayList<>();
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), 5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2),    5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2),    5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2),     5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1),       5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 1),         5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4),  new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1),     4, 10, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1),      3, 15, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), 3, 15, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1),        3, 15, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1),        3, 15, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 7),  new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1),          3, 15, 0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1), 5, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get(), 1),     4, 5,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.BOSUNS_WHISTLE.get(), 1),     3, 8,  0.05F));
        pool2.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4),  new ItemStack(CurrentsofTrade.TRADE_CHARTER.get(), 1),      4, 12, 0.05F));

        // ── Pool 3: Rare Relics, Shipwright Gear & Extra Exotic Livestock (picks 3) ─
        List<MerchantOffer> pool3 = new ArrayList<>();
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4),  new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1),         4, 12, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1),    3, 15, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1),        3, 15, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1),      3, 18, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1),      3, 18, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1),     3, 18, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1),    3, 18, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.CAPTAINS_PIPE.get(), 1),      2, 20, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 7),  new ItemStack(CurrentsofTrade.CAPTAINS_SPYGLASS.get(), 1),  2, 20, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 9),  new ItemStack(CurrentsofTrade.SWIVEL_HARPOON.get(), 1),     2, 22, 0.05F));
        ItemStack secondaryCage = com.lexovian.currentsoftrade.item.AnimalCageItem.createDeterministicCage((long) this.getId() * 104729L + 31L);
        EntityType<?> primaryType = com.lexovian.currentsoftrade.item.AnimalCageItem.getStoredAnimalType(primaryCage);
        for (int attempt = 1; attempt <= 12 && primaryType != null && primaryType == com.lexovian.currentsoftrade.item.AnimalCageItem.getStoredAnimalType(secondaryCage); attempt++) {
            secondaryCage = com.lexovian.currentsoftrade.item.AnimalCageItem.createDeterministicCage((long) this.getId() * 104729L + 31L + attempt * 997L);
        }
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  secondaryCage, 2, 14, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5),    3, 15, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.NARWHAL_IVORY.get(), 1),   new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6),    3, 18, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 10), Optional.of(new ItemCost(Items.EMERALD, 4)),  new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1),  2, 25, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 26), Optional.of(new ItemCost(Items.EMERALD, 6)),  new ItemStack(CurrentsofTrade.SLOOP_ITEM.get(), 1),       1, 30, 0.05F));
        pool3.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 18), Optional.of(new ItemCost(Items.EMERALD, 8)),  new ItemStack(Items.HEART_OF_THE_SEA, 1),                 1, 30, 0.05F));

        // ── Shuffle all 3 pools with deterministic seeds derived from this sailor's ID ─
        java.util.Random rng = new java.util.Random((long) this.getId() * 2654435761L);
        Collections.shuffle(pool1, rng);
        rng.setSeed((long) this.getId() * 2246822519L);
        Collections.shuffle(pool2, rng);
        rng.setSeed((long) this.getId() * 3266489917L);
        Collections.shuffle(pool3, rng);

        // Add up to 4 from Pool 1, 4 from Pool 2, and 3 from Pool 3 — strictly preventing duplicates & same-sailor buy/sell loops
        addFilteredFromPool(offers, pool1, 4);
        addFilteredFromPool(offers, pool2, 4);
        addFilteredFromPool(offers, pool3, 3);
    }

    private static void addFilteredFromPool(MerchantOffers offers, List<MerchantOffer> pool, int limit) {
        int added = 0;
        for (MerchantOffer cand : pool) {
            if (added >= limit) break;
            if (!conflictsWithExisting(offers, cand)) {
                offers.add(cand);
                added++;
            }
        }
    }

    private static boolean conflictsWithExisting(MerchantOffers offers, MerchantOffer cand) {
        ItemStack candA = cand.getBaseCostA();
        ItemStack candRes = cand.getResult();
        boolean candSellsItem = candA.is(CurrentsofTrade.DOUBLOON.get()) || candA.is(Items.EMERALD);
        boolean candBuysItem = candRes.is(CurrentsofTrade.DOUBLOON.get()) || candRes.is(Items.EMERALD);

        for (MerchantOffer ex : offers) {
            ItemStack exA = ex.getBaseCostA();
            ItemStack exRes = ex.getResult();
            boolean exIsCurrencySwap = (exA.is(Items.EMERALD) && exRes.is(CurrentsofTrade.DOUBLOON.get()))
                    || (exA.is(CurrentsofTrade.DOUBLOON.get()) && exRes.is(Items.EMERALD));
            if (exIsCurrencySwap) continue;

            // Block duplicate sales of the same item (allow a second Animal Cage ONLY if it holds a different species)
            if (candSellsItem && ItemStack.isSameItem(exRes, candRes)) {
                if (candRes.getItem() instanceof com.lexovian.currentsoftrade.item.AnimalCageItem) {
                    EntityType<?> exAnimal = com.lexovian.currentsoftrade.item.AnimalCageItem.getStoredAnimalType(exRes);
                    EntityType<?> candAnimal = com.lexovian.currentsoftrade.item.AnimalCageItem.getStoredAnimalType(candRes);
                    if (exAnimal != null && exAnimal == candAnimal) return true;
                } else {
                    return true;
                }
            }
            // Block duplicate buybacks of the same item
            if (candBuysItem && ItemStack.isSameItem(exA, candA)) {
                return true;
            }
            // Block inverse buy/sell arbitrage loop on the same sailor
            if (candSellsItem && ItemStack.isSameItem(exA, candRes)) return true;
            if (candBuysItem && ItemStack.isSameItem(exRes, candA)) return true;
        }
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.is(Items.NAME_TAG)) {
            itemstack.interactLivingEntity(player, this, hand);
            return InteractionResult.SUCCESS;
        }
        if (this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            if (--this.sailorDespawnDelay <= 0) {
                // Despawn gracefully with smoke particles
                if (this.getVehicle() != null) {
                    var vehicle = this.getVehicle();
                    this.stopRiding();
                    vehicle.discard();
                }
                if (this.mooredBoatUUID != null && this.level() instanceof ServerLevel serverLevel) {
                    net.minecraft.world.entity.Entity boat = serverLevel.getEntity(this.mooredBoatUUID);
                    if (boat != null && boat.isAlive()) {
                        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                boat.getX(), boat.getY() + 0.5, boat.getZ(),
                                15, 0.4, 0.4, 0.4, 0.02);
                        boat.discard();
                    }
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            this.getX(), this.getY() + 1.0, this.getZ(),
                            20, 0.5, 0.5, 0.5, 0.02);
                }
                this.discard();
            }
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!this.level().isClientSide() && this.mooredBoatUUID != null && this.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity boat = serverLevel.getEntity(this.mooredBoatUUID);
            if (boat != null && boat.isAlive()) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                boat.getX(), boat.getY() + 0.5, boat.getZ(),
                                15, 0.4, 0.4, 0.4, 0.02);
                boat.discard();
            }
        }
    }

    public int getSailorDespawnDelay() {
        return this.sailorDespawnDelay;
    }

    public void setSailorDespawnDelay(int delay) {
        this.sailorDespawnDelay = delay;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SailorDespawnDelay", this.sailorDespawnDelay);
        output.putInt("TradeLeague", this.getTradeLeague());
        if (this.mooredBoatUUID != null) {
            output.store("MooredBoatUUID", UUIDUtil.CODEC, this.mooredBoatUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.sailorDespawnDelay = input.getIntOr("SailorDespawnDelay", this.sailorDespawnDelay);
        input.getInt("TradeLeague").ifPresent(league -> this.entityData.set(DATA_TRADE_LEAGUE, Mth.clamp(league, 0, LEAGUE_COUNT - 1)));
        this.mooredBoatUUID = input.read("MooredBoatUUID", UUIDUtil.CODEC).orElse(this.mooredBoatUUID);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }
}
