package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    public CargoBoatEntity(EntityType<? extends AbstractBoat> entityType, Level level) {
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
        this.updateParts();
        this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        this.setCustomName(Component.literal("Cargo Ship"));
        this.setCustomNameVisible(true);
    }

    // --- Synched Data & Steering ---

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_PLAYER_CONTROLLED, false);
        builder.define(SAIL_COLOR, -1);
        builder.define(BANNER_STACK, ItemStack.EMPTY);
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
        this.entityData.set(BANNER_STACK, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (entity instanceof Player
                && entity.getBoundingBox().minY < this.getBoundingBox().maxY - 0.15
                && entity.getBoundingBox().intersects(this.getBoundingBox().deflate(0.02))) {
            return false;
        }
        return super.canCollideWith(entity);
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
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this)) {
            return InteractionResult.SUCCESS;
        }
        if (this.isPlayerControlled()) {
            InteractionResult customResult = this.tryApplyCustomization(player, hand);
            if (customResult.consumesAction()) {
                return customResult;
            }
        }
        if (player.isSecondaryUseActive() || this.cargoDelivered) {
            return this.interactWithContainerVehicle(player);
        }

        if (this.isPlayerControlled()) {
            if (!this.level().isClientSide()) {
                return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }

        return this.interactWithContainerVehicle(player);
    }

    public InteractionResult tryApplyCustomization(Player player, InteractionHand hand) {
        if (!this.isPlayerControlled()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return InteractionResult.PASS;

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
                color = held.getOrDefault(DataComponents.DYE, DyeColor.WHITE);
                colorName = Component.translatable("color.minecraft." + color.getName());
            }
            if (this.getSailColor() != color.getId()) {
                if (!this.level().isClientSide()) {
                    this.setSailColor(color.getId());
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.cargo_dyed", colorName));
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 2. Wash Dyed Sails
        if ((held.is(Items.WATER_BUCKET) || held.is(Items.SPONGE) || held.is(Items.WET_SPONGE)) && this.getSailColor() >= 0) {
            if (!this.level().isClientSide()) {
                this.setSailColor(-1);
                this.level().playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.1F);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.cargo_washed"));
            }
            return InteractionResult.SUCCESS;
        }

        // 3. Mount Banner / Pennant ("Flama")
        if (held.getItem() instanceof BannerItem) {
            if (!this.level().isClientSide()) {
                if (!this.getBannerStack().isEmpty() && !player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                }
                this.setBannerStack(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.cargo_banner_mounted"));
            }
            return InteractionResult.SUCCESS;
        }

        // 4. Remove Banner with Shears
        if (held.is(Items.SHEARS) && !this.getBannerStack().isEmpty()) {
            if (!this.level().isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                }
                this.setBannerStack(ItemStack.EMPTY);
                this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.cargo_banner_removed"));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // --- Damage & Drops ---

    public ItemStack createDropStack() {
        ItemStack stack = new ItemStack(this.getTradeDropItem());
        if (this.hasCustomName() && !this.getCustomName().getString().equals("Cargo Ship")) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty()) {
            CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = existing.copyTag();
            if (this.getSailColor() >= 0) {
                tag.putInt("SailColor", this.getSailColor());
            }
            if (!this.getBannerStack().isEmpty()) {
                tag.store("BannerItem", ItemStack.CODEC, this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.getBannerStack());
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isPlayerControlled()) {
            if (this.isInvulnerableToBase(source)) return false;
            if (!this.isRemoved()) {
                Containers.dropContents(level, this, this);
                if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
                    this.spawnAtLocation(level, this.createDropStack());
                }
                this.discard();
                return true;
            }
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public Item getTradeDropItem() {
        return this.isPlayerControlled() ? CurrentsofTrade.CARGO_BOAT_ITEM.get() : Items.AIR;
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("item.currents_of_trade.cargo_boat");
    }

    // --- Serialization ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("PlayerControlled", this.isPlayerControlled());
        output.putInt("SailColor", this.getSailColor());
        if (!this.getBannerStack().isEmpty()) {
            output.store("BannerItem", ItemStack.CODEC, this. getBannerStack());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPlayerControlled(input.getBooleanOr("PlayerControlled", false));
        this.setSailColor(input.getIntOr("SailColor", -1));
        this.setBannerStack(input.read("BannerItem", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }
}
