package com.lexovian.currentsoftrade;

import com.lexovian.currentsoftrade.client.gui.AnchorPointScreen;
import com.lexovian.currentsoftrade.client.gui.RequestTradeScreen;
import com.lexovian.currentsoftrade.client.gui.SendItemsScreen;
import com.lexovian.currentsoftrade.client.gui.TravelScreen;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.client.renderer.SloopRenderer;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CurrentsofTrade.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT)
public class CurrentsofTradeClient {

    // --- Config Screen ---

    public CurrentsofTradeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    // --- Client Setup & Predicates ---

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    CurrentsofTrade.STORM_GLASS.get(),
                    ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "weather"),
                    (stack, level, entity, seed) -> {
                        net.minecraft.world.item.component.CustomData customData =
                                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (customData != null) {
                            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                            if (tag.contains(com.lexovian.currentsoftrade.item.StormGlassItem.TAG_WEATHER_STATE)) {
                                return (float) tag.getInt(com.lexovian.currentsoftrade.item.StormGlassItem.TAG_WEATHER_STATE);
                            }
                        }
                        Level world = level;
                        if (world == null && entity != null) {
                            world = entity.level();
                        }
                        if (world == null) return 0.0F;
                        if (world.isThundering()) return 2.0F;
                        if (world.isRaining()) return 1.0F;
                        return 0.0F;
                    }
            );
        });
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
}
