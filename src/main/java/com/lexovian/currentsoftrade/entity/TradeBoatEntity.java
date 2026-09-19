package com.lexovian.currentsoftrade.entity;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TradeBoatEntity extends Boat implements HasCustomInventoryScreen, ContainerEntity {

    public static final int CARGO_SLOTS = 54;
    private static final double CARGO_MAX_SPEED = 1.0;
    private static final int[] DETOUR_OFFSETS_DEG = { 20, 32, 45, 60, 75, 90, 105, 120, 135 };

    protected BlockPos targetPos;
    private String harborName = "Unknown Port";
    private int idleTicks = 0;
    protected double cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
    protected boolean arrived = false;

    // --- Navigation & Obstacle Avoidance State ---
    private int avoidanceSide = 0;
    private int stuckTicks = 0;
    @Nullable private Vec3 detourWaypoint = null;
    private int detourTicks = 0;
    private boolean lastDirectClear = true;

    // --- Cargo & Trade Hold ---
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(CARGO_SLOTS, ItemStack.EMPTY);
    @Nullable private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;
    protected boolean cargoDelivered = false;
    protected int emptyTicks = 0;

    // --- Trade Mission State ---
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
    protected int blockedTicks = 0;

    private boolean voyageShip = false;

    public final ShipPart<TradeBoatEntity> sternPart;
    public final ShipPart<TradeBoatEntity> prowPart;
    private final ShipPart<?>[] subEntities;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_WANDERING_TRADER_SHIP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TradeBoatEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

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
        this.setCustomName(Component.literal("Trade Ship"));
        this.setCustomNameVisible(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_WANDERING_TRADER_SHIP, false);
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
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
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
    public float maxUpStep() {
        return 0.0F;
    }

    public void setVoyageTarget(BlockPos target, String harborName) {
        this.targetPos = target;
        this.harborName = harborName;
        if (target != null) {
            double dx = (target.getX() + 0.5) - this.getX();
            double dz = (target.getZ() + 0.5) - this.getZ();
            float initialYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.setYRot(initialYaw);
            this.setYHeadRot(initialYaw);
            this.yRotO = initialYaw;
        }
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
        if (voyageShip) {
            this.cruiseSpeed = (Config.FAST_TRAVEL_BOAT_SPEED != null) ? Config.FAST_TRAVEL_BOAT_SPEED.get() : 1.8;
        } else {
            this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        }
    }

    public boolean isVoyageShip() {
        return this.voyageShip;
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
        if (this.targetPos != null && !this.cargoDelivered && !this.level().isClientSide) {
            return false;
        }
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
                this.destroy(source);
                return true;
            }
            if (this.cargoDelivered) {
                this.destroy(source);
                return true;
            }
        }
        return false;
    }

    public void destroy(DamageSource source) {
        this.destroy(this.getDropItem());
    }

    public void destroy(Item dropItem) {
        Containers.dropContents(this.level(), this, this);
        if (dropItem != Items.AIR && this.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(dropItem);
        }
        this.discard();
    }

    @Override
    public Item getDropItem() {
        return CurrentsofTrade.TRADE_BOAT_ITEM != null && CurrentsofTrade.TRADE_BOAT_ITEM.get() != null
                ? CurrentsofTrade.TRADE_BOAT_ITEM.get()
                : Items.OAK_BOAT;
    }

    @Override
    public Component getTypeName() {
        return Component.literal(this.isTradeMission ? "Trade Ship" : "Trade Boat");
    }

    @Override
    public Component getDisplayName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getTypeName();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
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
    @Nullable @Override public ResourceKey<LootTable> getLootTable() { return this.lootTable; }
    @Override public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) { this.lootTable = lootTable; }
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
            for (BlockPos p : BlockPos.betweenClosed(this.homeHarborPos.offset(-4, -3, -4), this.homeHarborPos.offset(4, 1, 4))) {
                if (serverLevel.getFluidState(p).is(FluidTags.WATER) && serverLevel.getBlockState(p.above()).isAir()) {
                    returnPos = p.immutable();
                    break;
                }
            }
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
        double half = 1.30;
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
        if (this.cargoDelivered) {
            this.updateParts();
            if (this.level().isClientSide) return;
            this.setDeltaMovement(0.0, 0.0, 0.0);

            if (this.isEmpty()) {
                this.emptyTicks++;
                if (this.emptyTicks >= 20) {
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
            return;
        }

        if (!this.level().isClientSide && !this.voyageShip && this.level() instanceof ServerLevel serverLevel) {
            ChunkPos currentChunk = new ChunkPos(this.blockPosition());
            if (this.lastCenterChunk == null || !this.lastCenterChunk.equals(currentChunk)) {
                this.updateForcedChunks(serverLevel, currentChunk);
                this.lastCenterChunk = currentChunk;
            }
        }

        super.tick();
        this.setBoundingBox(this.makeBoundingBox());
        this.updateParts();

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

            double dx = (this.targetPos.getX() + 0.5) - this.getX();
            double dz = (this.targetPos.getZ() + 0.5) - this.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            double horizontalDist = Math.sqrt(horizontalDistSq);

            if (horizontalDist <= 6.0) {
                this.arriveAtDestination(passenger);
                return;
            }

            double directAngle = Mth.atan2(dz, dx);
            double directCheckDist = Math.min(50.0, horizontalDist);
            double corridorBuffer = (horizontalDist < 16.0) ? Math.max(0.5, (horizontalDist - 4.0) / 4.0) : 3.0;

            boolean shouldScanDirect = (this.tickCount % 8 == 0)
                    || (this.detourWaypoint != null)
                    || (this.stuckTicks > 0)
                    || (horizontalDist < 30.0);

            boolean directPathClear;
            if (shouldScanDirect) {
                directPathClear = isCorridorClear(this.level(), directAngle, directCheckDist, corridorBuffer);
                this.lastDirectClear = directPathClear;
            } else {
                directPathClear = this.lastDirectClear;
            }

            double chosenAngle;
            double currentSpeed = this.cruiseSpeed;

            if (directPathClear) {
                this.detourWaypoint = null;
                this.avoidanceSide = 0;
                this.detourTicks = 0;
                chosenAngle = directAngle;

                if (horizontalDist < 25.0) {
                    currentSpeed = Math.max(0.4, this.cruiseSpeed * (horizontalDist / 25.0));
                }
            } else {
                this.detourTicks++;

                boolean needNewWaypoint = (this.detourWaypoint == null);
                if (!needNewWaypoint) {
                    double distToWp = this.position().distanceTo(this.detourWaypoint);
                    if (distToWp < 6.0 || this.detourTicks % 16 == 0) {
                        needNewWaypoint = true;
                    } else {
                        double wpAngle = Mth.atan2(this.detourWaypoint.z - this.getZ(), this.detourWaypoint.x - this.getX());
                        if (!isLineClearOfLand(this.level(), wpAngle, Math.min(distToWp, 20.0))) {
                            needNewWaypoint = true;
                        }
                    }
                }

                if (needNewWaypoint) {
                    double detourAngle = findBestDetourAngle(directAngle, corridorBuffer);
                    double wpDist = Math.min(32.0, Math.max(12.0, horizontalDist));
                    this.detourWaypoint = new Vec3(
                            this.getX() + Math.cos(detourAngle) * wpDist,
                            this.getY(),
                            this.getZ() + Math.sin(detourAngle) * wpDist
                    );
                }

                chosenAngle = Mth.atan2(this.detourWaypoint.z - this.getZ(), this.detourWaypoint.x - this.getX());

                double distToLand = getDistanceToLand(this.level(), chosenAngle, 28.0);
                if (distToLand < 10.0) {
                    currentSpeed = Math.min(0.6, this.cruiseSpeed * 0.7);
                } else if (distToLand < 20.0) {
                    currentSpeed = Math.max(0.4, this.cruiseSpeed * (distToLand / 20.0));
                } else {
                    currentSpeed = Math.min(this.cruiseSpeed, 1.4);
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

            float maxTurnRate;
            if (this.tickCount <= 5) {
                maxTurnRate = 180.0F;
            } else if (this.stuckTicks > 0) {
                maxTurnRate = 35.0F;
            } else if (this.detourWaypoint != null) {
                double distToLand = getDistanceToLand(this.level(), chosenAngle, 20.0);
                maxTurnRate = (distToLand < 12.0) ? 22.0F : 15.0F;
            } else {
                maxTurnRate = 9.0F;
            }

            float newYaw = currentYaw + Mth.clamp(yawDiff, -maxTurnRate, maxTurnRate);
            this.setYRot(newYaw);
            this.setYHeadRot(newYaw);
            this.yRotO = newYaw;

            double moveHeadingAngle = (newYaw + 90.0F) * (Math.PI / 180.0);
            double normX = Math.cos(moveHeadingAngle);
            double normZ = Math.sin(moveHeadingAngle);
            double velX = normX * currentSpeed;
            double velZ = normZ * currentSpeed;

            double bowDist = 2.2;
            double bowX = this.getX() + normX * bowDist;
            double bowZ = this.getZ() + normZ * bowDist;
            double leftBowX = bowX - normZ * 0.9;
            double leftBowZ = bowZ + normX * 0.9;
            double rightBowX = bowX + normZ * 0.9;
            double rightBowZ = bowZ - normX * 0.9;

            boolean bowBlocked = !isNavigableWater(this.level(), bowX, bowZ)
                    || !isNavigableWater(this.level(), leftBowX, leftBowZ)
                    || !isNavigableWater(this.level(), rightBowX, rightBowZ);

            if (bowBlocked || !inWaterCurrent || this.horizontalCollision) {
                velX = 0.0;
                velZ = 0.0;
                double escapeAngle = findOpenWaterEscapeAngle();
                velX += Math.cos(escapeAngle) * 0.35;
                velZ += Math.sin(escapeAngle) * 0.35;
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
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                        this.getX() - normX * 0.8, this.getY() + 0.1, this.getZ() - normZ * 0.8,
                        5, 0.3, 0.1, 0.3, 0.05);
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        this.getX() - normX * 0.5, this.getY(), this.getZ() - normZ * 0.5,
                        3, 0.2, 0.1, 0.2, 0.02);
            }

            if (passenger instanceof Player player && this.tickCount % 4 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.voyage_hud", this.harborName, (int) horizontalDist),
                        true
                );
            }
        }

        if (!this.level().isClientSide && !this.cargoDelivered && !this.voyageShip && this.level() instanceof ServerLevel serverLevel) {
            if (this.stuckTicks > 0 || (this.detourTicks > 80 && this.getDeltaMovement().horizontalDistanceSqr() < 0.005)) {
                this.blockedTicks++;
                if (this.blockedTicks >= MAX_BLOCKED_TICKS) {
                    this.abortAndReturnCargo(serverLevel);
                }
            } else if (this.getDeltaMovement().horizontalDistanceSqr() > 0.04) {
                this.blockedTicks = Math.max(0, this.blockedTicks - 1);
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
            this.setVoyageTarget(this.homeHarborPos, this.homeHarborName);
            this.setCustomName(Component.literal("Trade Ship"));
            this.setCustomNameVisible(true);
            return;
        }

        if (this.cargoDelivered) return;
        this.cargoDelivered = true;
        this.setDeltaMovement(0.0, 0.0, 0.0);
        this.releaseAllForcedChunks();

        if (this.targetPos != null) {
            this.level().playSound(null, this.targetPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (this.level().getBlockEntity(this.targetPos) instanceof AnchorPointBlockEntity targetAnchor) {
                targetAnchor.triggerTravelCooldown();
            }
        }

        Component senderMsg;
        Component receiverMsg;
        if (this.isTradeMission && this.missionPhase == TradeMissionPhase.RETURNING) {
            senderMsg   = Component.translatable("message.currents_of_trade.trade_arrived_home",     this.homeHarborName);
            receiverMsg = Component.translatable("message.currents_of_trade.trade_docked_at_harbor", this.homeHarborName);
        } else {
            senderMsg   = Component.translatable("message.currents_of_trade.cargo_arrived_sender",   this.getHarborName());
            receiverMsg = Component.translatable("message.currents_of_trade.cargo_arrived",          this.getHarborName());
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

    // --- Pathfinding & Navigable Water Checks ---

    private double findBestDetourAngle(double directAngle, double bufferMargin) {
        int side = this.avoidanceSide;
        if (side == 0) {
            double portDist = getDistanceToLand(this.level(), directAngle - Math.toRadians(45.0), 30.0);
            double stbdDist = getDistanceToLand(this.level(), directAngle + Math.toRadians(45.0), 30.0);
            side = (portDist >= stbdDist) ? -1 : 1;
        }

        double bestAngle = directAngle;
        double bestScore = -1.0;
        int chosenSide = 0;

        for (int offsetDeg : DETOUR_OFFSETS_DEG) {
            double offsetRad = Math.toRadians(offsetDeg);
            for (int s : new int[]{ side, -side }) {
                double candidateAngle = directAngle + s * offsetRad;
                if (isCorridorClear(this.level(), candidateAngle, 24.0, bufferMargin)) {
                    double clearRun = getDistanceToLand(this.level(), candidateAngle, 40.0);
                    double score = clearRun - (offsetDeg * 0.15);
                    if (score > bestScore) {
                        bestScore = score;
                        bestAngle = candidateAngle;
                        chosenSide = s;
                    }
                }
            }
            if (bestScore > 0.0) break;
        }

        if (chosenSide != 0) this.avoidanceSide = chosenSide;
        return (bestScore > 0.0) ? bestAngle : findOpenWaterEscapeAngle();
    }

    private boolean isLineClearOfLand(Level level, double angle, double maxDist) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);

        for (double d = 2.0; d <= maxDist; d += 2.0) {
            double checkX = this.getX() + cosA * d;
            double checkZ = this.getZ() + sinA * d;
            if (!isNavigableWater(level, checkX, checkZ)) return false;
        }
        return true;
    }

    private boolean isCorridorClear(Level level, double angle, double maxDist, double bufferMargin) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double normalX = -sinA;
        double normalZ = cosA;

        for (double d = 2.0; d <= maxDist; d += 2.0) {
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

        for (double d = 2.0; d <= maxDist; d += 2.0) {
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
        if (this.targetPos != null) {
            double destDx = x - (this.targetPos.getX() + 0.5);
            double destDz = z - (this.targetPos.getZ() + 0.5);
            if (destDx * destDx + destDz * destDz <= 36.0) {
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
        this.addChestVehicleSaveData(tag, this.registryAccess());

        if (this.targetPos != null) {
            tag.putInt("TargetX", this.targetPos.getX());
            tag.putInt("TargetY", this.targetPos.getY());
            tag.putInt("TargetZ", this.targetPos.getZ());
        }
        tag.putString("HarborName", this.harborName);
        tag.putDouble("CruiseSpeed", this.cruiseSpeed);
        tag.putInt("AvoidanceSide", this.avoidanceSide);
        tag.putBoolean("IsWanderingTraderShip", this.isWanderingTraderShip());
        tag.putBoolean("VoyageShip", this.voyageShip);

        tag.putBoolean("CargoDelivered", this.cargoDelivered);
        tag.putInt("EmptyTicks", this.emptyTicks);
        tag.putInt("BlockedTicks", this.blockedTicks);
        if (this.senderUUID != null) tag.putUUID("SenderUUID", this.senderUUID);
        if (this.senderName != null) tag.putString("SenderName", this.senderName);
        tag.putBoolean("IsTradeMission", this.isTradeMission);
        tag.putInt("MissionPhase", this.missionPhase.ordinal());
        if (this.homeHarborPos != null) tag.putLong("HomeHarborPos", this.homeHarborPos.asLong());
        if (this.homeHarborName != null) tag.putString("HomeHarborName", this.homeHarborName);

        if (!this.tradeRewards.isEmpty()) {
            ListTag rewardList = new ListTag();
            for (ItemStack reward : this.tradeRewards) {
                rewardList.add(reward.save(this.registryAccess()));
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

        this.readChestVehicleSaveData(tag, this.registryAccess());

        if (tag.contains("TargetX")) {
            this.targetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        if (tag.contains("HarborName")) this.harborName = tag.getString("HarborName");
        if (tag.contains("AvoidanceSide")) this.avoidanceSide = tag.getInt("AvoidanceSide");
        if (tag.contains("IsWanderingTraderShip")) this.setWanderingTraderShip(tag.getBoolean("IsWanderingTraderShip"));
        if (tag.contains("VoyageShip")) this.voyageShip = tag.getBoolean("VoyageShip");

        this.cargoDelivered = tag.getBoolean("CargoDelivered");
        if (tag.contains("EmptyTicks")) this.emptyTicks = tag.getInt("EmptyTicks");
        if (tag.contains("BlockedTicks")) this.blockedTicks = tag.getInt("BlockedTicks");
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
                ItemStack.parse(this.registryAccess(), rewardList.getCompound(i))
                        .ifPresent(this.tradeRewards::add);
            }
        }

        if (this.getCustomName() == null) {
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
