package com.lexovian.currentsoftrade.world.harbor;

import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class HarborTradeOffer {

    private final ItemStack costA;
    private final ItemStack costB;
    private final ItemStack result;
    private final boolean urgentBounty;

    public HarborTradeOffer(net.minecraft.world.item.trading.MerchantOffer offer) {
        this(offer.getBaseCostA(), offer.getCostB(), offer.getResult(), false);
    }

    public HarborTradeOffer(ItemStack costA, ItemStack costB, ItemStack result) {
        this(costA, costB, result, false);
    }

    public HarborTradeOffer(ItemStack costA, ItemStack costB, ItemStack result, boolean urgentBounty) {
        this.costA = costA.copy();
        this.costB = costB != null ? costB.copy() : ItemStack.EMPTY;
        this.result = result.copy();
        this.urgentBounty = urgentBounty;
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

    public boolean isUrgentBounty() {
        return urgentBounty;
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!costA.isEmpty()) {
            tag.put("CostA", CustomData.saveStack(costA, registries));
        }
        if (!costB.isEmpty()) {
            tag.put("CostB", CustomData.saveStack(costB, registries));
        }
        if (!result.isEmpty()) {
            tag.put("Result", CustomData.saveStack(result, registries));
        }
        if (urgentBounty) {
            tag.putBoolean("UrgentBounty", true);
        }
        return tag;
    }

    public static HarborTradeOffer fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack a = tag.contains("CostA") ? CustomData.parseStack(registries, tag.getCompound("CostA")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack b = tag.contains("CostB") ? CustomData.parseStack(registries, tag.getCompound("CostB")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack res = tag.contains("Result") ? CustomData.parseStack(registries, tag.getCompound("Result")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        boolean urgent = tag.getBoolean("UrgentBounty");
        return new HarborTradeOffer(a, b, res, urgent);
    }
}
