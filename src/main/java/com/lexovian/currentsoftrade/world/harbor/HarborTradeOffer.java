package com.lexovian.currentsoftrade.world.harbor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

public class HarborTradeOffer {

    public static final Codec<HarborTradeOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.lenientOptionalFieldOf("CostA").forGetter(o -> o.costA.isEmpty() ? Optional.empty() : Optional.of(o.costA)),
            ItemStack.CODEC.lenientOptionalFieldOf("CostB").forGetter(o -> o.costB.isEmpty() ? Optional.empty() : Optional.of(o.costB)),
            ItemStack.CODEC.lenientOptionalFieldOf("Result").forGetter(o -> o.result.isEmpty() ? Optional.empty() : Optional.of(o.result)),
            Codec.BOOL.optionalFieldOf("UrgentBounty", false).forGetter(HarborTradeOffer::isUrgentBounty)
    ).apply(instance, (a, b, res, bounty) -> new HarborTradeOffer(
            a.orElse(ItemStack.EMPTY),
            b.orElse(ItemStack.EMPTY),
            res.orElse(ItemStack.EMPTY),
            bounty
    )));

    private final ItemStack costA;
    private final ItemStack costB;
    private final ItemStack result;
    private final boolean urgentBounty;

    public HarborTradeOffer(ItemStack costA, ItemStack costB, ItemStack result, boolean urgentBounty) {
        this.costA = costA.copy();
        this.costB = costB != null ? costB.copy() : ItemStack.EMPTY;
        this.result = result.copy();
        this.urgentBounty = urgentBounty;
    }

    public HarborTradeOffer(ItemStack costA, ItemStack costB, ItemStack result) {
        this(costA, costB, result, false);
    }

    public HarborTradeOffer(ItemStack costA, ItemStack result, boolean urgentBounty) {
        this(costA, ItemStack.EMPTY, result, urgentBounty);
    }

    public HarborTradeOffer(ItemStack costA, ItemStack result) {
        this(costA, ItemStack.EMPTY, result, false);
    }

    public HarborTradeOffer(MerchantOffer offer) {
        this(offer.getBaseCostA(), offer.getCostB(), offer.getResult(), false);
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
        DynamicOps<Tag> ops = registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        CompoundTag tag = new CompoundTag();
        if (!costA.isEmpty()) {
            tag.store("CostA", ItemStack.CODEC, ops, costA);
        }
        if (!costB.isEmpty()) {
            tag.store("CostB", ItemStack.CODEC, ops, costB);
        }
        if (!result.isEmpty()) {
            tag.store("Result", ItemStack.CODEC, ops, result);
        }
        if (urgentBounty) {
            tag.putBoolean("UrgentBounty", true);
        }
        return tag;
    }

    public static HarborTradeOffer fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        DynamicOps<Tag> ops = registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        ItemStack a = tag.contains("CostA") ? tag.read("CostA", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack b = tag.contains("CostB") ? tag.read("CostB", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ItemStack res = tag.contains("Result") ? tag.read("Result", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        boolean bounty = tag.getBooleanOr("UrgentBounty", false);
        return new HarborTradeOffer(a, b, res, bounty);
    }
}
