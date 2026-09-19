package com.lexovian.currentsoftrade.world.harbor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class HarborTradeOffer {

    private final ItemStack costA;
    private final ItemStack costB;
    private final ItemStack result;

    public HarborTradeOffer(net.minecraft.world.item.trading.MerchantOffer offer) {
        this(offer.getBaseCostA(), offer.getCostB(), offer.getResult());
    }

    public HarborTradeOffer(ItemStack costA, ItemStack costB, ItemStack result) {
        this.costA = costA.copy();
        this.costB = costB != null ? costB.copy() : ItemStack.EMPTY;
        this.result = result.copy();
    }

    public ItemStack getCostA() {
        return costA;
    }

    public ItemStack getCostB() {
        return costB;
    }

    public ItemStack getResult() {
        return result;
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!costA.isEmpty()) {
            tag.put("CostA", costA.save(registries));
        }
        if (!costB.isEmpty()) {
            tag.put("CostB", costB.save(registries));
        }
        if (!result.isEmpty()) {
            tag.put("Result", result.save(registries));
        }
        return tag;
    }

    public static HarborTradeOffer fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack a = tag.contains("CostA") ? ItemStack.parse(registries, tag.getCompound("CostA")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack b = tag.contains("CostB") ? ItemStack.parse(registries, tag.getCompound("CostB")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack res = tag.contains("Result") ? ItemStack.parse(registries, tag.getCompound("Result")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        return new HarborTradeOffer(a, b, res);
    }
}
