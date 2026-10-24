package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Player-drivable sailing ship with custom 3D model, quarterdeck helm seating,
 * 18-slot Captain's Hold, and modular vessel customization (dyed sails, banner heraldry,
 * bow lantern, and copper-sheathed hull).
 */
public class SloopEntity extends Boat implements HasCustomInventoryScreen, ContainerEntity {

    public static final int SLOOP_CARGO_SLOTS = 18;

    // --- Multi-Part Hull Hitboxes ---
    public final ShipPart<SloopEntity> sternPart;
    public final ShipPart<SloopEntity> prowPart;
    private final ShipPart<?>[] subEntities;

    // --- Captain's Hold Inventory ---
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(SLOOP_CARGO_SLOTS, ItemStack.EMPTY);
    @Nullable
    private ResourceLocation lootTable;
    private long lootTableSeed;

    public SloopEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
    }

    public SloopEntity(Level level, double x, double y, double z) {
        super(CurrentsofTrade.SLOOP.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
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
    public PartEntity<?>[] getParts() {
        return this.subEntities;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy() && !this.isMerchantShip()) {
            Containers.dropContents(this.level(), this, this);
        }
        super.remove(reason);
        for (var part : this.subEntities) {
            part.remove(reason);
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        // Compact 2.4m x 2.4m central waist bounding box (around main deck & mast)
        // Perfectly fits within the 2.7m hull beam at ANY angle (even 45-degree diagonal!)
        double half = 1.20;
        return new AABB(
                this.getX() - half, this.getY(), this.getZ() - half,
                this.getX() + half, this.getY() + 0.85, this.getZ() + half
        );
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // Broad culling box matching the grand 2.4x scale model (16m mast height, 14m length, 10m beam)
        return new AABB(
                this.getX() - 10.0, this.getY() - 3.0, this.getZ() - 10.0,
                this.getX() + 10.0, this.getY() + 20.0, this.getZ() + 10.0
        );
    }

    // --- Synched Customization & State Data ---

    private static final EntityDataAccessor<Boolean> IS_MERCHANT_SHIP =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SAIL_COLOR =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> BANNER_STACK =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> BOW_LANTERN_TYPE =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COPPER_HULL =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);

    public boolean isMerchantShip() {
        return this.entityData.get(IS_MERCHANT_SHIP);
    }

    public void setMerchantShip(boolean merchantShip) {
        this.entityData.set(IS_MERCHANT_SHIP, merchantShip);
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

    /** 0 = none, 1 = Lantern, 2 = Soul Lantern */
    public int getBowLanternType() {
        return this.entityData.get(BOW_LANTERN_TYPE);
    }

    public void setBowLanternType(int lanternType) {
        this.entityData.set(BOW_LANTERN_TYPE, Mth.clamp(lanternType, 0, 2));
    }

    public boolean hasCopperHull() {
        return this.entityData.get(COPPER_HULL);
    }

    public void setCopperHull(boolean copperHull) {
        this.entityData.set(COPPER_HULL, copperHull);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_MERCHANT_SHIP, false);
        this.entityData.define(SAIL_COLOR, -1);
        this.entityData.define(BANNER_STACK, ItemStack.EMPTY);
        this.entityData.define(BOW_LANTERN_TYPE, 0);
        this.entityData.define(COPPER_HULL, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsMerchantShip", this.isMerchantShip());
        tag.putInt("SailColor", this.getSailColor());
        if (!this.getBannerStack().isEmpty()) {
            tag.put("BannerItem", this.getBannerStack().save(new CompoundTag()));
        }
        tag.putInt("BowLanternType", this.getBowLanternType());
        tag.putBoolean("CopperHull", this.hasCopperHull());
        this.addChestVehicleSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("IsMerchantShip")) {
            this.setMerchantShip(tag.getBoolean("IsMerchantShip"));
        }
        if (tag.contains("SailColor")) {
            this.setSailColor(tag.getInt("SailColor"));
        }
        if (tag.contains("BannerItem")) {
            this.setBannerStack(ItemStack.of(tag.getCompound("BannerItem")));
        } else {
            this.setBannerStack(ItemStack.EMPTY);
        }
        if (tag.contains("BowLanternType")) {
            this.setBowLanternType(tag.getInt("BowLanternType"));
        }
        if (tag.contains("CopperHull")) {
            this.setCopperHull(tag.getBoolean("CopperHull"));
        }
        this.readChestVehicleSaveData(tag);
    }

    public ItemStack createDropStack() {
        ItemStack stack = new ItemStack(this.getDropItem());
        if (this.hasCustomName()) {
            stack.setHoverName(this.getCustomName());
        }
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty()
                || this.getBowLanternType() > 0 || this.hasCopperHull()) {
            CustomData.update(stack, tag -> {
                tag.putInt("SailColor", this.getSailColor());
                if (!this.getBannerStack().isEmpty()) {
                    tag.put("BannerItem", this.getBannerStack().save(new CompoundTag()));
                }
                tag.putInt("BowLanternType", this.getBowLanternType());
                tag.putBoolean("CopperHull", this.hasCopperHull());
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
            if (tag.contains("BowLanternType")) {
                this.setBowLanternType(tag.getInt("BowLanternType"));
            }
            if (tag.contains("CopperHull")) {
                this.setCopperHull(tag.getBoolean("CopperHull"));
            }
        }
    }

    private void awardFlagshipAdvancement(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            net.minecraft.advancements.Advancement adv = serverPlayer.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation(CurrentsofTrade.MODID, "flagship_armada"));
            if (adv != null) {
                for (String criterion : adv.getCriteria().keySet()) {
                    serverPlayer.getAdvancements().award(adv, criterion);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // If this is the Wandering Trader's ship, players cannot board it.
        // Instead, right-clicking opens the trader's trading screen if sailor is present!
        if (this.isMerchantShip()) {
            for (Entity p : this.getPassengers()) {
                if (p instanceof WanderingSailorEntity sailor) {
                    return sailor.mobInteract(player, hand);
                }
            }
            return InteractionResult.PASS;
        }

        // Sneak + Right-Click opens the 18-slot Captain's Hold
        if (player.isSecondaryUseActive()) {
            return this.interactWithContainerVehicle(player);
        }

        ItemStack held = player.getItemInHand(hand);

        // 1. Dye Sails (16 DyeColors)
        if (held.getItem() instanceof DyeItem dyeItem) {
            DyeColor color = dyeItem.getDyeColor();
            if (this.getSailColor() != color.getId()) {
                if (!this.level().isClientSide) {
                    this.setSailColor(color.getId());
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_dyed",
                            Component.translatable("color.minecraft." + color.getName())), true);
                    this.awardFlagshipAdvancement(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        // 2. Wash Dyed Sails (Water Bucket or Sponge)
        if ((held.is(Items.WATER_BUCKET) || held.is(Items.SPONGE) || held.is(Items.WET_SPONGE)) && this.getSailColor() >= 0) {
            if (!this.level().isClientSide) {
                this.setSailColor(-1);
                this.level().playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.1F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_washed"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 3. Mount Banner Heraldry on Main Sail
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
                player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_banner_mounted"), true);
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 4. Remove Banner or Bow Lantern with Shears
        if (held.is(Items.SHEARS) && (!this.getBannerStack().isEmpty() || this.getBowLanternType() > 0)) {
            if (!this.level().isClientSide) {
                if (!this.getBannerStack().isEmpty()) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                    }
                    this.setBannerStack(ItemStack.EMPTY);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_banner_removed"), true);
                } else if (this.getBowLanternType() > 0) {
                    if (!player.getAbilities().instabuild) {
                        Item lanternItem = this.getBowLanternType() == 2 ? Items.SOUL_LANTERN : Items.LANTERN;
                        player.getInventory().placeItemBackInInventory(new ItemStack(lanternItem));
                    }
                    this.setBowLanternType(0);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.LANTERN_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_lantern_removed"), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 5. Mount Bow Lantern or Soul Lantern
        if (held.is(Items.LANTERN) || held.is(Items.SOUL_LANTERN)) {
            int newType = held.is(Items.SOUL_LANTERN) ? 2 : 1;
            if (this.getBowLanternType() != newType) {
                if (!this.level().isClientSide) {
                    if (this.getBowLanternType() > 0 && !player.getAbilities().instabuild) {
                        Item prevLantern = this.getBowLanternType() == 2 ? Items.SOUL_LANTERN : Items.LANTERN;
                        player.getInventory().placeItemBackInInventory(new ItemStack(prevLantern));
                    }
                    this.setBowLanternType(newType);
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.LANTERN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_lantern_mounted",
                            held.getHoverName()), true);
                    this.awardFlagshipAdvancement(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        // 6. Apply Copper-Sheathed Hull Upgrade
        if ((held.is(Items.COPPER_BLOCK) || held.is(Items.WAXED_COPPER_BLOCK)) && !this.hasCopperHull()) {
            if (!this.level().isClientSide) {
                this.setCopperHull(true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.25F);
                this.level().playSound(null, this.blockPosition(), SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.sloop_copper_sheathed"), true);
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.interact(player, hand);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.isMerchantShip()) {
            player.openMenu(this);
        }
    }

    // --- ContainerEntity Implementation (18-Slot Captain's Hold) ---

    @Override public NonNullList<ItemStack> getItemStacks() { return this.itemStacks; }
    @Override public void clearItemStacks() { this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); }
    @Override public int getContainerSize() { return SLOOP_CARGO_SLOTS; }
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
        return new ChestMenu(MenuType.GENERIC_9x2, containerId, playerInventory, this, 2);
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.hasCopperHull() && !(source.getEntity() instanceof Player p && p.getAbilities().instabuild)) {
            amount *= 0.5F;
        }
        return super.hurt(source, amount);
    }

    @Override
    public Item getDropItem() {
        // Merchant ship drops nothing when broken or destroyed!
        return this.isMerchantShip() ? Items.AIR : CurrentsofTrade.SLOOP_ITEM.get();
    }

    @Override
    protected void destroy(DamageSource source) {
        this.destroy(this.getDropItem());
    }

    public void destroy(Item dropItem) {
        if (this.isMerchantShip()) {
            // Eject passengers and silently discard without dropping any item
            this.ejectPassengers();
            this.discard();
            return;
        }
        Containers.dropContents(this.level(), this, this);
        if (dropItem != Items.AIR && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(this.createDropStack());
        }
        this.discard();
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.currents_of_trade.sloop");
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getTypeName();
    }

    @Override
    public Component getDisplayName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : Component.translatable("gui.currents_of_trade.sloop_hold");
    }

    @Override
    public void tick() {
        float prevYaw = this.getYRot();
        super.tick();

        // 1. Significantly reduce steering rotation speed (A, D keys):
        // Replaces quick dinghy snap-turning with a majestic, heavy sailing ship feel (~86% damping).
        float rawTurnDelta = Mth.wrapDegrees(this.getYRot() - prevYaw);
        if (Math.abs(rawTurnDelta) > 0.001F) {
            float dampedTurn = rawTurnDelta * 0.14F;
            this.setYRot(prevYaw + dampedTurn);
            this.yRotO = prevYaw;
        }

        // 2. Synchronize multi-part hitboxes along ship's spine (Stern & Prow):
        // Enables walking on the entire ship deck (helm to bow) WITHOUT sticking out into water when diagonal!
        double yawRad = Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // Stern Part (-1.90m aft under helm & quarterdeck)
        double sternX = this.getX() + 1.90 * sin;
        double sternZ = this.getZ() - 1.90 * cos;
        this.sternPart.moveTo(sternX, this.getY(), sternZ);

        // Prow Part (+2.10m forward under foredeck & bow)
        double prowX = this.getX() - 2.10 * sin;
        double prowZ = this.getZ() + 2.10 * cos;
        this.prowPart.moveTo(prowX, this.getY(), prowZ);

        // 3. Anti-sinking buoyancy protection:
        // Guarantees the ship always floats gracefully at the water surface and can never sink!
        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        if (fluid.is(FluidTags.WATER) || this.level().getFluidState(pos.below()).is(FluidTags.WATER)) {
            double waterSurfaceY = pos.getY() + (fluid.is(FluidTags.WATER) ? fluid.getHeight(this.level(), pos) : 0.0);
            if (!fluid.is(FluidTags.WATER)) {
                FluidState belowFluid = this.level().getFluidState(pos.below());
                waterSurfaceY = pos.below().getY() + belowFluid.getHeight(this.level(), pos.below());
            }

            double targetY = waterSurfaceY - 0.45;
            if (this.getY() < targetY) {
                Vec3 motion = this.getDeltaMovement();
                double upwardCorrection = Math.min(0.25, (targetY - this.getY()) * 0.6);
                this.setDeltaMovement(motion.x, Math.max(motion.y, upwardCorrection), motion.z);
                this.setOnGround(false);
            }
        }

        // 4. Storm dynamic sea spray & Bow Lantern effects:
        if (this.level().isClientSide && this.level().isThundering() && this.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
            double sprayX = prowX + (this.random.nextDouble() - 0.5) * 1.2;
            double sprayZ = prowZ + (this.random.nextDouble() - 0.5) * 1.2;
            this.level().addParticle(ParticleTypes.SPLASH, sprayX, this.getY() + 0.3, sprayZ, 0.0, 0.15, 0.0);
            if (this.random.nextFloat() < 0.3F) {
                this.level().addParticle(ParticleTypes.BUBBLE, sprayX, this.getY() + 0.1, sprayZ, 0.0, 0.05, 0.0);
            }
        }

        int lanternType = this.getBowLanternType();
        boolean isNightOrStorm = !this.level().isDay() || this.level().isRaining();
        if (lanternType > 0) {
            if (this.level().isClientSide && this.random.nextFloat() < 0.25F) {
                double lanternX = this.getX() - 4.25 * sin + (this.random.nextDouble() - 0.5) * 0.15;
                double lanternZ = this.getZ() + 4.25 * cos + (this.random.nextDouble() - 0.5) * 0.15;
                this.level().addParticle(
                        lanternType == 2 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        lanternX, this.getY() + 1.85, lanternZ, 0.0, 0.01, 0.0
                );
            }
            if (!this.level().isClientSide && isNightOrStorm && this.tickCount % 20 == 0) {
                for (Entity passenger : this.getPassengers()) {
                    if (passenger instanceof Player rider) {
                        rider.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
                    }
                }
            }
        }

        // 5. Tailwind, Captain's Tricorne, Copper Hull & Soul Lantern sailing boosts:
        boolean hasTricorne = false;
        LivingEntity captain = this.getControllingPassenger();
        if (captain instanceof Player player) {
            hasTricorne = player.getItemBySlot(EquipmentSlot.HEAD).is(CurrentsofTrade.CAPTAINS_TRICORNE.get());
        }

        if (this.tailwindTicks > 0) {
            this.tailwindTicks--;
        }

        boolean hasCopper = this.hasCopperHull();
        boolean hasSoulLanternBoost = (lanternType == 2 && isNightOrStorm);

        if ((this.tailwindTicks > 0 || hasTricorne || hasCopper || hasSoulLanternBoost)
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            Vec3 motion = this.getDeltaMovement();
            double boost = (this.tailwindTicks > 0 ? 1.028 : 1.0)
                    * (hasTricorne ? 1.02 : 1.0)
                    * (hasCopper ? 1.015 : 1.0)
                    * (hasSoulLanternBoost ? 1.01 : 1.0);
            this.setDeltaMovement(motion.x * boost, motion.y, motion.z * boost);

            if (this.level().isClientSide && (this.tailwindTicks > 0 || hasSoulLanternBoost) && this.random.nextFloat() < 0.35F) {
                this.level().addParticle(hasSoulLanternBoost ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.GLOW,
                        this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                        this.getY() + 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
                        0.0, 0.04, 0.0);
            }
        }

        // Keep center bounding box synchronized
        this.setBoundingBox(this.makeBoundingBox());
    }

    private int tailwindTicks = 0;

    public void applyTailwind(int ticks) {
        this.tailwindTicks = Math.max(this.tailwindTicks, ticks);
    }

    public boolean hasTailwind() {
        return this.tailwindTicks > 0;
    }

    @Override
    public void push(Entity entity) {
        // Prevent swimming mobs, monsters, and animals from automatically climbing aboard on collision
        if (!this.isPassengerOfSameVehicle(entity) && !entity.noPhysics && !this.noPhysics) {
            double d0 = entity.getX() - this.getX();
            double d1 = entity.getZ() - this.getZ();
            double d2 = Mth.absMax(d0, d1);
            if (d2 >= 0.01F) {
                d2 = Math.sqrt(d2);
                d0 /= d2;
                d1 /= d2;
                double d3 = Math.min(1.0, 1.0 / d2);
                d0 *= d3 * 0.05F;
                d1 *= d3 * 0.05F;
                if (!entity.isVehicle() && entity.isPushable()) {
                    entity.push(d0, 0.0, d1);
                }
            }
        }
    }

    @Override
    protected int getMaxPassengers() {
        return 3;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        // If this is a Merchant Sloop, nobody (players, animals, mobs) can board it except the trader itself!
        if (this.isMerchantShip()) {
            return passenger instanceof WanderingSailorEntity && this.getPassengers().isEmpty();
        }

        if (this.getPassengers().size() >= this.getMaxPassengers() || this.canBoatInFluid(this.getEyeInFluidType())) {
            return false;
        }
        // Captain seat (Station 1) is strictly reserved for players (or the WanderingSailor trader).
        // Animals and regular mobs cannot board an empty ship (captain seat), and cannot exceed 2 passenger seats.
        if (!(passenger instanceof Player) && !(passenger instanceof WanderingSailorEntity)) {
            if (this.getPassengers().isEmpty()) {
                return false;
            }
            int nonPlayerCount = 0;
            for (Entity p : this.getPassengers()) {
                if (!(p instanceof Player)) {
                    nonPlayerCount++;
                }
            }
            if (nonPlayerCount >= 2) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // Only a Player can ever be the captain and steer the ship
        for (Entity p : this.getPassengers()) {
            if (p instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) return;

        float forwardOffset;
        float verticalOffset;

        boolean isCaptain = (passenger instanceof Player || passenger instanceof WanderingSailorEntity) && this.getPassengers().indexOf(passenger) == 0;
        if (isCaptain) {
            // Station 1: Captain at the helm on elevated quarterdeck (players or Wandering Sailor)
            forwardOffset = -2.00F;
            verticalOffset = 0.98F;
        } else {
            // Station 2 & 3: Middle (waist) and Bow (foredeck) seats for other players or animals/mobs
            int nonCaptainIndex = 0;
            for (Entity p : this.getPassengers()) {
                if (p == passenger) break;
                if (!((p instanceof Player || p instanceof WanderingSailorEntity) && this.getPassengers().indexOf(p) == 0)) {
                    nonCaptainIndex++;
                }
            }

            if (nonCaptainIndex == 0) {
                // Station 2: Middle waist bench (between sail/mast and quarterdeck)
                forwardOffset = 0.65F;
                verticalOffset = 0.70F;
            } else {
                // Station 3: Foredeck bow bench (between bow and sail)
                forwardOffset = 2.20F;
                verticalOffset = 0.72F;
            }
        }

        float yawRad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double worldX = -forwardOffset * sin;
        double worldZ =  forwardOffset * cos;

        callback.accept(passenger, this.getX() + worldX, this.getY() + verticalOffset, this.getZ() + worldZ);
        this.clampRotation(passenger);

        if (passenger instanceof net.minecraft.world.entity.animal.Animal) {
            int i = passenger.getId() % 2 == 0 ? 90 : 270;
            passenger.setYBodyRot(((net.minecraft.world.entity.animal.Animal) passenger).yBodyRot + (float) i);
            passenger.setYHeadRot(passenger.getYHeadRot() + (float) i);
        }
    }
}
