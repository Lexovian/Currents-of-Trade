package com.lexovian.currentsoftrade.world.inventory;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AnchorPointMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player player;

    public AnchorPointMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, BlockPos.ZERO);
    }

    public AnchorPointMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos pos) {
        super(CurrentsofTrade.ANCHOR_POINT_MENU.get(), containerId);
        this.access = access;
        this.pos = pos;
        this.player = playerInventory.player;

        // Player Inventory (3 rows x 9 columns at x=18, y=123)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 18 + col * 18, 123 + row * 18));
            }
        }

        // Player Hotbar (9 slots at x=18, y=183)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 18 + col * 18, 183));
        }
    }

    public BlockPos getBlockPos() {
        if (this.pos != null && !this.pos.equals(BlockPos.ZERO)) {
            return this.pos;
        }
        if (this.player != null) {
            return this.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    public String getHarborName() {
        if (this.player != null && this.player.level() != null) {
            BlockPos p = getBlockPos();
            net.minecraft.world.level.block.entity.BlockEntity be = this.player.level().getBlockEntity(p);
            if (be instanceof com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity anchor) {
                return anchor.getHarborName();
            }
            for (BlockPos check : BlockPos.betweenClosed(this.player.blockPosition().offset(-5, -3, -5), this.player.blockPosition().offset(5, 3, 5))) {
                if (this.player.level().getBlockEntity(check) instanceof com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity anchor) {
                    return anchor.getHarborName();
                }
            }
        }
        return "";
    }

    @Override
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
