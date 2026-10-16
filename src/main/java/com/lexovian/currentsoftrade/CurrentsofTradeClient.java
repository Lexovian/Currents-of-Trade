package com.lexovian.currentsoftrade;

import com.lexovian.currentsoftrade.client.gui.AnchorPointScreen;
import com.lexovian.currentsoftrade.client.gui.RequestTradeScreen;
import com.lexovian.currentsoftrade.client.gui.SendItemsScreen;
import com.lexovian.currentsoftrade.client.gui.TravelScreen;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.client.renderer.SloopRenderer;
import com.lexovian.currentsoftrade.compat.CustomData;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client-only mod-bus listeners.
 *
 * <p>Note: NeoForge 1.21's built-in config screen ({@code ConfigurationScreen}) has no Forge 1.20.1
 * equivalent; the config remains editable via {@code config/currents_of_trade-common.toml}.</p>
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CurrentsofTradeClient {

    // --- Client Setup, Predicates & Screen Registration ---

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    CurrentsofTrade.STORM_GLASS.get(),
                    new ResourceLocation(CurrentsofTrade.MODID, "weather"),
                    (stack, level, entity, seed) -> {
                        CustomData customData = CustomData.getOrNull(stack);
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

            // Forge 1.20.1 registers menu screens via MenuScreens (NeoForge uses RegisterMenuScreensEvent).
            MenuScreens.register(CurrentsofTrade.ANCHOR_POINT_MENU.get(), AnchorPointScreen::new);
            MenuScreens.register(CurrentsofTrade.TRAVEL_MENU.get(), TravelScreen::new);
            MenuScreens.register(CurrentsofTrade.SEND_ITEMS_MENU.get(), SendItemsScreen::new);
            MenuScreens.register(CurrentsofTrade.REQUEST_TRADE_MENU.get(), RequestTradeScreen::new);
        });
        CurrentsofTrade.LOGGER.info("Currents of Trade client setup completed.");
    }

    // --- Entity & Layer Renderers ---

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SloopModel.LAYER_LOCATION, SloopModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CurrentsofTrade.SLOOP.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.TRADE_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.CARGO_BOAT.get(), SloopRenderer::new);
        event.registerEntityRenderer(CurrentsofTrade.WANDERING_SAILOR.get(), WanderingTraderRenderer::new);
    }
}
