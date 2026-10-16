package com.lexovian.currentsoftrade.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.world.level.Level;

/**
 * Salt Pouch - Increases nutrition and saturation of food items and marks them as salted.
 * Supports both hand use (off-hand food) and inventory click combination.
 */
public class SaltPouchItem extends Item {

    public SaltPouchItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    /** Bonus nutrition granted by salt (matches the 1.21 FOOD component boost). */
    public static final int SALT_NUTRITION_BONUS = 2;
    /** Bonus saturation points granted by salt (matches the 1.21 FOOD component boost). */
    public static final float SALT_SATURATION_BONUS = 2.0F;

    /**
     * Applies salt to a food item, boosting nutrition (+2) and saturation (+2.0f).
     *
     * <p>1.20.1 note: food values are per-item, so the stack is only marked as salted here and the
     * bonus is granted on consumption via {@link #applySaltedBonus(LivingEntity, ItemStack)}.</p>
     */
    public static boolean applySalt(ItemStack foodStack, Level level, Player player) {
        if (foodStack.isEmpty() || !foodStack.isEdible() || isSalted(foodStack)) {
            return false;
        }

        CustomData.update(foodStack, tag -> tag.putBoolean("Salted", true));
        return true;
    }

    /** Grants the salted-food bonus after a salted food has been eaten. */
    public static void applySaltedBonus(LivingEntity entity, ItemStack eatenStack) {
        if (entity instanceof Player player && isSalted(eatenStack)) {
            FoodData food = player.getFoodData();
            // eat(n, m) adds n nutrition and n * m * 2 saturation -> 2 * 0.5 * 2 = 2.0 saturation
            food.eat(SALT_NUTRITION_BONUS, SALT_SATURATION_BONUS / (2.0F * SALT_NUTRITION_BONUS));
        }
    }

    public static boolean isSalted(ItemStack stack) {
        if (stack.isEmpty() || !stack.isEdible()) {
            return false;
        }
        CustomData existing = CustomData.getOrNull(stack);
        return existing != null && existing.copyTag().getBoolean("Salted");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack saltStack = player.getItemInHand(hand);
        InteractionHand other = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack targetStack = player.getItemInHand(other);

        if (targetStack.isEmpty() || !targetStack.isEdible()) {
            if (level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.salt_no_food"), true);
            }
            return InteractionResultHolder.fail(saltStack);
        }

        if (isSalted(targetStack)) {
            if (level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.salt_already_salted"), true);
            }
            return InteractionResultHolder.fail(saltStack);
        }

        Component foodName = targetStack.getHoverName().copy();

        if (!level.isClientSide) {
            applySalt(targetStack, level, player);

            if (!player.getAbilities().instabuild) {
                saltStack.shrink(1);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.salt_applied", foodName), true);

            if (player instanceof ServerPlayer sp) {
                sp.inventoryMenu.broadcastChanges();
            }
        }

        return InteractionResultHolder.sidedSuccess(saltStack, level.isClientSide);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack saltStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }

        ItemStack targetStack = slot.getItem();
        if (targetStack.isEmpty() || !targetStack.isEdible() || isSalted(targetStack)) {
            return false;
        }

        Component foodName = targetStack.getHoverName().copy();
        applySalt(targetStack, player.level(), player);
        slot.setChanged();

        if (!player.getAbilities().instabuild) {
            saltStack.shrink(1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

        player.displayClientMessage(
                Component.translatable("message.currents_of_trade.salt_applied", foodName), true);

        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }

        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack saltStack, ItemStack other, Slot slot,
                                           ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }

        if (other.isEmpty() || !other.isEdible() || isSalted(other)) {
            return false;
        }

        Component foodName = other.getHoverName().copy();
        applySalt(other, player.level(), player);

        if (!player.getAbilities().instabuild) {
            saltStack.shrink(1);
            slot.setChanged();
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);

        player.displayClientMessage(
                Component.translatable("message.currents_of_trade.salt_applied", foodName), true);

        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, java.util.List<Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.salt_pouch_use"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.salt_pouch_effect"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.commodity.salt_pouch.market"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
