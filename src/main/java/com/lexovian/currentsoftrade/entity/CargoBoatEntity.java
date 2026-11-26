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
    private static final EntityDataAccessor<Boolean> HAS_TRAWL_NET =
            SynchedEntityData.defineId(CargoBoatEntity.class, EntityDataSerializers.BOOLEAN);

    // --- Constructors ---

    public CargoBoatEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        this.cruiseSpeed = (Config.CARGO_BOAT_SPEED != null) ? Config.CARGO_BOAT_SPEED.get() : 0.9;
        this.setCustomName(Component.translatable("entity.currents_of_trade.cargo_boat"));
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
        this.setCustomName(Component.translatable("entity.currents_of_trade.cargo_boat"));
        this.setCustomNameVisible(true);
    }

    // --- Synched Data & Steering ---

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PLAYER_CONTROLLED, false);
        this.entityData.define(SAIL_COLOR, -1);
        this.entityData.define(BANNER_STACK, ItemStack.EMPTY);
        this.entityData.define(HAS_TRAWL_NET, false);
    }

    public boolean hasTrawlNet() {
        return this.entityData.get(HAS_TRAWL_NET);
    }

    public void setTrawlNet(boolean trawlNet) {
        this.entityData.set(HAS_TRAWL_NET, trawlNet);
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

    @Nullable private Vec3 summonTarget = null;
    private int summonTicks = 0;

    public void summonToward(Vec3 target) {
        if (!this.isPlayerControlled()) return;
        this.summonTarget = target;
        this.summonTicks = 120;
    }

    private void tickSummonGlide() {
        if (this.summonTicks <= 0 || this.summonTarget == null) return;
        if (this.isVehicle() || !this.isPlayerControlled()) {
            this.summonTicks = 0;
            this.summonTarget = null;
            return;
        }
        this.summonTicks--;
        double dx = this.summonTarget.x - this.getX();
        double dz = this.summonTarget.z - this.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist <= 2.3D || this.summonTicks <= 0) {
            Vec3 cur = this.getDeltaMovement();
            this.setDeltaMovement(cur.x * 0.25D, cur.y, cur.z * 0.25D);
            this.summonTicks = 0;
            this.summonTarget = null;
            return;
        }
        double nx = dx / dist;
        double nz = dz / dist;
        net.minecraft.core.BlockPos ahead = net.minecraft.core.BlockPos.containing(this.getX() + nx * 1.5D, this.getY(), this.getZ() + nz * 1.5D);
        if (!this.level().getFluidState(ahead).is(net.minecraft.tags.FluidTags.WATER)
                && !this.level().getFluidState(ahead.below()).is(net.minecraft.tags.FluidTags.WATER)) {
            this.summonTicks = 0;
            this.summonTarget = null;
            return;
        }
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        this.setYRot(this.getYRot() + Mth.clamp(diff, -7.0F, 7.0F));
        double speed = Math.min(0.20D, Math.max(0.08D, dist * 0.05D));
        Vec3 cur = this.getDeltaMovement();
        this.setDeltaMovement(nx * speed, cur.y, nz * speed);
        this.hasImpulse = true;
    }

    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        if (this.entityData != null && this.isPlayerControlled()) {
            double half = 1.225D;
            return new net.minecraft.world.phys.AABB(
                    this.getX() - half, this.getY(), this.getZ() - half,
                    this.getX() + half, this.getY() + 0.82D, this.getZ() + half
            );
        }
        return super.makeBoundingBox();
    }

    @Override
    public void tick() {
        if (this.isPlayerControlled()) {
            double prevShipX = this.getX();
            double prevShipY = this.getY();
            double prevShipZ = this.getZ();
            float prevYaw = this.getYRot();
            super.tick();

            if (!this.level().isClientSide) {
                this.tickSummonGlide();
                if (this.hasTrawlNet()) {
                    com.lexovian.currentsoftrade.item.TrawlNetItem.tickShipTrawlNet(this, this);
                }
            }

            // Heavy steering feel for cargo ship: 8% damping (vs 14% on Sloop)
            float rawTurnDelta = Mth.wrapDegrees(this.getYRot() - prevYaw);
            if (Math.abs(rawTurnDelta) > 0.001F) {
                float dampedTurn = rawTurnDelta * 0.08F;
                this.setYRot(prevYaw + dampedTurn);
                this.yRotO = prevYaw;
            }

            // Heavy cargo displacement speed limiter: ~35% slower than Sloop (+20% with Captain's Tricorne, +15% with Trade Winds)
            boolean hasTricorne = this.getControllingPassenger() instanceof Player p
                    && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(CurrentsofTrade.CAPTAINS_TRICORNE.get());
            boolean hasTradeWind = com.lexovian.currentsoftrade.world.market.TradeWinds.hasFavorableTradeWind(this.level(), this.getYRot());
            Vec3 delta = this.getDeltaMovement();
            if ((hasTricorne || hasTradeWind) && delta.horizontalDistanceSqr() > 0.002D) {
                double boost = (hasTricorne ? 1.02D : 1.0D) * (hasTradeWind ? 1.015D : 1.0D);
                delta = new Vec3(delta.x * boost, delta.y, delta.z * boost);
                this.setDeltaMovement(delta);
            }
            double horizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double maxSpeed = (hasTricorne ? 0.264D : 0.22D) * (hasTradeWind ? 1.15D : 1.0D);
            if (horizontalSpeed > maxSpeed) {
                double scale = maxSpeed / horizontalSpeed;
                this.setDeltaMovement(delta.x * scale, delta.y, delta.z * scale);
            }
            this.setBoundingBox(this.makeBoundingBox());
            this.updateParts();
            ShipPart.supportDeckEntities(this, this.getShipParts(), prevShipX, prevShipY, prevShipZ, prevYaw);
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
            InteractionResult customResult = this.tryApplyCustomization(player, hand);
            if (customResult.consumesAction()) {
                return customResult;
            }
        } else {
            InteractionResult harpoonRes = this.tryApplyHarpoonCustomization(player, hand);
            if (harpoonRes.consumesAction()) {
                return harpoonRes;
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
                    SloopEntity.awardFlagshipAdvancement(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        // 2. Wash Dyed Sails
        if ((held.is(Items.WATER_BUCKET) || held.is(Items.SPONGE) || held.is(Items.WET_SPONGE)) && this.getSailColor() >= 0) {
            if (!this.level().isClientSide) {
                this.setSailColor(-1);
                this.level().playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.1F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_washed"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 3. Mount Banner / Pennant ("Flama")
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
                SloopEntity.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 4. Mount or Remove Swivel Harpoon (or Remove Banner with Shears)
        InteractionResult harpoonRes = this.tryApplyHarpoonCustomization(player, hand);
        if (harpoonRes.consumesAction()) {
            return harpoonRes;
        }

        if (held.is(CurrentsofTrade.TRAWL_NET.get()) && !this.hasTrawlNet()) {
            if (!this.level().isClientSide) {
                this.setTrawlNet(true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.trawl_net_mounted"), true);
                SloopEntity.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (held.is(Items.SHEARS) && ((this.hasTrawlNet() && player.isSecondaryUseActive()) || !this.getBannerStack().isEmpty())) {
            if (!this.level().isClientSide) {
                if (this.hasTrawlNet() && player.isSecondaryUseActive()) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(CurrentsofTrade.TRAWL_NET.get()));
                    }
                    this.setTrawlNet(false);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.trawl_net_removed"), true);
                } else {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                    }
                    this.setBannerStack(ItemStack.EMPTY);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_banner_removed"), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    public ItemStack createCustomizedDropStack() {
        ItemStack stack = new ItemStack(this.getDropItem());
        if (this.hasCustomName()
                && !this.getCustomName().equals(Component.translatable("entity.currents_of_trade.cargo_boat"))
                && !this.getCustomName().getString().equals("Cargo Ship")) {
            stack.setHoverName(this.getCustomName());
        }
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty() || this.hasSwivelHarpoon() || this.hasTrawlNet()) {
            CustomData.update(stack, tag -> {
                if (this.getSailColor() >= 0) {
                    tag.putInt("SailColor", this.getSailColor());
                }
                if (!this.getBannerStack().isEmpty()) {
                    tag.put("BannerItem", this.getBannerStack().save(new CompoundTag()));
                }
                if (this.hasSwivelHarpoon()) {
                    tag.putBoolean("SwivelHarpoon", true);
                }
                if (this.hasTrawlNet()) {
                    tag.putBoolean("TrawlNet", true);
                }
            });
        }
        return stack;
    }

    public void loadUpgradesFromStack(ItemStack stack) {
        if (stack.hasCustomHoverName()) {
            this.setCustomName(stack.getHoverName());
        }
        CustomData customData = CustomData.getOrNull(stack);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("SailColor")) {
                this.setSailColor(tag.getInt("SailColor"));
            }
            if (tag.contains("BannerItem")) {
                this.setBannerStack(ItemStack.of(tag.getCompound("BannerItem")));
            }
            if (tag.contains("SwivelHarpoon")) {
                this.setSwivelHarpoon(tag.getBoolean("SwivelHarpoon"));
            }
            if (tag.contains("TrawlNet")) {
                this.setTrawlNet(tag.getBoolean("TrawlNet"));
            }
        }
    }

    @Override
    public void destroy(DamageSource source) {
        if (!this.isPlayerControlled()) {
            super.destroy(source);
            return;
        }
        this.chestVehicleDestroyed(source, this.level(), this);
        this.kill();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(this.createCustomizedDropStack());
        }
    }

    // --- Damage & Drops ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isPlayerControlled()) {
            if (this.isInvulnerableTo(source)) return false;
            if (this.level().isClientSide || this.isRemoved()) return true;
            this.setHurtDir(-this.getHurtDir());
            this.setHurtTime(10);
            this.markHurt();
            float effectiveAmount = this.drydockReinforced ? amount * 0.5F : amount;
            this.setDamage(this.getDamage() + effectiveAmount * 10.0F);
            boolean instantBreak = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
            if (instantBreak || this.getDamage() > 40.0F) {
                this.ejectPassengers();
                if (instantBreak) {
                    this.chestVehicleDestroyed(source, this.level(), this);
                    this.discard();
                } else {
                    this.destroy(source);
                }
            }
            return true;
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
        tag.putBoolean("TrawlNet", this.hasTrawlNet());
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
        } else {
            this.setBannerStack(ItemStack.EMPTY);
        }
        if (tag.contains("TrawlNet")) {
            this.setTrawlNet(tag.getBoolean("TrawlNet"));
        }
    }
}
