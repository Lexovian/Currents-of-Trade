package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    // --- Constants ---

    public static final int MAX_DOUBLOONS = 64;
    public static final int MAX_CARGO_SLOTS = 54;
    public static final int SLOTS_PER_PAGE = 18;

    private final ContainerLevelAccess access;
    private final BlockPos currentPos;
    private final Player player;
    private final SimpleContainer travelContainer = new SimpleContainer(2);
    private final SimpleContainer cargoContainer = new SimpleContainer(MAX_CARGO_SLOTS);
    private final ContainerData data;

    private int currentDisplayPage = 0;

    // --- Constructors & Setup ---

    public SendItemsMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO, createClientContainerData());
    }

    private static ContainerData createClientContainerData() {
        SimpleContainerData clientData = new SimpleContainerData(3);
        clientData.set(0, 0); // cooldown
        clientData.set(1, AnchorPointBlockEntity.HarborStatus.VALID.ordinal());
        clientData.set(2, 18); // default cargo slot count
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

        // Slot 0: Nautical Chart or Route Journal Slot (at x=24, y=36)
        this.addSlot(new Slot(this.travelContainer, 0, 24, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof NauticalChartItem || stack.getItem() instanceof RouteJournalItem;
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

        // Cargo slots: Always add all 54 slots (3 pages of 18) to ensure client and server
        // slot counts match (total 92 slots). On client, only current page slots are active.
        for (int i = 0; i < MAX_CARGO_SLOTS; ++i) {
            final int slotIndex = i;
            final int page = i / SLOTS_PER_PAGE;
            int pageSlot = i % SLOTS_PER_PAGE;
            int row = pageSlot / 9;
            int col = pageSlot % 9;

            this.addSlot(new Slot(this.cargoContainer, i, 18 + col * 18, 70 + row * 18) {
                @Override
                public boolean isActive() {
                    if (slotIndex >= getCargoSlotCount()) {
                        return false;
                    }
                    if (SendItemsMenu.this.player != null && SendItemsMenu.this.player.level().isClientSide()) {
                        return page == SendItemsMenu.this.currentDisplayPage;
                    }
                    return true;
                }
            });
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
                        if (index == 0) return anchor.getRemainingCooldownSeconds();
                        if (index == 1) return anchor.getHarborStatus().ordinal();
                        if (index == 2) return anchor.getCargoSlotCount();
                    }
                    return 0;
                }).orElse(0);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    // --- Pagination & Cargo Hold ---

    public int getCargoSlotCount() {
        if (this.data != null && this.data.getCount() > 2) {
            int val = this.data.get(2);
            if (val >= 18 && val <= MAX_CARGO_SLOTS) return val;
        }
        return 18;
    }

    public int getCurrentPage() {
        return this.currentDisplayPage;
    }

    public int getMaxPages() {
        return Math.max(1, (int) Math.ceil((double) getCargoSlotCount() / SLOTS_PER_PAGE));
    }

    public void setPage(int page) {
        this.currentDisplayPage = Math.max(0, Math.min(getMaxPages() - 1, page));
    }

    public void nextPage() {
        if (this.currentDisplayPage < getMaxPages() - 1) {
            this.currentDisplayPage++;
        }
    }

    public void prevPage() {
        if (this.currentDisplayPage > 0) {
            this.currentDisplayPage--;
        }
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
        return AnchorPointBlockEntity.HarborStatus.NO_WATER;
    }

    public BlockPos getCurrentPos() {
        return currentPos;
    }

    public BlockPos getOriginPos() {
        if (this.currentPos != null && !this.currentPos.equals(BlockPos.ZERO)) {
            return this.currentPos;
        }
        if (this.player != null) {
            return this.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    public ItemStack getChartStack() {
        return this.travelContainer.getItem(0);
    }

    public ItemStack getDoubloonStack() {
        return this.travelContainer.getItem(1);
    }

    // --- Voyage & Fee Calculation ---

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
        for (int i = 0; i < getCargoSlotCount(); i++) {
            if (!this.cargoContainer.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int getCargoItemCount() {
        int count = 0;
        for (int i = 0; i < getCargoSlotCount(); i++) {
            ItemStack stack = this.cargoContainer.getItem(i);
            if (!stack.isEmpty()) count += stack.getCount();
        }
        return count;
    }

    // --- Cargo Dispatch ---

    public boolean canSendCargo() {
        return getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID
                && getCooldownSeconds() <= 0
                && hasValidChart()
                && hasEnoughDoubloons()
                && !isTooExpensive()
                && hasCargo();
    }

    public boolean executeSendCargo(ServerPlayer player) {
        if (!canSendCargo()) return false;

        Level rawLevel = player.level();
        if (!(rawLevel instanceof ServerLevel level)) return false;

        ItemStack chart = getChartStack();
        BlockPos target = NauticalChartItem.getTargetPos(chart);
        String destHarbor = NauticalChartItem.getTargetHarbor(chart);
        int fee = getRequiredFee();
        BlockPos origin = getOriginPos();

        this.travelContainer.removeItem(1, fee);

        CargoBoatEntity cargoBoat = new CargoBoatEntity(level, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
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
        int activeSlots = getCargoSlotCount();
        for (int i = 0; i < activeSlots; i++) {
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

    // --- Container Operations ---

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
            // 2..55: Cargo (54 slots)
            // 56..82: Player main inventory (27 slots)
            // 83..91: Player hotbar (9 slots)
            if (index == 0 || index == 1) {
                // Move from chart/doubloon to player inventory
                if (!this.moveItemStackTo(currentStack, 56, 92, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 2 && index < 56) {
                // Move from cargo slots to player inventory
                if (!this.moveItemStackTo(currentStack, 56, 92, true)) {
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
                    // Try to move into cargo hold (only active slots)
                    int activeCargoEnd = 2 + getCargoSlotCount();
                    if (!this.moveItemStackTo(currentStack, 2, activeCargoEnd, false)) {
                        if (index < 83) {
                            if (!this.moveItemStackTo(currentStack, 83, 92, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (!this.moveItemStackTo(currentStack, 56, 83, false)) {
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
