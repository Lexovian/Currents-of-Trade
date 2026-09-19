package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TravelMenu extends AbstractContainerMenu {

    /** Maximum fee that fits in a single Doubloon slot (one stack = 64). */
    public static final int MAX_DOUBLOONS = 64;

    /** Fallback harbor name when the chart has no recorded name. */
    private static final String UNKNOWN_PORT = "Unknown Port";

    private final ContainerLevelAccess access;
    private final BlockPos currentPos;
    private final Player player;
    private final SimpleContainer travelContainer = new SimpleContainer(2);
    private final ContainerData data;

    // Client-side constructor (no level access)
    public TravelMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO, createClientContainerData());
    }

    private static ContainerData createClientContainerData() {
        SimpleContainerData clientData = new SimpleContainerData(2);
        clientData.set(1, AnchorPointBlockEntity.HarborStatus.VALID.ordinal());
        return clientData;
    }

    // Server-side constructor
    public TravelMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos) {
        this(containerId, playerInventory, access, currentPos, createContainerData(access, currentPos));
    }

    public TravelMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos, ContainerData data) {
        super(CurrentsofTrade.TRAVEL_MENU.get(), containerId);
        this.access = access;
        this.currentPos = currentPos;
        this.player = playerInventory.player;
        this.data = data;

        // Sync to client whenever a slot changes
        this.travelContainer.addListener(this::slotsChanged);

        // Slot 0: Nautical Chart (x=43, y=36)
        this.addSlot(new Slot(this.travelContainer, 0, 43, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof NauticalChartItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 1: Doubloon fee (x=137, y=36)
        this.addSlot(new Slot(this.travelContainer, 1, 137, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DoubloonItem;
            }
        });

        // Player inventory (3 rows x 9 columns, x=18, y=123)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 18 + col * 18, 123 + row * 18));
            }
        }

        // Player hotbar (9 slots, x=18, y=183)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 18 + col * 18, 183));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData createContainerData(ContainerLevelAccess access, BlockPos pos) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return access.evaluate((level, p) -> {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof AnchorPointBlockEntity anchor) {
                        if (index == 0) return anchor.getRemainingCooldownSeconds();
                        if (index == 1) return anchor.getHarborStatus().ordinal();
                    }
                    return index == 1 ? AnchorPointBlockEntity.HarborStatus.NO_WATER.ordinal() : 0;
                }, index == 1 ? AnchorPointBlockEntity.HarborStatus.NO_WATER.ordinal() : 0);
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    // --- Data accessors  ---

    public int getCooldownSeconds() {
        return this.data.get(0);
    }

    public AnchorPointBlockEntity.HarborStatus getHarborStatus() {
        int ordinal = this.data.get(1);
        AnchorPointBlockEntity.HarborStatus[] values = AnchorPointBlockEntity.HarborStatus.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : AnchorPointBlockEntity.HarborStatus.VALID;
    }

    public boolean isNearWater() {
        return getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.broadcastChanges();
    }

    // --- Position resolution  ---

    /**
     * Returns the anchor point position this menu is attached to.
     * Searches around the player if the stored position doesn't resolve to a block entity.
     */
    public BlockPos getOriginPos() {
        if (this.currentPos != null && !this.currentPos.equals(BlockPos.ZERO)) {
            if (this.player != null && this.player.level() != null) {
                if (this.player.level().getBlockEntity(this.currentPos) instanceof AnchorPointBlockEntity) {
                    return this.currentPos;
                }
            } else {
                return this.currentPos;
            }
        }
        if (this.player != null && this.player.level() != null) {
            BlockPos pPos = this.player.blockPosition();
            for (BlockPos check : BlockPos.betweenClosed(pPos.offset(-5, -3, -5), pPos.offset(5, 3, 5))) {
                if (this.player.level().getBlockEntity(check) instanceof AnchorPointBlockEntity) {
                    return check.immutable();
                }
            }
            return this.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    public BlockPos getCurrentPos() {
        return getOriginPos();
    }

    // --- Slot helpers  ---

    public ItemStack getChartStack() {
        return this.travelContainer.getItem(0);
    }

    public ItemStack getDoubloonStack() {
        return this.travelContainer.getItem(1);
    }

    // --- Distance & fee calculation  ---

    public int getDistance() {
        ItemStack chart = getChartStack();
        if (chart.getItem() instanceof NauticalChartItem) {
            BlockPos target = NauticalChartItem.getTargetPos(chart);
            if (target != null) {
                BlockPos origin = getOriginPos();
                double dx = target.getX() - origin.getX();
                double dz = target.getZ() - origin.getZ();
                return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            }
        }
        return 0;
    }

    /** 1 Doubloon per 100 blocks, minimum 1. */
    public int getRequiredDoubloons() {
        int distance = getDistance();
        if (distance <= 0) return 0;
        return Math.max(1, (int) Math.ceil(distance / 100.0));
    }

    public static int getMaxDoubloons() {
        int maxDist = (com.lexovian.currentsoftrade.Config.MAX_VOYAGE_DISTANCE != null)
                ? com.lexovian.currentsoftrade.Config.MAX_VOYAGE_DISTANCE.get() : 6400;
        return Math.max(1, (int) Math.ceil(maxDist / 100.0));
    }

    public boolean isTooExpensive() {
        return getRequiredDoubloons() > getMaxDoubloons();
    }

    public boolean canTravel() {
        if (!isNearWater()) return false;
        if (getCooldownSeconds() > 0) return false;
        int req = getRequiredDoubloons();
        int maxFee = getMaxDoubloons();
        if (req <= 0 || req > maxFee) return false;
        ItemStack doubloons = getDoubloonStack();
        return !doubloons.isEmpty() && doubloons.getCount() >= req;
    }

    // --- Travel execution  ---

    public boolean executeTravel(ServerPlayer player) {
        if (!canTravel()) return false;

        ItemStack chart = getChartStack();
        BlockPos target = NauticalChartItem.getTargetPos(chart);
        if (target == null) return false;

        // Sea voyages are restricted to the Overworld
        if (player.level().dimension() != Level.OVERWORLD) {
            player.displayClientMessage(Component.translatable("message.currents_of_trade.overworld_only"), true);
            return false;
        }

        int req = getRequiredDoubloons();
        this.travelContainer.removeItem(1, req);

        // Start cooldown on the departure anchor point
        this.access.execute((level, p) -> {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof AnchorPointBlockEntity anchor) {
                anchor.triggerTravelCooldown();
            }
        });

        // Find the highest open water surface adjacent to the anchor point
        BlockPos origin = getOriginPos();
        BlockPos waterSpawn = null;
        int highestWaterY = Integer.MIN_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (player.level().getFluidState(check).is(FluidTags.WATER)) {
                        BlockPos above = check.above();
                        if (!player.level().getFluidState(above).is(FluidTags.WATER)) {
                            if (check.getY() > highestWaterY) {
                                highestWaterY = check.getY();
                                waterSpawn = check.immutable();
                            }
                        }
                    }
                }
            }
        }
        if (waterSpawn == null) waterSpawn = origin;

        // Spawn the travel Sloop on the water surface, facing the destination
        String destHarbor = NauticalChartItem.getHarborName(chart);
        if (destHarbor == null) destHarbor = UNKNOWN_PORT;

        double spawnX = waterSpawn.getX() + 0.5;
        double spawnY = waterSpawn.getY() + 0.85;
        double spawnZ = waterSpawn.getZ() + 0.5;

        double initDx = (target.getX() + 0.5) - spawnX;
        double initDz = (target.getZ() + 0.5) - spawnZ;
        float initialYaw = (float) (Mth.atan2(initDz, initDx) * (180.0 / Math.PI)) - 90.0F;

        TradeBoatEntity boat = new TradeBoatEntity(player.level(), spawnX, spawnY, spawnZ);
        boat.setYRot(initialYaw);
        boat.setYHeadRot(initialYaw);
        boat.yRotO = initialYaw;
        boat.setVoyageShip(true);
        boat.setVoyageTarget(target, destHarbor);
        boat.setCustomName(Component.literal("Trade Sloop"));
        boat.setCustomNameVisible(true);
        player.level().addFreshEntity(boat);

        // Mount the player into the sloop at deck height
        player.teleportTo(spawnX, spawnY + 0.1, spawnZ);
        player.setYRot(initialYaw);
        player.setYHeadRot(initialYaw);
        player.startRiding(boat, true);

        // Departure audio & HUD
        player.level().playSound(null, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.currents_of_trade.voyage_departed", destHarbor), true);

        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.travelContainer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack currentStack = slot.getItem();
            itemstack = currentStack.copy();

            if (index == 0 || index == 1) {
                // From custom slots -> inventory
                if (!this.moveItemStackTo(currentStack, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From inventory -> matching custom slot
                if (currentStack.getItem() instanceof NauticalChartItem) {
                    if (!this.moveItemStackTo(currentStack, 0, 1, false)) return ItemStack.EMPTY;
                } else if (currentStack.getItem() instanceof DoubloonItem) {
                    if (!this.moveItemStackTo(currentStack, 1, 2, false)) return ItemStack.EMPTY;
                } else if (index < 29) {
                    if (!this.moveItemStackTo(currentStack, 29, 38, false)) return ItemStack.EMPTY;
                } else if (index < 38) {
                    if (!this.moveItemStackTo(currentStack, 2, 29, false)) return ItemStack.EMPTY;
                }
            }

            if (currentStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.access == ContainerLevelAccess.NULL) return true;
        return this.access.evaluate((level, pos) ->
                level.getBlockState(pos).is(CurrentsofTrade.ANCHOR_POINT.get())
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true
        );
    }
}
