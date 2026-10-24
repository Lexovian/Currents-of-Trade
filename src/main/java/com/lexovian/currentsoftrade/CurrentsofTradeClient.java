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

    @EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
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
            int btnX = containerScreen.getGuiLeft() + containerScreen.getXSize() - 58;
            int btnY = Math.max(2, containerScreen.getGuiTop() - 16);
            net.minecraft.client.gui.components.Button unloadBtn = net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.translatable("gui.currents_of_trade.btn_unload_ship"),
                    b -> com.lexovian.currentsoftrade.network.ModPayloads.sendToServer(
                            new com.lexovian.currentsoftrade.network.ModPayloads.UnloadShipToHarborPayload(shipId))
            ).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.unload_ship_hint")
            )).bounds(btnX, btnY, 58, 15).build();

            event.addListener(unloadBtn);
        }
    }
}
