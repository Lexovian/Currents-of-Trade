package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sealed Guild Trade Charter — portable merchant guild contract requiring delivery of bulk
 * exotic commodities to an Anchor Point in a specific {@link MaritimeTradeRegion}.
 *
 * <p>Can be fulfilled either by hand at a matching harbor's Anchor Point, or automatically by
 * loading the Charter alongside its required cargo onto a Cargo Boat or Trade Ship bound for that region.
 */
public class TradeCharterItem extends Item {

    public static final String TAG_TARGET_REGION = "TargetRegion";
    public static final String TAG_REQUIRED_ITEM = "RequiredItem";
    public static final String TAG_REQUIRED_COUNT = "RequiredCount";
    public static final String TAG_REWARD_DOUBLOONS = "RewardDoubloons";
    public static final String TAG_REWARD_STRONGBOXES = "RewardStrongboxes";

    public record CharterSpec(MaritimeTradeRegion targetRegion, Item requiredItem, int requiredCount,
                              int rewardDoubloons, int rewardStrongboxes) {}

    public TradeCharterItem(Properties properties) {
        super(properties);
    }

    public static boolean isInitialized(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_TARGET_REGION) && tag.contains(TAG_REQUIRED_ITEM) && tag.getIntOr(TAG_REQUIRED_COUNT, 0) > 0;
    }

    @Nullable
    public static CharterSpec getSpec(ItemStack stack) {
        if (!isInitialized(stack)) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        MaritimeTradeRegion region = MaritimeTradeRegion.byId(tag.getStringOr(TAG_TARGET_REGION, ""));
        Identifier itemId = Identifier.tryParse(tag.getStringOr(TAG_REQUIRED_ITEM, ""));
        if (itemId == null) return null;
        Item reqItem = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (reqItem == null || reqItem == Items.AIR) return null;
        int reqCount = Math.max(1, tag.getIntOr(TAG_REQUIRED_COUNT, 1));
        int dbl = Math.max(1, tag.getIntOr(TAG_REWARD_DOUBLOONS, 1));
        int boxes = Math.max(1, tag.getIntOr(TAG_REWARD_STRONGBOXES, 1));
        return new CharterSpec(region, reqItem, reqCount, dbl, boxes);
    }

    public static void ensureInitialized(ItemStack stack, Level level, @Nullable BlockPos originPos) {
        if (stack.isEmpty() || isInitialized(stack) || level == null || level.isClientSide()) return;

        RandomSource rng = level.getRandom();
        MaritimeTradeRegion currentRegion = originPos != null
                ? MaritimeTradeRegion.fromLevelAndPos(level, originPos)
                : null;

        List<MaritimeTradeRegion> candidates = new ArrayList<>();
        for (MaritimeTradeRegion r : MaritimeTradeRegion.values()) {
            if (r != currentRegion) {
                candidates.add(r);
            }
        }
        MaritimeTradeRegion targetRegion = candidates.get(rng.nextInt(candidates.size()));
        int variant = rng.nextInt(5);

        Item reqItem;
        int reqCount;
        int rewardDoubloons;
        int rewardBoxes = 1;

        switch (targetRegion) {
            case ARID -> {
                switch (variant) {
                    case 0 -> { reqItem = CurrentsofTrade.AMBER_VIAL.get(); reqCount = 3; rewardDoubloons = 28; }
                    case 1 -> { reqItem = CurrentsofTrade.CACAO_ELIXIR.get(); reqCount = 3; rewardDoubloons = 24; }
                    case 2 -> { reqItem = CurrentsofTrade.JADE_FIGURINE.get(); reqCount = 2; rewardDoubloons = 32; }
                    case 3 -> { reqItem = CurrentsofTrade.TYRIAN_PURPLE.get(); reqCount = 2; rewardDoubloons = 28; }
                    default -> { reqItem = CurrentsofTrade.NARWHAL_IVORY.get(); reqCount = 2; rewardDoubloons = 32; }
                }
            }
            case COLD -> {
                switch (variant) {
                    case 0 -> { reqItem = CurrentsofTrade.TEA_BRICK.get(); reqCount = 6; rewardDoubloons = 24; }
                    case 1 -> { reqItem = CurrentsofTrade.VANILLA_BUNDLE.get(); reqCount = 6; rewardDoubloons = 24; }
                    case 2 -> { reqItem = CurrentsofTrade.SAFFRON_BUNDLE.get(); reqCount = 4; rewardDoubloons = 26; }
                    case 3 -> { reqItem = CurrentsofTrade.FRANKINCENSE_TEARS.get(); reqCount = 4; rewardDoubloons = 26; }
                    default -> { reqItem = CurrentsofTrade.DAMASCUS_STEEL.get(); reqCount = 2; rewardDoubloons = 32; }
                }
            }
            case TROPICAL -> {
                switch (variant) {
                    case 0 -> { reqItem = CurrentsofTrade.FINE_PORCELAIN.get(); reqCount = 3; rewardDoubloons = 28; }
                    case 1 -> { reqItem = CurrentsofTrade.SILK_BALE.get(); reqCount = 3; rewardDoubloons = 28; }
                    case 2 -> { reqItem = CurrentsofTrade.TYRIAN_PURPLE.get(); reqCount = 2; rewardDoubloons = 28; }
                    case 3 -> { reqItem = CurrentsofTrade.DAMASCUS_STEEL.get(); reqCount = 2; rewardDoubloons = 32; }
                    default -> { reqItem = CurrentsofTrade.NARWHAL_IVORY.get(); reqCount = 2; rewardDoubloons = 32; }
                }
            }
            default -> {
                switch (variant) {
                    case 0 -> { reqItem = CurrentsofTrade.SANDALWOOD.get(); reqCount = 6; rewardDoubloons = 24; }
                    case 1 -> { reqItem = CurrentsofTrade.SAFFRON_BUNDLE.get(); reqCount = 4; rewardDoubloons = 26; }
                    case 2 -> { reqItem = CurrentsofTrade.AMBER_VIAL.get(); reqCount = 3; rewardDoubloons = 26; }
                    case 3 -> { reqItem = CurrentsofTrade.FRANKINCENSE_TEARS.get(); reqCount = 4; rewardDoubloons = 26; }
                    default -> { reqItem = CurrentsofTrade.JADE_FIGURINE.get(); reqCount = 2; rewardDoubloons = 32; }
                }
            }
        }

        Identifier key = BuiltInRegistries.ITEM.getKey(reqItem);
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = existing.copyTag();
        tag.putString(TAG_TARGET_REGION, targetRegion.getId());
        tag.putString(TAG_REQUIRED_ITEM, key.toString());
        tag.putInt(TAG_REQUIRED_COUNT, reqCount);
        tag.putInt(TAG_REWARD_DOUBLOONS, rewardDoubloons);
        tag.putInt(TAG_REWARD_STRONGBOXES, rewardBoxes);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!isInitialized(stack)) {
            ensureInitialized(stack, level, entity != null ? entity.blockPosition() : null);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            ensureInitialized(stack, level, player.blockPosition());
            CharterSpec spec = getSpec(stack);
            if (spec != null) {
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.0F);
                ItemStack sample = new ItemStack(spec.requiredItem());
                player.sendOverlayMessage(Component.translatable(
                        "message.currents_of_trade.charter_inspect",
                        spec.requiredCount(),
                        sample.getHoverName(),
                        spec.targetRegion().getDisplayName(),
                        spec.rewardDoubloons(),
                        spec.rewardStrongboxes()
                ));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to fulfill a Sealed Trade Charter by hand at the given Anchor Point.
     */
    public static boolean tryFulfillAtHarbor(Player player, @Nullable ItemStack heldStack, AnchorPointBlockEntity anchor) {
        if (player == null || anchor == null || anchor.getLevel() == null) return false;
        Level level = anchor.getLevel();
        AnchorPointBlockEntity.HarborStatus status = anchor.getHarborStatus();
        if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
            if (heldStack != null && heldStack.getItem() instanceof TradeCharterItem) {
                if (!level.isClientSide()) {
                    Component warnMsg = switch (status) {
                        case TOO_SMALL -> Component.translatable("message.currents_of_trade.harbor_too_small");
                        case UNDERGROUND -> Component.translatable("message.currents_of_trade.harbor_underground");
                        case TOO_SHALLOW -> Component.translatable("message.currents_of_trade.harbor_too_shallow");
                        case WRONG_DIMENSION -> Component.translatable("message.currents_of_trade.overworld_only");
                        default -> Component.translatable("message.currents_of_trade.anchor_placed_no_water");
                    };
                    level.playSound(null, anchor.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                    player.sendOverlayMessage(warnMsg);
                }
                return true;
            }
            return false;
        }
        MaritimeTradeRegion portRegion = anchor.getTradeRegion();

        ItemStack charterStack = ItemStack.EMPTY;
        if (heldStack != null && heldStack.getItem() instanceof TradeCharterItem) {
            if (level.isClientSide()) {
                return true;
            }
            ensureInitialized(heldStack, level, player.blockPosition());
            CharterSpec heldSpec = getSpec(heldStack);
            if (heldSpec == null) return false;
            if (heldSpec.targetRegion() != portRegion) {
                if (!level.isClientSide()) {
                    level.playSound(null, anchor.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                    player.sendOverlayMessage(Component.translatable(
                            "message.currents_of_trade.charter_wrong_region",
                            heldSpec.targetRegion().getDisplayName(),
                            portRegion.getDisplayName()
                    ));
                }
                return true;
            }
            charterStack = heldStack;
        } else if (heldStack == null || heldStack.isEmpty()) {
            // Search player inventory for a ready charter matching this port's region
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() instanceof TradeCharterItem && isInitialized(s)) {
                    CharterSpec spec = getSpec(s);
                    if (spec != null && spec.targetRegion() == portRegion) {
                        ItemStack req = new ItemStack(spec.requiredItem());
                        if (AnchorPointBlockEntity.countPlayerMatchingItems(player, req) >= spec.requiredCount()) {
                            charterStack = s;
                            break;
                        }
                    }
                }
            }
            if (charterStack.isEmpty()) return false;
        } else {
            return false;
        }

        CharterSpec spec = getSpec(charterStack);
        if (spec == null) return false;

        ItemStack reqTemplate = new ItemStack(spec.requiredItem());
        int available = AnchorPointBlockEntity.countPlayerMatchingItems(player, reqTemplate);
        if (available < spec.requiredCount()) {
            if (!level.isClientSide()) {
                level.playSound(null, anchor.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8F, 1.0F);
                player.sendOverlayMessage(Component.translatable(
                        "message.currents_of_trade.charter_missing_cargo",
                        spec.requiredCount(),
                        reqTemplate.getHoverName(),
                        available
                ));
            }
            return true;
        }

        if (!level.isClientSide()) {
            int finalDoubloons = spec.rewardDoubloons();
            if (anchor.getSpecialization() == HarborSpecialization.FREE_PORT) {
                finalDoubloons = Math.max(finalDoubloons + 1, Math.round(finalDoubloons * 1.25F));
            }

            if (!player.getAbilities().instabuild) {
                AnchorPointBlockEntity.consumeFromPlayerInventory(player, null, reqTemplate, spec.requiredCount());
                charterStack.shrink(1);
            }

            giveOrDrop(player, new ItemStack(CurrentsofTrade.DOUBLOON.get(), finalDoubloons));
            giveOrDrop(player, new ItemStack(CurrentsofTrade.GUILD_STRONGBOX.get(), spec.rewardStrongboxes()));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }

            level.playSound(null, anchor.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.85F, 1.15F);
            level.playSound(null, anchor.getBlockPos(), SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.05F);
            if (level instanceof ServerLevel serverLevel) {
                double px = anchor.getBlockPos().getX() + 0.5;
                double py = anchor.getBlockPos().getY() + 1.25;
                double pz = anchor.getBlockPos().getZ() + 0.5;
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, 22, 0.35, 0.35, 0.35, 0.12);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 14, 0.35, 0.3, 0.35, 0.05);
            }

            awardCharterAdvancement(player);

            player.sendOverlayMessage(Component.translatable(
                    "message.currents_of_trade.charter_fulfilled",
                    anchor.getHarborName(),
                    finalDoubloons,
                    spec.rewardStrongboxes()
            ));
        }

        return true;
    }

    /**
     * Automatically fulfills any matching Sealed Trade Charters loaded inside an arriving Cargo Boat or Trade Ship.
     */
    public static int tryFulfillFromShipHold(ServerLevel level, BlockPos targetHarborPos, Container shipHold,
                                             @Nullable List<ItemStack> returnRewardList, @Nullable UUID senderUUID,
                                             boolean bonusFreePort) {
        return tryFulfillFromShipHold(level, targetHarborPos, null, shipHold, returnRewardList, senderUUID, bonusFreePort);
    }

    public static int tryFulfillFromShipHold(ServerLevel level, BlockPos targetHarborPos, @Nullable BlockPos originHarborPos,
                                             Container shipHold, @Nullable List<ItemStack> returnRewardList,
                                             @Nullable UUID senderUUID, boolean bonusFreePort) {
        if (level == null || targetHarborPos == null || shipHold == null) return 0;

        MaritimeTradeRegion destRegion = MaritimeTradeRegion.fromLevelAndPos(level, targetHarborPos);
        String harborName = "Harbor";
        var savedData = com.lexovian.currentsoftrade.world.harbor.HarborSavedData.get(level);
        boolean isDestFreePort = bonusFreePort
                || savedData.getHarborSpecialization(targetHarborPos) == HarborSpecialization.FREE_PORT
                || (originHarborPos != null && savedData.getHarborSpecialization(originHarborPos) == HarborSpecialization.FREE_PORT);
        if (level.isLoaded(targetHarborPos) && level.getBlockEntity(targetHarborPos) instanceof AnchorPointBlockEntity anchor) {
            if (anchor.getHarborStatus() != AnchorPointBlockEntity.HarborStatus.VALID) {
                return 0;
            }
            harborName = anchor.getHarborName();
            if (anchor.getSpecialization() == HarborSpecialization.FREE_PORT) {
                isDestFreePort = true;
            }
        } else {
            String savedName = savedData.getHarborName(targetHarborPos);
            if (savedName != null && !savedName.isEmpty()) harborName = savedName;
        }

        int fulfilledCount = 0;
        int totalDoubloons = 0;
        int totalBoxes = 0;

        for (int slot = 0; slot < shipHold.getContainerSize(); slot++) {
            ItemStack stack = shipHold.getItem(slot);
            if (!(stack.getItem() instanceof TradeCharterItem)) continue;
            ensureInitialized(stack, level, originHarborPos);
            CharterSpec spec = getSpec(stack);
            if (spec == null || spec.targetRegion() != destRegion) continue;

            ItemStack reqTemplate = new ItemStack(spec.requiredItem());
            int available = countContainerMatching(shipHold, reqTemplate);
            if (available < spec.requiredCount()) continue;

            // Consume required cargo & charter
            consumeContainerMatching(shipHold, reqTemplate, spec.requiredCount());
            stack.shrink(1);
            if (stack.isEmpty()) {
                shipHold.setItem(slot, ItemStack.EMPTY);
            }

            int doubloonPayout = spec.rewardDoubloons();
            if (isDestFreePort) {
                doubloonPayout = Math.max(doubloonPayout + 1, Math.round(doubloonPayout * 1.25F));
            }

            List<ItemStack> charterRewards = new ArrayList<>();
            while (doubloonPayout > 0) {
                int batch = Math.min(64, doubloonPayout);
                charterRewards.add(new ItemStack(CurrentsofTrade.DOUBLOON.get(), batch));
                doubloonPayout -= batch;
                totalDoubloons += batch;
            }
            charterRewards.add(new ItemStack(CurrentsofTrade.GUILD_STRONGBOX.get(), spec.rewardStrongboxes()));
            totalBoxes += spec.rewardStrongboxes();

            if (returnRewardList != null) {
                returnRewardList.addAll(charterRewards);
            } else {
                for (ItemStack rew : charterRewards) {
                    insertIntoContainerOrDrop(level, targetHarborPos, shipHold, rew);
                }
            }

            fulfilledCount++;
        }

        if (fulfilledCount > 0) {
            level.playSound(null, targetHarborPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.85F, 1.15F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    targetHarborPos.getX() + 0.5, targetHarborPos.getY() + 1.3, targetHarborPos.getZ() + 0.5,
                    20, 0.4, 0.35, 0.4, 0.1);

            if (senderUUID != null && level.getServer() != null) {
                ServerPlayer sender = level.getServer().getPlayerList().getPlayer(senderUUID);
                if (sender != null) {
                    awardCharterAdvancement(sender);
                    sender.sendSystemMessage(Component.translatable(
                            "message.currents_of_trade.charter_fulfilled_ship",
                            fulfilledCount,
                            harborName,
                            totalDoubloons,
                            totalBoxes
                    ));
                }
            }
        }

        return fulfilledCount;
    }

    private static int countContainerMatching(Container container, ItemStack template) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItem(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void consumeContainerMatching(Container container, ItemStack template, int needed) {
        int rem = needed;
        for (int i = 0; i < container.getContainerSize() && rem > 0; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItem(s, template)) {
                int take = Math.min(rem, s.getCount());
                s.shrink(take);
                rem -= take;
                if (s.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        container.setChanged();
    }

    private static void insertIntoContainerOrDrop(ServerLevel level, BlockPos dropPos, Container container, ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !rem.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, rem)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, rem.getCount());
                    existing.grow(move);
                    rem.shrink(move);
                }
            }
        }
        for (int i = 0; i < container.getContainerSize() && !rem.isEmpty(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, rem.copy());
                rem = ItemStack.EMPTY;
            }
        }
        if (!rem.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, dropPos.getX() + 0.5, dropPos.getY() + 1.0, dropPos.getZ() + 0.5, rem);
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        int rem = stack.getCount();
        while (rem > 0) {
            int batch = Math.min(stack.getMaxStackSize(), rem);
            ItemStack copy = stack.copyWithCount(batch);
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            rem -= batch;
        }
    }

    public static void awardCharterAdvancement(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level().getServer() != null) {
            AdvancementHolder adv = serverPlayer.level().getServer().getAdvancements()
                    .get(Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "chartered_merchant"));
            if (adv != null) {
                for (String criterion : adv.value().criteria().keySet()) {
                    serverPlayer.getAdvancements().award(adv, criterion);
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CharterSpec spec = getSpec(stack);
        if (spec == null) {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.trade_charter.unsealed"));
        } else {
            ItemStack sample = new ItemStack(spec.requiredItem());
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.trade_charter.region", spec.targetRegion().getDisplayName()));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.trade_charter.cargo", spec.requiredCount(), sample.getHoverName()));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.trade_charter.reward", spec.rewardDoubloons(), spec.rewardStrongboxes()));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.trade_charter.hint"));
        }
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
