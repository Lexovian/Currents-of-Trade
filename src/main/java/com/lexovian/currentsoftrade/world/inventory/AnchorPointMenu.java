package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AnchorPointMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player player;
    private final ContainerData data;

    // --- Constructors & Factory ---

    public AnchorPointMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos(), buf.readVarInt());
    }

    private AnchorPointMenu(int containerId, Inventory playerInventory, BlockPos pos, int level) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, pos, createClientContainerData(pos, level));
    }

    private static ContainerData createClientContainerData(BlockPos pos, int level) {
        SimpleContainerData clientData = new SimpleContainerData(6);
        clientData.set(0, level);
        clientData.set(1, pos.getX());
        clientData.set(2, pos.getY());
        clientData.set(3, pos.getZ());
        clientData.set(4, 0);
        clientData.set(5, 0);
        return clientData;
    }

    public AnchorPointMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO, createClientContainerData(BlockPos.ZERO, 1));
    }

    public AnchorPointMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos pos) {
        this(containerId, playerInventory, access, pos, createContainerData(access, pos));
    }

    private static ContainerData createContainerData(ContainerLevelAccess access, BlockPos pos) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> access.evaluate((level, p) -> {
                        if (level.getBlockEntity(p) instanceof AnchorPointBlockEntity a) {
                            return a.getTradeLevel();
                        }
                        return 1;
                    }).orElse(1);
                    case 1 -> pos.getX();
                    case 2 -> pos.getY();
                    case 3 -> pos.getZ();
                    case 4 -> access.evaluate((level, p) -> {
                        if (level.getBlockEntity(p) instanceof AnchorPointBlockEntity a) {
                            return a.getSpecialization().getIndex();
                        }
                        return 0;
                    }).orElse(0);
                    case 5 -> access.evaluate((level, p) -> {
                        if (level.getBlockEntity(p) instanceof AnchorPointBlockEntity a && a.isSpecializationChosen()) {
                            return 1;
                        }
                        return 0;
                    }).orElse(0);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    public AnchorPointMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos pos, ContainerData data) {
        super(CurrentsofTrade.ANCHOR_POINT_MENU.get(), containerId);
        this.access = access;
        this.pos = pos;
        this.player = playerInventory.player;
        this.data = data;

        int invStartY = 157;
        int hotbarY = 217;

        // Player Inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 18 + col * 18, invStartY + row * 18));
            }
        }

        // Player Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 18 + col * 18, hotbarY));
        }

        this.addDataSlots(data);
    }

    // --- Harbor Data & Upgrades ---

    public BlockPos getBlockPos() {
        if (this.pos != null && !this.pos.equals(BlockPos.ZERO)) {
            return this.pos;
        }
        if (this.data != null && (this.data.get(1) != 0 || this.data.get(2) != 0 || this.data.get(3) != 0)) {
            return new BlockPos(this.data.get(1), this.data.get(2), this.data.get(3));
        }
        if (this.player != null) {
            return this.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    private AnchorPointBlockEntity findAnchorBE() {
        if (this.player == null || this.player.level() == null) return null;
        BlockPos p = getBlockPos();
        var level = this.player.level();
        if (level.getBlockEntity(p) instanceof AnchorPointBlockEntity a) return a;

        BlockPos pPos = this.player.blockPosition();
        for (BlockPos check : BlockPos.betweenClosed(pPos.offset(-5, -3, -5), pPos.offset(5, 3, 5))) {
            if (level.getBlockEntity(check) instanceof AnchorPointBlockEntity a)
                return a;
        }
        return null;
    }

    public String getHarborName() {
        var anchor = findAnchorBE();
        return anchor != null ? anchor.getHarborName() : "";
    }

    public int getHarborLevel() {
        if (this.data != null) {
            int lvl = this.data.get(0);
            if (lvl >= 1 && lvl <= 5) return lvl;
        }
        var anchor = findAnchorBE();
        return anchor != null ? anchor.getTradeLevel() : 1;
    }

    public HarborUpgradeCost getUpgradeCost() {
        return HarborUpgradeCost.forLevel(getHarborLevel());
    }

    public boolean canPlayerUpgrade() {
        int level = getHarborLevel();
        if (level >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) return false;
        HarborUpgradeCost cost = getUpgradeCost();
        return cost != null && this.player != null && cost.canAfford(this.player);
    }

    public HarborSpecialization getSpecialization() {
        if (this.data != null && this.data.getCount() >= 5) {
            int idx = this.data.get(4);
            if (idx > 0) return HarborSpecialization.byIndex(idx);
        }
        var anchor = findAnchorBE();
        return anchor != null ? anchor.getSpecialization() : HarborSpecialization.NONE;
    }

    public boolean isSpecializationChosen() {
        if (this.data != null && this.data.getCount() >= 6) {
            if (this.data.get(5) != 0) return true;
        }
        var anchor = findAnchorBE();
        return anchor != null && anchor.isSpecializationChosen();
    }

    public boolean canAffordSpecializationSwitch() {
        if (!isSpecializationChosen()) return true;
        if (this.player == null) return false;
        if (this.player.getAbilities().instabuild) return true;
        return AnchorPointBlockEntity.countPlayerMatchingItems(
                this.player, new ItemStack(CurrentsofTrade.DOUBLOON.get())) >= HarborSpecialization.SWITCH_COST_DOUBLOONS;
    }

    // --- Container Operations ---
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack currentStack = slot.getItem();
            itemstack = currentStack.copy();

            // Slots 0-26: player inventory, Slots 27-35: hotbar
            if (index < 27) {
                if (!this.moveItemStackTo(currentStack, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < 36) {
                if (!this.moveItemStackTo(currentStack, 0, 27, false)) {
                    return ItemStack.EMPTY;
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
        if (this.access == ContainerLevelAccess.NULL) {
            return true;
        }
        return this.access.evaluate((level, pos) -> {
            return level.getBlockState(pos).is(CurrentsofTrade.ANCHOR_POINT.get())
                    && player.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0;
        }, true);
    }
}
