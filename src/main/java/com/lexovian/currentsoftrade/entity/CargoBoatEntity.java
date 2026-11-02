package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CargoBoatEntity extends TradeBoatEntity {

    private static final EntityDataAccessor<Boolean> IS_PLAYER_CONTROLLED =
            SynchedEntityData.defineId(CargoBoatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SAIL_COLOR =
            SynchedEntityData.defineId(CargoBoatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> BANNER_STACK =
            SynchedEntityData.defineId(CargoBoatEntity.class, EntityDataSerializers.ITEM_STACK);

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PLAYER_CONTROLLED, false);
        this.entityData.define(SAIL_COLOR, -1);
        this.entityData.define(BANNER_STACK, ItemStack.EMPTY);
    }

    public boolean isPlayerControlled() {
        return this.entityData.get(IS_PLAYER_CONTROLLED);
    }

    public void setPlayerControlled(boolean playerControlled) {
        this.entityData.set(IS_PLAYER_CONTROLLED, playerControlled);
    }

    public int getSailColor() {
        return this.entityData.get(SAIL_COLOR);
    }

    public void setSailColor(int colorId) {
        this.entityData.set(SAIL_COLOR, colorId);
    }

    public ItemStack getBannerStack() {
        return this.entityData.get(BANNER_STACK);
    }

    public void setBannerStack(ItemStack stack) {
        this.entityData.set(BANNER_STACK, stack.copy());
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
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isPlayerControlled()) {
            InteractionResult customResult = this.tryApplyCustomization(player, player.getItemInHand(hand));
            if (customResult != InteractionResult.PASS) {
                return customResult;
            }
        }

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

    public InteractionResult tryApplyCustomization(Player player, ItemStack held) {
        // 1. Dye Sails (16 DyeColors, Royal Tyrian Purple, or Dragon's Blood Red)
        if (held.getItem() instanceof DyeItem || held.is(CurrentsofTrade.TYRIAN_PURPLE.get()) || held.is(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get())) {
            DyeColor color;
            Component colorName;
            if (held.is(CurrentsofTrade.TYRIAN_PURPLE.get())) {
                color = DyeColor.PURPLE;
                colorName = Component.translatable("color.currents_of_trade.tyrian_purple");
            } else if (held.is(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get())) {
                color = DyeColor.RED;
                colorName = Component.translatable("color.currents_of_trade.dragons_blood");
            } else {
                color = ((DyeItem) held.getItem()).getDyeColor();
                colorName = Component.translatable("color.minecraft." + color.getName());
            }
            if (this.getSailColor() != color.getId()) {
                if (!this.level().isClientSide) {
                    this.setSailColor(color.getId());
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_dyed", colorName), true);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if ((held.is(Items.WATER_BUCKET) || held.is(Items.SPONGE) || held.is(Items.WET_SPONGE)) && this.getSailColor() >= 0) {
            if (!this.level().isClientSide) {
                this.setSailColor(-1);
                this.level().playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.1F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_washed"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (held.getItem() instanceof BannerItem) {
            if (!this.level().isClientSide) {
                if (!this.getBannerStack().isEmpty() && !player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                }
                this.setBannerStack(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_banner_mounted"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (held.is(Items.SHEARS) && !this.getBannerStack().isEmpty()) {
            if (!this.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                }
                this.setBannerStack(ItemStack.EMPTY);
                this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_banner_removed"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    public ItemStack createCustomizedDropStack() {
        ItemStack stack = new ItemStack(this.getDropItem());
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty()) {
            CustomData.update(stack, tag -> {
                tag.putInt("SailColor", this.getSailColor());
                if (!this.getBannerStack().isEmpty()) {
                    tag.put("BannerItem", this.getBannerStack().save(new CompoundTag()));
                }
            });
        }
        return stack;
    }

    public void loadUpgradesFromStack(ItemStack stack) {
        CustomData customData = CustomData.getOrNull(stack);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("SailColor")) {
                this.setSailColor(tag.getInt("SailColor"));
            }
            if (tag.contains("BannerItem")) {
                this.setBannerStack(ItemStack.of(tag.getCompound("BannerItem")));
            }
        }
    }

    @Override
    public void destroy(DamageSource source) {
        this.chestVehicleDestroyed(source, this.level(), this);
        this.kill();
        if (this.isPlayerControlled() && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(this.createCustomizedDropStack());
        }
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
        return Component.translatable("item.currents_of_trade.cargo_boat");
    }

    // --- NBT Serialization ---

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PlayerControlled", this.isPlayerControlled());
        tag.putInt("SailColor", this.getSailColor());
        if (!this.getBannerStack().isEmpty()) {
            tag.put("BannerItem", this.getBannerStack().save(new CompoundTag()));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PlayerControlled")) {
            this.setPlayerControlled(tag.getBoolean("PlayerControlled"));
        }
        if (tag.contains("SailColor")) {
            this.setSailColor(tag.getInt("SailColor"));
        }
        if (tag.contains("BannerItem")) {
            this.setBannerStack(ItemStack.of(tag.getCompound("BannerItem")));
        }
    }
}
