package com.lexovian.currentsoftrade.network;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModPayloads {

    // --- Helper Methods ---

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

    public record OpenTravelPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL != null
                            && !com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL.get()) {
                        player.displayClientMessage(Component.translatable("message.currents_of_trade.travel_disabled_config"), true);
                        return;
                    }
                    BlockPos targetPos = findAnchorPointNear(player, pos);
                    if (player.containerMenu instanceof AnchorPointMenu anchorMenu) {
                        BlockPos serverPos = anchorMenu.getBlockPos();
                        if (serverPos != null && !serverPos.equals(BlockPos.ZERO)
                                && player.level().getBlockEntity(serverPos) instanceof AnchorPointBlockEntity) {
                            targetPos = serverPos;
                        }
                    }
                    final BlockPos finalPos = targetPos;
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inv, p) -> new TravelMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.travel")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Travel ---

    public record ExecuteTravelPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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

    public record OpenSendItemsPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inv, p) -> new SendItemsMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.send_items.title")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Send Cargo ---

    public record ExecuteSendCargoPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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

    public record OpenAnchorPointPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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
                    int harborLevel = 1;
                    if (player.level().getBlockEntity(finalPos) instanceof AnchorPointBlockEntity anchor) {
                        harborLevel = anchor.getTradeLevel();
                    }
                    final int finalLevel = harborLevel;
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inv, p) -> new AnchorPointMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.anchor_point")
                    ), buf -> {
                        buf.writeBlockPos(finalPos);
                        buf.writeVarInt(finalLevel);
                    });
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

    public record OpenRequestTradePayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inv, p) -> new RequestTradeMenu(id, inv, ContainerLevelAccess.create(player.level(), finalPos), finalPos),
                            Component.translatable("gui.currents_of_trade.request_trade.title")
                    ));
                    player.inventoryMenu.broadcastChanges();
                }
            });
        }
    }

    // --- Execute Request Trade (dispatch ship) ---

    public record ExecuteRequestTradePayload(BlockPos pos, int tradeIndex) {
        public void handle(PayloadContext context) {
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

    public record AddTradeToOrderPayload(BlockPos pos, int tradeIndex, boolean maxAll) {
        public void handle(PayloadContext context) {
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

    public record SelectTradeIndexPayload(BlockPos pos, int tradeIndex) {
        public void handle(PayloadContext context) {
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

    public record ResetTradeOrderPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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

    public record RenameHarborPayload(BlockPos pos, String newName) {
        public void handle(PayloadContext context) {
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

    // --- Upgrade Harbor ---

    public record UpgradeHarborPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
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
                        if (anchor.getTradeLevel() >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) {
                            player.displayClientMessage(
                                    Component.translatable("message.currents_of_trade.harbor_max_level"), true);
                            return;
                        }
                        if (!anchor.canUpgrade(player)) {
                            player.displayClientMessage(
                                    Component.translatable("message.currents_of_trade.harbor_cannot_afford"), true);
                            return;
                        }
                        anchor.executeUpgrade(player);
                        int newLevel = anchor.getTradeLevel();
                        String levelName = anchor.getHarborLevelName();
                        player.displayClientMessage(
                                Component.translatable("message.currents_of_trade.harbor_upgraded",
                                        newLevel, levelName), true);
                        level.playSound(null, targetPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9F, 1.2F);
                        level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);

                        if (newLevel >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) {
                            final BlockPos finalTargetPos = targetPos;
                            final int finalLevel = newLevel;
                            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                                    (id, inv, p) -> new AnchorPointMenu(id, inv, ContainerLevelAccess.create(level, finalTargetPos), finalTargetPos),
                                    Component.translatable("gui.currents_of_trade.anchor_point")
                            ), buf -> {
                                buf.writeBlockPos(finalTargetPos);
                                buf.writeVarInt(finalLevel);
                            });
                        } else {
                            player.containerMenu.broadcastChanges();
                        }
                    }
                }
            });
        }
    }

    // --- Repeat Last Trade Order Preset ---

    public record RepeatTradeOrderPayload(BlockPos pos) {
        public void handle(PayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.containerMenu instanceof RequestTradeMenu requestMenu) {
                        requestMenu.repeatLastTradeOrder(player);
                    }
                }
            });
        }
    }

    // --- Unload Ship Cargo to Nearby Harbor Depot ---

    public record UnloadShipToHarborPayload(int entityId) {
        public void handle(PayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    Level level = player.level();
                    net.minecraft.world.Container shipContainer = null;
                    net.minecraft.world.entity.Entity shipEntity = null;

                    if (entityId >= 0) {
                        net.minecraft.world.entity.Entity e = level.getEntity(entityId);
                        if (e instanceof net.minecraft.world.Container c && player.distanceToSqr(e) <= 256.0) {
                            shipContainer = c;
                            shipEntity = e;
                        }
                    }
                    if (shipContainer == null && player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu chestMenu) {
                        if (chestMenu.getContainer() instanceof net.minecraft.world.entity.Entity e && player.distanceToSqr(e) <= 256.0) {
                            shipContainer = chestMenu.getContainer();
                            shipEntity = e;
                        }
                    }
                    if (shipContainer == null && player.getVehicle() instanceof net.minecraft.world.Container c) {
                        shipContainer = c;
                        shipEntity = player.getVehicle();
                    }
                    if (shipContainer == null || shipEntity == null) return;

                    BlockPos center = shipEntity.blockPosition();
                    BlockPos nearestAnchor = null;
                    double bestDistSq = Double.MAX_VALUE;
                    for (BlockPos check : BlockPos.betweenClosed(center.offset(-16, -6, -16), center.offset(16, 6, 16))) {
                        if (level.getBlockEntity(check) instanceof AnchorPointBlockEntity) {
                            double dSq = check.distSqr(center);
                            if (dSq < bestDistSq) {
                                bestDistSq = dSq;
                                nearestAnchor = check.immutable();
                            }
                        }
                    }

                    if (nearestAnchor == null) {
                        player.displayClientMessage(Component.translatable("message.currents_of_trade.ship_unload_no_harbor"), true);
                        return;
                    }

                    String harborName = "Harbor";
                    if (level.getBlockEntity(nearestAnchor) instanceof AnchorPointBlockEntity anchor) {
                        harborName = anchor.getHarborName();
                    }

                    int transferred = com.lexovian.currentsoftrade.entity.TradeBoatEntity.unloadContainerToHarborStorage(
                            level, nearestAnchor, shipContainer);
                    if (transferred > 0) {
                        level.playSound(null, nearestAnchor, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                    nearestAnchor.getX() + 0.5, nearestAnchor.getY() + 1.2, nearestAnchor.getZ() + 0.5,
                                    10, 0.35, 0.25, 0.35, 0.05);
                        }
                        player.displayClientMessage(
                                Component.translatable("message.currents_of_trade.ship_unloaded_to_harbor", transferred, harborName),
                                true
                        );
                        player.containerMenu.broadcastChanges();
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.currents_of_trade.ship_unload_no_storage", harborName),
                                true
                        );
                    }
                }
            });
        }
    }

    // --- Forge SimpleChannel (replaces NeoForge payload registrar) ---

    private static final String PROTOCOL_VERSION = "1.0.0";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CurrentsofTrade.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** Minimal stand-in for NeoForge's IPayloadContext so payload handlers stay identical to 1.21.1. */
    public static final class PayloadContext {
        private final NetworkEvent.Context ctx;
        PayloadContext(NetworkEvent.Context ctx) { this.ctx = ctx; }
        public void enqueueWork(Runnable work) { ctx.enqueueWork(work); }
        public net.minecraft.world.entity.player.Player player() { return ctx.getSender(); }
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    private static int nextId = 0;

    private static <T> void playToServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                         Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PayloadContext> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder,
                (T msg, Supplier<NetworkEvent.Context> sup) -> {
                    NetworkEvent.Context ctx = sup.get();
                    handler.accept(msg, new PayloadContext(ctx));
                    ctx.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void register() {
        playToServer(OpenTravelPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new OpenTravelPayload(b.readBlockPos()), OpenTravelPayload::handle);
        playToServer(ExecuteTravelPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new ExecuteTravelPayload(b.readBlockPos()), ExecuteTravelPayload::handle);
        playToServer(OpenSendItemsPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new OpenSendItemsPayload(b.readBlockPos()), OpenSendItemsPayload::handle);
        playToServer(ExecuteSendCargoPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new ExecuteSendCargoPayload(b.readBlockPos()), ExecuteSendCargoPayload::handle);
        playToServer(OpenAnchorPointPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new OpenAnchorPointPayload(b.readBlockPos()), OpenAnchorPointPayload::handle);
        playToServer(OpenRequestTradePayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new OpenRequestTradePayload(b.readBlockPos()), OpenRequestTradePayload::handle);
        playToServer(ExecuteRequestTradePayload.class,
                (m, b) -> { b.writeBlockPos(m.pos()); b.writeVarInt(m.tradeIndex()); },
                b -> new ExecuteRequestTradePayload(b.readBlockPos(), b.readVarInt()), ExecuteRequestTradePayload::handle);
        playToServer(AddTradeToOrderPayload.class,
                (m, b) -> { b.writeBlockPos(m.pos()); b.writeVarInt(m.tradeIndex()); b.writeBoolean(m.maxAll()); },
                b -> new AddTradeToOrderPayload(b.readBlockPos(), b.readVarInt(), b.readBoolean()), AddTradeToOrderPayload::handle);
        playToServer(SelectTradeIndexPayload.class,
                (m, b) -> { b.writeBlockPos(m.pos()); b.writeVarInt(m.tradeIndex()); },
                b -> new SelectTradeIndexPayload(b.readBlockPos(), b.readVarInt()), SelectTradeIndexPayload::handle);
        playToServer(ResetTradeOrderPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new ResetTradeOrderPayload(b.readBlockPos()), ResetTradeOrderPayload::handle);
        playToServer(RenameHarborPayload.class,
                (m, b) -> { b.writeBlockPos(m.pos()); b.writeUtf(m.newName()); },
                b -> new RenameHarborPayload(b.readBlockPos(), b.readUtf()), RenameHarborPayload::handle);
        playToServer(UpgradeHarborPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new UpgradeHarborPayload(b.readBlockPos()), UpgradeHarborPayload::handle);
        playToServer(RepeatTradeOrderPayload.class,
                (m, b) -> b.writeBlockPos(m.pos()), b -> new RepeatTradeOrderPayload(b.readBlockPos()), RepeatTradeOrderPayload::handle);
        playToServer(UnloadShipToHarborPayload.class,
                (m, b) -> b.writeVarInt(m.entityId()), b -> new UnloadShipToHarborPayload(b.readVarInt()), UnloadShipToHarborPayload::handle);
    }
}