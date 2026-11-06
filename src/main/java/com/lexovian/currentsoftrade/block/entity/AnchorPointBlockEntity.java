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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        public static final com.mojang.serialization.Codec<TradePresetEntry> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(inst -> inst.group(
                HarborTradeOffer.CODEC.fieldOf("Offer").forGetter(TradePresetEntry::offer),
                com.mojang.serialization.Codec.INT.optionalFieldOf("Count", 1).forGetter(TradePresetEntry::count)
        ).apply(inst, (o, c) -> new TradePresetEntry(o, Math.max(1, c))));

        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put("Offer", offer.toTag(registries));
            tag.putInt("Count", count);
            return tag;
        }

        public static TradePresetEntry fromTag(CompoundTag tag, HolderLookup.Provider registries) {
            HarborTradeOffer offer = HarborTradeOffer.fromTag(tag.getCompoundOrEmpty("Offer"), registries);
            int count = Math.max(1, tag.getIntOr("Count", 1));
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

    public static int countPlayerMatchingItems(net.minecraft.world.entity.player.Player player, ItemStack required) {
        if (player == null || required.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isStackMatchingTradeRequirement(s, required)) {
                total += s.getCount();
            }
        }
        return total;
    }

    public static int consumeFromPlayerInventory(net.minecraft.world.entity.player.Player player, @Nullable ItemStack preferredStack, ItemStack required, int needed) {
        if (player == null || required.isEmpty() || needed <= 0) return 0;
        int remaining = needed;
        if (preferredStack != null && !preferredStack.isEmpty() && isStackMatchingTradeRequirement(preferredStack, required)) {
            int take = Math.min(remaining, preferredStack.getCount());
            preferredStack.shrink(take);
            remaining -= take;
        }
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isStackMatchingTradeRequirement(s, required)) {
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

    /**
     * Allows a player to directly deliver the port's Daily Urgent Bounty (or a High-Demand regional import commodity)
     * at the Anchor Point without needing to dispatch a ship from a second port.
     *
     * @param player    the interacting player
     * @param heldStack the stack in the player's hand when right-clicking the block, or null if triggered via GUI click
     * @return true if the interaction was handled (either fulfilled or showed a missing-count hint for the held bounty item)
     */
    public boolean tryDeliverUrgentBounty(net.minecraft.world.entity.player.Player player, @Nullable ItemStack heldStack) {
        if (this.level == null || player == null) return false;

        MaritimeTradeRegion region = getTradeRegion();
        HarborTradeOffer bounty = region.getUrgentBountyOffer(this.level, this.worldPosition);

        HarborTradeOffer matchedOffer = null;
        if (heldStack != null && !heldStack.isEmpty()) {
            if (bounty != null && !bounty.getCostA().isEmpty() && isStackMatchingTradeRequirement(heldStack, bounty.getCostA())) {
                matchedOffer = bounty;
            } else if (!heldStack.is(CurrentsofTrade.DOUBLOON.get())) {
                // Also check if the held item is one of this port's High-Demand regional import commodities
                for (HarborTradeOffer offer : region.generateDefaultHarborTrades(MAX_HARBOR_LEVEL)) {
                    if (offer.getCostB().isEmpty()
                            && offer.getResult().is(CurrentsofTrade.DOUBLOON.get())
                            && !offer.getCostA().is(CurrentsofTrade.DOUBLOON.get())
                            && isStackMatchingTradeRequirement(heldStack, offer.getCostA())) {
                        // Only match mod trade commodities for direct hand-in so vanilla fish/melon still open the GUI unless desired
                        var key = offer.getCostA().getItem().builtInRegistryHolder().key();
                        if (key != null && CurrentsofTrade.MODID.equals(key.identifier().getNamespace())) {
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
            // GUI click: target today's Daily Urgent Bounty
            matchedOffer = bounty;
            if (matchedOffer == null || matchedOffer.getCostA().isEmpty()) {
                return false;
            }
        }

        ItemStack required = matchedOffer.getCostA();
        int needed = required.getCount();
        int available = countPlayerMatchingItems(player, required);

        if (available < needed) {
            if (!this.level.isClientSide()) {
                this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                player.sendOverlayMessage(Component.translatable(
                        "message.currents_of_trade.bounty_missing_items",
                        needed,
                        required.getHoverName(),
                        matchedOffer.getResult().getCount(),
                        matchedOffer.getResult().getHoverName()
                ));
            }
            return true;
        }

        if (!this.level.isClientSide()) {
            if (!player.getAbilities().instabuild) {
                consumeFromPlayerInventory(player, heldStack, required, needed);
            }
            ItemStack reward = matchedOffer.getResult().copy();
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
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

            player.sendOverlayMessage(Component.translatable(
                    "message.currents_of_trade.bounty_delivered",
                    needed,
                    required.getHoverName(),
                    getHarborName(),
                    matchedOffer.getResult().getCount(),
                    matchedOffer.getResult().getHoverName()
            ));
        }
        return true;
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
                    .map(k -> k.identifier().getPath())
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
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && serverLevel.getServer() != null) {
                    net.minecraft.advancements.AdvancementHolder adv = serverLevel.getServer().getAdvancements()
                            .get(net.minecraft.resources.Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "royal_dockyard"));
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("TradeLevel", this.tradeLevel);
        output.putString("HarborName", this.harborName);
        output.putLong("LastTravelTime", this.lastTravelTime);

        var tradesOut = output.list("HarborTrades", HarborTradeOffer.CODEC);
        for (HarborTradeOffer offer : this.harborTrades) {
            tradesOut.add(offer);
        }

        if (!this.savedTradePreset.isEmpty()) {
            var presetOut = output.list("SavedTradePreset", TradePresetEntry.CODEC);
            for (TradePresetEntry entry : this.savedTradePreset) {
                presetOut.add(entry);
            }
        }

        if (this.savedTargetPos != null) {
            output.store("SavedTargetPos", BlockPos.CODEC, this.savedTargetPos);
            output.putString("SavedTargetName", this.savedTargetHarborName);
        }
        if (!this.savedWaypoints.isEmpty()) {
            var wpOut = output.list("SavedWaypoints", BlockPos.CODEC);
            for (BlockPos wp : this.savedWaypoints) {
                wpOut.add(wp);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.tradeLevel = input.getIntOr("TradeLevel", 1);
        this.harborName = input.getStringOr("HarborName", "Harbor");
        this.lastTravelTime = input.getLongOr("LastTravelTime", 0L);

        this.harborTrades.clear();
        input.list("HarborTrades", HarborTradeOffer.CODEC).ifPresent(list -> {
            for (HarborTradeOffer offer : list) {
                this.harborTrades.add(offer);
            }
        });

        this.savedTradePreset.clear();
        input.list("SavedTradePreset", TradePresetEntry.CODEC).ifPresent(list -> {
            for (TradePresetEntry entry : list) {
                this.savedTradePreset.add(entry);
            }
        });

        this.savedTargetPos = input.read("SavedTargetPos", BlockPos.CODEC).orElse(null);
        this.savedTargetHarborName = input.getStringOr("SavedTargetName", "");

        this.savedWaypoints.clear();
        input.list("SavedWaypoints", BlockPos.CODEC).ifPresent(list -> {
            for (BlockPos wp : list) {
                this.savedWaypoints.add(wp);
            }
        });

        syncToSavedData();
    }

    // --- Client Sync ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {

        return this.saveCustomOnly(registries);

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
