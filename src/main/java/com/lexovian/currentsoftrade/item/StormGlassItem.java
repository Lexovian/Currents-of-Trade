package com.lexovian.currentsoftrade.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import org.jspecify.annotations.Nullable;

/**
 * Weather forecasting sealed barometer vial.
 *
 * <p>Predicts storms and rainfall up to 2,400 ticks (2 minutes) in advance.
 * When right-clicked, sends an atmospheric forecast actionbar message to the player.
 * Also dynamically switches its item model texture between clear, rain, and storm states.</p>
 */
public class StormGlassItem extends Item {

    public static final String TAG_WEATHER_STATE = "WeatherState";
    public static final int FORECAST_TICKS = 2400;

    public StormGlassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // --- Right-Click Forecast Check ---

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            int state = updateForecastState(stack, serverLevel);
            Component forecast = resolveForecastMessage(serverLevel, state);
            player.sendOverlayMessage(forecast);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.8F, 1.3F);
        }

        return InteractionResult.SUCCESS;
    }

    // --- Inventory Tick & Weather Forecast ---

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel serverLevel, Entity entity, @Nullable EquipmentSlot slot) {
        if (serverLevel.getGameTime() % 40L == 0L) {
            updateForecastState(stack, serverLevel);
        }
    }

    public static int updateForecastState(ItemStack stack, ServerLevel serverLevel) {
        int state = calculateWeatherState(serverLevel);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_WEATHER_STATE) || tag.getIntOr(TAG_WEATHER_STATE, -1) != state) {
            tag.putInt(TAG_WEATHER_STATE, state);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return state;
    }

    public static int calculateWeatherState(ServerLevel serverLevel) {
        if (serverLevel.isThundering()) return 2;
        if (serverLevel.isRaining()) return 1;

        WeatherData wd = serverLevel.getWeatherData();
        if (wd != null && wd.getClearWeatherTime() <= 0) {
            if (wd.getThunderTime() <= FORECAST_TICKS) {
                return 2;
            }
            if (wd.getRainTime() <= FORECAST_TICKS) {
                return 1;
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

        WeatherData wd = serverLevel.getWeatherData();
        if (wd != null && wd.getClearWeatherTime() <= 0) {
            if (wd.getThunderTime() <= FORECAST_TICKS) {
                return Component.translatable("message.currents_of_trade.storm_glass_thunder_incoming");
            }
            if (wd.getRainTime() <= FORECAST_TICKS) {
                return Component.translatable("message.currents_of_trade.storm_glass_rain_incoming");
            }
        }
        return Component.translatable("message.currents_of_trade.storm_glass_clear");
    }

    // --- Tooltip ---

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.accept(Component.translatable("tooltip.currents_of_trade.storm_glass.desc"));
        tooltip.accept(Component.translatable("tooltip.currents_of_trade.storm_glass.lore"));
        tooltip.accept(Component.translatable("tooltip.currents_of_trade.storm_glass_use_hint"));
        super.appendHoverText(stack, context, display, tooltip, tooltipFlag);
    }
}
