package com.lexovian.currentsoftrade.recipe;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class TreasureMapCraftingRecipe extends CustomRecipe {

    public static final String TAG_PENDING_TREASURE_MAP = "PendingBottleTreasureMap";

    public TreasureMapCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        int bottleCount = 0;
        boolean hasCenterMap = false;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack stack = input.getItem(col, row);
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
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // Defer expensive structure search & map_<id>.dat allocation until actual craft completion
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(TAG_PENDING_TREASURE_MAP, true);
        preview.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        preview.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.buried_treasure"));
        return preview;
    }

    public static boolean resolvePendingTreasureMap(ItemStack stack, ServerLevel serverLevel, BlockPos origin) {
        if (stack.isEmpty() || !stack.is(Items.FILLED_MAP)) {
            return false;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
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
        MapItemSavedData.addTargetDecoration(generated, target, "+", MapDecorationTypes.RED_X);
        generated.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.buried_treasure"));

        stack.remove(DataComponents.CUSTOM_DATA);
        stack.applyComponents(generated.getComponentsPatch());
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
