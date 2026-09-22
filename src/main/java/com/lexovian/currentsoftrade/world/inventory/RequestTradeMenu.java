package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RequestTradeMenu extends AbstractContainerMenu {

    private final Container tradeContainer;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos currentPos;
    private final Player player;
    private final Level level;

    private int selectedTradeIndex = 0;

    // Staged batch trade orders (Shopping Cart)
    private final List<ItemStack> stagedPayments = new ArrayList<>();
    private final List<ItemStack> stagedRewards = new ArrayList<>();
    private int stagedTradeCount = 0;
    private int stagedItemCount = 0;
    private int cargoSurcharge = 0;

    public static final int SLOT_CHART = 0;
    public static final int SLOT_FEE = 1;
    public static final int SLOT_COST_A = 2;
    public static final int SLOT_COST_B = 3;

    // Client-side constructor
    public RequestTradeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO, new SimpleContainerData(5));
    }

    // Server-side constructor
    public RequestTradeMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos) {
        this(containerId, playerInventory, access, currentPos, new SimpleContainerData(5));
    }

    public RequestTradeMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos currentPos, ContainerData data) {
        super(CurrentsofTrade.REQUEST_TRADE_MENU.get(), containerId);
        checkContainerDataCount(data, 5);

        this.tradeContainer = new SimpleContainer(4);
        this.data = data;
        this.access = access;
        this.currentPos = currentPos;
        this.player = playerInventory.player;
        this.level = playerInventory.player.level();

        if (this.tradeContainer instanceof SimpleContainer sc) {
            sc.addListener(this::slotsChanged);
        }

        // Slot 0: Nautical Chart or Route Journal (x=24, y=36)
        this.addSlot(new Slot(this.tradeContainer, SLOT_CHART, 24, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof NauticalChartItem || stack.getItem() instanceof RouteJournalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 1: Doubloon Fee (x=64, y=36)
        this.addSlot(new Slot(this.tradeContainer, SLOT_FEE, 64, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DoubloonItem;
            }
        });

        // Slot 2: Cost A (x=24, y=87)
        this.addSlot(new Slot(this.tradeContainer, SLOT_COST_A, 24, 87));

        // Slot 3: Cost B (x=44, y=87)
        this.addSlot(new Slot(this.tradeContainer, SLOT_COST_B, 44, 87));

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

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (!this.level.isClientSide && this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ItemStack chart = getChartStack();
            if (hasValidChart()) {
                BlockPos targetPos = NauticalChartItem.getTargetPos(chart);
                if (targetPos != null) {
                    List<HarborTradeOffer> liveTrades = HarborSavedData.get(serverLevel).getTrades(targetPos);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, chart, tag -> {
                        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                        for (HarborTradeOffer offer : liveTrades) {
                            list.add(offer.toTag(serverLevel.registryAccess()));
                        }
                        tag.put("HarborTrades", list);
                    });
                }
            }
        }
        this.broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (!this.level.isClientSide) {
            this.access.execute((lvl, pos) -> {
                BlockEntity be = lvl.getBlockEntity(pos);
                if (be instanceof AnchorPointBlockEntity anchor) {
                    this.data.set(0, anchor.getRemainingCooldownSeconds());
                    this.data.set(1, anchor.getHarborStatus().ordinal());
                }
            });
            this.data.set(2, this.stagedTradeCount);
            this.data.set(3, this.stagedItemCount);
            this.data.set(4, this.cargoSurcharge);
        }
        super.broadcastChanges();
    }

    public BlockPos getCurrentPos() {
        return currentPos;
    }

    public int getSelectedTradeIndex() {
        return selectedTradeIndex;
    }

    public void setSelectedTradeIndex(int index) {
        this.selectedTradeIndex = index;
    }

    public ItemStack getChartStack() {
        return this.tradeContainer.getItem(SLOT_CHART);
    }

    public boolean hasValidChart() {
        ItemStack chart = getChartStack();
        return !chart.isEmpty() && chart.getItem() instanceof NauticalChartItem && NauticalChartItem.isBound(chart);
    }

    public BlockPos getTargetPos() {
        ItemStack stack = getChartStack();
        if (hasValidChart()) {
            return NauticalChartItem.getTargetPos(stack);
        }
        return null;
    }

    public String getTargetHarborName() {
        ItemStack stack = getChartStack();
        if (hasValidChart()) {
            return NauticalChartItem.getTargetHarbor(stack);
        }
        return "No Chart";
    }

    public List<HarborTradeOffer> getHarborTrades() {
        ItemStack stack = getChartStack();
        if (hasValidChart()) {
            return NauticalChartItem.getHarborTrades(stack, this.level.registryAccess(), this.level);
        }
        return Collections.emptyList();
    }

    public HarborTradeOffer getSelectedTrade() {
        List<HarborTradeOffer> trades = getHarborTrades();
        if (!trades.isEmpty() && selectedTradeIndex >= 0 && selectedTradeIndex < trades.size()) {
            return trades.get(selectedTradeIndex);
        }
        return null;
    }

    public double getDistance() {
        BlockPos target = getTargetPos();
        if (target == null) return 0;
        BlockPos origin = (this.currentPos != null && !this.currentPos.equals(BlockPos.ZERO))
                ? this.currentPos : this.player.blockPosition();
        double dx = origin.getX() - target.getX();
        double dz = origin.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int getBaseFee() {
        double dist = getDistance();
        if (dist <= 0) return 0;
        // Half travel price: 1 Doubloon per 200 blocks
        return Math.max(1, (int) Math.ceil(dist / 200.0));
    }

    public int getCargoSurcharge() {
        return this.data.get(4);
    }

    public int getRequiredFee() {
        int base = getBaseFee();
        if (base <= 0) return 0;
        return base + getCargoSurcharge();
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
        ItemStack feeStack = this.tradeContainer.getItem(SLOT_FEE);
        return !feeStack.isEmpty() && feeStack.getItem() instanceof DoubloonItem
                && feeStack.getCount() >= getRequiredFee();
    }

    public boolean hasRequiredTradeItems() {
        HarborTradeOffer trade = getSelectedTrade();
        if (trade == null) return false;

        ItemStack costA = trade.getCostA();
        ItemStack costB = trade.getCostB();

        boolean aSatisfied = costA.isEmpty() || checkSlotHasItem(SLOT_COST_A, costA) || checkSlotHasItem(SLOT_COST_B, costA);
        boolean bSatisfied = costB.isEmpty();

        if (!costB.isEmpty()) {
            if (checkSlotHasItem(SLOT_COST_A, costA) && checkSlotHasItem(SLOT_COST_B, costB)) {
                bSatisfied = true;
            } else if (checkSlotHasItem(SLOT_COST_B, costA) && checkSlotHasItem(SLOT_COST_A, costB)) {
                bSatisfied = true;
            }
        }

        return aSatisfied && bSatisfied;
    }

    public boolean isTradeItemMatching(ItemStack inSlot, ItemStack required) {
        if (required.isEmpty()) return true;
        if (inSlot.isEmpty()) return false;
        if (!ItemStack.isSameItem(inSlot, required)) return false;
        if (inSlot.getCount() < required.getCount()) return false;
        if (required.isComponentsPatchEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(inSlot, required);
    }

    private boolean checkSlotHasItem(int slotIndex, ItemStack required) {
        if (required.isEmpty()) return true;
        ItemStack inSlot = this.tradeContainer.getItem(slotIndex);
        return isTradeItemMatching(inSlot, required);
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

    public int getStagedTradeCount() {
        return this.data.get(2);
    }

    public int getStagedItemCount() {
        return this.data.get(3);
    }

    public boolean canRequestTrade() {
        boolean hasTradesToShip = getStagedTradeCount() > 0 || hasRequiredTradeItems();
        return hasValidChart()
                && getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID
                && getCooldownSeconds() <= 0
                && !isTooExpensive()
                && hasEnoughDoubloons()
                && hasTradesToShip;
    }

    // Add current trade offer to the staged manifest
    public boolean addCurrentTradeToOrder(Player player, int tradeIndex, boolean maxAll) {
        if (this.level.isClientSide) return false;
        this.selectedTradeIndex = tradeIndex;
        if (!hasValidChart()) return false;
        if (!hasRequiredTradeItems()) return false;

        HarborTradeOffer trade = getSelectedTrade();
        if (trade == null) return false;

        int addedCount = 0;
        while (hasRequiredTradeItems()) {
            ItemStack costA = trade.getCostA();
            ItemStack costB = trade.getCostB();
            ItemStack result = trade.getResult();

            // Check if adding exceeds boat container limit (18 slots)
            if (countMergedSlots(this.stagedPayments, costA, costB) > 18 || countMergedSlots(this.stagedRewards, result, ItemStack.EMPTY) > 18) {
                if (addedCount == 0) {
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.boat_hold_full"), true);
                }
                break;
            }

            // Deduct payment items from slots
            deductRequiredItem(costA);
            if (!costB.isEmpty()) {
                deductRequiredItem(costB);
            }

            // Stage payments
            mergeIntoList(this.stagedPayments, costA.copy());
            if (!costB.isEmpty()) {
                mergeIntoList(this.stagedPayments, costB.copy());
            }

            // Stage rewards
            mergeIntoList(this.stagedRewards, result.copy());

            this.stagedTradeCount++;
            addedCount++;

            if (!maxAll || addedCount >= 64) {
                break;
            }
        }

        if (addedCount > 0) {
            recalculateStagingMetrics();
            ItemStack result = trade.getResult();
            this.level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.trade_staged_success", this.stagedTradeCount, result.getCount() * addedCount, result.getHoverName().getString()),
                    true
            );
            this.broadcastChanges();
            return true;
        }

        return false;
    }

    public boolean addCurrentTradeToOrder(Player player, int tradeIndex) {
        return addCurrentTradeToOrder(player, tradeIndex, false);
    }

    public boolean addCurrentTradeToOrder(Player player) {
        return addCurrentTradeToOrder(player, this.selectedTradeIndex, false);
    }

    // Reset staged order and refund items to player
    public void resetStagedOrder(Player player) {
        if (this.level.isClientSide) return;
        for (ItemStack stack : this.stagedPayments) {
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        this.stagedPayments.clear();
        this.stagedRewards.clear();
        this.stagedTradeCount = 0;
        this.stagedItemCount = 0;
        this.cargoSurcharge = 0;
        this.data.set(2, 0);
        this.data.set(3, 0);
        this.data.set(4, 0);

        this.level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_DISPENSE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.currents_of_trade.trade_staged_cleared"), true);
        this.broadcastChanges();
    }

    private void recalculateStagingMetrics() {
        int totalItems = 0;
        int totalStacks = 0;
        for (ItemStack s : this.stagedPayments) {
            if (!s.isEmpty()) {
                totalItems += s.getCount();
                totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
            }
        }
        for (ItemStack s : this.stagedRewards) {
            if (!s.isEmpty()) {
                totalItems += s.getCount();
                totalStacks += (int) Math.ceil(s.getCount() / (double) s.getMaxStackSize());
            }
        }
        this.stagedItemCount = totalItems;
        // Shipping surcharge: +1 Doubloon per 3 item stacks
        this.cargoSurcharge = totalStacks > 0 ? (int) Math.ceil(totalStacks / 3.0) : 0;
        this.data.set(2, this.stagedTradeCount);
        this.data.set(3, this.stagedItemCount);
        this.data.set(4, this.cargoSurcharge);
    }

    private void mergeIntoList(List<ItemStack> list, ItemStack toAdd) {
        if (toAdd.isEmpty()) return;
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameComponents(existing, toAdd)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int transfer = Math.min(space, toAdd.getCount());
                existing.grow(transfer);
                toAdd.shrink(transfer);
                if (toAdd.isEmpty()) return;
            }
        }
        if (!toAdd.isEmpty()) {
            list.add(toAdd.copy());
        }
    }

    private int countMergedSlots(List<ItemStack> currentList, ItemStack addA, ItemStack addB) {
        List<ItemStack> sim = new ArrayList<>();
        for (ItemStack s : currentList) {
            sim.add(s.copy());
        }
        mergeIntoList(sim, addA.copy());
        mergeIntoList(sim, addB.copy());
        return sim.size();
    }

    public boolean executeRequestTrade(Player player, int requestedTradeIndex) {
        if (this.level.isClientSide) return false;

        // Auto-stage single trade if player placed items without explicit confirmation
        if (this.stagedTradeCount == 0 && hasRequiredTradeItems()) {
            addCurrentTradeToOrder(player);
        }

        if (this.stagedTradeCount == 0 || !canRequestTrade()) {
            return false;
        }

        BlockPos target = getTargetPos();
        String destHarbor = getTargetHarborName();
        if (target == null) return false;

        BlockPos origin = (this.currentPos != null && !this.currentPos.equals(BlockPos.ZERO))
                ? this.currentPos : player.blockPosition();

        // Locate water surface near the departure dock
        BlockPos waterStart = null;
        for (BlockPos check : BlockPos.betweenClosed(origin.offset(-4, -3, -4), origin.offset(4, 1, 4))) {
            if (this.level.getFluidState(check).is(FluidTags.WATER)) {
                waterStart = check.immutable();
                break;
            }
        }

        if (waterStart == null) {
            player.displayClientMessage(Component.translatable("message.currents_of_trade.anchor_not_near_water"), true);
            return false;
        }

        int topWaterY = waterStart.getY();
        while (this.level.getFluidState(waterStart.above()).is(FluidTags.WATER) && topWaterY < 320) {
            waterStart = waterStart.above();
            topWaterY = waterStart.getY();
        }

        // Deduct Doubloon fee (base fee + cargo surcharge)
        int fee = getRequiredFee();
        ItemStack feeStack = this.tradeContainer.getItem(SLOT_FEE);
        feeStack.shrink(fee);
        if (feeStack.isEmpty()) {
            this.tradeContainer.setItem(SLOT_FEE, ItemStack.EMPTY);
        }

        // Resolve home harbor name and trigger cooldown
        String homeHarborName = "Home Port";
        if (this.level.getBlockEntity(origin) instanceof AnchorPointBlockEntity anchor) {
            homeHarborName = anchor.getHarborName();
            anchor.triggerTravelCooldown();
        }

        // Spawn two-way trade vessel
        double spawnX = waterStart.getX() + 0.5;
        double spawnY = topWaterY + 0.85;
        double spawnZ = waterStart.getZ() + 0.5;

        double dx = (target.getX() + 0.5) - spawnX;
        double dz = (target.getZ() + 0.5) - spawnZ;
        float initialYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

        TradeBoatEntity tradeBoat = new TradeBoatEntity(this.level, spawnX, spawnY, spawnZ);
        tradeBoat.setYRot(initialYaw);
        tradeBoat.setYHeadRot(initialYaw);
        tradeBoat.yRotO = initialYaw;
        tradeBoat.setVoyageTarget(target, destHarbor);
        tradeBoat.setSender(player);
        tradeBoat.setCustomName(Component.literal("Trade Ship"));
        tradeBoat.setCustomNameVisible(true);

        // Configure as two-way Trade Mission carrying all staged rewards
        tradeBoat.setTradeMission(origin, homeHarborName, this.stagedRewards);

        // Put all staged payment items in the boat's inventory for the outbound voyage
        for (int i = 0; i < this.stagedPayments.size() && i < tradeBoat.getContainerSize(); i++) {
            tradeBoat.setItem(i, this.stagedPayments.get(i).copy());
        }

        int countTrades = this.stagedTradeCount;
        int countItems = this.stagedItemCount;

        // Clear staged lists so removed() won't refund them!
        this.stagedPayments.clear();
        this.stagedRewards.clear();
        this.stagedTradeCount = 0;
        this.stagedItemCount = 0;
        this.cargoSurcharge = 0;
        this.data.set(2, 0);
        this.data.set(3, 0);
        this.data.set(4, 0);

        this.level.addFreshEntity(tradeBoat);

        // Sound & departure notification
        this.level.playSound(null, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(
                Component.translatable("message.currents_of_trade.trade_departed_batch", destHarbor, countTrades, countItems),
                false
        );

        this.broadcastChanges();
        return true;
    }

    private void deductRequiredItem(ItemStack required) {
        if (required.isEmpty()) return;
        int needed = required.getCount();

        for (int slotIdx : new int[]{SLOT_COST_A, SLOT_COST_B}) {
            ItemStack inSlot = this.tradeContainer.getItem(slotIdx);
            if (!inSlot.isEmpty() && isTradeItemMatching(inSlot, required)) {
                int toTake = Math.min(needed, inSlot.getCount());
                inSlot.shrink(toTake);
                needed -= toTake;
                if (inSlot.isEmpty()) {
                    this.tradeContainer.setItem(slotIdx, ItemStack.EMPTY);
                } else {
                    this.tradeContainer.setItem(slotIdx, inSlot);
                }
                if (needed <= 0) break;
            }
        }
        this.tradeContainer.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos checkPos = (this.currentPos != null && !this.currentPos.equals(BlockPos.ZERO))
                ? this.currentPos : player.blockPosition();
        return player.distanceToSqr(checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // From Trade Container (0..3) to Player Inventory (4..39)
            if (index < 4) {
                if (!this.moveItemStackTo(stackInSlot, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From Player Inventory to Trade Container
                if (stackInSlot.getItem() instanceof NauticalChartItem) {
                    if (!this.moveItemStackTo(stackInSlot, SLOT_CHART, SLOT_CHART + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (stackInSlot.getItem() instanceof DoubloonItem) {
                    if (!this.moveItemStackTo(stackInSlot, SLOT_FEE, SLOT_FEE + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try cost slots
                    if (!this.moveItemStackTo(stackInSlot, SLOT_COST_A, SLOT_COST_B + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }
        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.tradeContainer);
        // Refund any staged payment items that haven't been dispatched
        for (ItemStack stack : this.stagedPayments) {
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        this.stagedPayments.clear();
        this.stagedRewards.clear();
    }
}
