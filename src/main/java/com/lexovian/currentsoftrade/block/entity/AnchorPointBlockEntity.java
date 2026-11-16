package com.lexovian.currentsoftrade.block.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.TradeCharterItem;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class AnchorPointBlockEntity extends BlockEntity {

    public enum HarborStatus {
        VALID,
        NO_WATER,
        TOO_SMALL,
        UNDERGROUND,
        TOO_SHALLOW,
        WRONG_DIMENSION
    }

    public static final int MAX_HARBOR_TRADES = 12;
    public static final int MIN_HARBOR_WATER_BLOCKS = 40;
    public static final int MAX_HARBOR_LEVEL = 5;
    public static final int MAX_DAILY_BOUNTY_CLAIMS = 3;

    // Harbor state
    private int tradeLevel = 1;
    private String harborName = "";
    private boolean customNamed = false;
    private boolean hasCustomTrades = false;
    private long lastTravelTime = 0;
    private HarborSpecialization specialization = HarborSpecialization.NONE;
    private boolean specializationChosen = false;
    @Nullable
    private java.util.UUID ownerUUID = null;
    private long bountyClaimDay = -1L;
    private int primaryBountyClaims = 0;
    private int secondaryBountyClaims = 0;
    private final List<HarborTradeOffer> harborTrades = new ArrayList<>();
    private final List<TradePresetEntry> savedTradePreset = new ArrayList<>();
    private BlockPos savedTargetPos = null;
    private String savedTargetHarborName = "";
    private final List<BlockPos> savedWaypoints = new ArrayList<>();

    public void setOwnerUUID(@Nullable java.util.UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    @Nullable
    public java.util.UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public int getRemainingPrimaryBountyClaims() {
        long today = this.level != null ? (this.level.getDayTime() / 24000L) : 0L;
        if (this.bountyClaimDay != today) return MAX_DAILY_BOUNTY_CLAIMS;
        return Math.max(0, MAX_DAILY_BOUNTY_CLAIMS - this.primaryBountyClaims);
    }

    public int getRemainingSecondaryBountyClaims() {
        long today = this.level != null ? (this.level.getDayTime() / 24000L) : 0L;
        if (this.bountyClaimDay != today) return MAX_DAILY_BOUNTY_CLAIMS;
        return Math.max(0, MAX_DAILY_BOUNTY_CLAIMS - this.secondaryBountyClaims);
    }

    private void recordBountyClaim(boolean isSecondary) {
        if (this.level == null) return;
        long today = this.level.getDayTime() / 24000L;
        if (this.bountyClaimDay != today) {
            this.bountyClaimDay = today;
            this.primaryBountyClaims = 0;
            this.secondaryBountyClaims = 0;
        }
        if (isSecondary) {
            this.secondaryBountyClaims++;
        } else {
            this.primaryBountyClaims++;
        }
        setChanged();
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    public record TradePresetEntry(HarborTradeOffer offer, int count) {
        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put("Offer", offer.toTag(registries));
            tag.putInt("Count", count);
            return tag;
        }

        public static TradePresetEntry fromTag(CompoundTag tag, HolderLookup.Provider registries) {
            HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompound("Offer"), registries);
            int count = Math.max(1, tag.getInt("Count"));
            return new TradePresetEntry(offer, count);
        }
    }

    public List<TradePresetEntry> getSavedTradePreset() {
        return Collections.unmodifiableList(this.savedTradePreset);
    }

    public void setSavedTradePreset(List<TradePresetEntry> preset) {
        this.savedTradePreset.clear();
        if (preset != null) {
            this.savedTradePreset.addAll(preset);
        }
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setSavedDestination(BlockPos targetPos, String targetHarborName, List<BlockPos> waypoints) {
        this.savedTargetPos = targetPos != null ? targetPos.immutable() : null;
        this.savedTargetHarborName = targetHarborName != null ? targetHarborName : "";
        this.savedWaypoints.clear();
        if (waypoints != null) {
            this.savedWaypoints.addAll(waypoints);
        }
        setChanged();
    }

    public BlockPos getSavedTargetPos() {
        return this.savedTargetPos;
    }

    public String getSavedTargetHarborName() {
        return this.savedTargetHarborName;
    }

    // --- Redstone Auto-Dispatch & Depot Helpers ---

    /**
     * Comparator signal output:
     * - 15 when a Trade/Cargo/Sloop ship with items is docked within 12 blocks
     * - 8 when harbor is ready to dispatch (cooldown == 0 and has a saved preset)
     * - 0 when on cooldown or unconfigured
     */
    public int getComparatorSignal() {
        if (this.level == null) return 0;
        net.minecraft.world.phys.AABB dockArea = new net.minecraft.world.phys.AABB(this.worldPosition).inflate(12.0D);
        for (com.lexovian.currentsoftrade.entity.TradeBoatEntity ship : this.level.getEntitiesOfClass(com.lexovian.currentsoftrade.entity.TradeBoatEntity.class, dockArea)) {
            for (int i = 0; i < ship.getContainerSize(); i++) {
                if (!ship.getItem(i).isEmpty()) {
                    return 15;
                }
            }
        }
        if (getRemainingCooldownSeconds() <= 0 && !this.savedTradePreset.isEmpty()) {
            return 8;
        }
        return 0;
    }

    public static boolean isGuildDepotAt(Level level, BlockPos anchorPos) {
        return level != null && anchorPos != null
                && level.getBlockEntity(anchorPos) instanceof AnchorPointBlockEntity a
                && a.getSpecialization() == HarborSpecialization.GUILD_DEPOT;
    }

    /**
     * Collects all adjacent/nearby Harbor Depot containers (chests, barrels, etc. within 4 blocks, or 8x4x8 at Guild Depots).
     */
    public static List<net.minecraft.world.Container> getNearbyDepotContainers(Level level, BlockPos anchorPos) {
        List<net.minecraft.world.Container> containers = new ArrayList<>();
        if (level == null || anchorPos == null) return containers;
        boolean expanded = isGuildDepotAt(level, anchorPos);
        int rx = expanded ? 8 : 4;
        int ryDown = expanded ? 3 : 2;
        int ryUp = expanded ? 4 : 3;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos check : BlockPos.betweenClosed(anchorPos.offset(-rx, -ryDown, -rx), anchorPos.offset(rx, ryUp, rx))) {
            if (!seen.add(check.immutable()) || !level.isLoaded(check)) continue;
            BlockEntity be = level.getBlockEntity(check);
            if (be == null
                    || be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity
                    || be instanceof AnchorPointBlockEntity
                    || be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                    || be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
                continue;
            }
            BlockState state = level.getBlockState(check);
            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                    && state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chestBlock) {
                net.minecraft.world.Container chestContainer =
                        net.minecraft.world.level.block.ChestBlock.getContainer(chestBlock, state, level, check, true);
                if (chestContainer != null && !containers.contains(chestContainer)) {
                    containers.add(chestContainer);
                    continue;
                }
            }
            if (be instanceof net.minecraft.world.Container container && !containers.contains(container)) {
                containers.add(container);
            }
        }
        return containers;
    }

    public static int countInContainers(List<net.minecraft.world.Container> containers, ItemStack required) {
        if (required.isEmpty()) return Integer.MAX_VALUE;
        int total = 0;
        for (net.minecraft.world.Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (isStackMatchingForDepot(stack, required)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    public static int consumeFromContainers(List<net.minecraft.world.Container> containers, ItemStack required, int amountNeeded) {
        if (required.isEmpty() || amountNeeded <= 0) return 0;
        int remaining = amountNeeded;
        for (net.minecraft.world.Container c : containers) {
            boolean modified = false;
            for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = c.getItem(i);
                if (isStackMatchingForDepot(stack, required)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                    if (stack.isEmpty()) {
                        c.setItem(i, ItemStack.EMPTY);
                    }
                    modified = true;
                }
            }
            if (modified) {
                c.setChanged();
            }
            if (remaining <= 0) break;
        }
        return amountNeeded - remaining;
    }

    public static boolean isStackMatchingForDepot(ItemStack candidate, ItemStack required) {
        if (candidate.isEmpty() || !ItemStack.isSameItem(candidate, required)) return false;
        if (!required.hasTag()) return true;
        return ItemStack.isSameItemSameTags(candidate, required);
    }

    public static int countPlayerMatchingItems(net.minecraft.world.entity.player.Player player, ItemStack required) {
        if (player == null || required.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isStackMatchingForDepot(s, required)) {
                total += s.getCount();
            }
        }
        return total;
    }

    public static int consumeFromPlayerInventory(net.minecraft.world.entity.player.Player player, @Nullable ItemStack preferredStack, ItemStack required, int needed) {
        if (player == null || required.isEmpty() || needed <= 0) return 0;
        int remaining = needed;
        if (preferredStack != null && !preferredStack.isEmpty() && isStackMatchingForDepot(preferredStack, required)) {
            int take = Math.min(remaining, preferredStack.getCount());
            preferredStack.shrink(take);
            remaining -= take;
        }
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isStackMatchingForDepot(s, required)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
                if (s.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
        player.getInventory().setChanged();
        return needed - remaining;
    }

    public List<HarborTradeOffer> getActiveUrgentBounties() {
        List<HarborTradeOffer> list = new ArrayList<>();
        if (this.level == null) return list;
        MaritimeTradeRegion region = getTradeRegion();
        if (getRemainingPrimaryBountyClaims() > 0) {
            HarborTradeOffer primary = region.getUrgentBountyOffer(this.level, this.worldPosition);
            if (primary != null && !primary.getCostA().isEmpty()) {
                list.add(primary);
            }
        }
        if (this.specialization == HarborSpecialization.GUILD_DEPOT && getRemainingSecondaryBountyClaims() > 0) {
            HarborTradeOffer secondary = region.getSecondaryUrgentBountyOffer(this.level, this.worldPosition);
            if (secondary != null && !secondary.getCostA().isEmpty()) {
                list.add(secondary);
            }
        }
        return list;
    }

    /**
     * Allows a player to directly deliver a Sealed Trade Charter, the port's Daily Urgent Bounty,
     * or a High-Demand regional import commodity at the Anchor Point without needing to dispatch a ship from a second port.
     */
    public boolean tryDeliverUrgentBounty(net.minecraft.world.entity.player.Player player, @Nullable ItemStack heldStack) {
        if (this.level == null || player == null) return false;

        // 1. Check Sealed Guild Trade Charter delivery first (includes HarborStatus.VALID check)
        if (TradeCharterItem.tryFulfillAtHarbor(player, heldStack, this)) {
            return true;
        }

        HarborStatus status = getHarborStatus();
        if (status != HarborStatus.VALID) {
            if (heldStack == null) {
                if (!this.level.isClientSide) {
                    Component warnMsg = switch (status) {
                        case TOO_SMALL -> Component.translatable("message.currents_of_trade.harbor_too_small");
                        case UNDERGROUND -> Component.translatable("message.currents_of_trade.harbor_underground");
                        case TOO_SHALLOW -> Component.translatable("message.currents_of_trade.harbor_too_shallow");
                        case WRONG_DIMENSION -> Component.translatable("message.currents_of_trade.overworld_only");
                        default -> Component.translatable("message.currents_of_trade.anchor_placed_no_water");
                    };
                    this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                    player.displayClientMessage(warnMsg, true);
                }
                return true;
            }
            return false;
        }

        MaritimeTradeRegion region = getTradeRegion();
        HarborTradeOffer primaryBounty = getRemainingPrimaryBountyClaims() > 0
                ? region.getUrgentBountyOffer(this.level, this.worldPosition) : null;
        HarborTradeOffer secondaryBounty = (this.specialization == HarborSpecialization.GUILD_DEPOT && getRemainingSecondaryBountyClaims() > 0)
                ? region.getSecondaryUrgentBountyOffer(this.level, this.worldPosition) : null;
        List<HarborTradeOffer> activeBounties = getActiveUrgentBounties();

        HarborTradeOffer matchedOffer = null;
        int matchedBountyType = 0; // 1 = primary urgent, 2 = secondary urgent, 0 = regular regional import
        if (heldStack != null && !heldStack.isEmpty()) {
            if (primaryBounty != null && !primaryBounty.getCostA().isEmpty() && isStackMatchingForDepot(heldStack, primaryBounty.getCostA())) {
                matchedOffer = primaryBounty;
                matchedBountyType = 1;
            } else if (secondaryBounty != null && !secondaryBounty.getCostA().isEmpty() && isStackMatchingForDepot(heldStack, secondaryBounty.getCostA())) {
                matchedOffer = secondaryBounty;
                matchedBountyType = 2;
            }
            if (matchedOffer == null && !heldStack.is(CurrentsofTrade.DOUBLOON.get())) {
                for (HarborTradeOffer offer : region.generateDefaultHarborTrades(MAX_HARBOR_LEVEL)) {
                    if (offer.getCostB().isEmpty()
                            && offer.getResult().is(CurrentsofTrade.DOUBLOON.get())
                            && !offer.getCostA().is(CurrentsofTrade.DOUBLOON.get())
                            && isStackMatchingForDepot(heldStack, offer.getCostA())) {
                        net.minecraft.resources.ResourceLocation key =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getCostA().getItem());
                        if (key != null && CurrentsofTrade.MODID.equals(key.getNamespace())) {
                            matchedOffer = offer;
                            break;
                        }
                    }
                }
            }
            if (matchedOffer == null) {
                return false;
            }
        } else {
            if (primaryBounty != null && !primaryBounty.getCostA().isEmpty()
                    && countPlayerMatchingItems(player, primaryBounty.getCostA()) >= primaryBounty.getCostA().getCount()) {
                matchedOffer = primaryBounty;
                matchedBountyType = 1;
            } else if (secondaryBounty != null && !secondaryBounty.getCostA().isEmpty()
                    && countPlayerMatchingItems(player, secondaryBounty.getCostA()) >= secondaryBounty.getCostA().getCount()) {
                matchedOffer = secondaryBounty;
                matchedBountyType = 2;
            } else if (!activeBounties.isEmpty()) {
                matchedOffer = activeBounties.get(0);
                matchedBountyType = (matchedOffer == secondaryBounty) ? 2 : 1;
            } else {
                HarborTradeOffer exhausted = region.getUrgentBountyOffer(this.level, this.worldPosition);
                if (exhausted != null && !exhausted.getCostA().isEmpty()) {
                    if (!this.level.isClientSide) {
                        this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                    }
                    return true;
                }
                return false;
            }
        }

        ItemStack required = matchedOffer.getCostA();
        int needed = required.getCount();
        int available = countPlayerMatchingItems(player, required);
        ItemStack baseReward = matchedOffer.getResult().copy();
        if (this.specialization == HarborSpecialization.FREE_PORT && baseReward.is(CurrentsofTrade.DOUBLOON.get())) {
            baseReward.setCount(Math.max(baseReward.getCount() + 1, Math.round(baseReward.getCount() * 1.25F)));
        }

        if (available < needed) {
            if (!this.level.isClientSide) {
                this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                player.displayClientMessage(Component.translatable(
                        "message.currents_of_trade.bounty_missing_items",
                        needed,
                        required.getHoverName(),
                        baseReward.getCount(),
                        baseReward.getHoverName()
                ), true);
            }
            return true;
        }

        if (!this.level.isClientSide) {
            if (!player.getAbilities().instabuild) {
                consumeFromPlayerInventory(player, heldStack, required, needed);
            }
            if (matchedBountyType == 1) {
                recordBountyClaim(false);
            } else if (matchedBountyType == 2) {
                recordBountyClaim(true);
            }
            int remReward = baseReward.getCount();
            while (remReward > 0) {
                int batch = Math.min(baseReward.getMaxStackSize(), remReward);
                ItemStack rewardPart = baseReward.copyWithCount(batch);
                if (!player.getInventory().add(rewardPart)) {
                    player.drop(rewardPart, false);
                }
                remReward -= batch;
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }

            this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.1F);
            this.level.playSound(null, this.worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.9F, 1.2F);
            if (this.level instanceof ServerLevel serverLevel) {
                double px = this.worldPosition.getX() + 0.5;
                double py = this.worldPosition.getY() + 1.2;
                double pz = this.worldPosition.getZ() + 0.5;
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, px, py, pz, 14, 0.35, 0.3, 0.35, 0.05);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, px, py, pz, 10, 0.3, 0.25, 0.3, 0.05);
            }

            player.displayClientMessage(Component.translatable(
                    "message.currents_of_trade.bounty_delivered",
                    needed,
                    required.getHoverName(),
                    getHarborName(),
                    baseReward.getCount(),
                    baseReward.getHoverName()
            ), true);
        }
        return true;
    }

    /**
     * Triggered when the Anchor Point receives a rising Redstone pulse.
     * Automatically pulls payment items + Doubloon fee from adjacent Harbor Depot chests/barrels
     * and dispatches a Trade Ship using the harbor's last saved trade order!
     */
    public boolean tryRedstoneAutoDispatch(ServerLevel serverLevel) {
        if (getHarborStatus() != HarborStatus.VALID || getRemainingCooldownSeconds() > 0) {
            return false;
        }
        if (this.savedTradePreset.isEmpty()) {
            return false;
        }

        // Resolve destination from saved target or from a Nautical Chart / Route Journal inside adjacent depot chests
        List<net.minecraft.world.Container> depots = getNearbyDepotContainers(serverLevel, this.worldPosition);
        if (depots.isEmpty()) {
            return false;
        }

        BlockPos targetPos = this.savedTargetPos;
        String targetName = this.savedTargetHarborName;
        List<BlockPos> waypoints = new ArrayList<>(this.savedWaypoints);

        for (net.minecraft.world.Container c : depots) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && (s.getItem() instanceof com.lexovian.currentsoftrade.item.NauticalChartItem
                        || s.getItem() instanceof com.lexovian.currentsoftrade.item.RouteJournalItem)
                        && com.lexovian.currentsoftrade.item.NauticalChartItem.isBound(s)) {
                    targetPos = com.lexovian.currentsoftrade.item.NauticalChartItem.getTargetPos(s);
                    targetName = com.lexovian.currentsoftrade.item.NauticalChartItem.getTargetHarbor(s, serverLevel);
                    waypoints = com.lexovian.currentsoftrade.item.NauticalChartItem.getWaypoints(s);
                    break;
                }
            }
        }

        if (targetPos == null) {
            return false;
        }
        double dist = Math.sqrt(this.worldPosition.distSqr(targetPos));
        if (dist <= 0) {
            return false;
        }
        int rawBaseFee = Math.max(1, (int) Math.ceil(dist / 200.0));
        ItemStack doubloonProbe = new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1);
        if (countInContainers(depots, doubloonProbe) < applyShippingFeeSpecialization(rawBaseFee)) {
            return false;
        }

        BlockPos waterStart = com.lexovian.currentsoftrade.entity.TradeBoatEntity.findBestHarborWaterSpawn(serverLevel, this.worldPosition);
        if (waterStart == null || !com.lexovian.currentsoftrade.entity.TradeBoatEntity.verifyPortWaterConnection(serverLevel, waterStart, waypoints, targetPos)) {
            return false;
        }

        // Stage as many trades from the preset as depot supplies & shipping fee reserve allow
        List<ItemStack> stagedPayments = new ArrayList<>();
        List<ItemStack> stagedRewards = new ArrayList<>();
        int maxSlots = getCargoSlotCount();
        int totalTrades = 0;

        var savedData = HarborSavedData.get(serverLevel);
        if (!savedData.hasHarbor(targetPos)) {
            return false;
        }
        List<HarborTradeOffer> validTargetOffers = new ArrayList<>(savedData.getTrades(targetPos));
        MaritimeTradeRegion destRegion = MaritimeTradeRegion.fromLevelAndPos(serverLevel, targetPos);
        HarborTradeOffer liveUrgent1 = destRegion.getUrgentBountyOffer(serverLevel, targetPos);
        if (liveUrgent1 != null) validTargetOffers.add(liveUrgent1);
        if (savedData.getHarborSpecialization(targetPos) == HarborSpecialization.GUILD_DEPOT) {
            HarborTradeOffer liveUrgent2 = destRegion.getSecondaryUrgentBountyOffer(serverLevel, targetPos);
            if (liveUrgent2 != null) validTargetOffers.add(liveUrgent2);
        }

        boolean destIsFreePort = savedData.getHarborSpecialization(targetPos) == HarborSpecialization.FREE_PORT;
        boolean bonusFreePort = (this.specialization == HarborSpecialization.FREE_PORT) || destIsFreePort;

        for (TradePresetEntry entry : this.savedTradePreset) {
            HarborTradeOffer offer = findMatchingLiveOffer120(entry.offer(), validTargetOffers);
            if (offer == null) continue;
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult().copy();
            if (bonusFreePort && result.is(CurrentsofTrade.DOUBLOON.get()) && !costA.is(CurrentsofTrade.DOUBLOON.get())) {
                result.setCount(Math.max(result.getCount() + 1, Math.round(result.getCount() * 1.25F)));
            }

            int tradeDoubloonCost = 0;
            if (costA.is(CurrentsofTrade.DOUBLOON.get())) tradeDoubloonCost += costA.getCount();
            if (!costB.isEmpty() && costB.is(CurrentsofTrade.DOUBLOON.get())) tradeDoubloonCost += costB.getCount();

            int maxAllowedCount = offer.isUrgentBounty() ? Math.min(entry.count(), MAX_DAILY_BOUNTY_CLAIMS) : entry.count();
            for (int c = 0; c < maxAllowedCount; c++) {
                List<ItemStack> nextPayments = new ArrayList<>(stagedPayments.size() + 2);
                for (ItemStack s : stagedPayments) nextPayments.add(s.copy());
                List<ItemStack> nextRewards = new ArrayList<>(stagedRewards.size() + 1);
                for (ItemStack s : stagedRewards) nextRewards.add(s.copy());

                mergeStackIntoList120(nextPayments, costA.copy());
                if (!costB.isEmpty()) mergeStackIntoList120(nextPayments, costB.copy());
                mergeStackIntoList120(nextRewards, result.copy());

                if (nextPayments.size() > maxSlots || nextRewards.size() > maxSlots) break;

                int nextStacks = 0;
                for (ItemStack s : nextPayments) nextStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
                for (ItemStack s : nextRewards) nextStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
                int nextSurcharge = nextStacks > 0 ? (int) Math.ceil(nextStacks / 3.0) : 0;
                int nextTotalFee = applyShippingFeeSpecialization(rawBaseFee + nextSurcharge);

                int currentDoubloons = countInContainers(depots, doubloonProbe);
                if (currentDoubloons - tradeDoubloonCost < nextTotalFee) break;

                boolean canPayA = costA.isEmpty() || countInContainers(depots, costA) >= costA.getCount();
                boolean canPayB = costB.isEmpty() || countInContainers(depots, costB) >= costB.getCount();
                if (!costA.isEmpty() && !costB.isEmpty() && ItemStack.isSameItem(costA, costB)) {
                    canPayB = countInContainers(depots, costA) >= (costA.getCount() + costB.getCount());
                }
                if (!canPayA || !canPayB) break;

                consumeFromContainers(depots, costA, costA.getCount());
                if (!costB.isEmpty()) {
                    consumeFromContainers(depots, costB, costB.getCount());
                }
                stagedPayments = nextPayments;
                stagedRewards = nextRewards;
                totalTrades++;
            }
        }

        if (totalTrades == 0) {
            return false;
        }

        int totalStacks = 0;
        for (ItemStack s : stagedPayments) totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
        for (ItemStack s : stagedRewards) totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
        int surcharge = totalStacks > 0 ? (int) Math.ceil(totalStacks / 3.0) : 0;
        int totalFee = applyShippingFeeSpecialization(rawBaseFee + surcharge);

        consumeFromContainers(depots, doubloonProbe, totalFee);
        triggerTravelCooldown();

        double spawnX = waterStart.getX() + 0.5;
        double spawnY = waterStart.getY() + 0.85;
        double spawnZ = waterStart.getZ() + 0.5;
        double dx = (targetPos.getX() + 0.5) - spawnX;
        double dz = (targetPos.getZ() + 0.5) - spawnZ;
        float initialYaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

        com.lexovian.currentsoftrade.entity.TradeBoatEntity tradeBoat =
                new com.lexovian.currentsoftrade.entity.TradeBoatEntity(serverLevel, spawnX, spawnY, spawnZ);
        tradeBoat.setYRot(initialYaw);
        tradeBoat.setYHeadRot(initialYaw);
        tradeBoat.yRotO = initialYaw;
        tradeBoat.setRouteWaypoints(waypoints);
        tradeBoat.setVoyageTarget(targetPos, targetName.isEmpty() ? "Target Port" : targetName);
        if (this.ownerUUID != null) {
            tradeBoat.setSenderUUID(this.ownerUUID);
        }
        tradeBoat.setCustomName(net.minecraft.network.chat.Component.translatable("entity.currents_of_trade.trade_boat"));
        tradeBoat.setCustomNameVisible(true);
        tradeBoat.setTradeMission(this.worldPosition, getHarborName(), stagedRewards);
        applyOutboundShipSpecialization(tradeBoat);

        for (int i = 0; i < stagedPayments.size() && i < tradeBoat.getContainerSize(); i++) {
            tradeBoat.setItem(i, stagedPayments.get(i).copy());
        }

        serverLevel.addFreshEntity(tradeBoat);
        serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_SPLASH_HIGH_SPEED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
        serverLevel.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        return true;
    }

    @Nullable
    private static HarborTradeOffer findMatchingLiveOffer120(HarborTradeOffer presetOffer, List<HarborTradeOffer> liveOffers) {
        if (presetOffer == null) return null;
        for (HarborTradeOffer live : liveOffers) {
            if (isStackMatchingForDepot(live.getCostA(), presetOffer.getCostA())
                    && live.getCostA().getCount() == presetOffer.getCostA().getCount()
                    && ((live.getCostB().isEmpty() && presetOffer.getCostB().isEmpty())
                        || (isStackMatchingForDepot(live.getCostB(), presetOffer.getCostB())
                            && live.getCostB().getCount() == presetOffer.getCostB().getCount()))
                    && ItemStack.isSameItem(live.getResult(), presetOffer.getResult())) {
                return live;
            }
        }
        return null;
    }

    private static void mergeStackIntoList120(List<ItemStack> list, ItemStack toAdd) {
        if (toAdd.isEmpty()) return;
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameTags(existing, toAdd)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int transfer = Math.min(space, toAdd.getCount());
                existing.grow(transfer);
                toAdd.shrink(transfer);
                if (toAdd.isEmpty()) return;
            }
        }
        if (!toAdd.isEmpty()) {
            list.add(toAdd.copy());
        }
    }

    public AnchorPointBlockEntity(BlockPos pos, BlockState state) {
        super(CurrentsofTrade.ANCHOR_POINT_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel) {
            if (this.harborTrades.isEmpty()) {
                seedRegionalTradesIfEmpty();
            }
        }
    }

    // --- Harbor Naming ---

    public static String generateDynamicHarborName(Level level, BlockPos pos) {
        if (level == null) {
            return "Port " + pos.getX() + ", " + pos.getZ();
        }
        try {
            var biomeHolder = level.getBiome(pos);
            String biomePath = biomeHolder.unwrapKey()
                    .map(k -> k.location().getPath())
                    .orElse("ocean");
            String[] parts = biomePath.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                }
            }
            return "Port " + sb;
        } catch (Exception e) {
            return "Port " + pos.getX() + ", " + pos.getZ();
        }
    }

    // --- Trade Offer Management ---

    public List<HarborTradeOffer> getHarborTrades() {
        return Collections.unmodifiableList(this.harborTrades);
    }

    public boolean addHarborTrade(HarborTradeOffer trade) {
        int maxTrades = getMaxTradeOffers();
        if (this.harborTrades.size() < maxTrades) {
            this.harborTrades.add(trade);
            this.hasCustomTrades = true;
            setChanged();
            syncToSavedData();
            return true;
        }
        return false;
    }

    public boolean removeHarborTrade(int index) {
        if (index >= 0 && index < this.harborTrades.size()) {
            this.harborTrades.remove(index);
            setChanged();
            syncToSavedData();
            return true;
        }
        return false;
    }

    /** Pushes current name, level, specialization, and trade list into the global HarborSavedData. */
    public void syncToSavedData() {
        if (this.level instanceof ServerLevel serverLevel) {
            HarborSavedData data = HarborSavedData.get(serverLevel);
            data.setTrades(this.getBlockPos(), this.harborTrades);
            data.setHarborName(this.getBlockPos(), this.getHarborName());
            data.setHarborLevel(this.getBlockPos(), this.tradeLevel);
            data.setHarborSpecialization(this.getBlockPos(), this.specialization);
        }
    }

    public MaritimeTradeRegion getTradeRegion() {
        return MaritimeTradeRegion.fromLevelAndPos(this.level, this.worldPosition);
    }

    public void seedRegionalTradesIfEmpty() {
        if (this.harborTrades.isEmpty() && this.level instanceof ServerLevel) {
            MaritimeTradeRegion region = getTradeRegion();
            List<HarborTradeOffer> defaults = region.generateDefaultHarborTrades(this.tradeLevel);
            int cap = getMaxTradeOffers();
            for (HarborTradeOffer offer : defaults) {
                if (this.harborTrades.size() < cap) {
                    this.harborTrades.add(offer);
                }
            }
            setChanged();
            syncToSavedData();
        }
    }

    private boolean containsSimilarTrade(HarborTradeOffer check) {
        for (HarborTradeOffer existing : this.harborTrades) {
            if (ItemStack.isSameItemSameTags(existing.getResult(), check.getResult())) {
                return true;
            }
        }
        return false;
    }

    // --- Getters / Setters ---

    public int getTradeLevel() {
        return tradeLevel;
    }

    public void setTradeLevel(int tradeLevel) {
        this.tradeLevel = Math.max(1, Math.min(MAX_HARBOR_LEVEL, tradeLevel));
        if (this.level instanceof ServerLevel) {
            MaritimeTradeRegion region = getTradeRegion();
            List<HarborTradeOffer> defaults = region.generateDefaultHarborTrades(this.tradeLevel);
            int cap = getMaxTradeOffers();
            for (HarborTradeOffer offer : defaults) {
                if (this.harborTrades.size() < cap && !containsSimilarTrade(offer)) {
                    this.harborTrades.add(offer);
                }
            }
        }
        setChanged();
        syncToSavedData();
    }

    public String getHarborLevelName() {
        return switch (this.tradeLevel) {
            case 2 -> "Trading Post";
            case 3 -> "Harbor";
            case 4 -> "Grand Port";
            case 5 -> "Royal Dockyard";
            default -> "Fishing Wharf";
        };
    }

    public HarborSpecialization getSpecialization() {
        return this.specialization != null ? this.specialization : HarborSpecialization.NONE;
    }

    public boolean isSpecializationChosen() {
        return this.specializationChosen;
    }

    public int applyShippingFeeSpecialization(int rawFee) {
        if (rawFee <= 0) return 0;
        if (this.specialization == HarborSpecialization.FREE_PORT) {
            return Math.max(1, (int) Math.floor(rawFee * 0.75D));
        }
        return rawFee;
    }

    public void applyOutboundShipSpecialization(TradeBoatEntity boat) {
        if (boat == null) return;
        if (this.specialization == HarborSpecialization.ROYAL_DRYDOCK) {
            boat.setCruiseSpeed(boat.getCruiseSpeed() * 1.20D);
            boat.setDrydockReinforced(true);
        }
    }

    public boolean tryCycleSpecialization(net.minecraft.world.entity.player.Player player) {
        if (player == null || this.level == null) return false;
        if (this.tradeLevel < HarborSpecialization.UNLOCK_LEVEL) {
            if (!this.level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.spec_locked", HarborSpecialization.UNLOCK_LEVEL),
                        true
                );
            }
            return false;
        }

        HarborSpecialization next = getSpecialization().nextActive();
        boolean needsFee = this.specializationChosen && !player.getAbilities().instabuild;
        ItemStack doubloonTemplate = new ItemStack(CurrentsofTrade.DOUBLOON.get());

        if (needsFee) {
            int have = countPlayerMatchingItems(player, doubloonTemplate);
            if (have < HarborSpecialization.SWITCH_COST_DOUBLOONS) {
                if (!this.level.isClientSide) {
                    this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.85F, 1.0F);
                    player.displayClientMessage(Component.translatable(
                            "message.currents_of_trade.spec_need_doubloons",
                            HarborSpecialization.SWITCH_COST_DOUBLOONS,
                            have
                    ), true);
                }
                return false;
            }
            if (!this.level.isClientSide) {
                consumeFromPlayerInventory(player, null, doubloonTemplate, HarborSpecialization.SWITCH_COST_DOUBLOONS);
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != null) player.containerMenu.broadcastChanges();
            }
        }

        if (!this.level.isClientSide) {
            this.specialization = next;
            this.specializationChosen = true;
            setChanged();
            syncToSavedData();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);

            this.level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.15F);
            this.level.playSound(null, this.worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.55F, 1.35F);
            if (this.level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                        this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.15, this.worldPosition.getZ() + 0.5,
                        16, 0.35, 0.3, 0.35, 0.08);
            }

            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
                net.minecraft.advancements.Advancement adv = serverPlayer.getServer().getAdvancements()
                        .getAdvancement(new net.minecraft.resources.ResourceLocation(CurrentsofTrade.MODID, "harbor_specialist"));
                if (adv != null) {
                    for (String criterion : adv.getCriteria().keySet()) {
                        serverPlayer.getAdvancements().award(adv, criterion);
                    }
                }
            }

            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.spec_selected", getHarborName(), next.getShortBadge()),
                    true
            );
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnchorPointBlockEntity anchor) {
        if (level.isClientSide || anchor.getSpecialization() != HarborSpecialization.ROYAL_DRYDOCK) return;
        if ((level.getGameTime() + pos.asLong()) % 40L != 0L) return;

        net.minecraft.world.phys.AABB dockRepairBox = new net.minecraft.world.phys.AABB(pos).inflate(14.0, 5.0, 14.0);
        boolean repairedAny = false;

        for (SloopEntity sloop : level.getEntitiesOfClass(SloopEntity.class, dockRepairBox, SloopEntity::isAlive)) {
            if (sloop.getDamage() > 0.0F) {
                sloop.setDamage(Math.max(0.0F, sloop.getDamage() - 4.0F));
                repairedAny = true;
            }
        }
        for (TradeBoatEntity boat : level.getEntitiesOfClass(TradeBoatEntity.class, dockRepairBox, TradeBoatEntity::isAlive)) {
            if (boat.healShipHull(5.0F)) {
                repairedAny = true;
            }
        }

        if (repairedAny && level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35F, 1.5F);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                    pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5,
                    6, 0.3, 0.25, 0.3, 0.04);
        }
    }

    public int getLevelCooldownSeconds() {
        int base = switch (this.tradeLevel) {
            case 2 -> 45;
            case 3 -> 30;
            case 4 -> 20;
            case 5 -> 10;
            default -> 60;
        };
        if (this.specialization == HarborSpecialization.ROYAL_DRYDOCK) {
            return Math.max(5, base / 2);
        }
        return base;
    }

    public int getCargoSlotCount() {
        int base = switch (this.tradeLevel) {
            case 2 -> 27;
            case 3 -> 36;
            case 4 -> 45;
            case 5 -> 54;
            default -> 18;
        };
        if (this.specialization == HarborSpecialization.GUILD_DEPOT) {
            return Math.min(54, base + 9);
        }
        return base;
    }

    public int getMaxTradeOffers() {
        return switch (this.tradeLevel) {
            case 2 -> 8;
            case 3 -> 10;
            case 4 -> 12;
            case 5 -> 14;
            default -> 6;
        };
    }

    public boolean canUpgrade(net.minecraft.world.entity.player.Player player) {
        if (this.tradeLevel >= MAX_HARBOR_LEVEL) return false;
        HarborUpgradeCost cost = HarborUpgradeCost.forLevel(this.tradeLevel);
        return cost != null && cost.canAfford(player);
    }

    public void executeUpgrade(net.minecraft.world.entity.player.Player player) {
        if (this.tradeLevel >= MAX_HARBOR_LEVEL) return;
        HarborUpgradeCost cost = HarborUpgradeCost.forLevel(this.tradeLevel);
        if (cost == null || !cost.canAfford(player)) return;
        cost.consume(player);
        this.tradeLevel++;
        if (this.level instanceof ServerLevel) {
            MaritimeTradeRegion region = getTradeRegion();
            List<HarborTradeOffer> defaults = region.generateDefaultHarborTrades(this.tradeLevel);
            int cap = getMaxTradeOffers();
            for (HarborTradeOffer offer : defaults) {
                if (this.harborTrades.size() < cap && !containsSimilarTrade(offer)) {
                    this.harborTrades.add(offer);
                }
            }
        }
        setChanged();
        syncToSavedData();
        if (this.level instanceof ServerLevel serverLevel) {
            HarborSavedData.get(serverLevel).setHarborLevel(this.worldPosition, this.tradeLevel);
            double px = this.worldPosition.getX() + 0.5;
            double py = this.worldPosition.getY() + 1.2;
            double pz = this.worldPosition.getZ() + 0.5;
            if (this.tradeLevel >= MAX_HARBOR_LEVEL) {
                serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 0.9F);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, 35, 0.4, 0.4, 0.4, 0.15);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, px, py, pz, 20, 0.3, 0.5, 0.3, 0.1);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
                    net.minecraft.advancements.Advancement adv = serverPlayer.getServer().getAdvancements()
                            .getAdvancement(new net.minecraft.resources.ResourceLocation(CurrentsofTrade.MODID, "royal_dockyard"));
                    if (adv != null) {
                        for (String criterion : adv.getCriteria().keySet()) {
                            serverPlayer.getAdvancements().award(adv, criterion);
                        }
                    }
                }
            } else {
                serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.2F);
                serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, px, py, pz, 16, 0.35, 0.35, 0.35, 0.05);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, px, py, pz, 25, 0.3, 0.2, 0.3, 0.1);
            }
        }
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    public String getHarborName() {
        boolean needsName = this.harborName == null
                || this.harborName.trim().isEmpty()
                || "Port Alpha".equalsIgnoreCase(this.harborName.trim());

        if (needsName) {
            if (this.level instanceof ServerLevel serverLevel) {
                String saved = HarborSavedData.get(serverLevel).getHarborName(this.worldPosition);
                if (saved != null && !saved.trim().isEmpty() && !"Port Alpha".equalsIgnoreCase(saved.trim())) {
                    this.harborName = saved.trim();
                    return this.harborName;
                }
            }
            this.harborName = (this.level != null)
                    ? generateDynamicHarborName(this.level, this.worldPosition)
                    : "Port " + this.worldPosition.getX() + ", " + this.worldPosition.getZ();
            setChanged();
        }
        return harborName;
    }

    public void initDefaultHarborName(String generatedName) {
        this.customNamed = false;
        applyHarborNameInternal(generatedName);
    }

    public void setHarborName(String harborName) {
        this.customNamed = true;
        applyHarborNameInternal(harborName);
    }

    private void applyHarborNameInternal(String harborName) {
        if (harborName == null || harborName.trim().isEmpty()) {
            harborName = (this.level != null)
                    ? generateDynamicHarborName(this.level, this.worldPosition)
                    : "Port " + this.worldPosition.getX() + ", " + this.worldPosition.getZ();
        }
        this.harborName = harborName.trim();
        setChanged();

        if (this.level != null) {
            if (this.level instanceof ServerLevel serverLevel) {
                try {
                    HarborSavedData.get(serverLevel).setHarborName(this.worldPosition, this.harborName);
                } catch (Throwable t) {
                    CurrentsofTrade.LOGGER.error("Failed to update HarborSavedData at {}", this.worldPosition, t);
                }
            }
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasPersistentUpgradeState() {
        return this.tradeLevel > 1
                || this.specializationChosen
                || this.customNamed
                || this.hasCustomTrades
                || !this.savedTradePreset.isEmpty()
                || getRemainingCooldownSeconds() > 0
                || getRemainingPrimaryBountyClaims() < MAX_DAILY_BOUNTY_CLAIMS
                || getRemainingSecondaryBountyClaims() < MAX_DAILY_BOUNTY_CLAIMS;
    }

    public CompoundTag saveToItemTag(@Nullable HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TradeLevel", this.tradeLevel);
        tag.putString("HarborName", this.harborName);
        tag.putBoolean("CustomNamed", this.customNamed);
        tag.putBoolean("HasCustomTrades", this.hasCustomTrades);
        tag.putLong("LastTravelTime", this.lastTravelTime);
        tag.putString("Specialization", getSpecialization().getId());
        tag.putBoolean("SpecializationChosen", this.specializationChosen);
        tag.putLong("BountyClaimDay", this.bountyClaimDay);
        tag.putInt("PrimaryBountyClaims", this.primaryBountyClaims);
        tag.putInt("SecondaryBountyClaims", this.secondaryBountyClaims);

        if (this.hasCustomTrades && !this.harborTrades.isEmpty()) {
            ListTag tradesList = new ListTag();
            for (HarborTradeOffer offer : this.harborTrades) {
                tradesList.add(offer.toTag(registries));
            }
            tag.put("HarborTrades", tradesList);
        }
        if (!this.savedTradePreset.isEmpty()) {
            ListTag presetList = new ListTag();
            for (TradePresetEntry entry : this.savedTradePreset) {
                presetList.add(entry.toTag(registries));
            }
            tag.put("SavedTradePreset", presetList);
        }
        return tag;
    }

    public void loadFromItemTag(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        if (tag.contains("TradeLevel")) {
            this.tradeLevel = Math.max(1, Math.min(MAX_HARBOR_LEVEL, tag.getInt("TradeLevel")));
        }
        if (tag.contains("HarborName")) {
            this.harborName = tag.getString("HarborName");
        }
        this.customNamed = tag.getBoolean("CustomNamed");
        this.hasCustomTrades = tag.getBoolean("HasCustomTrades");
        if (tag.contains("LastTravelTime")) {
            this.lastTravelTime = tag.getLong("LastTravelTime");
        }
        if (tag.contains("Specialization")) {
            this.specialization = HarborSpecialization.byId(tag.getString("Specialization"));
        }
        this.specializationChosen = tag.getBoolean("SpecializationChosen");
        this.bountyClaimDay = tag.contains("BountyClaimDay") ? tag.getLong("BountyClaimDay") : -1L;
        this.primaryBountyClaims = tag.getInt("PrimaryBountyClaims");
        this.secondaryBountyClaims = tag.getInt("SecondaryBountyClaims");

        this.harborTrades.clear();
        if (this.hasCustomTrades && tag.contains("HarborTrades", Tag.TAG_LIST)) {
            ListTag tradesList = tag.getList("HarborTrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < tradesList.size(); i++) {
                this.harborTrades.add(HarborTradeOffer.fromTag(tradesList.getCompound(i), registries));
            }
        }
        this.savedTradePreset.clear();
        if (tag.contains("SavedTradePreset", Tag.TAG_LIST)) {
            ListTag presetList = tag.getList("SavedTradePreset", Tag.TAG_COMPOUND);
            for (int i = 0; i < presetList.size(); i++) {
                this.savedTradePreset.add(TradePresetEntry.fromTag(presetList.getCompound(i), registries));
            }
        }
        setChanged();
        syncToSavedData();
    }

    // --- Harbor Validation ---

    public HarborStatus getHarborStatus() {
        if (this.level == null) return HarborStatus.VALID;
        if (this.level.dimension() != Level.OVERWORLD) return HarborStatus.WRONG_DIMENSION;

        BlockPos center = this.getBlockPos();
        BlockPos waterStart = null;

        for (BlockPos check : BlockPos.betweenClosed(center.offset(-10, -7, -10), center.offset(10, 2, 10))) {
            if (this.level.getFluidState(check).is(FluidTags.WATER)) {
                waterStart = check.immutable();
                break;
            }
        }

        if (waterStart == null) {
            return HarborStatus.NO_WATER;
        }

        int minWater = Config.MIN_HARBOR_WATER_BLOCKS != null ? Config.MIN_HARBOR_WATER_BLOCKS.get() : MIN_HARBOR_WATER_BLOCKS;

        int waterCount = 0;
        boolean hasSkyAccess = false;
        boolean hasDeepWater = false;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(waterStart);
        queue.add(waterStart);

        while (!queue.isEmpty() && waterCount < minWater) {
            BlockPos current = queue.poll();
            waterCount++;

            if (!hasSkyAccess && (this.level.canSeeSky(current.above()) || this.level.canSeeSky(current.above(2)) || current.getY() >= 50)) {
                hasSkyAccess = true;
            }
            if (!hasDeepWater && this.level.getFluidState(current.below()).is(FluidTags.WATER)) {
                hasDeepWater = true;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (neighbor.distManhattan(center) <= 32 && visited.add(neighbor)) {
                    if (this.level.getFluidState(neighbor).is(FluidTags.WATER)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        if (!hasSkyAccess)          return HarborStatus.UNDERGROUND;
        if (waterCount < minWater)  return HarborStatus.TOO_SMALL;
        if (!hasDeepWater)          return HarborStatus.TOO_SHALLOW;

        return HarborStatus.VALID;
    }

    public boolean isNearWater() {
        return getHarborStatus() == HarborStatus.VALID;
    }

    // --- Voyage Cooldown ---

    public int getRemainingCooldownSeconds() {
        if (this.level == null) return 0;
        long elapsed = this.level.getGameTime() - this.lastTravelTime;
        int levelCooldown = getLevelCooldownSeconds();
        int configCooldown = Config.VOYAGE_COOLDOWN_SECONDS != null ? Config.VOYAGE_COOLDOWN_SECONDS.get() : 60;
        int cooldownSec = Math.min(levelCooldown, configCooldown);
        long cooldownTicks = 20L * cooldownSec;
        if (elapsed < cooldownTicks && elapsed >= 0) {
            return (int) Math.ceil((cooldownTicks - elapsed) / 20.0);
        }
        return 0;
    }

    public void triggerTravelCooldown() {
        if (this.level != null) {
            this.lastTravelTime = this.level.getGameTime();
            setChanged();
            this.level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        }
    }

    // --- NBT Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        final HolderLookup.Provider registries = this.level != null ? this.level.registryAccess() : null;
        tag.putInt("TradeLevel", this.tradeLevel);
        tag.putString("HarborName", this.harborName);
        tag.putBoolean("CustomNamed", this.customNamed);
        tag.putBoolean("HasCustomTrades", this.hasCustomTrades);
        tag.putLong("LastTravelTime", this.lastTravelTime);
        tag.putString("Specialization", getSpecialization().getId());
        tag.putBoolean("SpecializationChosen", this.specializationChosen);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
        tag.putLong("BountyClaimDay", this.bountyClaimDay);
        tag.putInt("PrimaryBountyClaims", this.primaryBountyClaims);
        tag.putInt("SecondaryBountyClaims", this.secondaryBountyClaims);

        ListTag tradesList = new ListTag();
        for (HarborTradeOffer offer : this.harborTrades) {
            tradesList.add(offer.toTag(registries));
        }
        tag.put("HarborTrades", tradesList);

        if (!this.savedTradePreset.isEmpty()) {
            ListTag presetList = new ListTag();
            for (TradePresetEntry entry : this.savedTradePreset) {
                presetList.add(entry.toTag(registries));
            }
            tag.put("SavedTradePreset", presetList);
        }
        if (this.savedTargetPos != null) {
            tag.putInt("SavedTargetX", this.savedTargetPos.getX());
            tag.putInt("SavedTargetY", this.savedTargetPos.getY());
            tag.putInt("SavedTargetZ", this.savedTargetPos.getZ());
            tag.putString("SavedTargetName", this.savedTargetHarborName);
        }
        if (!this.savedWaypoints.isEmpty()) {
            ListTag wpList = new ListTag();
            for (BlockPos wp : this.savedWaypoints) {
                CompoundTag wpTag = new CompoundTag();
                wpTag.putInt("X", wp.getX());
                wpTag.putInt("Y", wp.getY());
                wpTag.putInt("Z", wp.getZ());
                wpList.add(wpTag);
            }
            tag.put("SavedWaypoints", wpList);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        final HolderLookup.Provider registries = this.level != null ? this.level.registryAccess() : null;
        if (tag.contains("TradeLevel"))    this.tradeLevel     = tag.getInt("TradeLevel");
        if (tag.contains("HarborName"))    this.harborName     = tag.getString("HarborName");
        this.customNamed = tag.getBoolean("CustomNamed");
        this.hasCustomTrades = tag.getBoolean("HasCustomTrades");
        if (tag.contains("LastTravelTime")) this.lastTravelTime = tag.getLong("LastTravelTime");
        if (tag.contains("Specialization")) {
            this.specialization = HarborSpecialization.byId(tag.getString("Specialization"));
        } else {
            this.specialization = HarborSpecialization.NONE;
        }
        this.specializationChosen = tag.getBoolean("SpecializationChosen");
        this.ownerUUID = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        this.bountyClaimDay = tag.contains("BountyClaimDay") ? tag.getLong("BountyClaimDay") : -1L;
        this.primaryBountyClaims = tag.getInt("PrimaryBountyClaims");
        this.secondaryBountyClaims = tag.getInt("SecondaryBountyClaims");

        this.harborTrades.clear();
        if (tag.contains("HarborTrades", Tag.TAG_LIST)) {
            ListTag tradesList = tag.getList("HarborTrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < tradesList.size(); i++) {
                this.harborTrades.add(HarborTradeOffer.fromTag(tradesList.getCompound(i), registries));
            }
        }

        this.savedTradePreset.clear();
        if (tag.contains("SavedTradePreset", Tag.TAG_LIST)) {
            ListTag presetList = tag.getList("SavedTradePreset", Tag.TAG_COMPOUND);
            for (int i = 0; i < presetList.size(); i++) {
                this.savedTradePreset.add(TradePresetEntry.fromTag(presetList.getCompound(i), registries));
            }
        }
        if (tag.contains("SavedTargetX")) {
            this.savedTargetPos = new BlockPos(tag.getInt("SavedTargetX"), tag.getInt("SavedTargetY"), tag.getInt("SavedTargetZ"));
            this.savedTargetHarborName = tag.getString("SavedTargetName");
        } else {
            this.savedTargetPos = null;
            this.savedTargetHarborName = "";
        }
        this.savedWaypoints.clear();
        if (tag.contains("SavedWaypoints", Tag.TAG_LIST)) {
            ListTag wpList = tag.getList("SavedWaypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < wpList.size(); i++) {
                CompoundTag wp = wpList.getCompound(i);
                this.savedWaypoints.add(new BlockPos(wp.getInt("X"), wp.getInt("Y"), wp.getInt("Z")));
            }
        }
        syncToSavedData();
    }

    // --- Client Sync ---

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
