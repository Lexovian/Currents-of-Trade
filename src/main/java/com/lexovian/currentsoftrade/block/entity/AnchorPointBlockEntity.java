package com.lexovian.currentsoftrade.block.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    // Harbor state
    private int tradeLevel = 1;
    private String harborName = "";
    private long lastTravelTime = 0;
    private final List<HarborTradeOffer> harborTrades = new ArrayList<>();
    private final List<TradePresetEntry> savedTradePreset = new ArrayList<>();
    private BlockPos savedTargetPos = null;
    private String savedTargetHarborName = "";
    private final List<BlockPos> savedWaypoints = new ArrayList<>();

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

    /**
     * Collects all adjacent/nearby Harbor Depot containers (chests, barrels, etc. within 4 blocks).
     */
    public static List<net.minecraft.world.Container> getNearbyDepotContainers(Level level, BlockPos anchorPos) {
        List<net.minecraft.world.Container> containers = new ArrayList<>();
        if (level == null) return containers;
        for (BlockPos check : BlockPos.betweenClosed(anchorPos.offset(-4, -2, -4), anchorPos.offset(4, 2, 4))) {
            if (check.equals(anchorPos)) continue;
            BlockEntity be = level.getBlockEntity(check);
            if (be instanceof net.minecraft.world.Container container && !(be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity)) {
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

    private static boolean isStackMatchingForDepot(ItemStack candidate, ItemStack required) {
        if (candidate.isEmpty() || !ItemStack.isSameItem(candidate, required)) return false;
        if (!required.hasTag()) return true;
        return ItemStack.isSameItemSameTags(candidate, required);
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

        BlockPos waterStart = com.lexovian.currentsoftrade.entity.TradeBoatEntity.findBestHarborWaterSpawn(serverLevel, this.worldPosition);
        if (waterStart == null || !com.lexovian.currentsoftrade.entity.TradeBoatEntity.verifyPortWaterConnection(serverLevel, waterStart, waypoints, targetPos)) {
            return false;
        }

        // Stage as many trades from the preset as depot supplies allow
        List<ItemStack> stagedPayments = new ArrayList<>();
        List<ItemStack> stagedRewards = new ArrayList<>();
        int maxSlots = getCargoSlotCount();
        int totalTrades = 0;
        int totalStacks = 0;

        for (TradePresetEntry entry : this.savedTradePreset) {
            HarborTradeOffer offer = entry.offer();
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();

            for (int c = 0; c < entry.count(); c++) {
                if (stagedPayments.size() >= maxSlots || stagedRewards.size() >= maxSlots) break;

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
                stagedPayments.add(costA.copy());
                if (!costB.isEmpty()) stagedPayments.add(costB.copy());
                stagedRewards.add(result.copy());
                totalTrades++;
                totalStacks += 2;
            }
        }

        if (totalTrades == 0) {
            return false;
        }

        double dist = Math.sqrt(this.worldPosition.distSqr(targetPos));
        int baseFee = Math.max(1, (int) Math.ceil(dist / 200.0));
        int surcharge = totalStacks > 0 ? (int) Math.ceil(totalStacks / 3.0) : 0;
        int totalFee = baseFee + surcharge;

        ItemStack doubloonReq = new ItemStack(CurrentsofTrade.DOUBLOON.get(), totalFee);
        if (countInContainers(depots, doubloonReq) < totalFee) {
            // Refund staged payments back into depots if Doubloon fee is missing
            for (ItemStack refund : stagedPayments) {
                for (net.minecraft.world.Container c : depots) {
                    for (int i = 0; i < c.getContainerSize() && !refund.isEmpty(); i++) {
                        ItemStack slot = c.getItem(i);
                        if (slot.isEmpty()) {
                            c.setItem(i, refund.copy());
                            refund.setCount(0);
                            c.setChanged();
                        } else if (ItemStack.isSameItemSameTags(slot, refund) && slot.getCount() < slot.getMaxStackSize()) {
                            int fit = Math.min(refund.getCount(), slot.getMaxStackSize() - slot.getCount());
                            slot.grow(fit);
                            refund.shrink(fit);
                            c.setChanged();
                        }
                    }
                }
            }
            return false;
        }

        consumeFromContainers(depots, doubloonReq, totalFee);
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
        tradeBoat.setCustomName(net.minecraft.network.chat.Component.literal("Automated Trade Ship"));
        tradeBoat.setCustomNameVisible(true);
        tradeBoat.setTradeMission(this.worldPosition, getHarborName(), stagedRewards);

        for (int i = 0; i < stagedPayments.size() && i < tradeBoat.getContainerSize(); i++) {
            tradeBoat.setItem(i, stagedPayments.get(i).copy());
        }

        serverLevel.addFreshEntity(tradeBoat);
        serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_SPLASH_HIGH_SPEED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
        serverLevel.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        return true;
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

    /** Pushes current name, level, and trade list into the global HarborSavedData. */
    public void syncToSavedData() {
        if (this.level instanceof ServerLevel serverLevel) {
            HarborSavedData data = HarborSavedData.get(serverLevel);
            data.setTrades(this.getBlockPos(), this.harborTrades);
            data.setHarborName(this.getBlockPos(), this.getHarborName());
            data.setHarborLevel(this.getBlockPos(), this.tradeLevel);
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

    public int getLevelCooldownSeconds() {
        return switch (this.tradeLevel) {
            case 2 -> 45;
            case 3 -> 30;
            case 4 -> 20;
            case 5 -> 10;
            default -> 60;
        };
    }

    public int getCargoSlotCount() {
        return switch (this.tradeLevel) {
            case 2 -> 27;
            case 3 -> 36;
            case 4 -> 45;
            case 5 -> 54;
            default -> 18;
        };
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

    public void setHarborName(String harborName) {
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
        }
    }

    // --- NBT Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        final HolderLookup.Provider registries = this.level != null ? this.level.registryAccess() : null;
        tag.putInt("TradeLevel", this.tradeLevel);
        tag.putString("HarborName", this.harborName);
        tag.putLong("LastTravelTime", this.lastTravelTime);

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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        final HolderLookup.Provider registries = this.level != null ? this.level.registryAccess() : null;
        if (tag.contains("TradeLevel"))    this.tradeLevel     = tag.getInt("TradeLevel");
        if (tag.contains("HarborName"))    this.harborName     = tag.getString("HarborName");
        if (tag.contains("LastTravelTime")) this.lastTravelTime = tag.getLong("LastTravelTime");

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
