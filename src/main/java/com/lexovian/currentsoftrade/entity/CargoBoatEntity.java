package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CargoBoatEntity extends TradeBoatEntity {

    private static final EntityDataAccessor<Boolean> IS_PLAYER_CONTROLLED =
            SynchedEntityData.defineId(CargoBoatEntity.class, EntityDataSerializers.BOOLEAN);

    // --- Constructors ---

    public CargoBoatEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        this.setCustomName(Component.literal("Cargo Ship"));
        this.setCustomNameVisible(true);
    }

    public CargoBoatEntity(Level level, double x, double y, double z) {
        super(CurrentsofTrade.CARGO_BOAT.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        this.setCustomName(Component.literal("Cargo Ship"));
        this.setCustomNameVisible(true);
    }

    // --- Synched Data & Steering ---

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_PLAYER_CONTROLLED, false);
    }

    public boolean isPlayerControlled() {
        return this.entityData.get(IS_PLAYER_CONTROLLED);
    }

    public void setPlayerControlled(boolean playerControlled) {
        this.entityData.set(IS_PLAYER_CONTROLLED, playerControlled);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (this.isPlayerControlled()) {
            Entity first = this.getFirstPassenger();
            if (first instanceof Player player) {
                return player;
            }
        }
        return super.getControllingPassenger();
    }

    // --- Tick Override (Player Control Physics) ---

    @Override
    public void tick() {
        if (this.isPlayerControlled()) {
            float prevYaw = this.getYRot();
            super.tick();

            // Heavy steering feel for cargo ship: 8% damping (vs 14% on Sloop)
            float rawTurnDelta = Mth.wrapDegrees(this.getYRot() - prevYaw);
            if (Math.abs(rawTurnDelta) > 0.001F) {
                float dampedTurn = rawTurnDelta * 0.08F;
                this.setYRot(prevYaw + dampedTurn);
                this.yRotO = prevYaw;
            }

            // Heavy cargo displacement speed limiter: ~35% slower than Sloop
            Vec3 delta = this.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double maxSpeed = 0.22D;
            if (horizontalSpeed > maxSpeed) {
                double scale = maxSpeed / horizontalSpeed;
                this.setDeltaMovement(delta.x * scale, delta.y, delta.z * scale);
            }
            this.updateParts();
            return;
        }

        super.tick();
    }

    // --- Interaction ---

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.cargoDelivered) {
            return this.interactWithContainerVehicle(player);
        }

        if (this.isPlayerControlled()) {
            if (!this.level().isClientSide) {
                return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }

        return this.interactWithContainerVehicle(player);
    }

    // --- Damage & Drops ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isPlayerControlled()) {
            if (this.isInvulnerableTo(source)) return false;
            if (!this.level().isClientSide && !this.isRemoved()) {
                this.destroy(source);
                return true;
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public Item getDropItem() {
        return this.isPlayerControlled() ? CurrentsofTrade.CARGO_BOAT_ITEM.get() : Items.AIR;
    }

    @Override
    public Component getTypeName() {
        return Component.literal("Cargo Ship");
    }

    // --- NBT Serialization ---

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PlayerControlled", this.isPlayerControlled());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PlayerControlled")) {
            this.setPlayerControlled(tag.getBoolean("PlayerControlled"));
        }
    }
}
