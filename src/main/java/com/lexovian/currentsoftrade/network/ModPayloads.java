package com.lexovian.currentsoftrade.network;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPayloads {

    /**
     * Resolves the real AnchorPointBlock position near the player.
     * Avoids (0,0,0) or player-foot coordinates that the client can accidentally send.
     */
    public static BlockPos findAnchorPointNear(ServerPlayer player, BlockPos preferredPos) {
        Level level = player.level();
        if (preferredPos != null && !preferredPos.equals(BlockPos.ZERO)) {
            if (level.getBlockEntity(preferredPos) instanceof AnchorPointBlockEntity) {
                return preferredPos;
            }
        }
        BlockPos playerPos = player.blockPosition();
        for (BlockPos check : BlockPos.betweenClosed(playerPos.offset(-5, -3, -5), playerPos.offset(5, 3, 5))) {
            if (level.getBlockEntity(check) instanceof AnchorPointBlockEntity) {
                return check.immutable();
            }
        }
        return preferredPos != null ? preferredPos : playerPos;
    }

    // --- Open Travel GUI ---

    public record OpenTravelPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenTravelPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "open_travel"));
        public static final StreamCodec<ByteBuf, OpenTravelPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenTravelPayload::pos, OpenTravelPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BlockPos targetPos = findAnchorPointNear(player, pos);
                    if (player.containerMenu instanceof AnchorPointMenu anchorMenu) {
                        BlockPos serverPos = anchorMenu.getBlockPos();
                        if (serverPos != null && !serverPos.equals(BlockPos.ZERO)
                                && player.level().getBlockEntity(serverPos) instanceof AnchorPointBlockEntity) {
                            targetPos = serverPos;
                        }
                    }
                    final BlockPos finalPos = targetPos;
                    player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new TravelMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.travel")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Travel ---

    public record ExecuteTravelPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ExecuteTravelPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "execute_travel"));
        public static final StreamCodec<ByteBuf, ExecuteTravelPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, ExecuteTravelPayload::pos, ExecuteTravelPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof TravelMenu travelMenu) {
                        boolean success = travelMenu.executeTravel(player);
                        if (success) {
                            player.closeContainer();
                            player.displayClientMessage(Component.translatable("message.currents_of_trade.travel_success"), true);
                        } else {
                            player.displayClientMessage(Component.translatable("message.currents_of_trade.travel_failed"), true);
                        }
                    }
                }
            });
        }
    }

    // --- Open Send Items GUI ---

    public record OpenSendItemsPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenSendItemsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "open_send_items"));
        public static final StreamCodec<ByteBuf, OpenSendItemsPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenSendItemsPayload::pos, OpenSendItemsPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BlockPos targetPos = findAnchorPointNear(player, pos);
                    if (player.containerMenu instanceof AnchorPointMenu anchorMenu) {
                        BlockPos serverPos = anchorMenu.getBlockPos();
                        if (serverPos != null && !serverPos.equals(BlockPos.ZERO)
                                && player.level().getBlockEntity(serverPos) instanceof AnchorPointBlockEntity) {
                            targetPos = serverPos;
                        }
                    }
                    final BlockPos finalPos = targetPos;
                    player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new SendItemsMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.send_items.title")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Send Cargo ---

    public record ExecuteSendCargoPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ExecuteSendCargoPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "execute_send_cargo"));
        public static final StreamCodec<ByteBuf, ExecuteSendCargoPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, ExecuteSendCargoPayload::pos, ExecuteSendCargoPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof SendItemsMenu sendItemsMenu) {
                        boolean success = sendItemsMenu.executeSendCargo(player);
                        if (success) player.closeContainer();
                    }
                }
            });
        }
    }

    // --- Open Anchor Point GUI (Back button) ---

    public record OpenAnchorPointPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenAnchorPointPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "open_anchor_point"));
        public static final StreamCodec<ByteBuf, OpenAnchorPointPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenAnchorPointPayload::pos, OpenAnchorPointPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BlockPos targetPos = findAnchorPointNear(player, pos);

                    // Prefer the position already confirmed in the currently open menu
                    if (player.containerMenu instanceof TravelMenu travelMenu) {
                        BlockPos serverPos = travelMenu.getCurrentPos();
                        if (isValidAnchorPos(player, serverPos)) targetPos = serverPos;
                    } else if (player.containerMenu instanceof SendItemsMenu sendItemsMenu) {
                        BlockPos serverPos = sendItemsMenu.getCurrentPos();
                        if (isValidAnchorPos(player, serverPos)) targetPos = serverPos;
                    } else if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        BlockPos serverPos = requestMenu.getCurrentPos();
                        if (isValidAnchorPos(player, serverPos)) targetPos = serverPos;
                    }

                    final BlockPos finalPos = targetPos;
                    player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new AnchorPointMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.anchor_point")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }

        private static boolean isValidAnchorPos(ServerPlayer player, BlockPos pos) {
            return pos != null && !pos.equals(BlockPos.ZERO)
                    && player.level().getBlockEntity(pos) instanceof AnchorPointBlockEntity;
        }
    }

    // --- Open Request Trade GUI ---

    public record OpenRequestTradePayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenRequestTradePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "open_request_trade"));
        public static final StreamCodec<ByteBuf, OpenRequestTradePayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenRequestTradePayload::pos, OpenRequestTradePayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BlockPos targetPos = findAnchorPointNear(player, pos);
                    if (player.containerMenu instanceof AnchorPointMenu anchorMenu) {
                        BlockPos serverPos = anchorMenu.getBlockPos();
                        if (serverPos != null && !serverPos.equals(BlockPos.ZERO)
                                && player.level().getBlockEntity(serverPos) instanceof AnchorPointBlockEntity) {
                            targetPos = serverPos;
                        }
                    }
                    final BlockPos finalPos = targetPos;
                    player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new RequestTradeMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.request_trade.title")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Request Trade (dispatch ship) ---

    public record ExecuteRequestTradePayload(BlockPos pos, int tradeIndex) implements CustomPacketPayload {
        public static final Type<ExecuteRequestTradePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "execute_request_trade"));
        public static final StreamCodec<ByteBuf, ExecuteRequestTradePayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ExecuteRequestTradePayload::pos,
                        ByteBufCodecs.VAR_INT,  ExecuteRequestTradePayload::tradeIndex,
                        ExecuteRequestTradePayload::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        boolean success = requestMenu.executeRequestTrade(player, tradeIndex);
                        if (success) player.closeContainer();
                    }
                }
            });
        }
    }

    // --- Add Trade to Order ---

    public record AddTradeToOrderPayload(BlockPos pos, int tradeIndex, boolean maxAll) implements CustomPacketPayload {
        public static final Type<AddTradeToOrderPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "add_trade_to_order"));
        public static final StreamCodec<ByteBuf, AddTradeToOrderPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,  AddTradeToOrderPayload::pos,
                        ByteBufCodecs.VAR_INT,  AddTradeToOrderPayload::tradeIndex,
                        ByteBufCodecs.BOOL,     AddTradeToOrderPayload::maxAll,
                        AddTradeToOrderPayload::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        requestMenu.addCurrentTradeToOrder(player, tradeIndex, maxAll);
                    }
                }
            });
        }
    }

    // --- Sync Selected Trade Index ---

    public record SelectTradeIndexPayload(BlockPos pos, int tradeIndex) implements CustomPacketPayload {
        public static final Type<SelectTradeIndexPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "select_trade_index"));
        public static final StreamCodec<ByteBuf, SelectTradeIndexPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SelectTradeIndexPayload::pos,
                        ByteBufCodecs.VAR_INT,  SelectTradeIndexPayload::tradeIndex,
                        SelectTradeIndexPayload::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        requestMenu.setSelectedTradeIndex(tradeIndex);
                    }
                }
            });
        }
    }

    // --- Reset Staged Order ---

    public record ResetTradeOrderPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ResetTradeOrderPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "reset_trade_order"));
        public static final StreamCodec<ByteBuf, ResetTradeOrderPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, ResetTradeOrderPayload::pos, ResetTradeOrderPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        requestMenu.resetStagedOrder(player);
                    }
                }
            });
        }
    }

    // --- Rename Harbor ---

    public record RenameHarborPayload(BlockPos pos, String newName) implements CustomPacketPayload {
        public static final Type<RenameHarborPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "rename_harbor"));
        public static final StreamCodec<ByteBuf, RenameHarborPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,       RenameHarborPayload::pos,
                        ByteBufCodecs.STRING_UTF8,   RenameHarborPayload::newName,
                        RenameHarborPayload::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BlockPos targetPos = findAnchorPointNear(player, pos);
                    if (player.containerMenu instanceof AnchorPointMenu anchorMenu) {
                        BlockPos serverPos = anchorMenu.getBlockPos();
                        if (serverPos != null && !serverPos.equals(BlockPos.ZERO)
                                && player.level().getBlockEntity(serverPos) instanceof AnchorPointBlockEntity) {
                            targetPos = serverPos;
                        }
                    }
                    Level level = player.level();
                    if (level.getBlockEntity(targetPos) instanceof AnchorPointBlockEntity anchor) {
                        String trimmed = (newName != null) ? newName.trim() : "";
                        if (trimmed.isEmpty()) {
                            trimmed = AnchorPointBlockEntity.generateDynamicHarborName(level, targetPos);
                        }
                        if (trimmed.length() > 32) {
                            trimmed = trimmed.substring(0, 32);
                        }
                        anchor.setHarborName(trimmed);
                        player.displayClientMessage(
                                Component.translatable("message.currents_of_trade.harbor_renamed", trimmed), true);
                        level.playSound(null, targetPos,
                                SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
            });
        }
    }

    // --- Payload registration ---

    @EventBusSubscriber(modid = CurrentsofTrade.MODID)
    public static class ModPayloadHandler {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("1.0.0");
            registrar.playToServer(OpenTravelPayload.TYPE,          OpenTravelPayload.STREAM_CODEC,          OpenTravelPayload::handle);
            registrar.playToServer(ExecuteTravelPayload.TYPE,       ExecuteTravelPayload.STREAM_CODEC,       ExecuteTravelPayload::handle);
            registrar.playToServer(OpenSendItemsPayload.TYPE,       OpenSendItemsPayload.STREAM_CODEC,       OpenSendItemsPayload::handle);
            registrar.playToServer(ExecuteSendCargoPayload.TYPE,    ExecuteSendCargoPayload.STREAM_CODEC,    ExecuteSendCargoPayload::handle);
            registrar.playToServer(OpenAnchorPointPayload.TYPE,     OpenAnchorPointPayload.STREAM_CODEC,     OpenAnchorPointPayload::handle);
            registrar.playToServer(OpenRequestTradePayload.TYPE,    OpenRequestTradePayload.STREAM_CODEC,    OpenRequestTradePayload::handle);
            registrar.playToServer(ExecuteRequestTradePayload.TYPE, ExecuteRequestTradePayload.STREAM_CODEC, ExecuteRequestTradePayload::handle);
            registrar.playToServer(AddTradeToOrderPayload.TYPE,     AddTradeToOrderPayload.STREAM_CODEC,     AddTradeToOrderPayload::handle);
            registrar.playToServer(SelectTradeIndexPayload.TYPE,    SelectTradeIndexPayload.STREAM_CODEC,    SelectTradeIndexPayload::handle);
            registrar.playToServer(ResetTradeOrderPayload.TYPE,     ResetTradeOrderPayload.STREAM_CODEC,     ResetTradeOrderPayload::handle);
            registrar.playToServer(RenameHarborPayload.TYPE,        RenameHarborPayload.STREAM_CODEC,        RenameHarborPayload::handle);
        }
    }
}
