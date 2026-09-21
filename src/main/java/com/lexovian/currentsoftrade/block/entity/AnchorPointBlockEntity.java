package com.lexovian.currentsoftrade.block.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
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

    public AnchorPointBlockEntity(BlockPos pos, BlockState state) {
        super(CurrentsofTrade.ANCHOR_POINT_BE.get(), pos, state);
    }

    // --- Harbor Naming ---

    /**
     * Generates a harbour name from the biome at the given position,
     * e.g. "Port Taiga" or "Port Ocean". Falls back to coordinates on failure.
     */
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
        // Use level-based cap; fall back to global config if somehow called before level is set
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

    /** Pushes current name and trade list into the global HarborSavedData. */
    public void syncToSavedData() {
        if (this.level instanceof ServerLevel serverLevel) {
            HarborSavedData data = HarborSavedData.get(serverLevel);
            data.setTrades(this.getBlockPos(), this.harborTrades);
            data.setHarborName(this.getBlockPos(), this.getHarborName());
        }
    }

    // --- Getters / Setters ---

    public int getTradeLevel() {
        return tradeLevel;
    }

    public void setTradeLevel(int tradeLevel) {
        this.tradeLevel = Math.max(1, Math.min(MAX_HARBOR_LEVEL, tradeLevel));
        setChanged();
    }

    /**
     * Human-readable level title, e.g. "Fishing Wharf" or "Royal Dockyard".
     */
    public String getHarborLevelName() {
        return switch (this.tradeLevel) {
            case 2 -> "Trading Post";
            case 3 -> "Harbor";
            case 4 -> "Grand Port";
            case 5 -> "Royal Dockyard";
            default -> "Fishing Wharf";
        };
    }

    /**
     * Returns the voyage cooldown in seconds for this harbor level.
     * Higher levels = shorter cooldowns.
     */
    public int getLevelCooldownSeconds() {
        return switch (this.tradeLevel) {
            case 2 -> 45;
            case 3 -> 30;
            case 4 -> 20;
            case 5 -> 10;
            default -> 60;
        };
    }

    /**
     * Returns the cargo slot count available for Send Items / Return Trade at this level.
     * Grows from 18 (Lv1) to 54 (Lv5) in steps of 9.
     */
    public int getCargoSlotCount() {
        return switch (this.tradeLevel) {
            case 2 -> 27;
            case 3 -> 36;
            case 4 -> 45;
            case 5 -> 54;
            default -> 18;
        };
    }

    /**
     * Returns the maximum number of trade offers this harbor can hold at its current level.
     */
    public int getMaxTradeOffers() {
        return switch (this.tradeLevel) {
            case 2 -> 8;
            case 3 -> 10;
            case 4 -> 12;
            case 5 -> 14;
            default -> 6;
        };
    }

    /**
     * Returns true when the player can pay the upgrade cost for the next harbor level.
     * Returns false at max level.
     */
    public boolean canUpgrade(net.minecraft.world.entity.player.Player player) {
        if (this.tradeLevel >= MAX_HARBOR_LEVEL) return false;
        HarborUpgradeCost cost = HarborUpgradeCost.forLevel(this.tradeLevel);
        return cost != null && cost.canAfford(player);
    }

    /**
     * Consumes the required materials and advances this harbor to the next level.
     * Caller MUST verify {@link #canUpgrade(net.minecraft.world.entity.player.Player)} first.
     */
    public void executeUpgrade(net.minecraft.world.entity.player.Player player) {
        if (this.tradeLevel >= MAX_HARBOR_LEVEL) return;
        HarborUpgradeCost cost = HarborUpgradeCost.forLevel(this.tradeLevel);
        if (cost == null || !cost.canAfford(player)) return;
        cost.consume(player);
        this.tradeLevel++;
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            HarborSavedData.get(serverLevel).setHarborLevel(this.worldPosition, this.tradeLevel);
        }
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    public String getHarborName() {
        boolean needsName = this.harborName == null
                || this.harborName.trim().isEmpty()
                || "Port Alpha".equalsIgnoreCase(this.harborName.trim());

        if (needsName) {
            // Try to pull a previously saved name from persistent data first
            if (this.level instanceof ServerLevel serverLevel) {
                String saved = HarborSavedData.get(serverLevel).getHarborName(this.worldPosition);
                if (saved != null && !saved.trim().isEmpty() && !"Port Alpha".equalsIgnoreCase(saved.trim())) {
                    this.harborName = saved.trim();
                    return this.harborName;
                }
            }
            // Fall back to biome-based dynamic name
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

    /**
     * Validates whether this anchor point sits beside a legitimate harbour:
     * open sky, minimum water volume, and at least 2 blocks of depth.
     */
    public HarborStatus getHarborStatus() {
        if (this.level == null) return HarborStatus.VALID;
        if (this.level.dimension() != Level.OVERWORLD) return HarborStatus.WRONG_DIMENSION;

        BlockPos center = this.getBlockPos();
        BlockPos waterStart = null;

        // Find any adjacent water block within search box (radius 8 to reach water from inside dock huts/offices)
        for (BlockPos check : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 2, 8))) {
            if (this.level.getFluidState(check).is(FluidTags.WATER)) {
                waterStart = check.immutable();
                break;
            }
        }

        if (waterStart == null) {
            return HarborStatus.NO_WATER;
        }

        int minWater = Config.MIN_HARBOR_WATER_BLOCKS != null ? Config.MIN_HARBOR_WATER_BLOCKS.get() : MIN_HARBOR_WATER_BLOCKS;

        // BFS flood-fill to count connected water blocks
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

            // Surface harbor check: Valid if open to sky OR at surface elevation (Y >= 50, sea level is 62/63)
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

    /** Returns seconds remaining on the post-departure cooldown (0 = ready to sail). */
    public int getRemainingCooldownSeconds() {
        if (this.level == null) return 0;
        long elapsed = this.level.getGameTime() - this.lastTravelTime;
        // Use level-based cooldown; config value acts as a hard ceiling if set lower
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
