package com.lexovian.currentsoftrade.block.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    // Harbor state
    private int tradeLevel = 1;
    private String harborName = "";
    private long lastTravelTime = 0;
    private final List<HarborTradeOffer> harborTrades = new ArrayList<>();
    private final List<TradePresetEntry> savedTradePreset = new ArrayList<>();
    @Nullable
    private BlockPos savedTargetPos = null;
    private String savedTargetHarborName = "";
    private final List<BlockPos> savedWaypoints = new ArrayList<>();

    public AnchorPointBlockEntity(BlockPos pos, BlockState state) {
        super(CurrentsofTrade.ANCHOR_POINT_BE.get(), pos, state);
    }

    public List<TradePresetEntry> getSavedTradePreset() {
        return Collections.unmodifiableList(this.savedTradePreset);
    }

    public boolean hasSavedTradePreset() {
        return !this.savedTradePreset.isEmpty();
    }

    public void setSavedTradePreset(List<TradePresetEntry> preset) {
        this.savedTradePreset.clear();
        if (preset != null) {
            for (TradePresetEntry entry : preset) {
                if (entry != null && entry.count() > 0 && !entry.offer().getResult().isEmpty()) {
                    this.savedTradePreset.add(entry);
                }
            }
        }
        setChanged();
    }

    public void setSavedRouteTarget(@Nullable BlockPos targetPos, String targetName, List<BlockPos> waypoints) {
        this.savedTargetPos = targetPos != null ? targetPos.immutable() : null;
        this.savedTargetHarborName = targetName != null ? targetName : "";
        this.savedWaypoints.clear();
        if (waypoints != null) {
            for (BlockPos wp : waypoints) {
                if (wp != null) this.savedWaypoints.add(wp.immutable());
            }
        }
        setChanged();
    }

    @Nullable
    public BlockPos getSavedTargetPos() {
        return this.savedTargetPos;
    }

    public String getSavedTargetHarborName() {
        return this.savedTargetHarborName;
    }

    public List<BlockPos> getSavedWaypoints() {
        return Collections.unmodifiableList(this.savedWaypoints);
    }

    // --- Harbor Depot Container Helpers & Redstone Automation ---

    public static List<Container> findNearbyDepotContainers(Level level, BlockPos anchorPos) {
        List<Container> containers = new ArrayList<>();
        if (level == null || anchorPos == null) return containers;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos check : BlockPos.betweenClosed(anchorPos.offset(-4, -2, -4), anchorPos.offset(4, 3, 4))) {
            if (!seen.add(check.immutable())) continue;
            BlockEntity be = level.getBlockEntity(check);
            if (be == null || be instanceof EnderChestBlockEntity || be instanceof AnchorPointBlockEntity) continue;
            BlockState state = level.getBlockState(check);
            if (be instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chestBlock) {
                Container chestContainer = ChestBlock.getContainer(chestBlock, state, level, check, true);
                if (chestContainer != null && !containers.contains(chestContainer)) {
                    containers.add(chestContainer);
                    continue;
                }
            }
            if (be instanceof Container c && !containers.contains(c)) {
                containers.add(c);
            }
        }
        return containers;
    }

    public static boolean isStackMatchingTradeRequirement(ItemStack candidate, ItemStack required) {
        if (candidate.isEmpty() || !ItemStack.isSameItem(candidate, required)) return false;
        if (required.isComponentsPatchEmpty()) return true;
        return ItemStack.isSameItemSameComponents(candidate, required);
    }

    public static int countDepotMatchingItems(Level level, BlockPos anchorPos, ItemStack required) {
        if (required.isEmpty()) return Integer.MAX_VALUE;
        int total = 0;
        for (Container container : findNearbyDepotContainers(level, anchorPos)) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (isStackMatchingTradeRequirement(s, required)) {
                    total += s.getCount();
                }
            }
        }
        return total;
    }

    public static int consumeFromDepotContainers(Level level, BlockPos anchorPos, ItemStack required, int needed) {
        if (required.isEmpty() || needed <= 0) return 0;
        int remaining = needed;
        for (Container container : findNearbyDepotContainers(level, anchorPos)) {
            boolean modified = false;
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack s = container.getItem(i);
                if (isStackMatchingTradeRequirement(s, required)) {
                    int take = Math.min(remaining, s.getCount());
                    s.shrink(take);
                    remaining -= take;
                    if (s.isEmpty()) {
                        container.setItem(i, ItemStack.EMPTY);
                    }
                    modified = true;
                }
            }
            if (modified) {
                container.setChanged();
            }
            if (remaining <= 0) break;
        }
        return needed - remaining;
    }

    public int getComparatorSignal() {
        if (this.level == null) return 0;
        AABB dockArea = new AABB(this.worldPosition).inflate(12.0, 5.0, 12.0);
        List<TradeBoatEntity> ships = this.level.getEntitiesOfClass(TradeBoatEntity.class, dockArea, e -> {
            for (int i = 0; i < e.getContainerSize(); i++) {
                if (!e.getItem(i).isEmpty()) return true;
            }
            return false;
        });
        if (!ships.isEmpty()) {
            return 15;
        }
        if (getRemainingCooldownSeconds() <= 0 && hasSavedTradePreset()) {
            return 8;
        }
        return 0;
    }

    /**
     * Triggered by a rising Redstone signal on the Anchor Point block.
     * Automatically pulls payment items and Doubloon shipping fees from adjacent Harbor Depot chests/barrels
     * and dispatches a Trade Ship using the harbor's saved trade preset.
     */
    public boolean tryRedstoneAutoDispatch(ServerLevel serverLevel) {
        if (getHarborStatus() != HarborStatus.VALID || getRemainingCooldownSeconds() > 0 || !hasSavedTradePreset()) {
            return false;
        }

        BlockPos target = this.savedTargetPos;
        String destHarbor = this.savedTargetHarborName;
        List<BlockPos> waypoints = new ArrayList<>(this.savedWaypoints);

        // Also check if an adjacent depot chest holds a bound Nautical Chart or Route Journal to override target
        for (Container c : findNearbyDepotContainers(serverLevel, this.worldPosition)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && (s.getItem() instanceof NauticalChartItem || s.getItem() instanceof RouteJournalItem)
                        && NauticalChartItem.isBound(s)) {
                    BlockPos chartTarget = NauticalChartItem.getTargetPos(s);
                    if (chartTarget != null) {
                        target = chartTarget;
                        destHarbor = NauticalChartItem.getTargetHarbor(s, serverLevel);
                        waypoints = NauticalChartItem.getWaypoints(s);
                        break;
                    }
                }
            }
        }

        if (target == null) return false;
        if (destHarbor == null || destHarbor.isEmpty()) {
            destHarbor = HarborSavedData.get(serverLevel).getHarborName(target);
            if (destHarbor == null || destHarbor.isEmpty()) destHarbor = "Remote Port";
        }

        BlockPos waterStart = TradeBoatEntity.findBestHarborWaterSpawn(serverLevel, this.worldPosition);
        if (waterStart == null || !TradeBoatEntity.verifyPortWaterConnection(serverLevel, waterStart, waypoints, target)) {
            return false;
        }

        double dx = this.worldPosition.getX() - target.getX();
        double dz = this.worldPosition.getZ() - target.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 0) return false;
        int baseFee = Math.max(1, (int) Math.ceil(dist / 200.0));

        // Check Doubloon availability in depot containers
        ItemStack doubloonReq = new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1);
        int availableDoubloons = countDepotMatchingItems(serverLevel, this.worldPosition, doubloonReq);
        if (availableDoubloons < baseFee) return false;

        int maxSlots = getCargoSlotCount();
        List<ItemStack> stagedPayments = new ArrayList<>();
        List<ItemStack> stagedRewards = new ArrayList<>();
        int stagedTradeCount = 0;

        for (TradePresetEntry entry : this.savedTradePreset) {
            HarborTradeOffer offer = entry.offer();
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();

            for (int c = 0; c < entry.count(); c++) {
                if (stagedPayments.size() + 2 > maxSlots || stagedRewards.size() + 1 > maxSlots) break;

                boolean hasA = costA.isEmpty() || countDepotMatchingItems(serverLevel, this.worldPosition, costA) >= costA.getCount();
                boolean hasB = costB.isEmpty() || countDepotMatchingItems(serverLevel, this.worldPosition, costB) >= costB.getCount();
                if (!costA.isEmpty() && !costB.isEmpty() && ItemStack.isSameItem(costA, costB)) {
                    hasB = countDepotMatchingItems(serverLevel, this.worldPosition, costA) >= (costA.getCount() + costB.getCount());
                }
                if (!hasA || !hasB) break;

                consumeFromDepotContainers(serverLevel, this.worldPosition, costA, costA.getCount());
                if (!costB.isEmpty()) {
                    consumeFromDepotContainers(serverLevel, this.worldPosition, costB, costB.getCount());
                }

                mergeStackIntoList(stagedPayments, costA.copy());
                if (!costB.isEmpty()) {
                    mergeStackIntoList(stagedPayments, costB.copy());
                }
                mergeStackIntoList(stagedRewards, result.copy());
                stagedTradeCount++;
            }
        }

        if (stagedTradeCount == 0) return false;

        int totalStacks = 0;
        for (ItemStack s : stagedPayments) totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
        for (ItemStack s : stagedRewards) totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
        int surcharge = totalStacks > 0 ? (int) Math.ceil(totalStacks / 3.0) : 0;
        int totalFee = Math.min(availableDoubloons, baseFee + surcharge);
        consumeFromDepotContainers(serverLevel, this.worldPosition, doubloonReq, totalFee);

        triggerTravelCooldown();

        double spawnX = waterStart.getX() + 0.5;
        double spawnY = waterStart.getY() + 0.85;
        double spawnZ = waterStart.getZ() + 0.5;
        float initialYaw = (float) (Mth.atan2((target.getZ() + 0.5) - spawnZ, (target.getX() + 0.5) - spawnX) * (180.0 / Math.PI)) - 90.0F;

        TradeBoatEntity tradeBoat = new TradeBoatEntity(serverLevel, spawnX, spawnY, spawnZ);
        tradeBoat.setYRot(initialYaw);
        tradeBoat.setYHeadRot(initialYaw);
        tradeBoat.yRotO = initialYaw;
        tradeBoat.setRouteWaypoints(waypoints);
        tradeBoat.setVoyageTarget(target, destHarbor);
        tradeBoat.setCustomName(Component.literal("Trade Ship"));
        tradeBoat.setCustomNameVisible(true);
        tradeBoat.setTradeMission(this.worldPosition, getHarborName(), stagedRewards);

        for (int i = 0; i < stagedPayments.size() && i < tradeBoat.getContainerSize(); i++) {
            tradeBoat.setItem(i, stagedPayments.get(i).copy());
        }

        serverLevel.addFreshEntity(tradeBoat);
        serverLevel.playSound(null, this.worldPosition, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.playSound(null, this.worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.9F, 1.2F);
        serverLevel.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        return true;
    }

    private static void mergeStackIntoList(List<ItemStack> list, ItemStack toAdd) {
        if (toAdd.isEmpty()) return;
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameComponents(existing, toAdd)) {
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
            if (ItemStack.isSameItemSameComponents(existing.getResult(), check.getResult())) {
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
                    net.minecraft.advancements.AdvancementHolder adv = serverPlayer.getServer().getAdvancements()
                            .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "royal_dockyard"));
                    if (adv != null) {
                        for (String criterion : adv.value().criteria().keySet()) {
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
            this.level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        }
    }

    // --- NBT Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("TradeLevel"))     this.tradeLevel     = tag.getInt("TradeLevel");
        if (tag.contains("HarborName"))     this.harborName     = tag.getString("HarborName");
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
