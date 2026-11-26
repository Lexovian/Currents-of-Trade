package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interactive Maritime Animal Cage.
 *
 * <p>Sold exclusively by Wandering Sailors carrying livestock and small creatures listed in the
 * {@code #currents_of_trade:cageable_animals} entity type tag.
 * <ul>
 *   <li>Right-click ground with a filled cage to release the animal (empties the cage).</li>
 *   <li>Sneak + Right-click a matching wild/baby animal with an empty cage to capture it.</li>
 *   <li>Deliver filled cages to Harbormasters (any animal) or Harbor Urgent Bounties (specific animal).</li>
 * </ul>
 */
public class AnimalCageItem extends Item {

    public static final String TAG_ANIMAL_ID = "AnimalId";
    public static final String TAG_IS_BABY = "IsBaby";

    public static final TagKey<EntityType<?>> CAGEABLE_ANIMALS =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "cageable_animals"));

    private static final List<EntityType<?>> FALLBACK_ANIMALS = List.of(
            EntityType.CHICKEN,
            EntityType.RABBIT,
            EntityType.PARROT,
            EntityType.COW,
            EntityType.SHEEP,
            EntityType.PIG
    );

    public AnimalCageItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static List<EntityType<?>> getCageableAnimals() {
        List<EntityType<?>> result = new ArrayList<>();
        for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(CAGEABLE_ANIMALS)) {
            if (holder.isBound()) {
                result.add(holder.value());
            }
        }
        if (result.isEmpty()) {
            return FALLBACK_ANIMALS;
        }
        result.sort(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        return result;
    }

    public static boolean shouldBeBaby(EntityType<?> type) {
        return type != null && (type.getWidth() > 0.75F || type.getHeight() > 0.95F);
    }

    public static void fillStack(ItemStack stack, EntityType<?> type, boolean isBaby) {
        if (stack.isEmpty() || type == null) return;
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ANIMAL_ID, key.toString());
        tag.putBoolean(TAG_IS_BABY, isBaby);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // UNCOMMON rarity marks filled cages so Harbormaster ItemCost accepts any filled cage and rejects empty ones
        stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
    }

    public static void clearStack(ItemStack stack) {
        if (stack.isEmpty()) return;
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.RARITY);
    }

    public static ItemStack createWithAnimal(EntityType<?> type) {
        ItemStack stack = new ItemStack(CurrentsofTrade.ANIMAL_CAGE.get(), 1);
        if (type == null) return stack;
        fillStack(stack, type, shouldBeBaby(type));
        return stack;
    }

    public static ItemStack createRandomCage(RandomSource rng) {
        List<EntityType<?>> animals = getCageableAnimals();
        return createWithAnimal(animals.get(rng.nextInt(animals.size())));
    }

    public static ItemStack createDeterministicCage(long seed) {
        List<EntityType<?>> animals = getCageableAnimals();
        int idx = (int) Math.floorMod(seed, animals.size());
        return createWithAnimal(animals.get(idx));
    }

    public static boolean isFilledMatcher(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.get(DataComponents.RARITY) == Rarity.UNCOMMON && getStoredAnimalType(stack) == null;
    }

    @Nullable
    public static EntityType<?> getStoredAnimalType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        String rawId = tag.getStringOr(TAG_ANIMAL_ID, "");
        if (rawId.isEmpty()) return null;
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public static boolean isStoredBaby(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyTag().getBooleanOr(TAG_IS_BABY, false);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = getStoredAnimalType(stack);
        if (type != null) {
            if (isStoredBaby(stack)) {
                return Component.translatable("item.currents_of_trade.animal_cage.filled_baby", type.getDescription());
            }
            return Component.translatable("item.currents_of_trade.animal_cage.filled", type.getDescription());
        }
        if (isFilledMatcher(stack)) {
            return Component.translatable("item.currents_of_trade.animal_cage.any_filled");
        }
        return super.getName(stack);
    }

    /**
     * Interactive Sneak + Right-Click capture of a tagged animal into an empty Animal Cage.
     * Called from {@code PlayerInteractEvent.EntityInteract} so it runs cleanly before vanilla mob interactions.
     */
    public static boolean tryCaptureEntity(Player player, Entity rawTarget, InteractionHand hand) {
        if (player == null || !player.isShiftKeyDown() || !(rawTarget instanceof LivingEntity target)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AnimalCageItem)) {
            return false;
        }

        if (player.level().isClientSide()) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel serverLevel) || !target.isAlive() || target instanceof Player) {
            return true;
        }

        if (getStoredAnimalType(stack) != null) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_full"));
            return true;
        }

        EntityType<?> type = target.getType();
        if (!type.builtInRegistryHolder().is(CAGEABLE_ANIMALS)) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_not_allowed"));
            return true;
        }

        if (target instanceof TamableAnimal tamable && tamable.isTame() && !tamable.isOwnedBy(player)) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_not_allowed"));
            return true;
        }

        boolean requiresBaby = shouldBeBaby(type);
        boolean targetIsBaby = target.isBaby();
        if (requiresBaby && !targetIsBaby) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_too_big"));
            return true;
        }

        fillStack(stack, type, targetIsBaby);
        double ex = target.getX();
        double ey = target.getY() + target.getBbHeight() * 0.5D;
        double ez = target.getZ();
        if (target instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash();
        }
        target.stopRiding();
        target.ejectPassengers();
        target.discard();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        serverLevel.playSound(null, BlockPos.containing(ex, ey, ez), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.9F, 1.15F);
        serverLevel.playSound(null, BlockPos.containing(ex, ey, ez), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.75F, 1.25F);
        serverLevel.sendParticles(ParticleTypes.CLOUD, ex, ey, ez, 10, 0.25D, 0.2D, 0.25D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, ex, ey + 0.2D, ez, 6, 0.25D, 0.2D, 0.25D, 0.02D);

        Component animalLabel = targetIsBaby
                ? Component.translatable("tooltip.currents_of_trade.animal_cage.baby_prefix", type.getDescription())
                : type.getDescription();
        player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_captured", animalLabel));
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = getStoredAnimalType(stack);

        if (type == null) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_empty_hint"));
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.relative(face);

        boolean isBaby = isStoredBaby(stack);
        Entity entity = type.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        double sx = spawnPos.getX() + 0.5D;
        double sy = spawnPos.getY() + 0.05D;
        double sz = spawnPos.getZ() + 0.5D;
        float yaw = player != null ? (player.getYRot() + 180.0F) % 360.0F : serverLevel.getRandom().nextFloat() * 360.0F;
        entity.snapTo(sx, sy, sz, yaw, 0.0F);

        if (entity instanceof Mob mob) {
            mob.yHeadRot = mob.getYRot();
            mob.yBodyRot = mob.getYRot();
            mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.SPAWN_ITEM_USE, null);
        }
        if (entity instanceof AgeableMob ageable) {
            ageable.setBaby(isBaby);
        }

        if (!serverLevel.addFreshEntity(entity)) {
            return InteractionResult.FAIL;
        }

        serverLevel.playSound(null, spawnPos, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.85F, 1.15F);
        serverLevel.playSound(null, spawnPos, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 0.7F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.CLOUD, sx, sy + 0.35D, sz, 10, 0.25D, 0.2D, 0.25D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, sx, sy + 0.45D, sz, 6, 0.25D, 0.2D, 0.25D, 0.02D);

        if (player != null) {
            Component animalLabel = isBaby
                    ? Component.translatable("tooltip.currents_of_trade.animal_cage.baby_prefix", type.getDescription())
                    : type.getDescription();
            player.sendOverlayMessage(Component.translatable("message.currents_of_trade.animal_cage_released", animalLabel));
            if (!player.getAbilities().instabuild) {
                clearStack(stack);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
            }
        } else {
            clearStack(stack);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        EntityType<?> type = getStoredAnimalType(stack);
        if (type != null) {
            Component animalLabel = isStoredBaby(stack)
                    ? Component.translatable("tooltip.currents_of_trade.animal_cage.baby_prefix", type.getDescription())
                    : type.getDescription();
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.contents", animalLabel));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.use"));
        } else if (isFilledMatcher(stack)) {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.any_filled_desc"));
        } else {
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.empty"));
            tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.capture_hint"));
        }
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.animal_cage.market"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
