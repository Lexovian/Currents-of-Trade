package com.lexovian.currentsoftrade;

import com.lexovian.currentsoftrade.client.gui.AnchorPointScreen;
import com.lexovian.currentsoftrade.client.gui.RequestTradeScreen;
import com.lexovian.currentsoftrade.client.gui.SendItemsScreen;
import com.lexovian.currentsoftrade.client.gui.TravelScreen;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.client.renderer.SloopRenderer;
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

// This class is only loaded on the client -- server-side code must never be called from here.
@Mod(value = CurrentsofTrade.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT)
public class CurrentsofTradeClient {

    public CurrentsofTradeClient(ModContainer container) {
        // Enables the mod's config screen via Mods -> Currents of Trade -> Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(
                    CurrentsofTrade.STORM_GLASS.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "weather"),
                    (stack, level, entity, seed) -> {
                        net.minecraft.world.level.Level world = level;
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

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CurrentsofTrade.ANCHOR_POINT_MENU.get(), AnchorPointScreen::new);
        event.register(CurrentsofTrade.TRAVEL_MENU.get(), TravelScreen::new);
        event.register(CurrentsofTrade.SEND_ITEMS_MENU.get(), SendItemsScreen::new);
        event.register(CurrentsofTrade.REQUEST_TRADE_MENU.get(), RequestTradeScreen::new);
    }

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SloopModel.LAYER_LOCATION, SloopModel::createBodyLayer);
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CurrentsofTrade.SLOOP.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.TRADE_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.CARGO_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.WANDERING_SAILOR.get(), net.minecraft.client.renderer.entity.WanderingTraderRenderer::new);
    }
}
