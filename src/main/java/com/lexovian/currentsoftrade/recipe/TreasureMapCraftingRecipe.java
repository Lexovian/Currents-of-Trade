package com.lexovian.currentsoftrade.recipe;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.common.ForgeHooks;

public class TreasureMapCraftingRecipe extends CustomRecipe {

    private static final ThreadLocal<Level> CURRENT_LEVEL = new ThreadLocal<>();

    public TreasureMapCraftingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        if (input.getWidth() != 3 || input.getHeight() != 3) {
            return false;
        }

        CURRENT_LEVEL.set(level);

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
        Level level = CURRENT_LEVEL.get();
        if (level instanceof ServerLevel serverLevel) {
            ServerPlayer crafter = ForgeHooks.getCraftingPlayer() instanceof ServerPlayer sp ? sp : null;
            if (crafter == null) {
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.containerMenu instanceof CraftingMenu) {
                        crafter = player;
                        break;
                    }
                }
            }

            BlockPos origin = crafter != null ? crafter.blockPosition() : serverLevel.getSharedSpawnPos();

            BlockPos treasurePos = serverLevel.findNearestMapStructure(
                    StructureTags.ON_TREASURE_MAPS,
                    origin,
                    250,
                    false
            );

            if (treasurePos != null) {
                ItemStack mapStack = MapItem.create(serverLevel, treasurePos.getX(), treasurePos.getZ(), (byte) 1, true, true);
                MapItem.renderBiomePreviewMap(serverLevel, mapStack);
                MapItemSavedData.addTargetDecoration(mapStack, treasurePos, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
                mapStack.setHoverName(Component.translatable("filled_map.buried_treasure"));
                return mapStack;
            } else {
                // If no buried treasure structure was found in radius, still create a fully valid map with real ID at origin
                ItemStack fallback = MapItem.create(serverLevel, origin.getX(), origin.getZ(), (byte) 1, true, true);
                MapItem.renderBiomePreviewMap(serverLevel, fallback);
                MapItemSavedData.addTargetDecoration(fallback, origin, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
                fallback.setHoverName(Component.translatable("filled_map.buried_treasure"));
                return fallback;
            }
        }

        // Preview map for client GUI or recipe book
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        preview.setHoverName(Component.translatable("filled_map.buried_treasure"));
        return preview;
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
