package com.lexovian.currentsoftrade.recipe;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.CommonHooks;

public class TreasureMapCraftingRecipe extends CustomRecipe {

    private static final ThreadLocal<Level> CURRENT_LEVEL = new ThreadLocal<>();

    public static final MapCodec<TreasureMapCraftingRecipe> MAP_CODEC = MapCodec.unit(TreasureMapCraftingRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, TreasureMapCraftingRecipe> STREAM_CODEC = StreamCodec.unit(new TreasureMapCraftingRecipe());
    public static final RecipeSerializer<TreasureMapCraftingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public TreasureMapCraftingRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        CURRENT_LEVEL.set(level);

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
    public ItemStack assemble(CraftingInput input) {
        Level level = CURRENT_LEVEL.get();
        if (level instanceof ServerLevel serverLevel) {
            ServerPlayer crafter = CommonHooks.getCraftingPlayer() instanceof ServerPlayer sp ? sp : null;
            if (crafter == null) {
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.containerMenu instanceof CraftingMenu) {
                        crafter = player;
                        break;
                    }
                }
            }

            BlockPos origin = crafter != null ? crafter.blockPosition() : serverLevel.getLevelData().getRespawnData().pos();

            BlockPos treasurePos = serverLevel.findNearestMapStructure(
                    StructureTags.ON_TREASURE_MAPS,
                    origin,
                    250,
                    false
            );

            if (treasurePos != null) {
                ItemStack mapStack = MapItem.create(serverLevel, treasurePos.getX(), treasurePos.getZ(), (byte) 1, true, true);
                MapItem.renderBiomePreviewMap(serverLevel, mapStack);
                MapItemSavedData.addTargetDecoration(mapStack, treasurePos, "+", MapDecorationTypes.RED_X);
                mapStack.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.buried_treasure"));
                return mapStack;
            } else {
                ItemStack fallback = MapItem.create(serverLevel, origin.getX(), origin.getZ(), (byte) 1, true, true);
                MapItem.renderBiomePreviewMap(serverLevel, fallback);
                MapItemSavedData.addTargetDecoration(fallback, origin, "+", MapDecorationTypes.RED_X);
                fallback.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.buried_treasure"));
                return fallback;
            }
        }

        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        preview.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.buried_treasure"));
        return preview;
    }

    @Override
    public RecipeSerializer<TreasureMapCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
