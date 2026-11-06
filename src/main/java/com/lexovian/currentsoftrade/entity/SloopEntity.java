package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
public class SloopEntity extends AbstractBoat implements HasCustomInventoryScreen, ContainerEntity {

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

    public SloopEntity(EntityType<? extends AbstractBoat> entityType, Level level) {
        super(entityType, level, () -> CurrentsofTrade.SLOOP_ITEM.get());
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
    }

    public SloopEntity(Level level, double x, double y, double z) {
        super(CurrentsofTrade.SLOOP.get(), level, () -> CurrentsofTrade.SLOOP_ITEM.get());
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
    }

    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return dimensions.height() / 3.0F;
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
        if (!this.level().isClientSide() && reason.shouldDestroy() && !this.isMerchantShip()) {
            Containers.dropContents(this.level(), this, this);
        }
        super.remove(reason);
        for (var part : this.subEntities) {
            part.remove(reason);
        }
    }

    // --- Synched Data ---

    private static final EntityDataAccessor<Integer> SAIL_COLOR =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> BANNER_STACK =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> BOW_LANTERN =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COPPER_HULL =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_MERCHANT_SHIP =
            SynchedEntityData.defineId(SloopEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SAIL_COLOR, -1);
        builder.define(BANNER_STACK, ItemStack.EMPTY);
        builder.define(BOW_LANTERN, 0);
        builder.define(COPPER_HULL, false);
        builder.define(IS_MERCHANT_SHIP, false);
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

    public int getBowLanternType() {
        return this.entityData.get(BOW_LANTERN);
    }

    public void setBowLanternType(int lanternType) {
        this.entityData.set(BOW_LANTERN, Mth.clamp(lanternType, 0, 2));
    }

    public boolean hasCopperHull() {
        return this.entityData.get(COPPER_HULL);
    }

    public void setCopperHull(boolean copperHull) {
        this.entityData.set(COPPER_HULL, copperHull);
    }

    public boolean isMerchantShip() {
        return this.entityData.get(IS_MERCHANT_SHIP);
    }

    public void setMerchantShip(boolean merchant) {
        this.entityData.set(IS_MERCHANT_SHIP, merchant);
    }

    // --- Customization Upgrades (Sails, Pennants, Lanterns, Hull) ---

    private void awardFlagshipAdvancement(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = serverPlayer.level().getServer();
            if (server != null) {
                AdvancementHolder adv = server.getAdvancements()
                        .get(Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "flagship_armada"));
                if (adv != null) {
                    for (String criterion : adv.value().criteria().keySet()) {
                        serverPlayer.getAdvancements().award(adv, criterion);
                    }
                }
            }
        }
    }

    public ItemStack createDropStack() {
        ItemStack stack = new ItemStack(this.getTradeDropItem());
        if (this.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        if (this.getSailColor() >= 0 || !this.getBannerStack().isEmpty() || this.getBowLanternType() > 0 || this.hasCopperHull()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putInt("SailColor", this.getSailColor());
            if (!this.getBannerStack().isEmpty()) {
                tag.store("BannerItem", ItemStack.CODEC, this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.getBannerStack());
            }
            tag.putInt("BowLanternType", this.getBowLanternType());
            tag.putBoolean("CopperHull", this.hasCopperHull());
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
                this.setSailColor(tag.getIntOr("SailColor", -1));
            }
            if (tag.contains("BannerItem")) {
                tag.read("BannerItem", ItemStack.CODEC, this.registryAccess().createSerializationContext(NbtOps.INSTANCE)).ifPresent(this::setBannerStack);
            }
            if (tag.contains("BowLanternType")) {
                this.setBowLanternType(tag.getIntOr("BowLanternType", 0));
            } else if (tag.contains("BowLantern")) {
                this.setBowLanternType(tag.getIntOr("BowLantern", 0));
            }
            if (tag.contains("CopperHull")) {
                this.setCopperHull(tag.getBooleanOr("CopperHull", false));
            }
        }
    }

    public static boolean isShipCustomizationItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof DyeItem
                || stack.is(CurrentsofTrade.TYRIAN_PURPLE.get())
                || stack.is(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get())
                || item instanceof BannerItem
                || stack.is(Items.WATER_BUCKET) || stack.is(Items.SPONGE) || stack.is(Items.WET_SPONGE)
                || stack.is(Items.SHEARS)
                || stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN)
                || stack.is(Items.COPPER_BLOCK) || stack.is(Items.WAXED_COPPER_BLOCK);
    }

    public static boolean tryCustomizeNearbyShip(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isShipCustomizationItem(held)) {
            return false;
        }

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
                        || (e instanceof CargoBoatEntity c && c.isPlayerControlled()));

        Entity bestShip = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            AABB sailAndHullBox = new AABB(
                    candidate.getX() - 3.0, candidate.getY() - 0.5, candidate.getZ() - 3.0,
                    candidate.getX() + 3.0, candidate.getY() + 17.5, candidate.getZ() + 3.0
            );
            boolean hitRay = sailAndHullBox.clip(eye, reachEnd).isPresent() || sailAndHullBox.contains(eye);
            boolean standingOnDeck = candidate.getBoundingBox().inflate(1.35, 1.5, 1.35).contains(player.position());
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
        if (bestShip instanceof CargoBoatEntity cargo) {
            return cargo.tryApplyCustomization(player, hand).consumesAction();
        }
        return false;
    }

    public InteractionResult tryApplyCustomization(Player player, InteractionHand hand) {
        if (this.isMerchantShip()) {
            return InteractionResult.PASS;
        }

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
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_dyed", colorName));
                    this.awardFlagshipAdvancement(player);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 2. Wash Sails (Water Bucket or Sponge)
        if ((held.is(Items.WATER_BUCKET) || held.is(Items.SPONGE) || held.is(Items.WET_SPONGE)) && this.getSailColor() >= 0) {
            if (!this.level().isClientSide()) {
                this.setSailColor(-1);
                this.level().playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.1F);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_washed"));
            }
            return InteractionResult.SUCCESS;
        }

        // 3. Mount Banner / Pennant
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
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_banner_mounted"));
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.SUCCESS;
        }

        // 4. Remove Banner or Bow Lantern with Shears
        if (held.is(Items.SHEARS) && (!this.getBannerStack().isEmpty() || this.getBowLanternType() > 0)) {
            if (!this.level().isClientSide()) {
                if (!this.getBannerStack().isEmpty()) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(this.getBannerStack().copy());
                    }
                    this.setBannerStack(ItemStack.EMPTY);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_banner_removed"));
                } else if (this.getBowLanternType() > 0) {
                    if (!player.getAbilities().instabuild) {
                        Item lanternItem = this.getBowLanternType() == 2 ? Items.SOUL_LANTERN : Items.LANTERN;
                        player.getInventory().placeItemBackInInventory(new ItemStack(lanternItem));
                    }
                    this.setBowLanternType(0);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.LANTERN_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_lantern_removed"));
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 5. Bow Lantern
        if (held.is(Items.LANTERN) || held.is(Items.SOUL_LANTERN)) {
            int newType = held.is(Items.SOUL_LANTERN) ? 2 : 1;
            if (this.getBowLanternType() != newType) {
                if (!this.level().isClientSide()) {
                    if (this.getBowLanternType() > 0 && !player.getAbilities().instabuild) {
                        Item prevLantern = this.getBowLanternType() == 2 ? Items.SOUL_LANTERN : Items.LANTERN;
                        player.getInventory().placeItemBackInInventory(new ItemStack(prevLantern));
                    }
                    this.setBowLanternType(newType);
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.level().playSound(null, this.blockPosition(), SoundEvents.LANTERN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_lantern_mounted", held.getHoverName()));
                    this.awardFlagshipAdvancement(player);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 6. Copper-Sheathed Hull (Copper Block or Waxed Copper Block)
        if ((held.is(Items.COPPER_BLOCK) || held.is(Items.WAXED_COPPER_BLOCK)) && !this.hasCopperHull()) {
            if (!this.level().isClientSide()) {
                this.setCopperHull(true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.25F);
                this.level().playSound(null, this.blockPosition(), SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_copper_sheathed"));
                this.awardFlagshipAdvancement(player);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // --- Interaction & Captain Hold ---

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this)) {
            return InteractionResult.SUCCESS;
        }

        if (this.isMerchantShip()) {
            for (Entity p : this.getPassengers()) {
                if (p instanceof WanderingSailorEntity sailor) {
                    return sailor.mobInteract(player, hand);
                }
            }
            return InteractionResult.PASS;
        }

        InteractionResult customResult = this.tryApplyCustomization(player, hand);
        if (customResult.consumesAction()) {
            return customResult;
        }

        if (player.isSecondaryUseActive()) {
            return this.interactWithContainerVehicle(player);
        }

        return super.interact(player, hand, location);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.isMerchantShip()) {
            player.openMenu(this);
        }
    }

    // --- ContainerEntity Implementation ---

    @Override public NonNullList<ItemStack> getItemStacks() { return this.itemStacks; }
    @Override public void clearItemStacks() { this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); }
    @Override public int getContainerSize() { return SLOOP_CARGO_SLOTS; }
    @Override public boolean isEmpty() { return this.isChestVehicleEmpty(); }
    @Override public ItemStack getItem(int slot) { return this.getChestVehicleItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return this.removeChestVehicleItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return this.removeChestVehicleItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { this.setChestVehicleItem(slot, stack); }
    @Override public SlotAccess getSlot(int slot) { return this.getChestVehicleSlot(slot); }
    @Override public void setChanged() {}
    @Override public boolean stillValid(Player player) { return this.isChestVehicleStillValid(player); }
    @Override public void clearContent() { this.clearChestVehicleContent(); }
    @Nullable @Override public ResourceKey<LootTable> getContainerLootTable() { return this.lootTable; }
    @Override public void setContainerLootTable(@Nullable ResourceKey<LootTable> lootTable) { this.lootTable = lootTable; }
    @Override public long getContainerLootTableSeed() { return this.lootTableSeed; }
    @Override public void setContainerLootTableSeed(long seed) { this.lootTableSeed = seed; }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) {
            return null;
        }
        this.unpackChestVehicleLootTable(playerInventory.player);
        return new ChestMenu(MenuType.GENERIC_9x2, containerId, playerInventory, this, 2);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (entity instanceof ShipPart<?> part && part.getParent() == this) return false;
        // Do not trap players inside the hull
        if (entity instanceof Player && entity.getBoundingBox().minY < this.getBoundingBox().maxY - 0.15
                && entity.getBoundingBox().intersects(this.getBoundingBox().deflate(0.02))) {
            return false;
        }
        return super.canCollideWith(entity);
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        if (other instanceof ShipPart<?> part && part.getParent() == this) return false;
        if (other != null && this.isPassengerOfSameVehicle(other)) return false;
        // Do not trap players inside the hull (in 26.1.2 player.canCollideWith calls entity.canBeCollidedWith(player))
        if (other instanceof Player && other.getBoundingBox().minY < this.getBoundingBox().maxY - 0.15
                && other.getBoundingBox().intersects(this.getBoundingBox().deflate(0.02))) {
            return false;
        }
        return true;
    }

    @Override
    public float maxUpStep() {
        return 0.0F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.hasCopperHull() && !(source.getEntity() instanceof Player p && p.getAbilities().instabuild)) {
            amount *= 0.5F;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        this.destroy(level, this.getTradeDropItem());
    }

    @Override
    public void destroy(ServerLevel level, Item dropItem) {
        if (this.isMerchantShip()) {
            this.ejectPassengers();
            this.discard();
            return;
        }
        Containers.dropContents(level, this, this);
        if (dropItem != Items.AIR && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            this.spawnAtLocation(level, this.createDropStack());
        }
        this.discard();
    }

    public Item getTradeDropItem() {
        return this.isMerchantShip() ? Items.AIR : CurrentsofTrade.SLOOP_ITEM.get();
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

    // --- Tick Logic (Steering, Multi-Part, Anti-Sinking & Speeds) ---

    private int tailwindTicks = 0;

    public void applyTailwind(int ticks) {
        this.tailwindTicks = Math.max(this.tailwindTicks, ticks);
    }

    public boolean hasTailwind() {
        return this.tailwindTicks > 0;
    }

    @Override
    public void tick() {
        float prevYaw = this.getYRot();
        super.tick();

        // 1. Significantly reduce steering rotation speed:
        float rawTurnDelta = Mth.wrapDegrees(this.getYRot() - prevYaw);
        if (Math.abs(rawTurnDelta) > 0.001F) {
            float dampedTurn = rawTurnDelta * 0.14F;
            this.setYRot(prevYaw + dampedTurn);
            this.yRotO = prevYaw;
        }

        // 2. Synchronize multi-part hitboxes along ship's spine (Stern & Prow):
        double yawRad = Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double sternX = this.getX() + 1.90 * sin;
        double sternZ = this.getZ() - 1.90 * cos;
        this.sternPart.moveTo(sternX, this.getY(), sternZ);

        double prowX = this.getX() - 2.10 * sin;
        double prowZ = this.getZ() + 2.10 * cos;
        this.prowPart.moveTo(prowX, this.getY(), prowZ);

        // 3. Anti-sinking buoyancy protection:
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

        // 4. Storm sea spray & Bow Lantern effects:
        if (this.level().isClientSide() && this.level().isThundering() && this.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
            double sprayX = prowX + (this.random.nextDouble() - 0.5) * 1.2;
            double sprayZ = prowZ + (this.random.nextDouble() - 0.5) * 1.2;
            this.level().addParticle(ParticleTypes.SPLASH, sprayX, this.getY() + 0.3, sprayZ, 0.0, 0.15, 0.0);
            if (this.random.nextFloat() < 0.3F) {
                this.level().addParticle(ParticleTypes.BUBBLE, sprayX, this.getY() + 0.1, sprayZ, 0.0, 0.05, 0.0);
            }
        }

        int lanternType = this.getBowLanternType();
        boolean isNightOrStorm = this.level().isDarkOutside() || this.level().isRaining();
        if (lanternType > 0) {
            if (this.level().isClientSide() && this.random.nextFloat() < 0.25F) {
                double lanternX = this.getX() - 4.25 * sin + (this.random.nextDouble() - 0.5) * 0.15;
                double lanternZ = this.getZ() + 4.25 * cos + (this.random.nextDouble() - 0.5) * 0.15;
                this.level().addParticle(
                        lanternType == 2 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        lanternX, this.getY() + 1.85, lanternZ, 0.0, 0.01, 0.0
                );
            }
            if (!this.level().isClientSide() && isNightOrStorm && this.tickCount % 20 == 0) {
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

            if (this.level().isClientSide() && (this.tailwindTicks > 0 || hasSoulLanternBoost) && this.random.nextFloat() < 0.35F) {
                this.level().addParticle(hasSoulLanternBoost ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.GLOW,
                        this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                        this.getY() + 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
                        0.0, 0.04, 0.0);
            }
        }

        this.setBoundingBox(this.makeBoundingBox(this.position()));
    }

    @Override
    public void push(Entity entity) {
        if (entity == this || this.isPassengerOfSameVehicle(entity) || entity.noPhysics || this.noPhysics) {
            return;
        }
        double d0 = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double distSq = d0 * d0 + d1 * d1;
        if (distSq < 0.0001) {
            float yawRad = (float) Math.toRadians(this.getYRot());
            d0 = Math.cos(yawRad);
            d1 = Math.sin(yawRad);
            distSq = 1.0;
        }
        double dist = Math.sqrt(distSq);
        d0 /= dist;
        d1 /= dist;
        double pushForce = 0.06;
        if (!entity.isVehicle() && entity.isPushable()) {
            entity.push(d0 * pushForce, 0.0, d1 * pushForce);
        }
    }

    @Override
    protected int getMaxPassengers() {
        return 3;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (this.isMerchantShip()) {
            return passenger instanceof WanderingSailorEntity && this.getPassengers().isEmpty();
        }

        if (!super.canAddPassenger(passenger)) {
            return false;
        }

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
            forwardOffset = -2.00F;
            verticalOffset = 0.98F;
        } else {
            int nonCaptainIndex = 0;
            for (Entity p : this.getPassengers()) {
                if (p == passenger) break;
                if (!((p instanceof Player || p instanceof WanderingSailorEntity) && this.getPassengers().indexOf(p) == 0)) {
                    nonCaptainIndex++;
                }
            }

            if (nonCaptainIndex == 0) {
                forwardOffset = 0.65F;
                verticalOffset = 0.70F;
            } else {
                forwardOffset = 2.20F;
                verticalOffset = 0.72F;
            }
        }

        float yawRad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double worldX = -forwardOffset * sin;
        double worldZ =  forwardOffset * cos;

        callback.accept(passenger,
                this.getX() + worldX,
                this.getY() + verticalOffset,
                this.getZ() + worldZ);
        this.clampRotation(passenger);

        if (passenger instanceof net.minecraft.world.entity.animal.Animal animal) {
            int i = passenger.getId() % 2 == 0 ? 90 : 270;
            passenger.setYBodyRot(animal.yBodyRot + (float) i);
            passenger.setYHeadRot(passenger.getYHeadRot() + (float) i);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yawRad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // Try starboard (+cos, +sin) and port (-cos, -sin) side escape locations:
        double[][] sideOffsets = new double[][]{
                { cos * 2.2, sin * 2.2 },   // Starboard side
                { -cos * 2.2, -sin * 2.2 }, // Port side
                { -sin * 3.2, cos * 3.2 },  // Stern (behind)
                { sin * 3.2, -cos * 3.2 }   // Prow (in front)
        };

        for (double[] offset : sideOffsets) {
            double testX = this.getX() + offset[0];
            double testZ = this.getZ() + offset[1];
            double testY = this.getY() + 0.2;
            BlockPos testPos = BlockPos.containing(testX, testY, testZ);
            if (this.level().getFluidState(testPos).is(FluidTags.WATER)
                    || this.level().getBlockState(testPos).isAir()) {
                return new Vec3(testX, testY, testZ);
            }
        }

        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        double half = 0.68;
        return new AABB(
                position.x - half, position.y, position.z - half,
                position.x + half, position.y + 0.85, position.z + half
        );
    }

    // --- NBT Serialization ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.addChestVehicleSaveData(output);
        output.putInt("SailColor", this.getSailColor());
        if (!this.getBannerStack().isEmpty()) {
            output.store("BannerItem", ItemStack.CODEC, this.getBannerStack());
        }
        output.putInt("BowLanternType", this.getBowLanternType());
        output.putBoolean("CopperHull", this.hasCopperHull());
        output.putBoolean("IsMerchantShip", this.isMerchantShip());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.readChestVehicleSaveData(input);
        this.setSailColor(input.getIntOr("SailColor", -1));
        this.setBannerStack(input.read("BannerItem", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.setBowLanternType(input.getIntOr("BowLanternType", input.getIntOr("BowLantern", 0)));
        this.setCopperHull(input.getBooleanOr("CopperHull", false));
        this.setMerchantShip(input.getBooleanOr("IsMerchantShip", false));
    }
}

