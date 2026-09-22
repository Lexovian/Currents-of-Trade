package com.lexovian.currentsoftrade.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.List;

public class StormGlassItem extends Item {

    // --- Constants ---

    public static final int FORECAST_TICKS = 2400; // 2 minutes (120 seconds * 20 ticks)
    public static final String TAG_WEATHER_STATE = "WeatherState";

    public StormGlassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // --- Interaction ---

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        level.playSound(
                player,
                player.blockPosition(),
                SoundEvents.GLASS_HIT,
                SoundSource.PLAYERS,
                0.8F,
                1.3F
        );

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            int state = updateForecastState(stack, serverLevel);
            Component forecast = resolveForecastMessage(serverLevel, state);
            player.displayClientMessage(forecast, true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // --- Inventory Tick & Weather Forecast ---

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel && level.getGameTime() % 40L == 0L) {
            updateForecastState(stack, serverLevel);
        }
    }

    public static int updateForecastState(ItemStack stack, ServerLevel serverLevel) {
        int state = calculateWeatherState(serverLevel);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_WEATHER_STATE) || tag.getInt(TAG_WEATHER_STATE) != state) {
            tag.putInt(TAG_WEATHER_STATE, state);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return state;
    }

    public static int calculateWeatherState(ServerLevel serverLevel) {
        if (serverLevel.isThundering()) return 2;
        if (serverLevel.isRaining()) return 1;

        if (serverLevel.getLevelData() instanceof ServerLevelData sld) {
            if (sld.getClearWeatherTime() <= 0) {
                if (sld.getThunderTime() <= FORECAST_TICKS) {
                    return 2;
                }
                if (sld.getRainTime() <= FORECAST_TICKS) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private static Component resolveForecastMessage(ServerLevel serverLevel, int state) {
        if (serverLevel.isThundering()) {
            return Component.translatable("message.currents_of_trade.storm_glass_thunder");
        }
        if (serverLevel.isRaining()) {
            return Component.translatable("message.currents_of_trade.storm_glass_rain");
        }

        if (serverLevel.getLevelData() instanceof ServerLevelData sld && sld.getClearWeatherTime() <= 0) {
            if (sld.getThunderTime() <= FORECAST_TICKS) {
                return Component.translatable("message.currents_of_trade.storm_glass_thunder_incoming");
            }
            if (sld.getRainTime() <= FORECAST_TICKS) {
                return Component.translatable("message.currents_of_trade.storm_glass_rain_incoming");
            }
        }
        return Component.translatable("message.currents_of_trade.storm_glass_clear");
    }

    // --- Tooltip ---

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("tooltip.currents_of_trade.storm_glass_use_hint"));
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }
}
