package com.lexovian.currentsoftrade.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Coarse Sea Salt Pouch.
 *
 * <p>Uses:
 * <ul>
 *   <li>Primary (Right-Click from hand): Salts the food item held in the other hand.</li>
 *   <li>Inventory (Right-Click on food stack in inventory screen): Salts the target stack directly.</li>
 *   <li>Effect: Increases nutrition (+2 hunger shanks) and saturation (+2.0), tags as Salted.</li>
 * </ul>
 */
public class SaltPouchItem extends Item {

    public SaltPouchItem(Properties properties) {
        super(properties);
    }

    public static boolean applySalt(ItemStack foodStack, Level level, Player player) {
        if (!foodStack.has(DataComponents.FOOD) || isSalted(foodStack)) {
            return false;
        }

        FoodProperties original = foodStack.get(DataComponents.FOOD);
        if (original != null) {
            int boostedNutrition = original.nutrition() + 2;
            float boostedSaturation = original.saturation() + 2.0F;
            FoodProperties boosted = new FoodProperties(
                    boostedNutrition,
                    boostedSaturation,
                    original.canAlwaysEat()
            );
            foodStack.set(DataComponents.FOOD, boosted);
        }

        CustomData.update(DataComponents.CUSTOM_DATA, foodStack, tag -> tag.putBoolean("Salted", true));
        return true;
    }

    public static boolean isSalted(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) {
            return false;
        }
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        return existing != null && existing.copyTag().getBooleanOr("Salted", false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack saltStack = player.getItemInHand(hand);
        InteractionHand other = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack targetStack = player.getItemInHand(other);

        if (targetStack.isEmpty() || !targetStack.has(DataComponents.FOOD)) {
            if (level.isClientSide()) {
                player.sendOverlayMessage(
                        Component.translatable("message.currents_of_trade.salt_no_food"));
            }
            return InteractionResult.FAIL;
        }

        if (isSalted(targetStack)) {
            if (level.isClientSide()) {
                player.sendOverlayMessage(
                        Component.translatable("message.currents_of_trade.salt_already_salted"));
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            Component foodName = targetStack.getHoverName().copy();
            applySalt(targetStack, level, player);

            if (!player.hasInfiniteMaterials()) {
                saltStack.shrink(1);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

            player.sendOverlayMessage(
                    Component.translatable("message.currents_of_trade.salt_applied", foodName));

            if (player instanceof ServerPlayer sp) {
                sp.inventoryMenu.broadcastChanges();
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack saltStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }

        ItemStack targetStack = slot.getItem();
        if (targetStack.isEmpty() || !targetStack.has(DataComponents.FOOD) || isSalted(targetStack)) {
            return false;
        }

        Component foodName = targetStack.getHoverName().copy();
        applySalt(targetStack, player.level(), player);
        slot.setChanged();

        if (!player.hasInfiniteMaterials()) {
            saltStack.shrink(1);
            slot.setChanged();
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

        player.sendOverlayMessage(
                Component.translatable("message.currents_of_trade.salt_applied", foodName));

        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }

        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack saltStack, ItemStack targetStack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }

        if (targetStack.isEmpty() || !targetStack.has(DataComponents.FOOD) || isSalted(targetStack)) {
            return false;
        }

        Component foodName = targetStack.getHoverName().copy();
        applySalt(targetStack, player.level(), player);

        if (!player.hasInfiniteMaterials()) {
            saltStack.shrink(1);
            slot.setChanged();
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

        player.sendOverlayMessage(
                Component.translatable("message.currents_of_trade.salt_applied", foodName));

        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.salt_pouch_use"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.salt_pouch_effect"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.commodity.salt_pouch.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
