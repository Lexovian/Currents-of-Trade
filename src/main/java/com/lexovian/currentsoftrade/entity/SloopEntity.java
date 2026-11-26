package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
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
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    @SuppressWarnings("unchecked")
    public SloopEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        ShipPart<SloopEntity>[] parts = ShipPart.createShipDeckParts(this);
        this.sternPart = parts[0];
        this.prowPart = parts[3];
        this.subEntities = parts;
    }

    @SuppressWarnings("unchecked")
    public SloopEntity(Level level, double x, double y, double z) {
        super(CurrentsofTrade.SLOOP.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        ShipPart<SloopEntity>[] parts = ShipPart.createShipDeckParts(this);
        this.sternPart = parts[0];
        this.prowPart = parts[3];
        this.subEntities = parts;
        ShipPart.updateShipDeckParts(this, this.subEntities);
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
        double half = 0.68;
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
    private static final EntityDataAccessor<Boolean> HAS_SWIVEL_HARPOON =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_TRAWL_NET =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FIGUREHEAD_TYPE =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SYNCED_CARGO_STACKS =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);

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

    public boolean hasSwivelHarpoon() {
        return this.entityData.get(HAS_SWIVEL_HARPOON);
    }

    public void setSwivelHarpoon(boolean harpoon) {
        this.entityData.set(HAS_SWIVEL_HARPOON, harpoon);
    }

    public boolean hasTrawlNet() {
        return this.entityData.get(HAS_TRAWL_NET);
    }

    public void setTrawlNet(boolean trawlNet) {
        this.entityData.set(HAS_TRAWL_NET, trawlNet);
    }

    /** 0 = none, 1 = Jade Figurine (Luck + Trawl rare), 2 = Narwhal Ivory (+10% speed & -25% dmg), 3 = Ammonite Fossil (35% faster Trawl) */
    public int getFigureheadType() {
        return this.entityData.get(FIGUREHEAD_TYPE);
    }

    public void setFigureheadType(int type) {
        this.entityData.set(FIGUREHEAD_TYPE, Mth.clamp(type, 0, 3));
    }

    public int getSyncedCargoStacks() {
        return this.entityData.get(SYNCED_CARGO_STACKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_MERCHANT_SHIP, false);
        builder.define(SAIL_COLOR, -1);
        builder.define(BANNER_STACK, ItemStack.EMPTY);
        builder.define(BOW_LANTERN_TYPE, 0);
        builder.define(COPPER_HULL, false);
        builder.define(HAS_SWIVEL_HARPOON, false);
        builder.define(HAS_TRAWL_NET, false);
        builder.define(FIGUREHEAD_TYPE, 0);
        builder.define(SYNCED_CARGO_STACKS, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsMerchantShip", this.isMerchantShip());
        tag.putInt("SailColor", this.getSailColor());
        if (!this.getBannerStack().isEmpty()) {
            tag.put("BannerItem", this.getBannerStack().saveOptional(this.registryAccess()));
        }
        tag.putInt("BowLanternType", this.getBowLanternType());
        tag.putBoolean("CopperHull", this.hasCopperHull());
        tag.putBoolean("SwivelHarpoon", this.hasSwivelHarpoon());
        tag.putBoolean("TrawlNet", this.hasTrawlNet());
        tag.putInt("FigureheadType", this.getFigureheadType());
        this.addChestVehicleSaveData(tag, this.registryAccess());
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
            this.setBannerStack(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("BannerItem")));
        } else {
            this.setBannerStack(ItemStack.EMPTY);
        }
        if (tag.contains("BowLanternType")) {
            this.setBowLanternType(tag.getInt("BowLanternType"));
        }
        if (tag.contains("CopperHull")) {
            this.setCopperHull(tag.getBoolean("CopperHull"));
        }
        if (tag.contains("SwivelHarpoon")) {
            this.setSwivelHarpoon(tag.getBoolean("SwivelHarpoon"));
        }
        if (tag.contains("TrawlNet")) {
            this.setTrawlNet(tag.getBoolean("TrawlNet"));
        }
        if (tag.contains("FigureheadType")) {
            this.setFigureheadType(tag.getInt("FigureheadType"));
        }
        this.readChestVehicleSaveData(tag, this.registryAccess());
    }

    public ItemStack createDropStack() {
        ItemStack stack = new ItemStack(this.getDropItem());
        if (this.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty()
                || this.getBowLanternType() > 0 || this.hasCopperHull() || this.hasSwivelHarpoon() || this.hasTrawlNet() || this.getFigureheadType() > 0) {
            CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = existing.copyTag();
            tag.putInt("SailColor", this.getSailColor());
            if (!this.getBannerStack().isEmpty()) {
                tag.put("BannerItem", this.getBannerStack().saveOptional(this.registryAccess()));
            }
            tag.putInt("BowLanternType", this.getBowLanternType());
            tag.putBoolean("CopperHull", this.hasCopperHull());
            tag.putBoolean("SwivelHarpoon", this.hasSwivelHarpoon());
            tag.putBoolean("TrawlNet", this.hasTrawlNet());
            tag.putInt("FigureheadType", this.getFigureheadType());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    public void loadUpgradesFromStack(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            this.setCustomName(stack.getHoverName());
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("SailColor")) {
                this.setSailColor(tag.getInt("SailColor"));
            }
            if (tag.contains("BannerItem")) {
                this.setBannerStack(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("BannerItem")));
            }
            if (tag.contains("BowLanternType")) {
                this.setBowLanternType(tag.getInt("BowLanternType"));
            }
            if (tag.contains("CopperHull")) {
                this.setCopperHull(tag.getBoolean("CopperHull"));
            }
            if (tag.contains("SwivelHarpoon")) {
                this.setSwivelHarpoon(tag.getBoolean("SwivelHarpoon"));
            }
            if (tag.contains("TrawlNet")) {
                this.setTrawlNet(tag.getBoolean("TrawlNet"));
            }
            if (tag.contains("FigureheadType")) {
                this.setFigureheadType(tag.getInt("FigureheadType"));
            }
        }
    }

    public static void awardFlagshipAdvancement(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            net.minecraft.advancements.AdvancementHolder adv = serverPlayer.getServer().getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "flagship_armada"));
            if (adv != null) {
                for (String criterion : adv.value().criteria().keySet()) {
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

        // Check ship customization items FIRST (works whether normal-clicking or Shift+Right-Clicking!)
        InteractionResult customResult = this.tryApplyCustomization(player, hand);
        if (customResult.consumesAction()) {
            return customResult;
        }

        // Sneak + Right-Click opens the 18-slot Captain's Hold
        if (player.isSecondaryUseActive()) {
            return this.interactWithContainerVehicle(player);
        }

        return super.interact(player, hand);
    }

    public static boolean isShipCustomizationItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof DyeItem
                || stack.is(CurrentsofTrade.TYRIAN_PURPLE.get())
                || stack.is(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get())
                || item instanceof BannerItem
                || stack.is(Items.WATER_BUCKET)
                || stack.is(Items.SPONGE)
                || stack.is(Items.WET_SPONGE)
                || stack.is(Items.SHEARS)
                || stack.is(Items.LANTERN)
                || stack.is(Items.SOUL_LANTERN)
                || stack.is(Items.COPPER_BLOCK)
                || stack.is(Items.WAXED_COPPER_BLOCK)
                || stack.is(CurrentsofTrade.SWIVEL_HARPOON.get())
                || stack.is(CurrentsofTrade.TRAWL_NET.get())
                || stack.is(CurrentsofTrade.JADE_FIGURINE.get())
                || stack.is(CurrentsofTrade.NARWHAL_IVORY.get())
                || stack.is(CurrentsofTrade.AMMONITE_FOSSIL.get());
    }

    /**
     * Allows players to dye sails, mount banners/pennants ("flama"), or attach lanterns/copper
     * even when standing on deck, riding the ship, or aiming high up at the 16-block mast and sails
     * (above the 0.85m hull collision box).
     */
    public static boolean tryCustomizeNearbyShip(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isShipCustomizationItem(held)) return false;

        Entity rootVehicle = player.getRootVehicle();
        if (rootVehicle instanceof SloopEntity sloop && !sloop.isMerchantShip()) {
            return sloop.tryApplyCustomization(player, hand).consumesAction();
        }
        if (rootVehicle instanceof CargoBoatEntity cargo && cargo.isPlayerControlled()) {
            return cargo.tryApplyCustomization(player, hand).consumesAction();
        }

        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0F).scale(7.5));
        AABB searchArea = player.getBoundingBox().inflate(9.0, 18.0, 9.0);

        java.util.List<Entity> candidates = player.level().getEntities(player, searchArea,
                e -> (e instanceof SloopEntity s && !s.isMerchantShip())
                        || (e instanceof CargoBoatEntity c && c.isPlayerControlled())
                        || (e instanceof TradeBoatEntity tb && !tb.isWanderingTraderShip() && !tb.isVoyageShip()
                                && (held.is(CurrentsofTrade.SWIVEL_HARPOON.get()) || (held.is(Items.SHEARS) && tb.hasSwivelHarpoon()))));

        Entity bestShip = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            // Full 3D volume covering hull + 16m mast & sails
            AABB sailAndHullBox = new AABB(
                    candidate.getX() - 3.0, candidate.getY() - 0.5, candidate.getZ() - 3.0,
                    candidate.getX() + 3.0, candidate.getY() + 17.5, candidate.getZ() + 3.0
            );
            boolean hitRay = sailAndHullBox.clip(eye, reachEnd).isPresent() || sailAndHullBox.contains(eye);
            boolean standingOnDeck = candidate.getBoundingBox().inflate(3.8, 2.2, 3.8).contains(player.position());
            if (hitRay || standingOnDeck) {
                double dSq = candidate.distanceToSqr(player);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestShip = candidate;
                }
            }
        }

        if (bestShip instanceof SloopEntity sloop) {
            return sloop.tryApplyCustomization(player, hand).consumesAction();
        }
        if (bestShip instanceof CargoBoatEntity cargo && cargo.isPlayerControlled()) {
            return cargo.tryApplyCustomization(player, hand).consumesAction();
        }
        if (bestShip instanceof TradeBoatEntity tradeBoat) {
            return tradeBoat.tryApplyHarpoonCustomization(player, hand).consumesAction();
        }
        return false;
    }

    public InteractionResult tryApplyCustomization(Player player, InteractionHand hand) {
        if (this.isMerchantShip()) return InteractionResult.PASS;
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
                    String key = "message.currents_of_trade.sloop_dyed";
                    player.displayClientMessage(Component.translatable(key, colorName), true);
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

        // 3. Mount Banner / Pennant ("Flama") on Main Sail & Masthead
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

        // 4. Remove Figurehead / Trawl Net / Swivel Harpoon (Sneak + Shears), Banner, or Bow Lantern with Shears
        if (held.is(Items.SHEARS) && (((this.getFigureheadType() > 0 || this.hasTrawlNet() || this.hasSwivelHarpoon()) && player.isSecondaryUseActive()) || !this.getBannerStack().isEmpty() || this.getBowLanternType() > 0)) {
            if (!this.level().isClientSide) {
                if (this.getFigureheadType() > 0 && player.isSecondaryUseActive()) {
                    if (!player.getAbilities().instabuild) {
                        Item figItem = this.getFigureheadType() == 1 ? CurrentsofTrade.JADE_FIGURINE.get()
                                : this.getFigureheadType() == 2 ? CurrentsofTrade.NARWHAL_IVORY.get()
                                : CurrentsofTrade.AMMONITE_FOSSIL.get();
                        player.getInventory().placeItemBackInInventory(new ItemStack(figItem));
                    }
                    this.setFigureheadType(0);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.figurehead_removed"), true);
                } else if (this.hasTrawlNet() && player.isSecondaryUseActive()) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(CurrentsofTrade.TRAWL_NET.get()));
                    }
                    this.setTrawlNet(false);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.trawl_net_removed"), true);
                } else if (this.hasSwivelHarpoon() && player.isSecondaryUseActive()) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(CurrentsofTrade.SWIVEL_HARPOON.get()));
                    }
                    this.setSwivelHarpoon(false);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.harpoon_removed"), true);
                } else if (!this.getBannerStack().isEmpty()) {
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
                    Component lanternName = held.getHoverName();
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
                            lanternName), true);
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

        // 7. Mount Brass Swivel Harpoon on Foredeck
        if (held.is(CurrentsofTrade.SWIVEL_HARPOON.get()) && !this.hasSwivelHarpoon()) {
            if (!this.level().isClientSide) {
                this.setSwivelHarpoon(true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.75F, 1.3F);
                this.level().playSound(null, this.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.harpoon_mounted"), true);
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 8. Mount Shipboard Trawl Net on Stern
        if (held.is(CurrentsofTrade.TRAWL_NET.get()) && !this.hasTrawlNet()) {
            if (!this.level().isClientSide) {
                this.setTrawlNet(true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.trawl_net_mounted"), true);
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 9. Mount Ship Figurehead on Bow (Jade Figurine = 1, Narwhal Ivory = 2, Ammonite Fossil = 3)
        int newFig = held.is(CurrentsofTrade.JADE_FIGURINE.get()) ? 1
                : held.is(CurrentsofTrade.NARWHAL_IVORY.get()) ? 2
                : held.is(CurrentsofTrade.AMMONITE_FOSSIL.get()) ? 3 : 0;
        if (newFig > 0 && this.getFigureheadType() != newFig) {
            if (!this.level().isClientSide) {
                Component figName = held.getHoverName();
                if (this.getFigureheadType() > 0 && !player.getAbilities().instabuild) {
                    Item prev = this.getFigureheadType() == 1 ? CurrentsofTrade.JADE_FIGURINE.get()
                            : this.getFigureheadType() == 2 ? CurrentsofTrade.NARWHAL_IVORY.get()
                            : CurrentsofTrade.AMMONITE_FOSSIL.get();
                    player.getInventory().placeItemBackInInventory(new ItemStack(prev));
                }
                this.setFigureheadType(newFig);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.35F);
                player.displayClientMessage(Component.translatable("message.currents_of_trade.figurehead_mounted", figName), true);
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return InteractionResult.PASS;
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
    @Nullable @Override public ResourceKey<LootTable> getLootTable() { return this.lootTable; }
    @Override public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) { this.lootTable = lootTable; }
    @Override public long getLootTableSeed() { return this.lootTableSeed; }
    @Override public void setLootTableSeed(long seed) { this.lootTableSeed = seed; }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) return null;
        this.unpackChestVehicleLootTable(playerInventory.player);
        return new ChestMenu(MenuType.GENERIC_9x2, containerId, playerInventory, this, 2);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        // Never collide with our own multipart deck sections or let players stall ship motion
        if (entity instanceof ShipPart<?> part && part.getParent() == this) return false;
        if (entity instanceof Player) return false;
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
        if (this.getFigureheadType() == 2 && !(source.getEntity() instanceof Player p && p.getAbilities().instabuild)) {
            amount *= 0.75F;
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

    @Override
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
        double prevShipX = this.getX();
        double prevShipY = this.getY();
        double prevShipZ = this.getZ();
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

        // 2. Forward vector for bow effects:
        double yawRad = Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double prowX = this.getX() - 2.10 * sin;
        double prowZ = this.getZ() + 2.10 * cos;

        // 3. Anti-sinking buoyancy protection:
        // Guarantees the ship always floats gracefully at the true water surface and can never sink!
        BlockPos pos = this.blockPosition();
        BlockPos surfacePos = null;
        if (this.level().getFluidState(pos).is(FluidTags.WATER)) {
            surfacePos = pos;
        } else if (this.level().getFluidState(pos.below()).is(FluidTags.WATER)) {
            surfacePos = pos.below();
        }
        if (surfacePos != null) {
            for (int up = 0; up < 16; up++) {
                BlockPos above = surfacePos.above();
                if (this.level().getFluidState(above).is(FluidTags.WATER)) {
                    surfacePos = above;
                } else {
                    break;
                }
            }
            FluidState topFluid = this.level().getFluidState(surfacePos);
            double waterSurfaceY = surfacePos.getY() + topFluid.getHeight(this.level(), surfacePos);

            double targetY = waterSurfaceY - 0.35;
            if (this.getY() < waterSurfaceY - 0.75) {
                BlockPos floatPos = BlockPos.containing(this.getX(), waterSurfaceY + 0.05, this.getZ());
                if (this.level().getBlockState(floatPos).getCollisionShape(this.level(), floatPos).isEmpty()) {
                    this.setPos(this.getX(), waterSurfaceY - 0.10, this.getZ());
                    Vec3 motion = this.getDeltaMovement();
                    this.setDeltaMovement(motion.x, Math.max(0.0, motion.y), motion.z);
                    this.setOnGround(false);
                }
            } else if (this.getY() < targetY) {
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
        int figurehead = this.getFigureheadType();
        boolean hasTradeWind = com.lexovian.currentsoftrade.world.market.TradeWinds.hasFavorableTradeWind(this.level(), this.getYRot());

        if ((this.tailwindTicks > 0 || hasTricorne || hasCopper || hasSoulLanternBoost || hasTradeWind || figurehead == 2)
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            Vec3 motion = this.getDeltaMovement();
            double boost = (this.tailwindTicks > 0 ? 1.028 : 1.0)
                    * (hasTricorne ? 1.02 : 1.0)
                    * (hasCopper ? 1.015 : 1.0)
                    * (hasSoulLanternBoost ? 1.01 : 1.0)
                    * (hasTradeWind ? 1.016 : 1.0)
                    * (figurehead == 2 ? 1.010 : 1.0);
            this.setDeltaMovement(motion.x * boost, motion.y, motion.z * boost);

            if (this.level().isClientSide && (this.tailwindTicks > 0 || hasSoulLanternBoost || hasTradeWind) && this.random.nextFloat() < 0.35F) {
                this.level().addParticle(hasSoulLanternBoost ? ParticleTypes.SOUL_FIRE_FLAME : (hasTradeWind ? ParticleTypes.CLOUD : ParticleTypes.GLOW),
                        this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                        this.getY() + 0.65,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
                        0.0, 0.03, 0.0);
            }
        }

        if (!this.level().isClientSide && figurehead == 1 && this.tickCount % 40 == 0) {
            for (Entity passenger : this.getPassengers()) {
                if (passenger instanceof Player rider) {
                    rider.addEffect(new MobEffectInstance(MobEffects.LUCK, 120, 0, true, false, true));
                }
            }
        }

        // 6. Brass Swivel Harpoon defense, Shipboard Trawl Net & Bosun's Whistle shoreline glide
        if (!this.level().isClientSide) {
            if (this.tickCount % 10 == 0) {
                int used = 0;
                for (int i = 0; i < this.getContainerSize(); i++) {
                    if (!this.getItem(i).isEmpty()) used++;
                }
                this.entityData.set(SYNCED_CARGO_STACKS, used);
            }
            if (this.hasSwivelHarpoon()) {
                com.lexovian.currentsoftrade.item.SwivelHarpoonItem.tickShipHarpoonDefense(this);
            }
            if (this.hasTrawlNet() && !this.isMerchantShip()) {
                com.lexovian.currentsoftrade.item.TrawlNetItem.tickShipTrawlNet(this, this, figurehead);
            }
            this.tickSummonGlide();
        }

        // Keep center bounding box and multi-part deck sections synchronized
        this.setBoundingBox(this.makeBoundingBox());
        ShipPart.updateShipDeckParts(this, this.subEntities);
        ShipPart.supportDeckEntities(this, this.subEntities, prevShipX, prevShipY, prevShipZ, prevYaw);
    }

    private int tailwindTicks = 0;
    @Nullable private Vec3 summonTarget = null;
    private int summonTicks = 0;

    public void summonToward(Vec3 target) {
        this.summonTarget = target;
        this.summonTicks = 120;
    }

    private void tickSummonGlide() {
        if (this.summonTicks <= 0 || this.summonTarget == null) return;
        if (this.isVehicle() || this.isMerchantShip()) {
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
        BlockPos ahead = BlockPos.containing(this.getX() + nx * 1.5D, this.getY(), this.getZ() + nz * 1.5D);
        if (!this.level().getFluidState(ahead).is(FluidTags.WATER)
                && !this.level().getFluidState(ahead.below()).is(FluidTags.WATER)) {
            this.summonTicks = 0;
            this.summonTarget = null;
            return;
        }
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        this.setYRot(this.getYRot() + Mth.clamp(diff, -8.0F, 8.0F));
        double speed = Math.min(0.24D, Math.max(0.08D, dist * 0.05D));
        Vec3 cur = this.getDeltaMovement();
        this.setDeltaMovement(nx * speed, cur.y, nz * speed);
        this.hasImpulse = true;
    }

    public void applyTailwind(int ticks) {
        this.tailwindTicks = Math.max(this.tailwindTicks, ticks);
    }

    public boolean hasTailwind() {
        return this.tailwindTicks > 0;
    }

    @Override
    public void push(Entity entity) {
        // Prevent swimming mobs, monsters, and animals from automatically climbing aboard on collision,
        // while never shoving players or crew standing on top of the deck
        if (entity == this || entity instanceof ShipPart<?> || this.isPassengerOfSameVehicle(entity) || entity.noPhysics || this.noPhysics) {
            return;
        }
        if (entity.getBoundingBox().minY > this.getBoundingBox().minY + 0.15D) {
            return;
        }
        if (true) {
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

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 deckSpot = ShipPart.findDeckDismountLocation(this, passenger);
        if (deckSpot != null) {
            return deckSpot;
        }
        return super.getDismountLocationForPassenger(passenger);
    }
}
