package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
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

public class SendItemsMenu extends AbstractContainerMenu {

    public static final int MAX_DOUBLOONS = 64;
    public static final int CARGO_SLOT_COUNT = 18;

    private final ContainerLevelAccess access;
    private final BlockPos currentPos;
    private final Player player;
    private final SimpleContainer travelContainer = new SimpleContainer(2);
    private final SimpleContainer cargoContainer = new SimpleContainer(CARGO_SLOT_COUNT);
    private final ContainerData data;

    public SendItemsMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO, createClientContainerData());
    }

    private static ContainerData createClientContainerData() {
        SimpleContainerData clientData = new SimpleContainerData(2);
        clientData.set(1, AnchorPointBlockEntity.HarborStatus.VALID.ordinal());
        return clientData;
    }

    public SendItemsMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos) {
        this(containerId, playerInventory, access, currentPos, createContainerData(access, currentPos));
    }

    public SendItemsMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos, ContainerData data) {
        super(CurrentsofTrade.SEND_ITEMS_MENU.get(), containerId);
        this.access = access;
        this.currentPos = currentPos;
        this.player = playerInventory.player;
        this.data = data;

        this.travelContainer.addListener(this::slotsChanged);
        this.cargoContainer.addListener(this::slotsChanged);

        // Slot 0: Nautical Chart Slot (at x=24, y=36)
        this.addSlot(new Slot(this.travelContainer, 0, 24, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof NauticalChartItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 1: Doubloon Fee Slot (at x=64, y=36)
        this.addSlot(new Slot(this.travelContainer, 1, 64, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DoubloonItem;
            }
        });

        // Cargo slots: 18 slots (2 rows x 9 columns) starting at x=18, y=70
        for (int row = 0; row < 2; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(this.cargoContainer, col + row * 9, 18 + col * 18, 70 + row * 18));
            }
        }

        // Player Inventory (3 rows x 9 columns at x=18, y=147)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 18 + col * 18, 147 + row * 18));
            }
        }

        // Player Hotbar (9 slots at x=18, y=207)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 18 + col * 18, 207));
        }

        this.addDataSlots(data);
    }

    private static ContainerData createContainerData(ContainerLevelAccess access, BlockPos pos) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return access.evaluate((level, blockPos) -> {
                    BlockEntity be = level.getBlockEntity(blockPos);
                    if (be instanceof AnchorPointBlockEntity anchor) {
                        if (index == 0) {
                            return anchor.getRemainingCooldownSeconds();
                        } else if (index == 1) {
                            return anchor.getHarborStatus().ordinal();
                        }
                    }
                    return 0;
                }).orElse(0);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public int getCooldownSeconds() {
        return this.data.get(0);
    }

    public AnchorPointBlockEntity.HarborStatus getHarborStatus() {
        int ordinal = this.data.get(1);
        AnchorPointBlockEntity.HarborStatus[] values = AnchorPointBlockEntity.HarborStatus.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return AnchorPointBlockEntity.HarborStatus.VALID;
    }

    public boolean isNearWater() {
        return getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.broadcastChanges();
    }

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
        if (this.player != null) {
            if (this.player.level() != null) {
                BlockPos pPos = this.player.blockPosition();
                for (BlockPos check : BlockPos.betweenClosed(pPos.offset(-5, -3, -5), pPos.offset(5, 3, 5))) {
                    if (this.player.level().getBlockEntity(check) instanceof AnchorPointBlockEntity) {
                        return check.immutable();
                    }
                }
            }
            return this.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    public BlockPos getCurrentPos() {
        return getOriginPos();
    }

    public ItemStack getChartStack() {
        return this.travelContainer.getItem(0);
    }

    public ItemStack getDoubloonStack() {
        return this.travelContainer.getItem(1);
    }

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

    public int getRequiredFee() {
        int dist = getDistance();
        if (dist <= 0) return 0;
        int fee = (int) Math.ceil(dist / 100.0);
        return Math.max(1, fee);
    }

    public static int getMaxDoubloons() {
        int maxDist = (com.lexovian.currentsoftrade.Config.MAX_VOYAGE_DISTANCE != null)
                ? com.lexovian.currentsoftrade.Config.MAX_VOYAGE_DISTANCE.get() : 6400;
        return Math.max(1, (int) Math.ceil(maxDist / 100.0));
    }

    public boolean isTooExpensive() {
        return getRequiredFee() > getMaxDoubloons();
    }

    public boolean hasEnoughDoubloons() {
        int required = getRequiredFee();
        if (required <= 0) return false;
        if (required > getMaxDoubloons()) return false;
        ItemStack doubloons = getDoubloonStack();
        return !doubloons.isEmpty() && doubloons.getItem() instanceof DoubloonItem && doubloons.getCount() >= required;
    }

    public boolean hasValidChart() {
        ItemStack chart = getChartStack();
        return !chart.isEmpty() && chart.getItem() instanceof NauticalChartItem && NauticalChartItem.isBound(chart);
    }

    public boolean hasCargo() {
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            if (!this.cargoContainer.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int getCargoItemCount() {
        int count = 0;
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            ItemStack stack = this.cargoContainer.getItem(i);
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean canSendCargo() {
        return getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID
                && getCooldownSeconds() == 0
                && !isTooExpensive()
                && hasValidChart()
                && hasEnoughDoubloons()
                && hasCargo();
    }

    public boolean executeSendCargo(ServerPlayer player) {
        if (!canSendCargo()) {
            player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_failed"), true);
            return false;
        }

        ItemStack chart = getChartStack();
        BlockPos target = NauticalChartItem.getTargetPos(chart);
        String destHarbor = NauticalChartItem.getTargetHarbor(chart);
        if (target == null) return false;

        // Deduct shipping fee
        int requiredFee = getRequiredFee();
        ItemStack doubloons = getDoubloonStack();
        doubloons.shrink(requiredFee);
        this.travelContainer.setItem(1, doubloons);

        // Find initial launch position on local water surface
        BlockPos origin = getOriginPos();
        Level level = player.level();
        BlockPos waterStart = null;
        for (BlockPos check : BlockPos.betweenClosed(origin.offset(-4, -3, -4), centerOffset(origin, 4, 1, 4))) {
            if (level.getFluidState(check).is(FluidTags.WATER)) {
                waterStart = check.immutable();
                break;
            }
        }
        if (waterStart == null) {
            waterStart = origin;
        }

        int topWaterY = waterStart.getY();
        while (topWaterY < level.getMaxBuildHeight() && level.getFluidState(new BlockPos(waterStart.getX(), topWaterY + 1, waterStart.getZ())).is(FluidTags.WATER)) {
            topWaterY++;
        }

        double spawnX = waterStart.getX() + 0.5;
        double spawnY = topWaterY + 0.85;
        double spawnZ = waterStart.getZ() + 0.5;

        double dx = (target.getX() + 0.5) - spawnX;
        double dz = (target.getZ() + 0.5) - spawnZ;
        float initialYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

        // Initialize and load autonomous freight entity
        CargoBoatEntity cargoBoat = new CargoBoatEntity(level, spawnX, spawnY, spawnZ);
        cargoBoat.setYRot(initialYaw);
        cargoBoat.setYHeadRot(initialYaw);
        cargoBoat.yRotO = initialYaw;
        cargoBoat.setVoyageTarget(target, destHarbor);
        cargoBoat.setSender(this.player);

        String originName = "Home Harbor";
        if (level.getBlockEntity(origin) instanceof AnchorPointBlockEntity anchor) {
            originName = anchor.getHarborName();
        }
        cargoBoat.setHomeHarbor(origin, originName);

        cargoBoat.setCustomName(Component.literal("Cargo Ship"));
        cargoBoat.setCustomNameVisible(true);

        int totalItems = 0;
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            ItemStack stack = this.cargoContainer.getItem(i);
            if (!stack.isEmpty()) {
                cargoBoat.setItem(i, stack.copy());
                totalItems += stack.getCount();
            }
        }
        this.cargoContainer.clearContent();
        level.addFreshEntity(cargoBoat);

        if (level.getBlockEntity(origin) instanceof AnchorPointBlockEntity anchor) {
            anchor.triggerTravelCooldown();
        }

        level.playSound(null, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.currents_of_trade.cargo_departed", destHarbor, totalItems), false);

        return true;
    }

    private static BlockPos centerOffset(BlockPos pos, int x, int y, int z) {
        return pos.offset(x, y, z);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.travelContainer);
        this.clearContainer(player, this.cargoContainer);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack currentStack = slot.getItem();
            itemstack = currentStack.copy();

            // Total slots breakdown:
            // 0: Chart
            // 1: Doubloon
            // 2..19: Cargo (18 slots)
            // 20..46: Player main inventory (27 slots)
            // 47..55: Player hotbar (9 slots)
            if (index == 0 || index == 1) {
                // Move from chart/doubloon to player inventory
                if (!this.moveItemStackTo(currentStack, 20, 56, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 2 && index < 20) {
                // Move from cargo slots to player inventory
                if (!this.moveItemStackTo(currentStack, 20, 56, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Move from player inventory into menu
                if (currentStack.getItem() instanceof NauticalChartItem) {
                    if (!this.moveItemStackTo(currentStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (currentStack.getItem() instanceof DoubloonItem) {
                    if (!this.moveItemStackTo(currentStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try to move into cargo hold
                    if (!this.moveItemStackTo(currentStack, 2, 20, false)) {
                        if (index < 47) {
                            if (!this.moveItemStackTo(currentStack, 47, 56, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (!this.moveItemStackTo(currentStack, 20, 47, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
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
}
