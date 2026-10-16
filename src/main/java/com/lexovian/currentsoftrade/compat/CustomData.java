package com.lexovian.currentsoftrade.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 1.20.1 stand-in for Minecraft 1.21's {@code CustomData} item component.
 *
 * <p>In 1.21 the mod stores its data in the {@code minecraft:custom_data} component.
 * In 1.20.1 the equivalent is the ItemStack's root NBT tag. This class mirrors the
 * 1.21 API surface used by the mod so the gameplay logic stays identical.</p>
 */
public final class CustomData {

    /** Vanilla keys that live in the root tag in 1.20.1 and must survive a full {@link #set}. */
    private static final String[] PRESERVED_VANILLA_KEYS = {"display", "Damage", "Enchantments", "RepairCost", "Unbreakable"};

    public static final CustomData EMPTY = new CustomData(new CompoundTag());

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    // --- ItemStack accessors (replacements for stack.get/set/remove(DataComponents.CUSTOM_DATA)) ---

    /** Equivalent of {@code stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)}. */
    public static CustomData get(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? EMPTY : new CustomData(t);
    }

    /** Equivalent of {@code stack.get(DataComponents.CUSTOM_DATA)}. */
    @Nullable
    public static CustomData getOrNull(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return (t == null || t.isEmpty()) ? null : new CustomData(t);
    }

    /** Equivalent of {@code stack.set(DataComponents.CUSTOM_DATA, data)}. */
    public static void set(ItemStack stack, CustomData data) {
        CompoundTag newTag = data.copyTag();
        CompoundTag old = stack.getTag();
        if (old != null) {
            for (String key : PRESERVED_VANILLA_KEYS) {
                if (old.contains(key) && !newTag.contains(key)) {
                    newTag.put(key, old.get(key).copy());
                }
            }
        }
        stack.setTag(newTag.isEmpty() ? null : newTag);
    }

    /** Equivalent of {@code stack.remove(DataComponents.CUSTOM_DATA)} (keeps vanilla keys). */
    public static void remove(ItemStack stack) {
        set(stack, EMPTY);
    }

    /** Equivalent of {@code CustomData.update(DataComponents.CUSTOM_DATA, stack, updater)}. */
    public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
        CompoundTag t = stack.getOrCreateTag();
        updater.accept(t);
        if (t.isEmpty()) {
            stack.setTag(null);
        }
    }

    // --- ItemStack (de)serialization replacements for 1.21's registry-aware codecs ---

    /** Equivalent of {@code ItemStack.parse(registries, tag)}. */
    public static Optional<ItemStack> parseStack(@Nullable HolderLookup.Provider registries, CompoundTag tag) {
        ItemStack stack = ItemStack.of(tag);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    /** Equivalent of {@code stack.save(registries)}. */
    public static CompoundTag saveStack(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        return stack.save(new CompoundTag());
    }
}
