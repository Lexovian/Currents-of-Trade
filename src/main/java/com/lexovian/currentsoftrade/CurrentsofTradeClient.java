package com.lexovian.currentsoftrade;

import com.lexovian.currentsoftrade.client.gui.AnchorPointScreen;
import com.lexovian.currentsoftrade.client.gui.RequestTradeScreen;
import com.lexovian.currentsoftrade.client.gui.SendItemsScreen;
import com.lexovian.currentsoftrade.client.gui.TravelScreen;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.client.renderer.SloopRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jspecify.annotations.Nullable;

@Mod(value = CurrentsofTrade.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT)
public class CurrentsofTradeClient {

    // --- Config Screen ---

    public CurrentsofTradeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    // --- Item Model Properties ---

    public record StormGlassWeatherProperty() implements RangeSelectItemModelProperty {
        public static final MapCodec<StormGlassWeatherProperty> MAP_CODEC = MapCodec.unit(new StormGlassWeatherProperty());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains(com.lexovian.currentsoftrade.item.StormGlassItem.TAG_WEATHER_STATE)) {
                    return (float) tag.getInt(com.lexovian.currentsoftrade.item.StormGlassItem.TAG_WEATHER_STATE).orElse(0);
                }
            }
            Level world = level != null ? level : (owner != null ? owner.level() : null);
            if (world == null) return 0.0F;
            if (world.isThundering()) return 2.0F;
            if (world.isRaining()) return 1.0F;
            return 0.0F;
        }

        @Override
        public MapCodec<StormGlassWeatherProperty> type() {
            return MAP_CODEC;
        }
    }

    @SubscribeEvent
    static void onRegisterRangeProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "weather"), StormGlassWeatherProperty.MAP_CODEC);
    }

    // --- Client Setup ---

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CurrentsofTrade.LOGGER.info("Currents of Trade client setup completed.");
    }

    // --- Screen Registration ---

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CurrentsofTrade.ANCHOR_POINT_MENU.get(), AnchorPointScreen::new);
        event.register(CurrentsofTrade.TRAVEL_MENU.get(), TravelScreen::new);
        event.register(CurrentsofTrade.SEND_ITEMS_MENU.get(), SendItemsScreen::new);
        event.register(CurrentsofTrade.REQUEST_TRADE_MENU.get(), RequestTradeScreen::new);
    }

    // --- Entity & Layer Renderers ---

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SloopModel.LAYER_LOCATION, SloopModel::createBodyLayer);
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CurrentsofTrade.SLOOP.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.TRADE_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.CARGO_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.WANDERING_SAILOR.get(), WanderingTraderRenderer::new);
    }

    @SubscribeEvent
    static void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen containerScreen)) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        net.minecraft.world.entity.Entity targetShip = null;
        if (mc.player.getVehicle() instanceof com.lexovian.currentsoftrade.entity.SloopEntity
                || mc.player.getVehicle() instanceof com.lexovian.currentsoftrade.entity.TradeBoatEntity) {
            targetShip = mc.player.getVehicle();
        } else if (mc.crosshairPickEntity instanceof com.lexovian.currentsoftrade.entity.SloopEntity
                || mc.crosshairPickEntity instanceof com.lexovian.currentsoftrade.entity.TradeBoatEntity) {
            targetShip = mc.crosshairPickEntity;
        } else if (mc.crosshairPickEntity instanceof com.lexovian.currentsoftrade.entity.ShipPart<?> part) {
            targetShip = part.getParent();
        }

        if (targetShip == null) return;

        net.minecraft.core.BlockPos center = targetShip.blockPosition();
        boolean nearHarbor = false;
        for (net.minecraft.core.BlockPos check : net.minecraft.core.BlockPos.betweenClosed(
                center.offset(-16, -6, -16), center.offset(16, 6, 16))) {
            if (mc.level.getBlockEntity(check) instanceof com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity) {
                nearHarbor = true;
                break;
            }
        }
        if (!nearHarbor) return;

        final int shipId = targetShip.getId();
        int btnX = containerScreen.getLeftPos() + containerScreen.getImageWidth() - 58;
        int btnY = Math.max(2, containerScreen.getTopPos() - 16);
        net.minecraft.client.gui.components.Button unloadBtn = net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.translatable("gui.currents_of_trade.btn_unload_ship"),
                b -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                        new com.lexovian.currentsoftrade.network.ModPayloads.UnloadShipToHarborPayload(shipId))
        ).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.unload_ship_hint")
        )).bounds(btnX, btnY, 58, 15).build();

        event.addListener(unloadBtn);
    }
}
