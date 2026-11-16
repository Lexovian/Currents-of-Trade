package com.lexovian.currentsoftrade.recipe;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class TreasureMapCraftingRecipe extends CustomRecipe {

    public static final String TAG_PENDING_TREASURE_MAP = "PendingBottleTreasureMap";

    public TreasureMapCraftingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        if (input.getWidth() != 3 || input.getHeight() != 3) {
            return false;
        }

        int bottleCount = 0;
        boolean hasCenterMap = false;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack stack = input.getItem(col + row * input.getWidth());
                if (row == 1 && col == 1) {
                    if (stack.is(Items.MAP)) {
                        hasCenterMap = true;
                    } else {
                        return false;
                    }
                } else {
                    if (stack.is(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get())) {
                        bottleCount++;
                    } else {
                        return false;
                    }
                }
            }
        }

        return hasCenterMap && bottleCount == 8;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        // Defer expensive structure search & map_<id>.dat allocation until actual craft completion
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(TAG_PENDING_TREASURE_MAP, true);
        CustomData.set(preview, CustomData.of(marker));
        preview.setHoverName(Component.translatable("filled_map.buried_treasure"));
        return preview;
    }

    public static boolean resolvePendingTreasureMap(ItemStack stack, ServerLevel serverLevel, BlockPos origin) {
        if (stack.isEmpty() || !stack.is(Items.FILLED_MAP)) {
            return false;
        }
        CustomData cd = CustomData.getOrNull(stack);
        if (cd == null || !cd.copyTag().getBoolean(TAG_PENDING_TREASURE_MAP)) {
            return false;
        }

        BlockPos treasurePos = serverLevel.findNearestMapStructure(
                StructureTags.ON_TREASURE_MAPS,
                origin,
                250,
                false
        );
        BlockPos target = treasurePos != null ? treasurePos : origin;

        ItemStack generated = MapItem.create(serverLevel, target.getX(), target.getZ(), (byte) 1, true, true);
        MapItem.renderBiomePreviewMap(serverLevel, generated);
        MapItemSavedData.addTargetDecoration(generated, target, "+", MapDecoration.Type.RED_X);
        generated.setHoverName(Component.translatable("filled_map.buried_treasure"));

        stack.setTag(generated.getTag() != null ? generated.getTag().copy() : null);
        return true;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CurrentsofTrade.TREASURE_MAP_RECIPE_SERIALIZER.get();
    }
}
