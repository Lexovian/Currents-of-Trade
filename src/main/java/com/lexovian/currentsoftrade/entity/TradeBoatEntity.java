package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.compat.CustomData;
import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TradeBoatEntity extends Boat implements HasCustomInventoryScreen, ContainerEntity {

    public static final int CARGO_SLOTS = 54;
    public static final float MAX_SHIP_HEALTH = 100.0F;
    private static final double CARGO_MAX_SPEED = 1.0;
    private static final int[] DETOUR_OFFSETS_DEG = { 20, 32, 45, 60, 75, 90, 105, 120, 135, 150, 165 };

    protected BlockPos targetPos;
    protected final List<BlockPos> routeWaypoints = new ArrayList<>();
    protected int currentWaypointIndex = 0;
    private String harborName = "Unknown Port";
    private int idleTicks = 0;
    protected double cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
    protected boolean arrived = false;

    // Navigation, coarse A* pathfinding & obstacle avoidance state
    private int avoidanceSide = 0;
    private int stuckTicks = 0;
    @Nullable private Vec3 detourWaypoint = null;
    private int detourTicks = 0;
    private boolean lastDirectClear = true;
    private double detourStartDist = Double.MAX_VALUE;
    private double bestDistToGoal = Double.MAX_VALUE;
    private int noProgressTicks = 0;
    private final List<Vec3> autoPathWaypoints = new ArrayList<>();
    private int autoPathIndex = 0;
    private boolean autoPathReachedGoal = false;
    private int lastAutoPathTick = -1000;
    @Nullable private WaterRoutePlanner routePlanner = null;
    @Nullable private BlockPos plannedGoal = null;
    private static long plannerBudgetGameTime = Long.MIN_VALUE;
    private static long plannerBudgetUsedNs = 0L;
    private static final long PLANNER_SHIP_BUDGET_NS = 4_000_000L;   // max 4 ms per ship per tick
    private static final long PLANNER_TICK_BUDGET_NS = 8_000_000L;   // max 8 ms shared by all ships per tick
    private static final int PLANNER_MAX_EXPANSIONS = 60000;          // ~540k blocks^2 of explored water
    private final List<Vec3> recentBreadcrumbs = new ArrayList<>();
    @Nullable private BlockPos portApproachPos = null;
    @Nullable private BlockPos portApproachTarget = null;
    private boolean reachedPortApproach = false;

    // Hull HP & Cargo hold
    private float shipHealth = MAX_SHIP_HEALTH;
    private int lastHurtTick = -1000;
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(CARGO_SLOTS, ItemStack.EMPTY);
    @Nullable private ResourceLocation lootTable;
    private long lootTableSeed;
    protected boolean cargoDelivered = false;
    protected boolean hadInitialCargo = false;
    protected int emptyTicks = 0;

    // Trade mission state
    public enum TradeMissionPhase { NONE, OUTBOUND, RETURNING }

    protected boolean isTradeMission = false;
    protected TradeMissionPhase missionPhase = TradeMissionPhase.NONE;
    protected BlockPos homeHarborPos;
    protected String homeHarborName = "Home Port";
    protected final NonNullList<ItemStack> tradeRewards = NonNullList.create();

    @Nullable protected java.util.UUID senderUUID;
    @Nullable protected String senderName;
    protected final Set<ChunkPos> activeForcedChunks = new HashSet<>();
    @Nullable protected ChunkPos lastCenterChunk;
    protected static final int MAX_BLOCKED_TICKS = 500; // 25 seconds
    protected static final int MAX_NO_PROGRESS_TICKS = 600; // 30 seconds of circling without net progress
    protected int blockedTicks = 0;

    private boolean voyageShip = false;
    private int tailwindTicks = 0;

    public void applyTailwind(int ticks) {
        this.tailwindTicks = Math.max(this.tailwindTicks, ticks);
    }

    public boolean hasTailwind() {
        return this.tailwindTicks > 0;
    }

    public final ShipPart<TradeBoatEntity> sternPart;
    public final ShipPart<TradeBoatEntity> prowPart;
    private final ShipPart<?>[] subEntities;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_WANDERING_TRADER_SHIP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TradeBoatEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    // Synced so the client renders travel ships with the plain sloop look instead of the striped trade-ship look
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_VOYAGE_SHIP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TradeBoatEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** Ticks a travel ship may go without any progress before it is treated as blocked (30 seconds). */
    private static final int VOYAGE_GIVE_UP_TICKS = 600;

    // --- Constructors & Setup ---

    public TradeBoatEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(false);
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
        this.setCustomName(Component.literal("Trade Ship"));
        this.setCustomNameVisible(true);
    }

    public TradeBoatEntity(Level level, double x, double y, double z) {
        this(CurrentsofTrade.TRADE_BOAT.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WANDERING_TRADER_SHIP, false);
        this.entityData.define(IS_VOYAGE_SHIP, false);
    }

    public boolean isWanderingTraderShip() {
        return this.entityData.get(IS_WANDERING_TRADER_SHIP);
    }

    public void setWanderingTraderShip(boolean wanderingTraderShip) {
        this.entityData.set(IS_WANDERING_TRADER_SHIP, wanderingTraderShip);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.subEntities.length; ++i) {
            this.subEntities[i].setId(id + i + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.minecraftforge.entity.PartEntity<?>[] getParts() {
        return this.subEntities;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.releaseAllForcedChunks();
        super.remove(reason);
        for (var part : this.subEntities) {
            part.remove(reason);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        // Never collide with our own stern/prow multipart hitboxes (they would clip forward motion every tick)
        if (entity instanceof ShipPart<?> part && part.getParent() == this) return false;
        return super.canCollideWith(entity);
    }

    @Override
    public float maxUpStep() {
        return 0.0F;
    }

    public void setVoyageTarget(BlockPos target, String harborName) {
        this.targetPos = target;
        this.harborName = harborName;
        this.bestDistToGoal = Double.MAX_VALUE;
        this.detourStartDist = Double.MAX_VALUE;
        this.noProgressTicks = 0;
        this.autoPathWaypoints.clear();
        this.autoPathIndex = 0;
        this.autoPathReachedGoal = false;
        this.recentBreadcrumbs.clear();
        this.portApproachPos = null;
        this.portApproachTarget = null;
        this.reachedPortApproach = false;
        BlockPos firstGoal = (!this.routeWaypoints.isEmpty() && this.currentWaypointIndex < this.routeWaypoints.size())
                ? this.routeWaypoints.get(this.currentWaypointIndex)
                : target;
        if (firstGoal != null) {
            double dx = (firstGoal.getX() + 0.5) - this.getX();
            double dz = (firstGoal.getZ() + 0.5) - this.getZ();
            float initialYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.setYRot(initialYaw);
            this.setYHeadRot(initialYaw);
            this.yRotO = initialYaw;
        }
    }

    public void setRouteWaypoints(@Nullable List<BlockPos> waypoints) {
        this.routeWaypoints.clear();
        this.currentWaypointIndex = 0;
        if (waypoints != null) {
            for (BlockPos wp : waypoints) {
                if (wp == null) continue;
                // Snap each chart waypoint onto navigable open water; drop waypoints that are nowhere near water
                BlockPos snapped = this.level().isClientSide ? wp : findNearestNavigableWater(this.level(), wp, 6);
                if (snapped != null) this.routeWaypoints.add(snapped.immutable());
            }
        }
        this.bestDistToGoal = Double.MAX_VALUE;
        this.detourStartDist = Double.MAX_VALUE;
        this.noProgressTicks = 0;
        this.autoPathWaypoints.clear();
        this.autoPathIndex = 0;
        this.autoPathReachedGoal = false;
        this.recentBreadcrumbs.clear();
        this.portApproachPos = null;
        this.portApproachTarget = null;
        this.reachedPortApproach = false;
        if (!this.routeWaypoints.isEmpty()) {
            BlockPos first = this.routeWaypoints.get(0);
            double dx = (first.getX() + 0.5) - this.getX();
            double dz = (first.getZ() + 0.5) - this.getZ();
            float initialYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.setYRot(initialYaw);
            this.setYHeadRot(initialYaw);
            this.yRotO = initialYaw;
        }
    }

    @Nullable
    protected BlockPos getActiveNavigationGoal() {
        while (this.currentWaypointIndex < this.routeWaypoints.size()) {
            BlockPos wp = this.routeWaypoints.get(this.currentWaypointIndex);
            double dx = (wp.getX() + 0.5) - this.getX();
            double dz = (wp.getZ() + 0.5) - this.getZ();
            if (dx * dx + dz * dz <= 64.0) { // Within 8 blocks of intermediate waypoint
                this.currentWaypointIndex++;
                this.detourWaypoint = null;
                this.avoidanceSide = 0;
                this.detourTicks = 0;
                this.detourStartDist = Double.MAX_VALUE;
                this.bestDistToGoal = Double.MAX_VALUE;
                this.noProgressTicks = 0;
                this.autoPathWaypoints.clear();
                this.autoPathIndex = 0;
                this.autoPathReachedGoal = false;
                this.recentBreadcrumbs.clear();
            } else {
                return wp;
            }
        }

        if (this.targetPos == null) return null;

        // Two-stage port approach: stay in the middle of the river/channel until directly across
        // from the destination port, then turn into the harbor dock for the final 5-12 blocks.
        if (!this.reachedPortApproach) {
            double tdx = (this.targetPos.getX() + 0.5) - this.getX();
            double tdz = (this.targetPos.getZ() + 0.5) - this.getZ();
            double tDistSq = tdx * tdx + tdz * tdz;
            if (tDistSq <= 156.25) { // Already within 12.5m of target dock
                this.reachedPortApproach = true;
            } else {
                int tcx = this.targetPos.getX() >> 4;
                int tcz = this.targetPos.getZ() >> 4;
                if (ensureChunkLoadedForNav(this.level(), tcx, tcz)) {
                    if (this.portApproachPos == null || !this.targetPos.equals(this.portApproachTarget)) {
                        this.portApproachTarget = this.targetPos;
                        BlockPos bestWater = findBestHarborWaterSpawn(this.level(), this.targetPos);
                        this.portApproachPos = (bestWater != null) ? bestWater : this.targetPos;
                    }
                    if (this.portApproachPos != null && !this.portApproachPos.equals(this.targetPos)) {
                        double adx = (this.portApproachPos.getX() + 0.5) - this.getX();
                        double adz = (this.portApproachPos.getZ() + 0.5) - this.getZ();
                        if (adx * adx + adz * adz <= 25.0) { // Reached mid-river point in front of port (<= 5.0m)
                            this.reachedPortApproach = true;
                            this.detourWaypoint = null;
                            this.autoPathWaypoints.clear();
                            this.autoPathIndex = 0;
                            this.autoPathReachedGoal = false;
                        } else {
                            return this.portApproachPos;
                        }
                    } else {
                        this.reachedPortApproach = true;
                    }
                }
            }
        }

        return this.targetPos;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public String getHarborName() {
        return this.harborName;
    }

    public int getStuckTicks() {
        return this.stuckTicks;
    }

    public int getDetourTicks() {
        return this.detourTicks;
    }

    public void setSender(@Nullable Player player) {
        if (player != null) {
            this.senderUUID = player.getUUID();
            this.senderName = player.getName().getString();
        }
    }

    public void setHomeHarbor(@Nullable BlockPos homePos, @Nullable String homeName) {
        this.homeHarborPos = homePos != null ? homePos.immutable() : null;
        if (homeName != null) this.homeHarborName = homeName;
    }

    public void setTradeMission(BlockPos homePos, String homeName, List<ItemStack> rewards) {
        this.isTradeMission = true;
        this.missionPhase = TradeMissionPhase.OUTBOUND;
        this.homeHarborPos = homePos.immutable();
        this.homeHarborName = homeName;
        this.setCustomName(Component.literal("Trade Ship"));
        this.setCustomNameVisible(true);
        this.tradeRewards.clear();
        for (ItemStack reward : rewards) {
            if (!reward.isEmpty()) this.tradeRewards.add(reward.copy());
        }
    }

    public void setTradeMission(BlockPos homePos, String homeName, ItemStack reward) {
        setTradeMission(homePos, homeName, List.of(reward));
    }

    public boolean isTradeMission() {
        return this.isTradeMission;
    }

    public boolean isCargoDelivered() {
        return this.cargoDelivered;
    }

    public void setVoyageShip(boolean voyageShip) {
        this.voyageShip = voyageShip;
        this.entityData.set(IS_VOYAGE_SHIP, voyageShip);
        if (voyageShip) {
            this.cruiseSpeed = (Config.FAST_TRAVEL_BOAT_SPEED != null) ? Config.FAST_TRAVEL_BOAT_SPEED.get() : 1.8;
        } else {
            this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        }
    }

    public boolean isVoyageShip() {
        return this.entityData.get(IS_VOYAGE_SHIP);
    }

    protected boolean requiresPassenger() {
        return this.voyageShip;
    }

    protected boolean shouldDiscardOnArrival() {
        return this.voyageShip;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.level().isClientSide || this.isRemoved()) {
            return true;
        }

        // Creative mode players can always destroy any ship immediately in 1 hit
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            this.destroy(source);
            return true;
        }

        // Docked / delivered ships or idle ships without a target break immediately when hit
        if (this.cargoDelivered || this.targetPos == null) {
            this.destroy(source);
            return true;
        }

        // In-transit autonomous ships have 100 HP so players can break them if stuck
        if (source.getEntity() instanceof Player player) {
            float effectiveDmg = Math.max(4.0F, amount);
            if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem) {
                effectiveDmg *= 2.0F;
            }
            this.shipHealth = Math.max(0.0F, this.shipHealth - effectiveDmg);
            this.lastHurtTick = this.tickCount;
            this.markHurt();

            this.level().playSound(null, this.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1.0F, 0.85F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.getX(), this.getY() + 0.6, this.getZ(),
                        8, 0.5, 0.3, 0.5, 0.1);
            }

            if (this.shipHealth <= 0.0F) {
                this.level().playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.8F, 0.9F);
                this.destroy(Items.AIR);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.ship_hull_hp",
                                (int) Math.ceil(this.shipHealth), (int) MAX_SHIP_HEALTH),
                        true
                );
            }
            return true;
        }

        return false;
    }

    public void destroy(DamageSource source) {
        this.destroy(this.getDropItem());
    }

    public void destroy(Item dropItem) {
        this.releaseAllForcedChunks();
        Containers.dropContents(this.level(), this, this);
        if (dropItem != Items.AIR && this.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(dropItem);
        }
        this.discard();
    }

    @Override
    public Item getDropItem() {
        if (this.isTradeMission || this.voyageShip || this.targetPos != null || this.cargoDelivered || this.isWanderingTraderShip()) {
            return Items.AIR;
        }
        return CurrentsofTrade.TRADE_BOAT_ITEM != null && CurrentsofTrade.TRADE_BOAT_ITEM.get() != null
                ? CurrentsofTrade.TRADE_BOAT_ITEM.get()
                : Items.OAK_BOAT;
    }

    @Override
    public Component getTypeName() {
        if (this.isVoyageShip()) {
            return Component.translatable("entity.currents_of_trade.sloop");
        }
        return Component.literal(this.isTradeMission ? "Trade Ship" : "Trade Boat");
    }

    @Override
    public Component getDisplayName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getTypeName();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.isWanderingTraderShip()) {
            return InteractionResult.PASS;
        }
        if (this.voyageShip) {
            return super.interact(player, hand);
        }
        if (player.isSecondaryUseActive() || this.cargoDelivered) {
            return this.interactWithContainerVehicle(player);
        }
        return this.interactWithContainerVehicle(player);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        player.openMenu(this);
    }

    // --- ContainerEntity Implementation ---

    @Override public NonNullList<ItemStack> getItemStacks() { return this.itemStacks; }
    @Override public void clearItemStacks() { this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); }
    @Override public int getContainerSize() { return CARGO_SLOTS; }
    @Override public ItemStack getItem(int slot) { return this.getChestVehicleItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return this.removeChestVehicleItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return this.removeChestVehicleItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { this.setChestVehicleItem(slot, stack); }
    @Override public SlotAccess getSlot(int slot) { return this.getChestVehicleSlot(slot); }
    @Override public void setChanged() {}
    @Override public boolean stillValid(Player player) { return this.isChestVehicleStillValid(player); }
    @Override public void clearContent() { this.clearChestVehicleContent(); }
    @Nullable @Override public ResourceLocation getLootTable() { return this.lootTable; }
    @Override public void setLootTable(@Nullable ResourceLocation lootTable) { this.lootTable = lootTable; }
    @Override public long getLootTableSeed() { return this.lootTableSeed; }
    @Override public void setLootTableSeed(long seed) { this.lootTableSeed = seed; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.itemStacks) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) return null;
        this.unpackChestVehicleLootTable(playerInventory.player);
        return new ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, this, 6);
    }

    // --- Dynamic Chunk Loading & Safe Route Abort ---

    private void updateForcedChunks(ServerLevel serverLevel, ChunkPos centerChunk) {
        Set<ChunkPos> desired = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                desired.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
            }
        }

        float yawRad = (float) Math.toRadians(this.getYRot());
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        int forwardCx1 = (int) Math.floor((this.getX() + lookX * 24.0) / 16.0);
        int forwardCz1 = (int) Math.floor((this.getZ() + lookZ * 24.0) / 16.0);
        desired.add(new ChunkPos(forwardCx1, forwardCz1));
        int forwardCx2 = (int) Math.floor((this.getX() + lookX * 48.0) / 16.0);
        int forwardCz2 = (int) Math.floor((this.getZ() + lookZ * 48.0) / 16.0);
        desired.add(new ChunkPos(forwardCx2, forwardCz2));

        for (ChunkPos cp : this.activeForcedChunks) {
            if (!desired.contains(cp)) {
                serverLevel.setChunkForced(cp.x, cp.z, false);
            }
        }
        for (ChunkPos cp : desired) {
            if (!this.activeForcedChunks.contains(cp)) {
                serverLevel.setChunkForced(cp.x, cp.z, true);
            }
        }

        this.activeForcedChunks.clear();
        this.activeForcedChunks.addAll(desired);
    }

    private void releaseAllForcedChunks() {
        if (!this.activeForcedChunks.isEmpty() && this.level() instanceof ServerLevel serverLevel) {
            for (ChunkPos cp : this.activeForcedChunks) {
                serverLevel.setChunkForced(cp.x, cp.z, false);
            }
            this.activeForcedChunks.clear();
        }
        this.lastCenterChunk = null;
    }

    private void abortAndReturnCargo(ServerLevel serverLevel) {
        this.releaseAllForcedChunks();

        BlockPos returnPos = null;
        if (this.homeHarborPos != null) {
            returnPos = findBestHarborWaterSpawn(serverLevel, this.homeHarborPos);
            if (returnPos == null) {
                returnPos = this.homeHarborPos;
            }
        }

        if (returnPos != null) {
            int topY = returnPos.getY();
            while (topY < serverLevel.getMaxBuildHeight() && serverLevel.getFluidState(new BlockPos(returnPos.getX(), topY + 1, returnPos.getZ())).is(FluidTags.WATER)) {
                topY++;
            }

            TradeBoatEntity returnBoat;
            if (this instanceof CargoBoatEntity) {
                returnBoat = new CargoBoatEntity(serverLevel, returnPos.getX() + 0.5, topY + 0.85, returnPos.getZ() + 0.5);
            } else {
                returnBoat = new TradeBoatEntity(serverLevel, returnPos.getX() + 0.5, topY + 0.85, returnPos.getZ() + 0.5);
            }
            returnBoat.cargoDelivered = true;
            returnBoat.setDeltaMovement(0.0, 0.0, 0.0);
            returnBoat.setCustomName(Component.literal(this.isTradeMission ? "Trade Ship" : "Cargo Ship"));
            returnBoat.setCustomNameVisible(true);
            if (this.senderUUID != null) returnBoat.senderUUID = this.senderUUID;
            if (this.senderName != null) returnBoat.senderName = this.senderName;

            for (int i = 0; i < this.getContainerSize(); i++) {
                ItemStack stack = this.getItem(i);
                if (!stack.isEmpty()) {
                    returnBoat.setItem(i, stack.copy());
                }
            }
            this.clearContent();
            serverLevel.addFreshEntity(returnBoat);

            serverLevel.playSound(null, returnPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            Containers.dropContents(serverLevel, this, this);
        }

        String targetName = this.getHarborName() != null ? this.getHarborName() : "Destination";
        String homeName = this.homeHarborName != null ? this.homeHarborName : "Home Harbor";

        if (this.senderUUID != null && serverLevel.getServer() != null) {
            Player sender = serverLevel.getServer().getPlayerList().getPlayer(this.senderUUID);
            if (sender != null) {
                sender.displayClientMessage(
                        Component.translatable("message.currents_of_trade.cargo_route_blocked", targetName, homeName),
                        false
                );
            }
        }

        serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.3, this.getZ(), 25, 0.4, 0.3, 0.4, 0.1);
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.4, this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 0.7F);

        this.discard();
    }

    // --- Rider & Hitbox Positioning ---

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            float forwardOffset = -2.00F;
            float verticalOffset = 0.98F;

            float yawRad = (float) Math.toRadians(this.getYRot());
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);

            double worldX = -forwardOffset * sin;
            double worldZ = forwardOffset * cos;

            callback.accept(passenger, this.getX() + worldX, this.getY() + verticalOffset, this.getZ() + worldZ);
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        double half = 0.68;
        return new AABB(
                this.getX() - half, this.getY(), this.getZ() - half,
                this.getX() + half, this.getY() + 0.85, this.getZ() + half
        );
    }

    public void updateParts() {
        double yawRad = Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double sternX = this.getX() + 1.90 * sin;
        double sternZ = this.getZ() - 1.90 * cos;
        this.sternPart.moveTo(sternX, this.getY(), sternZ);

        double prowX = this.getX() - 2.10 * sin;
        double prowZ = this.getZ() + 2.10 * cos;
        this.prowPart.moveTo(prowX, this.getY(), prowZ);
    }

    // --- Tick & Navigation Cycle ---

    @Override
    public void tick() {
        if (!this.isEmpty()) {
            this.hadInitialCargo = true;
        }

        if (!this.level().isClientSide && this.shipHealth < MAX_SHIP_HEALTH && this.tickCount - this.lastHurtTick > 100) {
            this.shipHealth = Math.min(MAX_SHIP_HEALTH, this.shipHealth + 0.1F);
        }

        // Despawn whenever all cargo is taken out (both after arrival AND if looted mid-voyage while stuck)
        boolean isAutonomousCargoOrTrade = !this.voyageShip
                && !this.isWanderingTraderShip()
                && !(this instanceof CargoBoatEntity cb && cb.isPlayerControlled());

        if (!this.level().isClientSide && isAutonomousCargoOrTrade && (this.cargoDelivered || this.hadInitialCargo)) {
            if (this.isEmpty()) {
                this.emptyTicks++;
                if (this.emptyTicks >= 20) {
                    this.releaseAllForcedChunks();
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.SPLASH,
                                this.getX(), this.getY() + 0.3, this.getZ(), 20, 0.3, 0.2, 0.3, 0.1);
                        serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                                this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.2, 0.1, 0.2, 0.05);
                        serverLevel.playSound(null, this.blockPosition(),
                                SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 0.9F);
                    }
                    this.discard();
                    return;
                }
            } else {
                this.emptyTicks = 0;
            }
        }

        if (this.cargoDelivered) {
            this.updateParts();
            if (this.level().isClientSide) return;
            this.setDeltaMovement(0.0, 0.0, 0.0);
            return;
        }

        if (!this.level().isClientSide && !this.voyageShip && this.level() instanceof ServerLevel serverLevel) {
            ChunkPos currentChunk = new ChunkPos(this.blockPosition());
            if (this.lastCenterChunk == null || !this.lastCenterChunk.equals(currentChunk)) {
                this.updateForcedChunks(serverLevel, currentChunk);
                this.lastCenterChunk = currentChunk;
            }
        }

        boolean collidedLastMove = this.horizontalCollision;
        if (!this.level().isClientSide && this.targetPos != null && !this.cargoDelivered) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        double prevClientX = this.getX();
        double prevClientZ = this.getZ();
        float prevClientYaw = this.getYRot();

        super.tick();

        if (!this.level().isClientSide && this.targetPos != null && !this.cargoDelivered) {
            this.horizontalCollision = collidedLastMove;
        } else if (this.level().isClientSide && !this.cargoDelivered
                && !(this instanceof CargoBoatEntity cb && cb.isPlayerControlled())) {
            double cdx = this.getX() - prevClientX;
            double cdz = this.getZ() - prevClientZ;
            if (cdx * cdx + cdz * cdz > 0.0004) {
                float moveYaw = (float) (Mth.atan2(cdz, cdx) * (180.0 / Math.PI)) - 90.0F;
                float diff = Mth.wrapDegrees(moveYaw - prevClientYaw);
                float nextYaw = prevClientYaw + Mth.clamp(diff, -16.0F, 16.0F);
                this.yRotO = prevClientYaw;
                this.setYRot(nextYaw);
                this.setYHeadRot(nextYaw);
            }
        }
        this.setBoundingBox(this.makeBoundingBox());
        this.updateParts();

        if (this.tailwindTicks > 0) {
            this.tailwindTicks--;
            if (this.level().isClientSide && this.random.nextFloat() < 0.3F) {
                this.level().addParticle(ParticleTypes.GLOW,
                        this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                        this.getY() + 0.6,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
                        0.0, 0.04, 0.0);
            }
        }

        if (!this.level().isClientSide) {
            if (this.targetPos == null) {
                this.setNoGravity(false);
                return;
            }
            this.setNoGravity(true);

            Entity passenger = this.getFirstPassenger();
            if (this.requiresPassenger()) {
                if (passenger == null) {
                    this.idleTicks++;
                    if (this.idleTicks > 80) {
                        this.discard();
                    }
                    return;
                }
                passenger.fallDistance = 0.0F;
                passenger.resetFallDistance();
            }

            double finalDx = (this.targetPos.getX() + 0.5) - this.getX();
            double finalDz = (this.targetPos.getZ() + 0.5) - this.getZ();
            double finalDist = Math.sqrt(finalDx * finalDx + finalDz * finalDz);

            if (finalDist <= 6.0) {
                this.arriveAtDestination(passenger);
                return;
            }

            BlockPos activeGoal = this.getActiveNavigationGoal();
            if (activeGoal == null) activeGoal = this.targetPos;

            double dx = (activeGoal.getX() + 0.5) - this.getX();
            double dz = (activeGoal.getZ() + 0.5) - this.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            double horizontalDist = Math.sqrt(horizontalDistSq);

            // Record breadcrumb every 5 blocks of travel to prevent river-bend oscillation loops
            if (this.recentBreadcrumbs.isEmpty()
                    || this.position().distanceToSqr(this.recentBreadcrumbs.get(this.recentBreadcrumbs.size() - 1)) >= 25.0) {
                this.recentBreadcrumbs.add(this.position());
                if (this.recentBreadcrumbs.size() > 64) {
                    this.recentBreadcrumbs.remove(0);
                }
            }

            // Track net progress toward active goal to detect coast-circling loops
            // (paused while the ship waits in place for its route planner to finish)
            if (horizontalDist < this.bestDistToGoal - 2.0) {
                this.bestDistToGoal = horizontalDist;
                this.noProgressTicks = 0;
            } else if (!this.isAwaitingRoute()) {
                this.noProgressTicks++;
            }

            // Safety net for travel ships: never sail around indefinitely without getting anywhere
            if (this.voyageShip && this.noProgressTicks >= VOYAGE_GIVE_UP_TICKS) {
                this.noProgressTicks = 0;
                this.cancelVoyage();
                return;
            }

            double directAngle = Mth.atan2(dz, dx);
            // Only use the straight-line shortcut during the final harbor docking approach (<= 14m);
            // during open-water and river transit, always follow the verified coarse+fine water route.
            boolean shouldScanDirect = (this.reachedPortApproach || horizontalDist <= 14.0)
                    && ((this.tickCount % 6 == 0) || (this.detourWaypoint != null) || (this.stuckTicks > 0) || (horizontalDist < 14.0));

            boolean rawDirectClear;
            if (shouldScanDirect) {
                double margin = Math.max(0.45, Math.min(1.5, (horizontalDist - 2.5) / 8.0));
                rawDirectClear = isSegmentClearLoadedOnly(this.level(), this.getX(), this.getZ(), activeGoal.getX() + 0.5, activeGoal.getZ() + 0.5, margin);
                this.lastDirectClear = rawDirectClear;
            } else if (!this.reachedPortApproach && horizontalDist > 14.0) {
                rawDirectClear = false;
                this.lastDirectClear = false;
            } else {
                rawDirectClear = this.lastDirectClear;
            }

            boolean directPathClear = rawDirectClear
                    && (this.detourTicks == 0 || horizontalDist < this.detourStartDist - 3.0 || horizontalDist <= 14.0);

            double chosenAngle;
            double currentSpeed = this.cruiseSpeed * (this.hasTailwind() ? 1.25 : 1.0);

            if (directPathClear) {
                this.detourWaypoint = null;
                this.avoidanceSide = 0;
                this.detourTicks = 0;
                this.detourStartDist = Double.MAX_VALUE;
                // Keep the planned route: if the straight line closes again we resume it instead of re-planning
                chosenAngle = directAngle;

                if (finalDist < 25.0) {
                    currentSpeed = Math.max(0.4, (this.cruiseSpeed * (this.hasTailwind() ? 1.25 : 1.0)) * (finalDist / 25.0));
                }
            } else {
                if (this.detourTicks == 0) {
                    this.detourStartDist = horizontalDist;
                }
                this.detourTicks++;

                // Flip avoidance side and trigger background re-plan if circling without progress
                // (never clear existing autoPathWaypoints until the replacement route finishes)
                boolean forceReplan = false;
                if (this.noProgressTicks > 0 && this.noProgressTicks % 140 == 0) {
                    this.avoidanceSide = (this.avoidanceSide == 0) ? 1 : -this.avoidanceSide;
                    this.detourWaypoint = null;
                    this.autoPathReachedGoal = false;
                    forceReplan = true;
                }

                // 1. Re-sync onto the planned route (e.g. after a straight-line stretch) without cutting across land
                this.resyncRouteIndex();

                // Advance along the planned route; every reached waypoint of a complete route counts as real progress
                while (this.autoPathIndex < this.autoPathWaypoints.size()) {
                    Vec3 wp = this.autoPathWaypoints.get(this.autoPathIndex);
                    double distSq = this.position().distanceToSqr(wp.x, this.getY(), wp.z);
                    if (distSq <= 25.0) {
                        this.autoPathIndex++;
                        if (this.autoPathReachedGoal) this.noProgressTicks = 0;
                        continue;
                    }
                    break;
                }

                // Live check: if a player or WorldEdit closed the waterway along our upcoming planned route,
                // immediately drop the invalidated route and trigger a fresh full-route check (holding the ship in place)
                // instead of letting local raycast avoidance steer blindly into every side waterway.
                if (this.autoPathIndex < this.autoPathWaypoints.size()
                        && (this.tickCount % 4 == 0 || this.stuckTicks > 0)
                        && this.isUpcomingRouteBlocked(this.level())) {
                    this.autoPathWaypoints.clear();
                    this.autoPathIndex = 0;
                    this.autoPathReachedGoal = false;
                    this.detourWaypoint = null;
                    this.routePlanner = null;
                    this.startRoutePlanner(activeGoal);
                }

                // 2. Full-route water planner (incremental + time-budgeted). It floods bays, coves and closed
                //    river arms virtually and only hands the ship a route that is proven to reach the goal,
                //    so the ship never has to sail into a dead end to discover that it is one.
                if (this.routePlanner != null && !this.routePlanner.goal.equals(activeGoal)) {
                    this.routePlanner = null;
                }
                boolean goalChanged = this.plannedGoal == null || !this.plannedGoal.equals(activeGoal);
                boolean routeExhausted = this.autoPathIndex >= this.autoPathWaypoints.size();
                boolean nearingPartialEnd = !this.autoPathReachedGoal
                        && (this.autoPathWaypoints.size() - this.autoPathIndex <= 4);
                boolean offRoute = !routeExhausted
                        && this.position().distanceToSqr(this.autoPathWaypoints.get(this.autoPathIndex).x, this.getY(),
                                this.autoPathWaypoints.get(this.autoPathIndex).z) > 576.0;
                if (this.routePlanner == null
                        && (goalChanged
                            || forceReplan
                            || (routeExhausted && this.tickCount - this.lastAutoPathTick >= 10)
                            || (nearingPartialEnd && this.tickCount - this.lastAutoPathTick >= 20)
                            || (!this.autoPathReachedGoal && this.tickCount - this.lastAutoPathTick >= 80)
                            || ((this.stuckTicks >= 6 || offRoute) && this.tickCount - this.lastAutoPathTick >= 20))) {
                    this.startRoutePlanner(activeGoal);
                }
                this.tickRoutePlanner();
                if (this.isRemoved()) return;

                boolean usingAutoPath = false;
                boolean holdForRoute = false;
                if (this.autoPathIndex < this.autoPathWaypoints.size()) {
                    Vec3 subWp = this.autoPathWaypoints.get(this.autoPathIndex);
                    double subDx = subWp.x - this.getX();
                    double subDz = subWp.z - this.getZ();
                    chosenAngle = Mth.atan2(subDz, subDx);
                    usingAutoPath = true;
                    this.detourWaypoint = subWp;
                } else if (this.routePlanner != null) {
                    // No route yet: wait in place (keeping the current heading) instead of guessing into a cove
                    holdForRoute = true;
                    chosenAngle = (this.getYRot() + 90.0F) * (Math.PI / 180.0);
                } else {
                    chosenAngle = directAngle;
                }

                // 3. Fallback to Channel-Aware Raycast Steering only if the planner produced no route at all
                if (!usingAutoPath && !holdForRoute) {
                    boolean needNewWaypoint = (this.detourWaypoint == null);
                    if (!needNewWaypoint) {
                        double distToWp = this.position().distanceTo(this.detourWaypoint);
                        if (distToWp < 4.5 || this.detourTicks % 16 == 0 || this.stuckTicks > 2) {
                            needNewWaypoint = true;
                        } else {
                            double wpAngle = Mth.atan2(this.detourWaypoint.z - this.getZ(), this.detourWaypoint.x - this.getX());
                            if (!isLineClearOfLand(this.level(), wpAngle, Math.min(distToWp, 14.0))) {
                                needNewWaypoint = true;
                            }
                        }
                    }

                    if (needNewWaypoint) {
                        double detourAngle = findBestDetourAngle(directAngle, 1.2);
                        double clearRun = getCorridorDistanceToLand(this.level(), detourAngle, 28.0, 0.9);
                        double wpDist = Math.min(18.0, Math.max(6.5, clearRun * 0.72));
                        double rawWpX = this.getX() + Math.cos(detourAngle) * wpDist;
                        double rawWpZ = this.getZ() + Math.sin(detourAngle) * wpDist;
                        this.detourWaypoint = centerPointInChannel(this.level(), rawWpX, rawWpZ, Math.cos(detourAngle), Math.sin(detourAngle));
                    }

                    chosenAngle = Mth.atan2(this.detourWaypoint.z - this.getZ(), this.detourWaypoint.x - this.getX());

                    if (!this.reachedPortApproach && finalDist > 12.0) {
                        chosenAngle = applyChannelCenteringBias(this.level(), chosenAngle);
                    }
                }

                double distToLand = getDistanceToLand(this.level(), chosenAngle, 16.0);
                if (distToLand < 5.0) {
                    currentSpeed = Math.max(0.55, this.cruiseSpeed * 0.68);
                } else {
                    currentSpeed = this.cruiseSpeed * (this.hasTailwind() ? 1.25 : 1.0);
                }
                if (holdForRoute) {
                    currentSpeed = 0.0;
                }
            }

            BlockPos currentPos = this.blockPosition();
            boolean inWaterCurrent = this.level().getFluidState(currentPos).is(FluidTags.WATER)
                    || this.level().getFluidState(currentPos.below()).is(FluidTags.WATER);

            if (this.horizontalCollision || !inWaterCurrent) {
                this.stuckTicks++;
            } else {
                this.stuckTicks = Math.max(0, this.stuckTicks - 1);
            }

            float desiredYaw = (float)(chosenAngle * (180.0 / Math.PI)) - 90.0F;
            float currentYaw = this.getYRot();
            float yawDiff = Mth.wrapDegrees(desiredYaw - currentYaw);
            float absYawDiff = Math.abs(yawDiff);

            float maxTurnRate;
            if (this.stuckTicks > 0 || absYawDiff > 55.0F) {
                maxTurnRate = 28.0F;
            } else if (this.detourWaypoint != null || !this.autoPathWaypoints.isEmpty()) {
                maxTurnRate = 18.0F;
            } else {
                maxTurnRate = 14.0F;
            }

            if (absYawDiff > 40.0F) {
                double turnFactor = Math.max(0.55, Math.cos(Math.toRadians(Math.min(60.0F, absYawDiff))));
                currentSpeed *= turnFactor;
            }

            float newYaw = currentYaw + Mth.clamp(yawDiff, -maxTurnRate, maxTurnRate);
            this.setYRot(newYaw);
            this.setYHeadRot(newYaw);

            double moveHeadingAngle = (newYaw + 90.0F) * (Math.PI / 180.0);
            double normX = Math.cos(moveHeadingAngle);
            double normZ = Math.sin(moveHeadingAngle);
            double velX = normX * currentSpeed;
            double velZ = normZ * currentSpeed;

            double bowX = this.getX() + normX * 1.35;
            double bowZ = this.getZ() + normZ * 1.35;
            boolean awaitingRoute = this.isAwaitingRoute();
            boolean bowBlocked = !awaitingRoute && !isNavigableWater(this.level(), bowX, bowZ);

            if (!awaitingRoute && (bowBlocked || !inWaterCurrent || this.horizontalCollision)) {
                double escapeAngle = findBestDetourAngle(chosenAngle, 0.85);
                float escapeYaw = (float)(escapeAngle * (180.0 / Math.PI)) - 90.0F;
                float escDiff = Mth.wrapDegrees(escapeYaw - newYaw);
                newYaw = newYaw + Mth.clamp(escDiff, -32.0F, 32.0F);
                this.setYRot(newYaw);
                this.setYHeadRot(newYaw);
                moveHeadingAngle = (newYaw + 90.0F) * (Math.PI / 180.0);
                normX = Math.cos(moveHeadingAngle);
                normZ = Math.sin(moveHeadingAngle);
                velX = normX * 0.45;
                velZ = normZ * 0.45;
                this.hasImpulse = true;
            }

            double currentY = this.getY();
            double desiredY = 62.8;

            if (inWaterCurrent) {
                int waterY = currentPos.getY();
                if (this.level().getFluidState(currentPos.above()).is(FluidTags.WATER)) {
                    waterY++;
                }
                desiredY = Math.max(62.8, waterY - 0.2);
            }

            double velY = 0.0;
            if (currentY < desiredY) {
                velY = Math.min(0.5, (desiredY - currentY) * 0.4);
            } else if (currentY > desiredY) {
                velY = Math.max(-0.5, (desiredY - currentY) * 0.4);
            }

            this.setDeltaMovement(velX, velY, velZ);
            this.move(MoverType.SELF, this.getDeltaMovement());

            if (this.level() instanceof ServerLevel serverLevel) {
                int splashCount = this.level().isThundering() ? 14 : 5;
                int bubbleCount = this.level().isThundering() ? 8 : 3;
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                        this.getX() - normX * 0.8, this.getY() + 0.1, this.getZ() - normZ * 0.8,
                        splashCount, 0.4, 0.2, 0.4, 0.08);
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        this.getX() - normX * 0.5, this.getY(), this.getZ() - normZ * 0.5,
                        bubbleCount, 0.3, 0.1, 0.3, 0.04);
            }

            if (passenger instanceof Player player && this.tickCount % 4 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.voyage_hud", this.harborName, (int) finalDist),
                        true
                );
            }
        }

        if (!this.level().isClientSide && !this.cargoDelivered && !this.voyageShip && this.level() instanceof ServerLevel serverLevel) {
            if (this.stuckTicks > 0 || (this.detourTicks > 80 && !this.isAwaitingRoute() && this.getDeltaMovement().horizontalDistanceSqr() < 0.005)) {
                this.blockedTicks++;
            } else if (this.getDeltaMovement().horizontalDistanceSqr() > 0.04 && this.noProgressTicks < 60) {
                this.blockedTicks = Math.max(0, this.blockedTicks - 1);
            }
            if (this.blockedTicks >= MAX_BLOCKED_TICKS || this.noProgressTicks >= MAX_NO_PROGRESS_TICKS) {
                this.abortAndReturnCargo(serverLevel);
            }
        }
    }

    // --- Arrival Handling ---

    protected void arriveAtDestination(@Nullable Entity passenger) {
        if (this.voyageShip) {
            if (this.arrived) return;
            this.arrived = true;

            if (passenger != null) {
                passenger.stopRiding();
                BlockPos landing = this.targetPos.above();
                if (passenger instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.currents_of_trade.voyage_arrived", this.harborName),
                            true
                    );
                }
            }

            this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (this.targetPos != null) {
                this.level().playSound(null, this.targetPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (this.level().getBlockEntity(this.targetPos) instanceof AnchorPointBlockEntity targetAnchor) {
                    targetAnchor.triggerTravelCooldown();
                }
            }

            if (this.shouldDiscardOnArrival()) {
                this.discard();
            }
            return;
        }

        if (this.isTradeMission && this.missionPhase == TradeMissionPhase.OUTBOUND) {
            if (this.targetPos != null) {
                this.level().playSound(null, this.targetPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (this.level().getBlockEntity(this.targetPos) instanceof AnchorPointBlockEntity targetAnchor) {
                    targetAnchor.triggerTravelCooldown();
                }
            }

            this.clearContent();
            for (int i = 0; i < this.tradeRewards.size() && i < this.getContainerSize(); i++) {
                this.itemStacks.set(i, this.tradeRewards.get(i).copy());
            }

            if (this.level().getServer() != null && this.senderUUID != null) {
                Player sender = this.level().getServer().getPlayerList().getPlayer(this.senderUUID);
                if (sender != null) {
                    sender.displayClientMessage(
                            Component.translatable("message.currents_of_trade.trade_reached_target", this.getHarborName()),
                            false
                    );
                }
            }

            this.missionPhase = TradeMissionPhase.RETURNING;
            this.arrived = false;
            if (!this.routeWaypoints.isEmpty()) {
                java.util.Collections.reverse(this.routeWaypoints);
            }
            this.currentWaypointIndex = 0;
            this.setVoyageTarget(this.homeHarborPos, this.homeHarborName);
            this.setCustomName(Component.literal("Trade Ship"));
            this.setCustomNameVisible(true);
            return;
        }

        if (this.cargoDelivered) return;
        this.cargoDelivered = true;
        this.setDeltaMovement(0.0, 0.0, 0.0);
        this.releaseAllForcedChunks();

        boolean anyDeposited = false;
        boolean allDeposited = false;

        if (this.targetPos != null) {
            this.level().playSound(null, this.targetPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (this.level().getBlockEntity(this.targetPos) instanceof AnchorPointBlockEntity targetAnchor) {
                targetAnchor.triggerTravelCooldown();
            }

            anyDeposited = this.tryUnloadCargoToHarborStorage(this.targetPos);
            allDeposited = this.isEmpty();

            if (anyDeposited) {
                this.level().playSound(null, this.targetPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.9F, 1.0F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.2, this.targetPos.getZ() + 0.5,
                            12, 0.4, 0.3, 0.4, 0.05);
                }
            }
        }

        Component senderMsg;
        Component receiverMsg;
        if (this.isTradeMission && this.missionPhase == TradeMissionPhase.RETURNING) {
            if (allDeposited) {
                senderMsg   = Component.translatable("message.currents_of_trade.trade_arrived_home_depot",     this.homeHarborName);
                receiverMsg = Component.translatable("message.currents_of_trade.trade_docked_at_harbor_depot", this.homeHarborName);
            } else {
                senderMsg   = Component.translatable("message.currents_of_trade.trade_arrived_home",     this.homeHarborName);
                receiverMsg = Component.translatable("message.currents_of_trade.trade_docked_at_harbor", this.homeHarborName);
            }
        } else {
            if (allDeposited) {
                senderMsg   = Component.translatable("message.currents_of_trade.cargo_arrived_sender_depot",   this.getHarborName());
                receiverMsg = Component.translatable("message.currents_of_trade.cargo_arrived_depot",          this.getHarborName());
            } else {
                senderMsg   = Component.translatable("message.currents_of_trade.cargo_arrived_sender",   this.getHarborName());
                receiverMsg = Component.translatable("message.currents_of_trade.cargo_arrived",          this.getHarborName());
            }
        }

        if (this.level().getServer() != null) {
            if (this.senderUUID != null) {
                Player sender = this.level().getServer().getPlayerList().getPlayer(this.senderUUID);
                if (sender != null) sender.displayClientMessage(senderMsg, false);
            }
            double rangeSq = 128.0 * 128.0;
            for (Player nearbyPlayer : this.level().players()) {
                if (nearbyPlayer.getUUID().equals(this.senderUUID)) continue;
                if (nearbyPlayer.distanceToSqr(this) <= rangeSq) {
                    nearbyPlayer.displayClientMessage(receiverMsg, false);
                }
            }
        }
    }

    /**
     * Attempts to automatically unload cargo into adjacent depot storage blocks (chests, barrels,
     * hoppers, trapped chests, modded containers) located beside, above, or below the Anchor Point.
     * Explicitly ignores Ender Chests as requested.
     *
     * @param anchorPos The position of the destination/origin Anchor Point block entity.
     * @return true if at least one item was transferred into harbor storage.
     */
    protected boolean tryUnloadCargoToHarborStorage(BlockPos anchorPos) {
        return unloadContainerToHarborStorage(this.level(), anchorPos, this) > 0;
    }

    /**
     * Transfers items from a ship container into adjacent depot storage blocks (chests, barrels,
     * hoppers, modded containers) around the specified Anchor Point, ignoring Ender Chests.
     *
     * @return Total number of items transferred into harbor storage.
     */
    public static int unloadContainerToHarborStorage(Level level, BlockPos anchorPos, Container shipContainer) {
        if (level == null || level.isClientSide || anchorPos == null || shipContainer == null) return 0;

        int totalDeposited = 0;

        List<IItemHandler> targetHandlers = new ArrayList<>();
        List<Container> fallbackContainers = new ArrayList<>();
        Set<BlockPos> checkedPositions = new HashSet<>();

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = anchorPos.relative(dir);
            if (!checkedPositions.add(neighborPos)) continue;

            BlockState state = level.getBlockState(neighborPos);
            // Explicitly ignore Ender Chest
            if (state.is(Blocks.ENDER_CHEST)) {
                continue;
            }

            // A. Forge IItemHandler capability (handles double chests, barrels, hoppers, modded containers)
            IItemHandler handler = null;
            BlockEntity capBe = level.getBlockEntity(neighborPos);
            if (capBe != null) {
                handler = capBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).resolve().orElse(null);
                if (handler == null) {
                    handler = capBe.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
                }
            }

            if (handler != null) {
                targetHandlers.add(handler);
            } else {
                // B. Fallback: Vanilla Container block entity or double chest
                Container container = null;
                if (state.getBlock() instanceof ChestBlock chestBlock) {
                    container = ChestBlock.getContainer(chestBlock, state, level, neighborPos, true);
                } else {
                    BlockEntity be = level.getBlockEntity(neighborPos);
                    if (be instanceof Container c) {
                        container = c;
                    }
                }
                if (container != null) {
                    fallbackContainers.add(container);
                }
            }
        }

        if (targetHandlers.isEmpty() && fallbackContainers.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < shipContainer.getContainerSize(); i++) {
            ItemStack shipStack = shipContainer.getItem(i);
            if (shipStack.isEmpty()) continue;

            int originalCount = shipStack.getCount();

            for (IItemHandler handler : targetHandlers) {
                if (shipStack.isEmpty()) break;
                shipStack = ItemHandlerHelper.insertItemStacked(handler, shipStack, false);
            }

            if (!shipStack.isEmpty()) {
                for (Container container : fallbackContainers) {
                    if (shipStack.isEmpty()) break;
                    shipStack = insertIntoVanillaContainer(container, shipStack);
                }
            }

            if (shipStack.getCount() != originalCount) {
                totalDeposited += (originalCount - shipStack.getCount());
                shipContainer.setItem(i, shipStack);
                shipContainer.setChanged();
            }
        }

        return totalDeposited;
    }

    private static ItemStack insertIntoVanillaContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) return stack;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (stack.isEmpty()) break;
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack) && container.canPlaceItem(slot, stack)) {
                int max = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
                int fit = Math.min(stack.getCount(), max - existing.getCount());
                if (fit > 0) {
                    existing.grow(fit);
                    stack.shrink(fit);
                    container.setChanged();
                }
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (stack.isEmpty()) break;
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() && container.canPlaceItem(slot, stack)) {
                int max = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                int put = Math.min(stack.getCount(), max);
                ItemStack newStack = stack.split(put);
                container.setItem(slot, newStack);
                container.setChanged();
            }
        }

        return stack;
    }

    // --- Pathfinding & Navigable Water Checks ---

    private static final double[][] RING_DIRS = {
            { 1.0, 0.0 }, { -1.0, 0.0 }, { 0.0, 1.0 }, { 0.0, -1.0 },
            { 0.7071, 0.7071 }, { 0.7071, -0.7071 }, { -0.7071, 0.7071 }, { -0.7071, -0.7071 }
    };

    private static boolean ensureChunkLoadedForNav(Level level, int cx, int cz) {
        if (level.hasChunk(cx, cz)) return true;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunk(cx, cz);
            return level.hasChunk(cx, cz);
        }
        return false;
    }

    /**
     * Finds the best surface water block near a harbor Anchor Point by scoring each candidate
     * water block for multi-ring radial water clearance (pushing departure spawns and port approach
     * points out into the middle of the river/channel rather than hugging the 1-block shoreline).
     */
    @Nullable
    public static BlockPos findBestHarborWaterSpawn(Level level, BlockPos harborPos) {
        if (level == null || harborPos == null) return null;
        ensureChunkLoadedForNav(level, harborPos.getX() >> 4, harborPos.getZ() >> 4);
        BlockPos bestPos = null;
        double bestScore = -1e9;
        final double[] clearRadii = { 1.6, 3.0, 4.5, 6.0 };
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap nodeReachCache = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        nodeReachCache.defaultReturnValue(-1);

        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > 10.5) continue;

                BlockPos topWater = null;
                for (int dy = 2; dy >= -7; dy--) {
                    BlockPos check = harborPos.offset(dx, dy, dz);
                    if (!ensureChunkLoadedForNav(level, check.getX() >> 4, check.getZ() >> 4)) continue;
                    if (level.getFluidState(check).is(FluidTags.WATER)
                            && !level.getFluidState(check.above()).is(FluidTags.WATER)) {
                        BlockState aboveState = level.getBlockState(check.above());
                        if (!aboveState.blocksMotion() || aboveState.is(Blocks.LILY_PAD)) {
                            topWater = check.immutable();
                            break;
                        }
                    }
                }
                if (topWater == null) continue;

                int waterY = topWater.getY();
                double clearance = 0.0;
                for (double r : clearRadii) {
                    int ringWater = 0;
                    for (double[] dir : RING_DIRS) {
                        int sx = Mth.floor(topWater.getX() + 0.5 + dir[0] * r);
                        int sz = Mth.floor(topWater.getZ() + 0.5 + dir[1] * r);
                        if (ensureChunkLoadedForNav(level, sx >> 4, sz >> 4)) {
                            BlockPos sp = new BlockPos(sx, waterY, sz);
                            if (level.getFluidState(sp).is(FluidTags.WATER)
                                    || level.getFluidState(sp.below()).is(FluidTags.WATER)) {
                                ringWater++;
                            }
                        }
                    }
                    clearance += ringWater * (r <= 3.0 ? 2.6 : 1.5);
                }

                // Prefer open waterways over enclosed ponds/canals when a harbor sits near a 1-block dam
                int baseWaterY = (waterY > 64) ? waterY : 62;
                long ownerNode = directOwnerNodeOfBlock(level, topWater.getX(), topWater.getZ(), baseWaterY, null);
                int reachCount = 0;
                if (ownerNode != NO_NODE) {
                    reachCount = nodeReachCache.get(ownerNode);
                    if (reachCount < 0) {
                        reachCount = countReachableCoarseNodes(level, ownerNode, baseWaterY, 16);
                        nodeReachCache.put(ownerNode, reachCount);
                    }
                }

                // Prefer positions 4-7 blocks out in the channel (away from the immediate dock wall)
                double distPenalty = (dist < 3.8) ? (3.8 - dist) * 6.5 : Math.max(0.0, (dist - 4.5) * 0.85);
                double score = clearance + (reachCount * 35.0) - distPenalty + (waterY * 0.05);
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = topWater;
                }
            }
        }
        return bestPos;
    }

    private static int countReachableCoarseNodes(Level level, long startNode, int baseWaterY, int maxCount) {
        if (startNode == NO_NODE) return 0;
        java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet vis = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        vis.add(startNode);
        q.add(startNode);
        while (!q.isEmpty() && vis.size() < maxCount) {
            long node = q.poll();
            int cx = nodeCx(node);
            int cz = nodeCz(node);
            int label = nodeLabel(node);
            ChunkNav self = getChunkNav(level, cx, cz, baseWaterY);
            for (int d = 0; d < 4 && vis.size() < maxCount; d++) {
                int ncx = cx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int ncz = cz + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                ChunkNav other = getChunkNav(level, ncx, ncz, baseWaterY);
                for (int t = 0; t < 16 && vis.size() < maxCount; t++) {
                    int a;
                    int b;
                    switch (d) {
                        case 0 -> { a = 15 + (t << 4); b = t << 4; }
                        case 1 -> { a = t << 4; b = 15 + (t << 4); }
                        case 2 -> { a = t + 240; b = t; }
                        default -> { a = t; b = t + 240; }
                    }
                    if (self.labels[a] != label) continue;
                    int ol = other.labels[b];
                    if (ol == 0) continue;
                    long m = packNode(ncx, ncz, ol);
                    if (vis.add(m)) {
                        q.add(m);
                    }
                }
            }
        }
        return vis.size();
    }

    /**
     * Returns the nearest block (within {@code radius}) whose full 2x2 hull footprint is open surface water,
     * or null if there is none. Used to snap Nautical Chart waypoints onto navigable water.
     */
    @Nullable
    public static BlockPos findNearestNavigableWater(Level level, BlockPos around, int radius) {
        if (level == null || around == null) return null;
        int baseY = (around.getY() > 66) ? around.getY() - 1 : 62;
        int bx = around.getX();
        int bz = around.getZ();
        for (int r = 0; r <= radius; r++) {
            BlockPos best = null;
            int bestD = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = bx + dx;
                    int z = bz + dz;
                    if (isStaticPureWater(level, x + 0.5, z + 0.5, baseY)
                            && isStaticPureWater(level, x + 1.5, z + 0.5, baseY)
                            && isStaticPureWater(level, x + 0.5, z + 1.5, baseY)
                            && isStaticPureWater(level, x + 1.5, z + 1.5, baseY)) {
                        int d = dx * dx + dz * dz;
                        if (d < bestD) {
                            bestD = d;
                            best = new BlockPos(x, around.getY(), z);
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Verifies before dispatching a ship that a navigable water connection exists between the
     * departure harbor water, any logged route waypoints, and the destination harbor.
     * Returns false if either harbor sits in an enclosed lake/river/pond with no water route to the other.
     */
    public static boolean verifyPortWaterConnection(Level level, BlockPos startWater, @Nullable List<BlockPos> waypoints, BlockPos targetPos) {
        if (level == null || startWater == null || targetPos == null) return false;

        // Re-scan loaded chunks so player-placed dams/canals are immediately respected
        // (unloaded chunks cannot have been edited, so their cache stays valid and is NOT force-reloaded)
        refreshLoadedChunkNav(level);

        BlockPos destWater = findBestHarborWaterSpawn(level, targetPos);
        if (destWater == null) destWater = targetPos;

        List<BlockPos> legs = new ArrayList<>();
        legs.add(startWater);
        if (waypoints != null) {
            for (BlockPos wp : waypoints) {
                if (wp == null) continue;
                BlockPos snapped = findNearestNavigableWater(level, wp, 6);
                if (snapped != null) legs.add(snapped);
            }
        }
        legs.add(destWater);

        for (int i = 0; i < legs.size() - 1; i++) {
            if (!hasNavigableWaterConnection(level, legs.get(i), legs.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNavigableWaterConnection(Level level, BlockPos fromPos, BlockPos toPos) {
        if (fromPos.distSqr(toPos) <= 36.0) return true;
        int baseWaterY = (fromPos.getY() > 64) ? fromPos.getY() : 62;
        long startNode = findConnectedCoarseNode(level, fromPos.getX() + 0.5, fromPos.getZ() + 0.5, baseWaterY, 10);
        long goalNode = findConnectedCoarseNode(level, toPos.getX() + 0.5, toPos.getZ() + 0.5, baseWaterY, 10);
        if (startNode == NO_NODE || goalNode == NO_NODE) return false;
        if (startNode == goalNode) return true;

        ChunkNav startNav = getChunkNav(level, nodeCx(startNode), nodeCz(startNode), baseWaterY);
        ChunkNav goalNav = getChunkNav(level, nodeCx(goalNode), nodeCz(goalNode), baseWaterY);
        double startX = startNav.centroidX[nodeLabel(startNode)];
        double startZ = startNav.centroidZ[nodeLabel(startNode)];
        double goalX = goalNav.centroidX[nodeLabel(goalNode)];
        double goalZ = goalNav.centroidZ[nodeLabel(goalNode)];

        java.util.PriorityQueue<CoarseEntry> pqF = new java.util.PriorityQueue<>();
        java.util.PriorityQueue<CoarseEntry> pqB = new java.util.PriorityQueue<>();
        it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap gF = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap gB = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet closedF = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet closedB = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        gF.defaultReturnValue(Double.MAX_VALUE);
        gB.defaultReturnValue(Double.MAX_VALUE);

        gF.put(startNode, 0.0);
        gB.put(goalNode, 0.0);
        pqF.add(new CoarseEntry(startNode, 0.0, (Math.abs(goalX - startX) + Math.abs(goalZ - startZ)) * 1.05));
        pqB.add(new CoarseEntry(goalNode, 0.0, (Math.abs(startX - goalX) + Math.abs(startZ - goalZ)) * 1.05));

        final int maxCoarseNodes = 16000;
        while (!pqF.isEmpty() && !pqB.isEmpty() && (closedF.size() + closedB.size()) < maxCoarseNodes) {
            boolean forward = pqF.size() <= pqB.size();
            CoarseEntry e = forward ? pqF.poll() : pqB.poll();
            it.unimi.dsi.fastutil.longs.LongOpenHashSet selfClosed = forward ? closedF : closedB;
            it.unimi.dsi.fastutil.longs.LongOpenHashSet otherClosed = forward ? closedB : closedF;
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap selfG = forward ? gF : gB;
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap otherG = forward ? gB : gF;
            java.util.PriorityQueue<CoarseEntry> selfPq = forward ? pqF : pqB;
            double targetX = forward ? goalX : startX;
            double targetZ = forward ? goalZ : startZ;

            long node = e.node();
            if (!selfClosed.add(node)) continue;
            if (otherClosed.contains(node)) return true;

            int cx = nodeCx(node);
            int cz = nodeCz(node);
            int label = nodeLabel(node);
            ChunkNav self = getChunkNav(level, cx, cz, baseWaterY);
            double ex = self.centroidX[label];
            double ez = self.centroidZ[label];

            for (int d = 0; d < 4; d++) {
                int ncx = cx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int ncz = cz + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                ChunkNav other = getChunkNav(level, ncx, ncz, baseWaterY);
                long seenLow = 0L;
                long seenHigh = 0L;
                for (int t = 0; t < 16; t++) {
                    int a;
                    int b;
                    switch (d) {
                        case 0 -> { a = 15 + (t << 4); b = t << 4; }
                        case 1 -> { a = t << 4; b = 15 + (t << 4); }
                        case 2 -> { a = t + 240; b = t; }
                        default -> { a = t; b = t + 240; }
                    }
                    if (self.labels[a] != label) continue;
                    int ol = other.labels[b];
                    if (ol == 0) continue;
                    if (ol < 64) {
                        if ((seenLow & (1L << ol)) != 0) continue;
                        seenLow |= 1L << ol;
                    } else {
                        if ((seenHigh & (1L << (ol - 64))) != 0) continue;
                        seenHigh |= 1L << (ol - 64);
                    }
                    long m = packNode(ncx, ncz, ol);
                    if (otherG.get(m) < Double.MAX_VALUE) return true;
                    if (selfClosed.contains(m)) continue;
                    double mx = other.centroidX[ol];
                    double mz = other.centroidZ[ol];
                    double ng = e.g() + Math.hypot(mx - ex, mz - ez);
                    if (ng < selfG.get(m)) {
                        selfG.put(m, ng);
                        double h = (Math.abs(targetX - mx) + Math.abs(targetZ - mz)) * 1.05;
                        selfPq.add(new CoarseEntry(m, ng, ng + h));
                    }
                }
            }
        }
        return !pqF.isEmpty() && !pqB.isEmpty();
    }

    @Nullable
    private static net.minecraft.world.level.chunk.LevelChunk getLoadedChunkForNav(Level level, int cx, int cz) {
        if (level.hasChunk(cx, cz)) {
            return level.getChunk(cx, cz);
        }
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunk(cx, cz);
        }
        return null;
    }

    private static BlockState getChunkBlockStateFast(net.minecraft.world.level.chunk.LevelChunk chunk,
                                                     net.minecraft.world.level.chunk.LevelChunkSection[] sections,
                                                     int lx, int y, int lz) {
        int idx = chunk.getSectionIndex(y);
        if (idx < 0 || idx >= sections.length) return Blocks.AIR.defaultBlockState();
        net.minecraft.world.level.chunk.LevelChunkSection section = sections[idx];
        if (section == null || section.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return section.getBlockState(lx, y & 15, lz);
    }

    private static boolean isColumnPureWaterFast(@Nullable net.minecraft.world.level.chunk.LevelChunk chunk,
                                                 net.minecraft.world.level.chunk.LevelChunkSection[] sections,
                                                 int lx, int lz, int baseWaterY) {
        if (chunk == null) return false;
        BlockState s0 = getChunkBlockStateFast(chunk, sections, lx, baseWaterY, lz);
        BlockState s1 = getChunkBlockStateFast(chunk, sections, lx, baseWaterY + 1, lz);
        boolean hasWater = s0.getFluidState().is(FluidTags.WATER) || s1.getFluidState().is(FluidTags.WATER);
        if (!hasWater) return false;
        for (int y = baseWaterY + 1; y <= baseWaterY + 3; y++) {
            BlockState state = (y == baseWaterY + 1) ? s1 : getChunkBlockStateFast(chunk, sections, lx, y, lz);
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                if (state.blocksMotion() && !state.is(Blocks.LILY_PAD)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isStaticPureWater(Level level, double x, double z, int baseWaterY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        ChunkNav nav = getChunkNav(level, bx >> 4, bz >> 4, baseWaterY);
        return (nav.waterMask[bz & 15] & (1 << (bx & 15))) != 0;
    }

    private record PathNode(int gx, int gz, double g, double f, @Nullable PathNode parent) implements Comparable<PathNode> {
        @Override
        public int compareTo(PathNode other) {
            return Double.compare(this.f, other.f);
        }
    }

    // --- Full-Route Water Planner ---

    private boolean isAwaitingRoute() {
        return this.routePlanner != null && this.autoPathIndex >= this.autoPathWaypoints.size();
    }

    private void startRoutePlanner(BlockPos goal) {
        this.plannedGoal = goal;
        this.lastAutoPathTick = this.tickCount;
        // Always refresh loaded chunks before planning so WorldEdit / batch block edits are detected immediately
        // (unloaded chunks cannot have been edited by players, so their cached nav stays valid and is never re-loaded from disk)
        refreshLoadedChunkNav(this.level());
        int baseY = (this.getY() > 64.0) ? (int) Math.floor(this.getY()) : 62;
        double goalRadiusSq = this.reachedPortApproach ? 12.25 : 6.25;
        this.routePlanner = new WaterRoutePlanner(this.level(), this.getX(), this.getZ(), goal, baseY, goalRadiusSq,
                PLANNER_MAX_EXPANSIONS, new ArrayList<>(this.recentBreadcrumbs));
    }

    private void tickRoutePlanner() {
        if (this.routePlanner == null) return;
        long gameTime = this.level().getGameTime();
        if (gameTime != plannerBudgetGameTime) {
            plannerBudgetGameTime = gameTime;
            plannerBudgetUsedNs = 0L;
        }
        long remaining = PLANNER_TICK_BUDGET_NS - plannerBudgetUsedNs;
        long budget = Math.max(500_000L, Math.min(PLANNER_SHIP_BUDGET_NS, remaining));
        long t0 = System.nanoTime();
        boolean done = this.routePlanner.step(t0 + budget);
        plannerBudgetUsedNs += System.nanoTime() - t0;
        if (done) {
            WaterRoutePlanner finished = this.routePlanner;
            this.routePlanner = null;
            this.lastAutoPathTick = this.tickCount;
            this.applyPlannedRoute(finished);
        }
    }

    private void applyPlannedRoute(WaterRoutePlanner planner) {
        this.autoPathWaypoints.clear();
        this.autoPathIndex = 0;
        this.autoPathReachedGoal = planner.reachedGoal;
        if (planner.exhausted && !planner.reachedGoal) {
            boolean goalIsChartWaypoint = this.currentWaypointIndex < this.routeWaypoints.size()
                    && planner.goal.equals(this.routeWaypoints.get(this.currentWaypointIndex));
            if (goalIsChartWaypoint) {
                // An unreachable chart waypoint (plotted in a lake, on land, ...) is skipped instead of
                // blocking the whole voyage
                this.currentWaypointIndex++;
                this.plannedGoal = null;
                this.bestDistToGoal = Double.MAX_VALUE;
                this.noProgressTicks = 0;
                return;
            }
            if (!this.voyageShip) {
                // Entire connected water body flooded without reaching the goal (enclosed pond/river or canal closed mid-voyage)
                if (this.level() instanceof ServerLevel serverLevel) {
                    this.abortAndReturnCargo(serverLevel);
                }
                return;
            } else {
                // Travel ships: immediately cancel the voyage and stop instead of wandering every waterway
                this.cancelVoyage();
                return;
            }
        }
        List<double[]> cells = planner.extractPath();
        if (cells.isEmpty()) return;
        List<Vec3> rawPath = new ArrayList<>(cells.size());
        for (double[] c : cells) {
            rawPath.add(new Vec3(c[0], this.getY(), c[1]));
        }
        this.buildRouteWaypoints(this.level(), rawPath);
    }

    /** Immediately stops and cancels the voyage when no navigable water route exists to the destination. */
    private void cancelVoyage() {
        Entity passenger = this.getFirstPassenger();
        String blockedName = this.harborName != null ? this.harborName : "?";
        this.routePlanner = null;
        this.plannedGoal = null;
        this.autoPathWaypoints.clear();
        this.autoPathIndex = 0;
        this.setDeltaMovement(Vec3.ZERO);

        if (passenger != null) {
            passenger.stopRiding();
            if (passenger instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.currents_of_trade.voyage_route_blocked_cancel",
                        blockedName), false);
                serverPlayer.displayClientMessage(Component.translatable("message.currents_of_trade.voyage_route_blocked_cancel",
                        blockedName), true);
            }
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 0.7F);
        this.discard();
    }

    /**
     * Checks live block states along the next 3 planned waypoints so that closing a canal with WorldEdit (`//set`)
     * or placing blocks ahead of the ship is detected immediately without waiting for chunk cache expiry or collision.
     */
    private boolean isUpcomingRouteBlocked(Level level) {
        int size = this.autoPathWaypoints.size();
        if (this.autoPathIndex >= size) return false;
        double x0 = this.getX();
        double z0 = this.getZ();
        int checkCount = Math.min(size, this.autoPathIndex + 3);
        for (int i = this.autoPathIndex; i < checkCount; i++) {
            Vec3 wp = this.autoPathWaypoints.get(i);
            if (!this.isLiveLineWater(level, x0, z0, wp.x, wp.z)) {
                return true;
            }
            x0 = wp.x;
            z0 = wp.z;
        }
        return false;
    }

    private boolean isLiveLineWater(Level level, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double dist = Math.hypot(dx, dz);
        int samples = Math.max(2, (int) Math.ceil(dist / 0.85));
        int prevBx = Mth.floor(x0);
        int prevBz = Mth.floor(z0);
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double sx = x0 + dx * t;
            double sz = z0 + dz * t;
            int cx = Mth.floor(sx) >> 4;
            int cz = Mth.floor(sz) >> 4;
            if (!level.hasChunk(cx, cz)) continue;
            if (!this.isNavigableWater(level, sx, sz)) return false;
            int bx = Mth.floor(sx);
            int bz = Mth.floor(sz);
            if (bx != prevBx && bz != prevBz) {
                if (!this.isNavigableWater(level, prevBx + 0.5, bz + 0.5)
                        || !this.isNavigableWater(level, bx + 0.5, prevBz + 0.5)) {
                    return false;
                }
            }
            prevBx = bx;
            prevBz = bz;
        }
        return true;
    }

    /** Skips forward to the nearest upcoming route waypoint that is reachable in a straight line over water. */
    private void resyncRouteIndex() {
        int size = this.autoPathWaypoints.size();
        if (this.autoPathIndex >= size) return;
        Vec3 cur = this.autoPathWaypoints.get(this.autoPathIndex);
        double bestSq = this.position().distanceToSqr(cur.x, this.getY(), cur.z);
        int best = this.autoPathIndex;
        int limit = Math.min(size, this.autoPathIndex + 12);
        for (int j = this.autoPathIndex + 1; j < limit; j++) {
            Vec3 wp = this.autoPathWaypoints.get(j);
            double dSq = this.position().distanceToSqr(wp.x, this.getY(), wp.z);
            if (dSq < bestSq && isSegmentClearLoadedOnly(this.level(), this.getX(), this.getZ(), wp.x, wp.z, 0.9)) {
                bestSq = dSq;
                best = j;
            }
        }
        this.autoPathIndex = best;
    }

    /**
     * Turns the raw 2-block grid route into smooth, river-centred waypoints spaced ~8-11 blocks apart.
     */
    private void buildRouteWaypoints(Level level, List<Vec3> rawPath) {
        // Shift each grid waypoint onto the exact continuous midpoint between the left and right riverbanks
        List<Vec3> centeredPath = new ArrayList<>(rawPath.size());
        for (int i = 0; i < rawPath.size(); i++) {
            Vec3 pt = rawPath.get(i);
            Vec3 prev = (i < 3) ? this.position() : rawPath.get(i - 3);
            Vec3 next = rawPath.get(Math.min(rawPath.size() - 1, i + 3));
            double tx = next.x - prev.x;
            double tz = next.z - prev.z;
            if (this.targetPos != null && pt.distanceToSqr(this.targetPos.getX() + 0.5, this.getY(), this.targetPos.getZ() + 0.5) <= 25.0) {
                centeredPath.add(pt);
            } else {
                Vec3 centered = centerPointInChannel(level, pt.x, pt.z, tx, tz);
                // Never let centering hop the point across a thin land strip into another channel
                centeredPath.add(isLineWaterLoadedOnly(level, pt.x, pt.z, centered.x, centered.z) ? centered : pt);
            }
        }

        // 4 passes of Laplacian centerline smoothing to eliminate grid staircase zig-zags
        for (int pass = 0; pass < 4; pass++) {
            List<Vec3> nextPass = new ArrayList<>(centeredPath.size());
            nextPass.add(centeredPath.get(0));
            for (int i = 1; i < centeredPath.size() - 1; i++) {
                Vec3 prev = centeredPath.get(i - 1);
                Vec3 curr = centeredPath.get(i);
                Vec3 next = centeredPath.get(i + 1);
                double mx = prev.x * 0.25 + curr.x * 0.50 + next.x * 0.25;
                double mz = prev.z * 0.25 + curr.z * 0.50 + next.z * 0.25;
                // Only accept the smoothed point if it does not cut a headland corner
                if (isPureWaterLoadedOnly(level, mx, mz)
                        && isLineWaterLoadedOnly(level, prev.x, prev.z, mx, mz)
                        && isLineWaterLoadedOnly(level, mx, mz, next.x, next.z)) {
                    nextPass.add(new Vec3(mx, this.getY(), mz));
                } else {
                    nextPass.add(curr);
                }
            }
            if (centeredPath.size() > 1) {
                nextPass.add(centeredPath.get(centeredPath.size() - 1));
            }
            centeredPath = nextPass;
        }

        // Subsample / string-pull along the smoothed centerline (1.65m clearance up to 5 steps ahead ~8-11m apart)
        List<Vec3> smoothed = new ArrayList<>();
        Vec3 anchor = this.position();
        int idx = 0;
        while (idx < centeredPath.size()) {
            int furthest = idx;
            for (int j = Math.min(centeredPath.size() - 1, idx + 5); j > idx; j--) {
                Vec3 cand = centeredPath.get(j);
                if (isSegmentClearLoadedOnly(level, anchor.x, anchor.z, cand.x, cand.z, 1.65)) {
                    furthest = j;
                    break;
                }
            }
            Vec3 chosen = centeredPath.get(furthest);
            smoothed.add(chosen);
            anchor = chosen;
            idx = furthest + 1;
        }

        this.autoPathWaypoints.addAll(smoothed);
        this.autoPathIndex = 0;
    }

    /** Point-samples a straight line every 0.35 blocks; true only if every sample is open surface water with no diagonal corner jump. */
    private boolean isLineWaterLoadedOnly(Level level, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        int samples = Math.max(2, (int) Math.ceil(Math.hypot(dx, dz) / 0.35));
        int prevBx = Mth.floor(x0);
        int prevBz = Mth.floor(z0);
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double sx = x0 + dx * t;
            double sz = z0 + dz * t;
            if (!isPureWaterLoadedOnly(level, sx, sz)) return false;
            int bx = Mth.floor(sx);
            int bz = Mth.floor(sz);
            if (bx != prevBx && bz != prevBz) {
                if (!isPureWaterLoadedOnly(level, prevBx + 0.5, bz + 0.5)
                        || !isPureWaterLoadedOnly(level, bx + 0.5, prevBz + 0.5)) {
                    return false;
                }
            }
            prevBx = bx;
            prevBz = bz;
        }
        return true;
    }

    // --- Coarse chunk-level water graph (shared by all ships of a level) ---

    /** Navigable-water components of one 16x16 chunk (2x2 hull clearance, 4-connected) + raw water bitmask. */
    private static final class ChunkNav {
        final int[] waterMask = new int[16];
        final byte[] labels = new byte[256];
        float[] centroidX = new float[1];
        float[] centroidZ = new float[1];
        int baseY;
        long stamp;
    }

    private static final java.util.Map<Level, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav>> CHUNK_NAV_CACHE =
            new java.util.WeakHashMap<>();
    private static final long CHUNK_NAV_TTL_LOADED = 20L;    // 1s TTL for loaded chunks (catches WorldEdit / command fills immediately)
    private static final long CHUNK_NAV_TTL_UNLOADED = 6000L; // 5m TTL for unloaded chunks (cannot be edited while unloaded; avoids disk reloads)
    private static final int CHUNK_NAV_CACHE_MAX = 40000;
    private static final long NO_NODE = Long.MIN_VALUE;

    /**
     * Called on every server-side block change (place/break/bucket/fluid spread). Drops the cached nav of the
     * edited chunk plus its west/north/north-west neighbours, because those read this chunk's border column/row.
     */
    public static void invalidateChunkNavAt(Level level, BlockPos pos) {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav> map = CHUNK_NAV_CACHE.get(level);
        if (map == null || map.isEmpty()) return;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        map.remove(packGrid(cx, cz));
        map.remove(packGrid(cx - 1, cz));
        map.remove(packGrid(cx, cz - 1));
        map.remove(packGrid(cx - 1, cz - 1));
    }

    private static void invalidateChunkNavAround(Level level, BlockPos pos, int chunkRadius) {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav> map = CHUNK_NAV_CACHE.get(level);
        if (map == null || map.isEmpty()) return;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                map.remove(packGrid(cx + dx, cz + dz));
            }
        }
    }

    /** Drops cached nav only for currently loaded chunks (cheap in-memory re-scan, never forces disk loads). */
    private static void refreshLoadedChunkNav(Level level) {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav> map = CHUNK_NAV_CACHE.get(level);
        if (map == null || map.isEmpty()) return;
        it.unimi.dsi.fastutil.longs.LongIterator iter = map.keySet().iterator();
        while (iter.hasNext()) {
            long k = iter.nextLong();
            if (level.hasChunk((int) (k >> 32), (int) k)) iter.remove();
        }
    }

    private static ChunkNav getChunkNav(Level level, int cx, int cz, int baseY) {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav> map =
                CHUNK_NAV_CACHE.computeIfAbsent(level, l -> new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        long key = packGrid(cx, cz);
        long now = level.getGameTime();
        ChunkNav nav = map.get(key);
        long ttl = level.hasChunk(cx, cz) ? CHUNK_NAV_TTL_LOADED : CHUNK_NAV_TTL_UNLOADED;
        if (nav != null && nav.baseY == baseY && now - nav.stamp < ttl) return nav;
        if (map.size() > CHUNK_NAV_CACHE_MAX) map.clear();
        nav = computeChunkNav(level, map, cx, cz, baseY);
        nav.stamp = now;
        map.put(key, nav);
        return nav;
    }

    private static ChunkNav computeChunkNav(Level level,
                                            it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkNav> cacheMap,
                                            int cx, int cz, int baseY) {
        ChunkNav nav = new ChunkNav();
        nav.baseY = baseY;
        int x0 = cx << 4;
        int z0 = cz << 4;

        net.minecraft.world.level.chunk.LevelChunk chunk = getLoadedChunkForNav(level, cx, cz);
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = (chunk != null) ? chunk.getSections() : null;

        boolean[] water = new boolean[17 * 17];
        if (chunk != null && sections != null) {
            for (int lz = 0; lz < 16; lz++) {
                int rowMask = 0;
                for (int lx = 0; lx < 16; lx++) {
                    if (isColumnPureWaterFast(chunk, sections, lx, lz, baseY)) {
                        rowMask |= (1 << lx);
                        water[lx + lz * 17] = true;
                    }
                }
                nav.waterMask[lz] = rowMask;
            }
        }

        // East border column (lx = 16 -> lx = 0 of cx + 1, cz)
        ChunkNav eastNav = cacheMap.get(packGrid(cx + 1, cz));
        if (eastNav != null && eastNav.baseY == baseY) {
            for (int lz = 0; lz < 16; lz++) {
                water[16 + lz * 17] = (eastNav.waterMask[lz] & 1) != 0;
            }
        } else {
            net.minecraft.world.level.chunk.LevelChunk eastChunk = getLoadedChunkForNav(level, cx + 1, cz);
            net.minecraft.world.level.chunk.LevelChunkSection[] eastSections = (eastChunk != null) ? eastChunk.getSections() : null;
            if (eastChunk != null && eastSections != null) {
                for (int lz = 0; lz < 16; lz++) {
                    water[16 + lz * 17] = isColumnPureWaterFast(eastChunk, eastSections, 0, lz, baseY);
                }
            }
        }

        // South border row (lz = 16 -> lz = 0 of cx, cz + 1)
        ChunkNav southNav = cacheMap.get(packGrid(cx, cz + 1));
        if (southNav != null && southNav.baseY == baseY) {
            int mask0 = southNav.waterMask[0];
            for (int lx = 0; lx < 16; lx++) {
                water[lx + 16 * 17] = (mask0 & (1 << lx)) != 0;
            }
        } else {
            net.minecraft.world.level.chunk.LevelChunk southChunk = getLoadedChunkForNav(level, cx, cz + 1);
            net.minecraft.world.level.chunk.LevelChunkSection[] southSections = (southChunk != null) ? southChunk.getSections() : null;
            if (southChunk != null && southSections != null) {
                for (int lx = 0; lx < 16; lx++) {
                    water[lx + 16 * 17] = isColumnPureWaterFast(southChunk, southSections, lx, 0, baseY);
                }
            }
        }

        // South-East corner (lx = 16, lz = 16 -> (0, 0) of cx + 1, cz + 1)
        ChunkNav seNav = cacheMap.get(packGrid(cx + 1, cz + 1));
        if (seNav != null && seNav.baseY == baseY) {
            water[16 + 16 * 17] = (seNav.waterMask[0] & 1) != 0;
        } else {
            net.minecraft.world.level.chunk.LevelChunk seChunk = getLoadedChunkForNav(level, cx + 1, cz + 1);
            net.minecraft.world.level.chunk.LevelChunkSection[] seSections = (seChunk != null) ? seChunk.getSections() : null;
            if (seChunk != null && seSections != null) {
                water[16 + 16 * 17] = isColumnPureWaterFast(seChunk, seSections, 0, 0, baseY);
            }
        }

        // A block is navigable for the hull only if the full 2x2 footprint is open water
        boolean[] navOk = new boolean[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lx + lz * 17;
                navOk[lx + (lz << 4)] = water[i] && water[i + 1] && water[i + 17] && water[i + 18];
            }
        }
        int[] stack = new int[256];
        double[] sumX = new double[128];
        double[] sumZ = new double[128];
        int[] count = new int[128];
        int components = 0;
        for (int i = 0; i < 256; i++) {
            if (!navOk[i] || nav.labels[i] != 0) continue;
            if (components < 127) components++;
            byte label = (byte) components;
            int sp = 0;
            stack[sp++] = i;
            nav.labels[i] = label;
            while (sp > 0) {
                int j = stack[--sp];
                int jx = j & 15;
                int jz = j >> 4;
                sumX[components] += jx;
                sumZ[components] += jz;
                count[components]++;
                if (jx < 15 && navOk[j + 1] && nav.labels[j + 1] == 0) { nav.labels[j + 1] = label; stack[sp++] = j + 1; }
                if (jx > 0 && navOk[j - 1] && nav.labels[j - 1] == 0) { nav.labels[j - 1] = label; stack[sp++] = j - 1; }
                if (jz < 15 && navOk[j + 16] && nav.labels[j + 16] == 0) { nav.labels[j + 16] = label; stack[sp++] = j + 16; }
                if (jz > 0 && navOk[j - 16] && nav.labels[j - 16] == 0) { nav.labels[j - 16] = label; stack[sp++] = j - 16; }
            }
        }
        nav.centroidX = new float[components + 1];
        nav.centroidZ = new float[components + 1];
        for (int c = 1; c <= components; c++) {
            nav.centroidX[c] = (float) (x0 + sumX[c] / count[c] + 0.5);
            nav.centroidZ[c] = (float) (z0 + sumZ[c] / count[c] + 0.5);
        }
        return nav;
    }

    private static long packNode(int cx, int cz, int label) {
        return (((long) (cx & 0x3FFFFF)) << 30) | (((long) (cz & 0x3FFFFF)) << 8) | (label & 0xFF);
    }

    private static int nodeCx(long node) {
        return ((int) ((node >>> 30) & 0x3FFFFF) << 10) >> 10;
    }

    private static int nodeCz(long node) {
        return ((int) ((node >>> 8) & 0x3FFFFF) << 10) >> 10;
    }

    private static int nodeLabel(long node) {
        return (int) (node & 0xFF);
    }

    private static long nodeAtBlock(Level level, int bx, int bz, int baseY) {
        ChunkNav nav = getChunkNav(level, bx >> 4, bz >> 4, baseY);
        int label = nav.labels[(bx & 15) + ((bz & 15) << 4)];
        return label == 0 ? NO_NODE : packNode(bx >> 4, bz >> 4, label);
    }

    /**
     * Returns the coarse component node of one of the four 2x2 navigable water squares that physically contain
     * block {@code (bx, bz)}. Because {@code (bx, bz)} itself is one of the 4 blocks in that 2x2 square, this can
     * NEVER jump across a 1-block land strip or dam.
     */
    private static long directOwnerNodeOfBlock(Level level, int bx, int bz, int baseY,
                                               @Nullable it.unimi.dsi.fastutil.longs.LongOpenHashSet preferredSet) {
        long fallback = NO_NODE;
        for (int dz = 0; dz >= -1; dz--) {
            for (int dx = 0; dx >= -1; dx--) {
                long n = nodeAtBlock(level, bx + dx, bz + dz, baseY);
                if (n != NO_NODE) {
                    if (preferredSet == null || preferredSet.contains(n)) return n;
                    if (fallback == NO_NODE) fallback = n;
                }
            }
        }
        return fallback;
    }

    /**
     * Finds the coarse component node connected to {@code (x, z)} via 4-connected water within {@code maxRadius} blocks,
     * never crossing a 1-block land barrier.
     */
    private static long findConnectedCoarseNode(Level level, double x, double z, int baseY, int maxRadius) {
        int sx = Mth.floor(x);
        int sz = Mth.floor(z);
        long direct = directOwnerNodeOfBlock(level, sx, sz, baseY, null);
        if (direct != NO_NODE) return direct;

        // Find the nearest water block within 2 blocks of (sx, sz) to seed a 4-connected water BFS
        int seedX = Integer.MIN_VALUE;
        int seedZ = Integer.MIN_VALUE;
        for (int r = 0; r <= 2 && seedX == Integer.MIN_VALUE; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (isStaticPureWater(level, sx + dx + 0.5, sz + dz + 0.5, baseY)) {
                        seedX = sx + dx;
                        seedZ = sz + dz;
                        break;
                    }
                }
                if (seedX != Integer.MIN_VALUE) break;
            }
        }
        if (seedX == Integer.MIN_VALUE) return NO_NODE;

        java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet visited = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        long seedKey = packGrid(seedX, seedZ);
        visited.add(seedKey);
        queue.add(new long[]{ seedX, seedZ });
        final int[] dxs = { 1, -1, 0, 0 };
        final int[] dzs = { 0, 0, 1, -1 };

        while (!queue.isEmpty()) {
            long[] cur = queue.poll();
            int cx = (int) cur[0];
            int cz = (int) cur[1];
            long n = directOwnerNodeOfBlock(level, cx, cz, baseY, null);
            if (n != NO_NODE) return n;
            for (int d = 0; d < 4; d++) {
                int nx = cx + dxs[d];
                int nz = cz + dzs[d];
                if (Math.abs(nx - sx) > maxRadius || Math.abs(nz - sz) > maxRadius) continue;
                long k = packGrid(nx, nz);
                if (!visited.add(k)) continue;
                if (isStaticPureWater(level, nx + 0.5, nz + 0.5, baseY)) {
                    queue.add(new long[]{ nx, nz });
                }
            }
        }
        return NO_NODE;
    }

    private record CoarseEntry(long node, double g, double f) implements Comparable<CoarseEntry> {
        @Override
        public int compareTo(CoarseEntry other) {
            return Double.compare(this.f, other.f);
        }
    }

    /**
     * Two-level incremental water planner that runs across several ticks under a strict time budget.
     *
     * <p>Phase 1 (coarse): Balanced Bidirectional A* over chunk-level 2x2 water components between the ship and
     * the goal. Extracts the exact winning component corridor and true remaining water distance for every
     * component along the route, so bays, coves and closed river arms are rejected in a few dozen chunk steps.</p>
     *
     * <p>Phase 2 (fine): 2-block-grid A* with strict 0.5-block water edges, guided by the coarse corridor
     * distances and Thalweg shoreline centering.</p>
     */
    private static final class WaterRoutePlanner {
        private static final int STEP = 2;
        private static final int[] DXS = { 1, -1, 0, 0, 1, 1, -1, -1 };
        private static final int[] DZS = { 0, 0, 1, -1, 1, -1, 1, -1 };
        private static final double[] NO_POINT = new double[0];
        private static final int COARSE_MAX_NODES = 30000;

        final Level level;
        final BlockPos goal;
        final int baseY;
        final double goalRadiusSq;
        final double startX;
        final double startZ;
        final int startGx;
        final int startGz;
        final int goalGx;
        final int goalGz;
        final int maxExpansions;
        final List<Vec3> breadcrumbs;

        // Fast 1-element chunk nav cache for fine water queries
        int lastWaterCx = Integer.MIN_VALUE;
        int lastWaterCz = Integer.MIN_VALUE;
        @Nullable ChunkNav lastWaterNav = null;

        // Phase 1: Balanced Bidirectional Coarse A*
        boolean coarseInitialized = false;
        boolean coarseDone = false;
        boolean coarseOk = false;
        long coarseStart = NO_NODE;
        long coarseGoal = NO_NODE;
        double coarseStartX;
        double coarseStartZ;
        double coarseGoalX;
        double coarseGoalZ;
        long meetNode = NO_NODE;
        double bestTotal = Double.POSITIVE_INFINITY;
        int postMeetSteps = 0;
        final java.util.PriorityQueue<CoarseEntry> pqF = new java.util.PriorityQueue<>();
        final java.util.PriorityQueue<CoarseEntry> pqB = new java.util.PriorityQueue<>();
        final it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap gF = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        final it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap gB = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap parentF = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap parentB = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet closedF = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet closedB = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap coarseDist = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap coarseToward = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet coarseCorridor = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final List< Long > coarseChain = new ArrayList<>();

        // Phase 2: Fine 2-block-grid A*
        java.util.PriorityQueue<PathNode> open = new java.util.PriorityQueue<>();
        it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap bestG = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet closed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap shoreCostCache = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<double[]> cellPointCache = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        boolean fineStarted = false;

        int expansions = 0;
        boolean finished = false;
        boolean reachedGoal = false;
        boolean exhausted = false;
        @Nullable PathNode result = null;

        WaterRoutePlanner(Level level, double startX, double startZ, BlockPos goal, int baseY, double goalRadiusSq,
                          int maxExpansions, List<Vec3> breadcrumbs) {
            this.level = level;
            this.goal = goal;
            this.baseY = baseY;
            this.goalRadiusSq = goalRadiusSq;
            this.startX = startX;
            this.startZ = startZ;
            this.startGx = Math.floorDiv(Mth.floor(startX), STEP);
            this.startGz = Math.floorDiv(Mth.floor(startZ), STEP);
            this.goalGx = Math.floorDiv(goal.getX(), STEP);
            this.goalGz = Math.floorDiv(goal.getZ(), STEP);
            this.maxExpansions = maxExpansions;
            this.breadcrumbs = breadcrumbs;
            this.gF.defaultReturnValue(Double.MAX_VALUE);
            this.gB.defaultReturnValue(Double.MAX_VALUE);
            this.parentF.defaultReturnValue(NO_NODE);
            this.parentB.defaultReturnValue(NO_NODE);
            this.coarseDist.defaultReturnValue(Double.MAX_VALUE);
            this.coarseToward.defaultReturnValue(NO_NODE);
            this.shoreCostCache.defaultReturnValue(-1.0);
            if (this.startGx == this.goalGx && this.startGz == this.goalGz) {
                this.finished = true;
            }
        }

        /** Runs the search until done or until {@code deadlineNs}. Returns true when the search has finished. */
        boolean step(long deadlineNs) {
            if (this.finished) return true;
            if (!this.coarseDone && !this.coarseStep(deadlineNs)) return false;
            if (this.finished) return true;
            if (!this.fineStarted) this.startFine();
            return this.fineStep(deadlineNs);
        }

        // ---------------- Phase 1: Balanced Bidirectional Coarse A* ----------------

        private boolean coarseStep(long deadlineNs) {
            if (!this.coarseInitialized) {
                this.coarseInitialized = true;
                this.coarseStart = findConnectedCoarseNode(this.level, this.startX, this.startZ, this.baseY, 10);
                this.coarseGoal = findConnectedCoarseNode(this.level, this.goal.getX() + 0.5, this.goal.getZ() + 0.5, this.baseY, 10);
                if (this.coarseStart == NO_NODE || this.coarseGoal == NO_NODE) {
                    if (Math.hypot(this.goalGx - this.startGx, this.goalGz - this.startGz) > 6.0) {
                        this.exhausted = true;
                        this.finished = true;
                    }
                    this.coarseDone = true;
                    return true;
                }
                if (this.coarseStart == this.coarseGoal) {
                    this.meetNode = this.coarseStart;
                    this.bestTotal = 0.0;
                    this.buildCoarseCorridor();
                    this.coarseDone = true;
                    return true;
                }
                this.coarseStartX = this.centroidX(this.coarseStart);
                this.coarseStartZ = this.centroidZ(this.coarseStart);
                this.coarseGoalX = this.centroidX(this.coarseGoal);
                this.coarseGoalZ = this.centroidZ(this.coarseGoal);
                this.gF.put(this.coarseStart, 0.0);
                this.gB.put(this.coarseGoal, 0.0);
                this.pqF.add(new CoarseEntry(this.coarseStart, 0.0, this.coarseHeuristic(this.coarseStart, this.coarseGoalX, this.coarseGoalZ)));
                this.pqB.add(new CoarseEntry(this.coarseGoal, 0.0, this.coarseHeuristic(this.coarseGoal, this.coarseStartX, this.coarseStartZ)));
            }

            int sinceClockCheck = 0;
            while (!this.pqF.isEmpty() && !this.pqB.isEmpty() && (this.closedF.size() + this.closedB.size()) < COARSE_MAX_NODES) {
                if (++sinceClockCheck >= 8) {
                    sinceClockCheck = 0;
                    if (System.nanoTime() > deadlineNs) return false;
                }
                if (this.meetNode != NO_NODE) {
                    if (++this.postMeetSteps > 40) break;
                    double minF = Math.min(this.pqF.peek().f(), this.pqB.peek().f());
                    if (minF > this.bestTotal * 1.05 + 24.0) break;
                }
                if (this.pqF.size() <= this.pqB.size()) {
                    CoarseEntry e = this.pqF.poll();
                    if (!this.closedF.add(e.node())) continue;
                    if (this.closedB.contains(e.node())) {
                        double tot = e.g() + this.gB.get(e.node());
                        if (tot < this.bestTotal) {
                            this.bestTotal = tot;
                            this.meetNode = e.node();
                        }
                    }
                    this.expandCoarseSide(e, true);
                } else {
                    CoarseEntry e = this.pqB.poll();
                    if (!this.closedB.add(e.node())) continue;
                    if (this.closedF.contains(e.node())) {
                        double tot = e.g() + this.gF.get(e.node());
                        if (tot < this.bestTotal) {
                            this.bestTotal = tot;
                            this.meetNode = e.node();
                        }
                    }
                    this.expandCoarseSide(e, false);
                }
            }

            if (this.meetNode != NO_NODE) {
                this.buildCoarseCorridor();
            } else if (this.pqF.isEmpty() || this.pqB.isEmpty()) {
                // One frontier completely exhausted without meeting the other: ports are in disconnected water bodies
                if (Math.hypot(this.goalGx - this.startGx, this.goalGz - this.startGz) > 6.0) {
                    this.exhausted = true;
                    this.finished = true;
                }
            }
            this.pqF.clear();
            this.pqB.clear();
            this.coarseDone = true;
            return true;
        }

        private void expandCoarseSide(CoarseEntry e, boolean forward) {
            long node = e.node();
            int cx = nodeCx(node);
            int cz = nodeCz(node);
            int label = nodeLabel(node);
            ChunkNav self = getChunkNav(this.level, cx, cz, this.baseY);
            double ex = self.centroidX[label];
            double ez = self.centroidZ[label];

            it.unimi.dsi.fastutil.longs.LongOpenHashSet selfClosed = forward ? this.closedF : this.closedB;
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap selfG = forward ? this.gF : this.gB;
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap otherG = forward ? this.gB : this.gF;
            it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap selfParent = forward ? this.parentF : this.parentB;
            java.util.PriorityQueue<CoarseEntry> selfPq = forward ? this.pqF : this.pqB;
            double targetX = forward ? this.coarseGoalX : this.coarseStartX;
            double targetZ = forward ? this.coarseGoalZ : this.coarseStartZ;

            for (int d = 0; d < 4; d++) {
                int ncx = cx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int ncz = cz + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                ChunkNav other = getChunkNav(this.level, ncx, ncz, this.baseY);
                long seenLow = 0L;
                long seenHigh = 0L;
                for (int t = 0; t < 16; t++) {
                    int a;
                    int b;
                    switch (d) {
                        case 0 -> { a = 15 + (t << 4); b = t << 4; }
                        case 1 -> { a = t << 4; b = 15 + (t << 4); }
                        case 2 -> { a = t + 240; b = t; }
                        default -> { a = t; b = t + 240; }
                    }
                    if (self.labels[a] != label) continue;
                    int ol = other.labels[b];
                    if (ol == 0) continue;
                    if (ol < 64) {
                        if ((seenLow & (1L << ol)) != 0) continue;
                        seenLow |= 1L << ol;
                    } else {
                        if ((seenHigh & (1L << (ol - 64))) != 0) continue;
                        seenHigh |= 1L << (ol - 64);
                    }
                    long m = packNode(ncx, ncz, ol);
                    if (selfClosed.contains(m)) continue;
                    double mx = other.centroidX[ol];
                    double mz = other.centroidZ[ol];
                    double ng = e.g() + Math.hypot(mx - ex, mz - ez);
                    if (ng < selfG.get(m)) {
                        selfG.put(m, ng);
                        selfParent.put(m, node);
                        double h = (Math.abs(targetX - mx) + Math.abs(targetZ - mz)) * 1.05;
                        selfPq.add(new CoarseEntry(m, ng, ng + h));
                        double oppG = otherG.get(m);
                        if (oppG < Double.MAX_VALUE && ng + oppG < this.bestTotal) {
                            this.bestTotal = ng + oppG;
                            this.meetNode = m;
                        }
                    }
                }
            }
        }

        private void buildCoarseCorridor() {
            this.coarseChain.clear();
            long cur = this.meetNode;
            while (cur != NO_NODE) {
                this.coarseChain.add(cur);
                cur = this.parentF.get(cur);
            }
            java.util.Collections.reverse(this.coarseChain);
            cur = this.parentB.get(this.meetNode);
            while (cur != NO_NODE) {
                this.coarseChain.add(cur);
                cur = this.parentB.get(cur);
            }
            if (this.coarseChain.isEmpty()) return;

            int n = this.coarseChain.size();
            long last = this.coarseChain.get(n - 1);
            this.coarseDist.put(last, 0.0);
            this.coarseToward.put(last, NO_NODE);
            this.coarseCorridor.add(last);

            for (int i = n - 2; i >= 0; i--) {
                long u = this.coarseChain.get(i);
                long nxt = this.coarseChain.get(i + 1);
                double seg = Math.hypot(this.centroidX(nxt) - this.centroidX(u), this.centroidZ(nxt) - this.centroidZ(u));
                this.coarseDist.put(u, this.coarseDist.get(nxt) + seg);
                this.coarseToward.put(u, nxt);
                this.coarseCorridor.add(u);
            }

            // Only include a 1-ring neighbor component m if it bridges two chain nodes (minChainIdx < maxChainIdx),
            // never adding dead-end side branches or coves
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap minChainTouch = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap maxChainTouch = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            minChainTouch.defaultReturnValue(Integer.MAX_VALUE);
            maxChainTouch.defaultReturnValue(-1);
            for (int i = 0; i < n; i++) {
                long u = this.coarseChain.get(i);
                int cx = nodeCx(u);
                int cz = nodeCz(u);
                int label = nodeLabel(u);
                ChunkNav self = getChunkNav(this.level, cx, cz, this.baseY);

                for (int d = 0; d < 4; d++) {
                    int ncx = cx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                    int ncz = cz + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                    ChunkNav other = getChunkNav(this.level, ncx, ncz, this.baseY);
                    for (int t = 0; t < 16; t++) {
                        int a;
                        int b;
                        switch (d) {
                            case 0 -> { a = 15 + (t << 4); b = t << 4; }
                            case 1 -> { a = t << 4; b = 15 + (t << 4); }
                            case 2 -> { a = t + 240; b = t; }
                            default -> { a = t; b = t + 240; }
                        }
                        if (self.labels[a] != label) continue;
                        int ol = other.labels[b];
                        if (ol == 0) continue;
                        long m = packNode(ncx, ncz, ol);
                        if (this.coarseCorridor.contains(m)) continue;
                        if (i < minChainTouch.get(m)) minChainTouch.put(m, i);
                        if (i > maxChainTouch.get(m)) maxChainTouch.put(m, i);
                    }
                }
            }
            for (long m : maxChainTouch.keySet()) {
                int minI = minChainTouch.get(m);
                int maxI = maxChainTouch.get(m);
                if (maxI > minI) {
                    long fwd = this.coarseChain.get(maxI);
                    double dToFwd = Math.hypot(this.centroidX(fwd) - this.centroidX(m), this.centroidZ(fwd) - this.centroidZ(m));
                    this.coarseDist.put(m, this.coarseDist.get(fwd) + dToFwd);
                    this.coarseToward.put(m, fwd);
                    this.coarseCorridor.add(m);
                }
            }
            this.coarseOk = true;
        }

        private double coarseHeuristic(long node, double tx, double tz) {
            return (Math.abs(tx - this.centroidX(node)) + Math.abs(tz - this.centroidZ(node))) * 1.05;
        }

        private double centroidX(long node) {
            return getChunkNav(this.level, nodeCx(node), nodeCz(node), this.baseY).centroidX[nodeLabel(node)];
        }

        private double centroidZ(long node) {
            return getChunkNav(this.level, nodeCx(node), nodeCz(node), this.baseY).centroidZ[nodeLabel(node)];
        }

        private ChunkNav navForChunk(int cx, int cz) {
            if (cx == this.lastWaterCx && cz == this.lastWaterCz && this.lastWaterNav != null) {
                return this.lastWaterNav;
            }
            ChunkNav nav = getChunkNav(this.level, cx, cz, this.baseY);
            this.lastWaterCx = cx;
            this.lastWaterCz = cz;
            this.lastWaterNav = nav;
            return nav;
        }

        /**
         * Returns the coarse corridor component owning water block {@code (bx, bz)} without ever crossing a 1-block land barrier.
         */
        private long corridorNodeOfWaterBlock(int bx, int bz) {
            long direct = directOwnerNodeOfBlock(this.level, bx, bz, this.baseY, this.coarseCorridor);
            if (direct != NO_NODE && this.coarseCorridor.contains(direct)) return direct;
            // Check 4-connected adjacent water blocks (cannot cross any land block because both (bx,bz) and neighbor are water)
            final int[] dxs = { 1, -1, 0, 0 };
            final int[] dzs = { 0, 0, 1, -1 };
            for (int d = 0; d < 4; d++) {
                int nx = bx + dxs[d];
                int nz = bz + dzs[d];
                if (!this.waterBlock(nx, nz)) continue;
                long adj = directOwnerNodeOfBlock(this.level, nx, nz, this.baseY, this.coarseCorridor);
                if (adj != NO_NODE && this.coarseCorridor.contains(adj)) return adj;
            }
            return NO_NODE;
        }

        // ---------------- Phase 2: Fine 2-block-grid A* ----------------

        private void startFine() {
            this.fineStarted = true;
            this.open = new java.util.PriorityQueue<>();
            this.bestG = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();
            this.bestG.defaultReturnValue(Double.MAX_VALUE);
            this.closed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            this.expansions = 0;
            this.open.add(new PathNode(this.startGx, this.startGz, 0.0, 0.0, null));
            this.bestG.put(packGrid(this.startGx, this.startGz), 0.0);
        }

        /**
         * Remaining-distance estimate in grid units. With a coarse route this is the true remaining WATER
         * distance along the coarse component chain; returns -1 for cells outside the coarse corridor.
         */
        private double heuristic(int gx, int gz, double[] pt) {
            if (!this.coarseOk) {
                return Math.hypot(this.goalGx - gx, this.goalGz - gz);
            }
            if (this.nearGoal(pt[0], pt[1])) return 0.0;
            long n = this.corridorNodeOfWaterBlock(Mth.floor(pt[0]), Mth.floor(pt[1]));
            if (n == NO_NODE) return -1.0;
            long next = this.coarseToward.get(n);
            if (next == NO_NODE || !this.coarseCorridor.contains(next)) {
                return Math.hypot(this.goal.getX() + 0.5 - pt[0], this.goal.getZ() + 0.5 - pt[1]) / STEP;
            }
            double toNext = Math.hypot(this.centroidX(next) - pt[0], this.centroidZ(next) - pt[1]);
            return (this.coarseDist.get(next) + toNext) / STEP;
        }

        private boolean fineStep(long deadlineNs) {
            int sinceClockCheck = 0;
            while (!this.open.isEmpty() && this.expansions < this.maxExpansions) {
                if (++sinceClockCheck >= 16) {
                    sinceClockCheck = 0;
                    if (System.nanoTime() > deadlineNs) return false;
                }
                PathNode curr = this.open.poll();
                if (!this.closed.add(packGrid(curr.gx(), curr.gz()))) continue;
                this.expansions++;

                if (Math.hypot(this.goalGx - curr.gx(), this.goalGz - curr.gz()) <= 2.2) {
                    this.result = curr;
                    this.reachedGoal = true;
                    this.finished = true;
                    return true;
                }

                double[] cPt;
                if (curr.parent() == null) {
                    cPt = this.water(this.startX, this.startZ) ? new double[]{ this.startX, this.startZ } : this.cellPoint(curr.gx(), curr.gz());
                } else {
                    cPt = this.cellPoint(curr.gx(), curr.gz());
                }
                if (cPt == null) continue;

                for (int dir = 0; dir < 8; dir++) {
                    int nx = curr.gx() + DXS[dir];
                    int nz = curr.gz() + DZS[dir];
                    long key = packGrid(nx, nz);
                    if (this.closed.contains(key)) continue;

                    double[] nPt = this.cellPoint(nx, nz);
                    if (nPt == null) continue;
                    // Strict water edge: never hop over 1-block land strips / dams that close a waterway
                    if (!this.edgeWater(cPt, nPt, curr.parent() == null)) continue;

                    double moveCost = ((dir < 4) ? 1.0 : 1.414) + this.shoreCost(nx, nz)
                            + breadcrumbPenalty(this.breadcrumbs, nPt[0], nPt[1]) * 2.5;
                    double tentativeG = curr.g() + moveCost;
                    if (tentativeG < this.bestG.get(key)) {
                        double h = this.heuristic(nx, nz, nPt);
                        if (h < 0.0) continue; // outside the coarse corridor
                        this.bestG.put(key, tentativeG);
                        this.open.add(new PathNode(nx, nz, tentativeG, tentativeG + h * 1.35, curr));
                    }
                }
            }

            if (this.open.isEmpty()) {
                // Entire connected water body (or coarse corridor) flooded without reaching the goal
                this.exhausted = true;
            } else {
                // Expansion cap hit (extremely long route): continue towards the frontier with the smallest true remaining water distance
                double bestScore = Double.POSITIVE_INFINITY;
                for (PathNode cand : this.open) {
                    if (this.closed.contains(packGrid(cand.gx(), cand.gz()))) continue;
                    double fromStart = Math.hypot(cand.gx() - this.startGx, cand.gz() - this.startGz);
                    if (fromStart < 3.0) continue;
                    double score;
                    if (this.coarseOk) {
                        double[] pt = this.cellPoint(cand.gx(), cand.gz());
                        double h = (pt != null) ? this.heuristic(cand.gx(), cand.gz(), pt) : -1.0;
                        if (h < 0.0) continue;
                        score = h;
                    } else {
                        double h = Math.hypot(this.goalGx - cand.gx(), this.goalGz - cand.gz());
                        score = h - fromStart * 0.38
                                + breadcrumbPenalty(this.breadcrumbs, cand.gx() * STEP + 1.0, cand.gz() * STEP + 1.0) * 18.0;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        this.result = cand;
                    }
                }
            }
            this.finished = true;
            return true;
        }

        /** Route cell water points from the first step after the start up to the goal/frontier node. */
        List<double[]> extractPath() {
            List<double[]> path = new ArrayList<>();
            PathNode cur = this.result;
            while (cur != null && cur.parent() != null) {
                double[] pt = this.cellPoint(cur.gx(), cur.gz());
                path.add(pt != null ? pt : new double[]{ cur.gx() * STEP + 1.0, cur.gz() * STEP + 1.0 });
                cur = cur.parent();
            }
            java.util.Collections.reverse(path);
            return path;
        }

        private boolean nearGoal(double x, double z) {
            double dx = x - (this.goal.getX() + 0.5);
            double dz = z - (this.goal.getZ() + 0.5);
            return dx * dx + dz * dz <= this.goalRadiusSq;
        }

        private boolean waterBlock(int bx, int bz) {
            ChunkNav nav = this.navForChunk(bx >> 4, bz >> 4);
            return (nav.waterMask[bz & 15] & (1 << (bx & 15))) != 0;
        }

        private boolean water(double x, double z) {
            if (this.nearGoal(x, z)) return true;
            return this.waterBlock(Mth.floor(x), Mth.floor(z));
        }

        /** Requires a 0.9x0.9 hull water footprint so diagonal block corners between two land blocks are impassable. */
        private boolean hullWater(double x, double z) {
            if (this.nearGoal(x, z)) return true;
            int bx0 = Mth.floor(x - 0.45);
            int bx1 = Mth.floor(x + 0.45);
            int bz0 = Mth.floor(z - 0.45);
            int bz1 = Mth.floor(z + 0.45);
            return this.waterBlock(bx0, bz0)
                    && (bx1 == bx0 || this.waterBlock(bx1, bz0))
                    && (bz1 == bz0 || this.waterBlock(bx0, bz1))
                    && ((bx1 == bx0 && bz1 == bz0) || this.waterBlock(bx1, bz1));
        }

        @Nullable
        private double[] cellPoint(int gx, int gz) {
            long key = packGrid(gx, gz);
            double[] cached = this.cellPointCache.get(key);
            if (cached == null) {
                double wx = gx * STEP + 1.0;
                double wz = gz * STEP + 1.0;
                if (this.hullWater(wx, wz)) cached = new double[]{ wx, wz };
                else if (this.hullWater(wx - 0.5, wz - 0.5)) cached = new double[]{ wx - 0.5, wz - 0.5 };
                else if (this.hullWater(wx + 0.5, wz - 0.5)) cached = new double[]{ wx + 0.5, wz - 0.5 };
                else if (this.hullWater(wx - 0.5, wz + 0.5)) cached = new double[]{ wx - 0.5, wz + 0.5 };
                else if (this.hullWater(wx + 0.5, wz + 0.5)) cached = new double[]{ wx + 0.5, wz + 0.5 };
                else cached = NO_POINT;
                this.cellPointCache.put(key, cached);
            }
            return cached == NO_POINT ? null : cached;
        }

        private boolean edgeWater(double[] a, double[] b, boolean lenientStart) {
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double len = Math.hypot(dx, dz);
            int samples = Math.max(2, (int) Math.ceil(len / 0.35));
            int prevBx = Mth.floor(a[0]);
            int prevBz = Mth.floor(a[1]);
            for (int i = 1; i <= samples; i++) {
                double t = (double) i / samples;
                double sx = a[0] + dx * t;
                double sz = a[1] + dz * t;
                // A ship pressed against a bank may sit within 0.45 of land; let its first ~1 block leave on single-block water
                boolean ok = (lenientStart && t * len < 1.0) ? this.water(sx, sz) : this.hullWater(sx, sz);
                if (!ok) return false;
                int bx = Mth.floor(sx);
                int bz = Mth.floor(sz);
                if (bx != prevBx && bz != prevBz) {
                    if (!this.waterBlock(prevBx, bz) || !this.waterBlock(bx, prevBz)) return false;
                }
                prevBx = bx;
                prevBz = bz;
            }
            return true;
        }

        /** 3-ring shoreline distance cost field (Thalweg centering), cached per grid cell. */
        private double shoreCost(int gx, int gz) {
            long key = packGrid(gx, gz);
            double cost = this.shoreCostCache.get(key);
            if (cost < 0.0) {
                double wx = gx * STEP + 1.0;
                double wz = gz * STEP + 1.0;
                int r1 = 0, r2 = 0, r3 = 0;
                for (double[] rdir : RING_DIRS) {
                    if (!this.water(wx + rdir[0] * 1.8, wz + rdir[1] * 1.8)) r1++;
                    if (!this.water(wx + rdir[0] * 3.8, wz + rdir[1] * 3.8)) r2++;
                    if (!this.water(wx + rdir[0] * 6.0, wz + rdir[1] * 6.0)) r3++;
                }
                cost = r1 * 0.22 + r2 * 0.10 + r3 * 0.04;
                this.shoreCostCache.put(key, cost);
            }
            return cost;
        }
    }

    private static double breadcrumbPenalty(List<Vec3> breadcrumbs, double x, double z) {
        if (breadcrumbs.size() <= 1) return 0.0;
        double penalty = 0.0;
        for (int i = 0; i < breadcrumbs.size() - 1; i++) {
            Vec3 bc = breadcrumbs.get(i);
            double dx = x - bc.x;
            double dz = z - bc.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < 81.0) {
                penalty = Math.max(penalty, ((81.0 - distSq) / 81.0) * 2.5);
            }
        }
        return penalty;
    }

    /**
     * Shifts a waypoint `(wx, wz)` along the normal perpendicular to the tangent `(tx, tz)`
     * so that it sits equidistant from the left and right banks when inside a river/channel.
     */
    private Vec3 centerPointInChannel(Level level, double wx, double wz, double tx, double tz) {
        if (!isPureWaterLoadedOnly(level, wx, wz)) {
            boolean snapped = false;
            for (double r : new double[]{ 0.6, 1.1, 1.6 }) {
                for (double[] dir : RING_DIRS) {
                    if (isPureWaterLoadedOnly(level, wx + dir[0] * r, wz + dir[1] * r)) {
                        wx += dir[0] * r;
                        wz += dir[1] * r;
                        snapped = true;
                        break;
                    }
                }
                if (snapped) break;
            }
        }

        double len = Math.hypot(tx, tz);
        if (len < 1e-4) return new Vec3(wx, this.getY(), wz);
        double nx = -tz / len;
        double nz = tx / len;

        double maxProbe = 9.0;
        double dPlus = maxProbe;
        for (double d = 0.5; d <= maxProbe; d += 0.5) {
            if (!isPureWaterLoadedOnly(level, wx + nx * d, wz + nz * d)) {
                dPlus = d - 0.5;
                break;
            }
        }
        double dMinus = maxProbe;
        for (double d = 0.5; d <= maxProbe; d += 0.5) {
            if (!isPureWaterLoadedOnly(level, wx - nx * d, wz - nz * d)) {
                dMinus = d - 0.5;
                break;
            }
        }

        // Only shift when at least one riverbank is within probe range
        if (dPlus < maxProbe || dMinus < maxProbe) {
            double shift = Mth.clamp((dPlus - dMinus) * 0.5, -3.0, 3.0);
            double cx = wx + nx * shift;
            double cz = wz + nz * shift;
            if (isPureWaterLoadedOnly(level, cx, cz)) {
                return new Vec3(cx, this.getY(), cz);
            }
        }
        return new Vec3(wx, this.getY(), wz);
    }

    /**
     * Applies a smooth heading correction that balances perpendicular water clearance on the
     * port and starboard sides of the ship so it tracks the middle of rivers and narrow straits.
     */
    private double applyChannelCenteringBias(Level level, double angle) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        // Unit normal in the direction of +d(angle): d/da (cos a, sin a) = (-sin a, cos a)
        double nx = -sinA;
        double nz = cosA;

        double maxProbe = 8.0;
        double plusNow = getDistanceToLandFrom(level, this.getX(), this.getZ(), nx, nz, maxProbe);
        double minusNow = getDistanceToLandFrom(level, this.getX(), this.getZ(), -nx, -nz, maxProbe);

        double lookX = this.getX() + cosA * 2.0;
        double lookZ = this.getZ() + sinA * 2.0;
        double plusAhead = getDistanceToLandFrom(level, lookX, lookZ, nx, nz, maxProbe);
        double minusAhead = getDistanceToLandFrom(level, lookX, lookZ, -nx, -nz, maxProbe);

        double effPlus = Math.min(plusNow, plusAhead);
        double effMinus = Math.min(minusNow, minusAhead);

        if (effPlus < 6.5 || effMinus < 6.5) {
            double diff = effPlus - effMinus;
            if (Math.abs(diff) > 0.35) {
                double totalWidth = Math.max(2.5, effPlus + effMinus);
                double biasRad = Mth.clamp((diff / totalWidth) * 0.75, -Math.toRadians(26.0), Math.toRadians(26.0));
                double biasedAngle = angle + biasRad;
                if (getDistanceToLand(level, biasedAngle, 6.0) >= 3.5) {
                    return biasedAngle;
                }
            }
        }
        return angle;
    }

    private double getDistanceToLandFrom(Level level, double startX, double startZ, double dirX, double dirZ, double maxDist) {
        for (double d = 0.6; d <= maxDist; d += 0.6) {
            if (!isPureWaterLoadedOnly(level, startX + dirX * d, startZ + dirZ * d)) {
                return d - 0.6;
            }
        }
        return maxDist;
    }

    private boolean isNearShoreClear(Level level, double x, double z, double radius) {
        for (double[] dir : RING_DIRS) {
            if (!isPureWaterLoadedOnly(level, x + dir[0] * radius, z + dir[1] * radius)) {
                return false;
            }
        }
        return true;
    }

    private double getBreadcrumbPenalty(double x, double z) {
        if (this.recentBreadcrumbs.size() <= 1) return 0.0;
        double penalty = 0.0;
        // Ignore the newest breadcrumb right under the ship; penalize older visited spots
        for (int i = 0; i < this.recentBreadcrumbs.size() - 1; i++) {
            Vec3 bc = this.recentBreadcrumbs.get(i);
            double dx = x - bc.x;
            double dz = z - bc.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < 81.0) { // Within 9 blocks of a previously visited point
                double factor = (81.0 - distSq) / 81.0;
                penalty = Math.max(penalty, factor * 2.5);
            }
        }
        return penalty;
    }

    private static long packGrid(int gx, int gz) {
        return (((long) gx) << 32) | (gz & 0xFFFFFFFFL);
    }

    private boolean isSegmentClearLoadedOnly(Level level, double x0, double z0, double x1, double z1, double margin) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double dist = Math.hypot(dx, dz);
        if (dist < 1.0) return true;
        double cos = dx / dist;
        double sin = dz / dist;
        double nx = -sin;
        double nz = cos;
        double halfMargin = margin * 0.5;
        for (double d = 0.75; d < dist; d += 0.75) {
            double cx = x0 + cos * d;
            double cz = z0 + sin * d;
            if (!isPureWaterLoadedOnly(level, cx, cz)
                    || !isPureWaterLoadedOnly(level, cx + nx * halfMargin, cz + nz * halfMargin)
                    || !isPureWaterLoadedOnly(level, cx - nx * halfMargin, cz - nz * halfMargin)
                    || !isPureWaterLoadedOnly(level, cx + nx * margin, cz + nz * margin)
                    || !isPureWaterLoadedOnly(level, cx - nx * margin, cz - nz * margin)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPureWaterLoadedOnly(Level level, double x, double z) {
        int baseWaterY = (this.getY() > 64.0) ? (int) Math.floor(this.getY()) : 62;
        return isStaticPureWater(level, x, z, baseWaterY);
    }

    private boolean isGridCellNavigable(Level level, double wx, double wz, @Nullable BlockPos goal) {
        if (isNavigableWaterLoadedOnly(level, wx, wz, goal)) return true;
        return isPureWaterLoadedOnly(level, wx + 0.9, wz)
                || isPureWaterLoadedOnly(level, wx - 0.9, wz)
                || isPureWaterLoadedOnly(level, wx, wz + 0.9)
                || isPureWaterLoadedOnly(level, wx, wz - 0.9);
    }

    private boolean isNavigableWaterLoadedOnly(Level level, double x, double z, @Nullable BlockPos goal) {
        if (goal != null) {
            double destDx = x - (goal.getX() + 0.5);
            double destDz = z - (goal.getZ() + 0.5);
            double allowRadiusSq = this.reachedPortApproach ? 12.25 : 6.25;
            if (destDx * destDx + destDz * destDz <= allowRadiusSq) {
                return true;
            }
        }
        return isPureWaterLoadedOnly(level, x, z);
    }

    private double findBestDetourAngle(double directAngle, double bufferMargin) {
        double currentHeading = (this.getYRot() + 90.0F) * (Math.PI / 180.0);
        int side = this.avoidanceSide;
        if (side == 0) {
            double portDist = getCorridorDistanceToLand(this.level(), directAngle - Math.toRadians(40.0), 32.0, 0.9);
            double stbdDist = getCorridorDistanceToLand(this.level(), directAngle + Math.toRadians(40.0), 32.0, 0.9);
            side = (portDist >= stbdDist) ? -1 : 1;
        }

        double bestAngle = directAngle;
        double bestScore = -1e9;
        int chosenSide = side;

        // Evaluate all 36 directions (every 10 degrees) with continuous channel depth + bank clearance + anti-backtracking
        for (int stepDeg = 0; stepDeg <= 180; stepDeg += 10) {
            int[] signs = (stepDeg == 0 || stepDeg == 180) ? new int[]{ 1 } : new int[]{ side, -side };
            for (int s : signs) {
                double offsetRad = Math.toRadians(stepDeg * s);
                double candidateAngle = directAngle + offsetRad;
                double clearRun = getCorridorDistanceToLand(this.level(), candidateAngle, 36.0, 0.95);
                if (clearRun < 5.0) continue;

                double wideRun = getCorridorDistanceToLand(this.level(), candidateAngle, 28.0, 2.6);
                double sampleDist = Math.min(14.0, clearRun * 0.7);
                double sx = this.getX() + Math.cos(candidateAngle) * sampleDist;
                double sz = this.getZ() + Math.sin(candidateAngle) * sampleDist;

                double goalAlign = Math.cos(offsetRad); // +1 toward goal, -1 away from goal
                double forwardAlign = Math.cos(candidateAngle - currentHeading); // +1 forward, -1 reverse
                double sideBonus = (s == side) ? 3.0 : 0.0;
                double bcPenalty = getBreadcrumbPenalty(sx, sz) * 14.0;

                double score = (clearRun * 1.15) + (wideRun * 0.75) + (goalAlign * 8.0) + (forwardAlign * 5.5) + sideBonus - bcPenalty;
                if (score > bestScore) {
                    bestScore = score;
                    bestAngle = candidateAngle;
                    chosenSide = s;
                }
            }
        }

        if (bestScore > -1e8) {
            this.avoidanceSide = chosenSide;
            return bestAngle;
        }
        return findOpenWaterEscapeAngle();
    }

    private double getCorridorDistanceToLand(Level level, double angle, double maxDist, double hullHalfWidth) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double normalX = -sinA * hullHalfWidth;
        double normalZ = cosA * hullHalfWidth;

        for (double d = 1.5; d <= maxDist; d += 0.75) {
            double cx = this.getX() + cosA * d;
            double cz = this.getZ() + sinA * d;
            if (!isNavigableWater(level, cx, cz)
                    || !isNavigableWater(level, cx + normalX, cz + normalZ)
                    || !isNavigableWater(level, cx - normalX, cz - normalZ)) {
                return d - 0.75;
            }
        }
        return maxDist;
    }

    private boolean isLineClearOfLand(Level level, double angle, double maxDist) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double normalX = -sinA * 0.8;
        double normalZ = cosA * 0.8;

        for (double d = 0.9; d <= maxDist; d += 0.9) {
            double checkX = this.getX() + cosA * d;
            double checkZ = this.getZ() + sinA * d;
            if (!isNavigableWater(level, checkX, checkZ)
                    || !isNavigableWater(level, checkX + normalX, checkZ + normalZ)
                    || !isNavigableWater(level, checkX - normalX, checkZ - normalZ)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorridorClear(Level level, double angle, double maxDist, double bufferMargin) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double normalX = -sinA;
        double normalZ = cosA;

        for (double d = 2.0; d <= maxDist; d += 1.0) {
            double checkX = this.getX() + cosA * d;
            double checkZ = this.getZ() + sinA * d;

            if (!isNavigableWater(level, checkX, checkZ)) return false;

            if (bufferMargin > 0.4) {
                double leftX = checkX + normalX * bufferMargin;
                double leftZ = checkZ + normalZ * bufferMargin;
                if (!isNavigableWater(level, leftX, leftZ)) return false;

                double rightX = checkX - normalX * bufferMargin;
                double rightZ = checkZ - normalZ * bufferMargin;
                if (!isNavigableWater(level, rightX, rightZ)) return false;
            }
        }
        return true;
    }

    private double getDistanceToLand(Level level, double angle, double maxDist) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);

        for (double d = 2.0; d <= maxDist; d += 1.0) {
            if (!isNavigableWater(level, this.getX() + cosA * d, this.getZ() + sinA * d)) {
                return d;
            }
        }
        return maxDist;
    }

    private double findOpenWaterEscapeAngle() {
        double bestScore = -1.0;
        double bestAngle = (this.getYRot() + 90.0F) * (Math.PI / 180.0) + Math.PI;

        for (int i = 0; i < 16; i++) {
            double testAngle = i * (Math.PI / 8.0);
            double cos = Math.cos(testAngle);
            double sin = Math.sin(testAngle);

            double score = 0.0;
            for (double d : new double[]{ 2.0, 4.0, 7.0, 11.0, 16.0 }) {
                if (isNavigableWater(this.level(), this.getX() + cos * d, this.getZ() + sin * d)) {
                    score += d;
                } else {
                    break;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestAngle = testAngle;
            }
        }
        return bestAngle;
    }

    private boolean isNavigableWater(Level level, double x, double z) {
        if (this.reachedPortApproach && this.targetPos != null) {
            double destDx = x - (this.targetPos.getX() + 0.5);
            double destDz = z - (this.targetPos.getZ() + 0.5);
            if (destDx * destDx + destDz * destDz <= 16.0) {
                return true;
            }
        }

        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int cx = bx >> 4;
        int cz = bz >> 4;

        if (!level.hasChunk(cx, cz)) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunk(cx, cz);
            }
        }
        if (!level.hasChunk(cx, cz)) {
            return false;
        }

        int baseWaterY = 62;
        if (this.getY() > 64.0) {
            baseWaterY = (int) Math.floor(this.getY());
        }

        boolean hasWater = level.getFluidState(new BlockPos(bx, baseWaterY, bz)).is(FluidTags.WATER)
                || level.getFluidState(new BlockPos(bx, baseWaterY + 1, bz)).is(FluidTags.WATER);
        if (!hasWater) return false;

        for (int y = baseWaterY + 1; y <= baseWaterY + 3; y++) {
            BlockPos checkPos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                if (state.blocksMotion() && !state.is(Blocks.LILY_PAD)) {
                    return false;
                }
            }
        }

        return true;
    }

    // --- NBT Persistence ---

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addChestVehicleSaveData(tag);

        if (this.targetPos != null) {
            tag.putInt("TargetX", this.targetPos.getX());
            tag.putInt("TargetY", this.targetPos.getY());
            tag.putInt("TargetZ", this.targetPos.getZ());
        }
        if (!this.routeWaypoints.isEmpty()) {
            ListTag wpList = new ListTag();
            for (BlockPos wp : this.routeWaypoints) {
                CompoundTag wpTag = new CompoundTag();
                wpTag.putInt("X", wp.getX());
                wpTag.putInt("Y", wp.getY());
                wpTag.putInt("Z", wp.getZ());
                wpList.add(wpTag);
            }
            tag.put("RouteWaypoints", wpList);
            tag.putInt("CurrentWaypointIndex", this.currentWaypointIndex);
        }
        tag.putString("HarborName", this.harborName);
        tag.putDouble("CruiseSpeed", this.cruiseSpeed);
        tag.putInt("AvoidanceSide", this.avoidanceSide);
        tag.putBoolean("IsWanderingTraderShip", this.isWanderingTraderShip());
        tag.putBoolean("VoyageShip", this.voyageShip);
        tag.putFloat("ShipHealth", this.shipHealth);
        tag.putBoolean("HadInitialCargo", this.hadInitialCargo);

        tag.putBoolean("CargoDelivered", this.cargoDelivered);
        tag.putInt("EmptyTicks", this.emptyTicks);
        tag.putInt("BlockedTicks", this.blockedTicks);
        tag.putInt("NoProgressTicks", this.noProgressTicks);
        if (this.senderUUID != null) tag.putUUID("SenderUUID", this.senderUUID);
        if (this.senderName != null) tag.putString("SenderName", this.senderName);
        tag.putBoolean("IsTradeMission", this.isTradeMission);
        tag.putInt("MissionPhase", this.missionPhase.ordinal());
        if (this.homeHarborPos != null) tag.putLong("HomeHarborPos", this.homeHarborPos.asLong());
        if (this.homeHarborName != null) tag.putString("HomeHarborName", this.homeHarborName);

        if (!this.tradeRewards.isEmpty()) {
            ListTag rewardList = new ListTag();
            for (ItemStack reward : this.tradeRewards) {
                rewardList.add(reward.save(new CompoundTag()));
            }
            tag.put("TradeRewards", rewardList);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (!this.voyageShip && (!tag.contains("CruiseSpeed") || tag.getDouble("CruiseSpeed") > CARGO_MAX_SPEED)) {
            this.cruiseSpeed = 0.9;
        } else if (tag.contains("CruiseSpeed")) {
            this.cruiseSpeed = tag.getDouble("CruiseSpeed");
        }

        this.readChestVehicleSaveData(tag);

        if (tag.contains("TargetX")) {
            this.targetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        this.routeWaypoints.clear();
        if (tag.contains("RouteWaypoints", Tag.TAG_LIST)) {
            ListTag wpList = tag.getList("RouteWaypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < wpList.size(); i++) {
                CompoundTag wpTag = wpList.getCompound(i);
                this.routeWaypoints.add(new BlockPos(wpTag.getInt("X"), wpTag.getInt("Y"), wpTag.getInt("Z")));
            }
        }
        this.currentWaypointIndex = tag.getInt("CurrentWaypointIndex");

        if (tag.contains("HarborName")) this.harborName = tag.getString("HarborName");
        if (tag.contains("AvoidanceSide")) this.avoidanceSide = tag.getInt("AvoidanceSide");
        if (tag.contains("IsWanderingTraderShip")) this.setWanderingTraderShip(tag.getBoolean("IsWanderingTraderShip"));
        if (tag.contains("VoyageShip")) {
            this.voyageShip = tag.getBoolean("VoyageShip");
            this.entityData.set(IS_VOYAGE_SHIP, this.voyageShip);
        }
        if (tag.contains("ShipHealth")) this.shipHealth = tag.getFloat("ShipHealth");
        this.hadInitialCargo = tag.getBoolean("HadInitialCargo");

        this.cargoDelivered = tag.getBoolean("CargoDelivered");
        if (tag.contains("EmptyTicks")) this.emptyTicks = tag.getInt("EmptyTicks");
        if (tag.contains("BlockedTicks")) this.blockedTicks = tag.getInt("BlockedTicks");
        if (tag.contains("NoProgressTicks")) this.noProgressTicks = tag.getInt("NoProgressTicks");
        if (tag.hasUUID("SenderUUID")) this.senderUUID = tag.getUUID("SenderUUID");
        if (tag.contains("SenderName")) this.senderName = tag.getString("SenderName");

        this.isTradeMission = tag.getBoolean("IsTradeMission");
        int phaseIdx = tag.getInt("MissionPhase");
        this.missionPhase = (phaseIdx >= 0 && phaseIdx < TradeMissionPhase.values().length)
                ? TradeMissionPhase.values()[phaseIdx]
                : TradeMissionPhase.NONE;

        if (tag.contains("HomeHarborPos")) this.homeHarborPos = BlockPos.of(tag.getLong("HomeHarborPos"));
        if (tag.contains("HomeHarborName")) this.homeHarborName = tag.getString("HomeHarborName");

        this.tradeRewards.clear();
        if (tag.contains("TradeRewards", Tag.TAG_LIST)) {
            ListTag rewardList = tag.getList("TradeRewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < rewardList.size(); i++) {
                CustomData.parseStack(null, rewardList.getCompound(i))
                        .ifPresent(this.tradeRewards::add);
            }
        }

        if (this.voyageShip) {
            this.setCustomName(Component.translatable("entity.currents_of_trade.sloop"));
            this.setCustomNameVisible(false);
        } else if (this.getCustomName() == null) {
            this.setCustomName(Component.literal(this.isTradeMission ? "Trade Ship" : "Trade Boat"));
            this.setCustomNameVisible(true);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
                this.getX() - 10.0, this.getY() - 3.0, this.getZ() - 10.0,
                this.getX() + 10.0, this.getY() + 20.0, this.getZ() + 10.0
        );
    }
}
