package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Dynamic villager trade listing that adapts its prices, costs, and offered commodities
 * based on the trader entity's maritime region and climate.
 */
public class RegionalTradeListing {

    private final BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer> offerFactory;

    public RegionalTradeListing(BiFunction<MaritimeTradeRegion, RandomSource, MerchantOffer> offerFactory) {
        this.offerFactory = offerFactory;
    }

    @Nullable
    public MerchantOffer getOffer(Entity trader, RandomSource random) {
        MaritimeTradeRegion region = MaritimeTradeRegion.fromEntity(trader);
        return this.offerFactory.apply(region, random);
    }
}
