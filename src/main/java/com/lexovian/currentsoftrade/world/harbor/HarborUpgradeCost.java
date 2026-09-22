package com.lexovian.currentsoftrade.world.harbor;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the material cost and benefits for each harbor upgrade level.
 *
 * <p>Level 1 is the starting state (no upgrade needed).
 * Levels 2–5 each have a Doubloon cost and a list of required vanilla items.
 *
 * <pre>
 * Lv1: Fishing Wharf  — 60s cooldown | 18 cargo slots | 6  trade offers
 * Lv2: Trading Post   — 45s cooldown | 27 cargo slots | 8  trade offers
 * Lv3: Harbor         — 30s cooldown | 36 cargo slots | 10 trade offers
 * Lv4: Grand Port     — 20s cooldown | 45 cargo slots | 12 trade offers
 * Lv5: Royal Dockyard — 10s cooldown | 54 cargo slots | 14 trade offers
 * </pre>
 */
public enum HarborUpgradeCost {

    // --- Tier Definitions ---

    LEVEL_2(8,
            List.of(
                    new ItemStack(Items.OAK_PLANKS, 16)
            )),

    LEVEL_3(20,
            List.of(
                    new ItemStack(Items.IRON_INGOT, 8)
            )),

    LEVEL_4(40,
            List.of(
                    new ItemStack(Items.GOLD_INGOT, 4),
                    new ItemStack(CurrentsofTrade.SILK_BALE.get(), 2)
            )),

    LEVEL_5(64,
            List.of(
                    new ItemStack(Items.HEART_OF_THE_SEA, 1),
                    new ItemStack(Items.PRISMARINE_SHARD, 8)
            ));

    private final int doubloonCost;
    private final List<ItemStack> materialCost;

    HarborUpgradeCost(int doubloonCost, List<ItemStack> materialCost) {
        this.doubloonCost = doubloonCost;
        this.materialCost = materialCost;
    }

    // --- Cost Resolution ---

    public static HarborUpgradeCost forLevel(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> LEVEL_2;
            case 2 -> LEVEL_3;
            case 3 -> LEVEL_4;
            case 4 -> LEVEL_5;
            default -> null;
        };
    }

    public int getDoubloonCost() {
        return doubloonCost;
    }

    public List<ItemStack> getMaterialCost() {
        return materialCost;
    }

    public List<String> getCostLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§6" + doubloonCost + " Doubloon" + (doubloonCost != 1 ? "s" : ""));
        for (ItemStack stack : materialCost) {
            lines.add("§7" + stack.getCount() + "x " + stack.getHoverName().getString());
        }
        return lines;
    }

    // --- Inventory Operations ---

    public boolean canAfford(Player player) {
        int doubloons = countItem(player, CurrentsofTrade.DOUBLOON.get());
        if (doubloons < doubloonCost) return false;
        for (ItemStack required : materialCost) {
            if (countItem(player, required.getItem()) < required.getCount()) return false;
        }
        return true;
    }

    public void consume(Player player) {
        shrinkItem(player, CurrentsofTrade.DOUBLOON.get(), doubloonCost);
        for (ItemStack required : materialCost) {
            shrinkItem(player, required.getItem(), required.getCount());
        }
    }

    // --- Helper Methods ---

    private static int countItem(Player player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) count += s.getCount();
        }
        return count;
    }

    private static void shrinkItem(Player player, net.minecraft.world.item.Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
    }
}
