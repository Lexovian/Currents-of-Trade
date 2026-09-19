package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class RequestTradeScreen extends AbstractContainerScreen<RequestTradeMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/send_items_gui.png");

    private Button backButton;
    private Button prevTradeButton;
    private Button nextTradeButton;
    private Button addTradeButton;
    private Button clearOrderButton;
    private Button orderButton;

    public RequestTradeScreen(RequestTradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 236;
        this.titleLabelX = 48;
        this.titleLabelY = 9;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 136;
    }

    @Override
    protected void init() {
        super.init();

        // Compact Back button at top-left
        this.backButton = Button.builder(Component.translatable("gui.currents_of_trade.back"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.OpenAnchorPointPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 7, this.topPos + 6, 36, 15).build();

        // Prev trade button '<'
        this.prevTradeButton = Button.builder(Component.literal("\u25c0"), b -> {
            List<HarborTradeOffer> trades = this.menu.getHarborTrades();
            if (!trades.isEmpty()) {
                int current = this.menu.getSelectedTradeIndex();
                int next = (current - 1 + trades.size()) % trades.size();
                this.menu.setSelectedTradeIndex(next);
                PacketDistributor.sendToServer(new ModPayloads.SelectTradeIndexPayload(this.menu.getCurrentPos(), next));
            }
        }).bounds(this.leftPos + 18, this.topPos + 66, 16, 18).build();

        // Next trade button '>'
        this.nextTradeButton = Button.builder(Component.literal("\u25b6"), b -> {
            List<HarborTradeOffer> trades = this.menu.getHarborTrades();
            if (!trades.isEmpty()) {
                int current = this.menu.getSelectedTradeIndex();
                int next = (current + 1) % trades.size();
                this.menu.setSelectedTradeIndex(next);
                PacketDistributor.sendToServer(new ModPayloads.SelectTradeIndexPayload(this.menu.getCurrentPos(), next));
            }
        }).bounds(this.leftPos + 162, this.topPos + 66, 16, 18).build();

        // Add trade to batch order button
        this.addTradeButton = Button.builder(Component.translatable("gui.currents_of_trade.btn_add_trade"), b -> {
            boolean maxAll = hasShiftDown();
            PacketDistributor.sendToServer(new ModPayloads.AddTradeToOrderPayload(this.menu.getCurrentPos(), this.menu.getSelectedTradeIndex(), maxAll));
        }).tooltip(Tooltip.create(Component.translatable("tooltip.currents_of_trade.add_trade_hint")))
          .bounds(this.leftPos + 68, this.topPos + 86, 56, 18).build();

        // Reset / Clear staged order button
        this.clearOrderButton = Button.builder(Component.translatable("gui.currents_of_trade.btn_clear_order"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.ResetTradeOrderPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 128, this.topPos + 86, 50, 18).build();

        // Centered Order trade ship button inside the dedicated 24px button band
        int orderBtnWidth = 140;
        int orderBtnHeight = 18;
        int orderBtnX = this.leftPos + (this.imageWidth - orderBtnWidth) / 2;
        int orderBtnY = this.topPos + 110;

        this.orderButton = Button.builder(Component.translatable("gui.currents_of_trade.btn_order_ship"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.ExecuteRequestTradePayload(this.menu.getCurrentPos(), this.menu.getSelectedTradeIndex()));
        }).bounds(orderBtnX, orderBtnY, orderBtnWidth, orderBtnHeight).build();

        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.prevTradeButton);
        this.addRenderableWidget(this.nextTradeButton);
        this.addRenderableWidget(this.addTradeButton);
        this.addRenderableWidget(this.clearOrderButton);
        this.addRenderableWidget(this.orderButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        List<HarborTradeOffer> trades = this.menu.getHarborTrades();
        boolean hasTrades = !trades.isEmpty();

        if (this.prevTradeButton != null) {
            this.prevTradeButton.active = hasTrades && trades.size() > 1;
        }
        if (this.nextTradeButton != null) {
            this.nextTradeButton.active = hasTrades && trades.size() > 1;
        }

        if (this.addTradeButton != null) {
            this.addTradeButton.active = this.menu.hasValidChart() && this.menu.hasRequiredTradeItems();
            if (hasShiftDown()) {
                this.addTradeButton.setMessage(Component.translatable("gui.currents_of_trade.btn_add_trade_all"));
            } else {
                this.addTradeButton.setMessage(Component.translatable("gui.currents_of_trade.btn_add_trade"));
            }
        }

        if (this.clearOrderButton != null) {
            this.clearOrderButton.active = this.menu.getStagedTradeCount() > 0;
        }

        if (this.orderButton != null) {
            this.orderButton.active = this.menu.canRequestTrade();

            AnchorPointBlockEntity.HarborStatus status = this.menu.getHarborStatus();
            int stagedCount = this.menu.getStagedTradeCount();

            if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_invalid_harbor"));
            } else if (this.menu.getCooldownSeconds() > 0) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_cooldown", this.menu.getCooldownSeconds()));
            } else if (!this.menu.hasValidChart()) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_chart"));
            } else if (stagedCount == 0 && !this.menu.hasRequiredTradeItems() && !hasTrades) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_trades"));
            } else if (this.menu.isTooExpensive()) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_too_expensive"));
            } else if (!this.menu.hasEnoughDoubloons()) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_fee"));
            } else if (stagedCount > 0) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_order_ship_batch", stagedCount));
            } else if (this.menu.hasRequiredTradeItems()) {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_order_ship"));
            } else {
                this.orderButton.setMessage(Component.translatable("gui.currents_of_trade.btn_missing_items"));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Main container background
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        // Chart slot box (slot at x=24, y=36 -> box at 23, 35)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 23, this.topPos + 35, 17, 146, 18, 18);
        // Fee slot box (slot at x=64, y=36 -> box at 63, 35)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 63, this.topPos + 35, 17, 146, 18, 18);

        // Cost A slot box (slot at x=24, y=87 -> box at 23, 86)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 23, this.topPos + 86, 17, 146, 18, 18);
        // Cost B slot box (slot at x=44, y=87 -> box at 43, 86)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 43, this.topPos + 86, 17, 146, 18, 18);

        // Trade offer display frame plate in the middle
        guiGraphics.fill(this.leftPos + 36, this.topPos + 66, this.leftPos + 160, this.topPos + 84, 0x22000000);
        guiGraphics.renderOutline(this.leftPos + 36, this.topPos + 66, 124, 18, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Window Title
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        // Slot headers directly above Chart and Fee slots (at y=24, safely clear of back button)
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_chart"), 20, 24, 0x555555, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_fee"), 64, 24, 0x555555, false);

        // Destination Info to the right of Chart and Fee slots (x=88)
        if (this.menu.hasValidChart()) {
            String destHarbor = this.menu.getTargetHarborName();
            int dist = (int) this.menu.getDistance();
            int fee = this.menu.getRequiredFee();
            int surcharge = this.menu.getCargoSurcharge();

            Component destText = Component.literal("§2§l" + this.font.plainSubstrByWidth(destHarbor, 95));
            guiGraphics.drawString(this.font, destText, 88, 24, 0x1B4D3E, false);

            Component distText = Component.literal("§8Dist: §0" + dist + "m");
            guiGraphics.drawString(this.font, distText, 88, 35, 0x404040, false);

            if (this.menu.isTooExpensive()) {
                guiGraphics.drawString(this.font, Component.literal("§cToo Far (> 64)"), 88, 46, 0xAA0000, false);
            } else {
                String surchargeStr = surcharge > 0 ? " §8(+§e" + surcharge + "§8)" : "";
                Component feeText = Component.literal("§8Fee: §6" + fee + " Dbl" + surchargeStr);
                guiGraphics.drawString(this.font, feeText, 88, 46, 0x8B5A00, false);
            }
        } else {
            guiGraphics.drawString(this.font, Component.literal("§7No Chart"), 88, 28, 0x777777, false);
            guiGraphics.drawString(this.font, Component.literal("§8Insert chart"), 88, 40, 0x999999, false);
        }

        // Section header: Harbor Offer
        List<HarborTradeOffer> trades = this.menu.getHarborTrades();
        if (!trades.isEmpty()) {
            int selectedIdx = this.menu.getSelectedTradeIndex();
            if (selectedIdx >= trades.size()) {
                selectedIdx = 0;
            }
            Component offerHeader = Component.translatable("gui.currents_of_trade.harbor_offer");
            String counter = " §8(" + (selectedIdx + 1) + "/" + trades.size() + ")";
            guiGraphics.drawString(this.font, offerHeader.getString() + counter, 18, 56, 0x333333, false);
        } else {
            guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.harbor_offer"), 18, 56, 0x333333, false);
        }

        // Player Inventory title
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        List<HarborTradeOffer> trades = this.menu.getHarborTrades();
        if (!trades.isEmpty()) {
            int selectedIdx = this.menu.getSelectedTradeIndex();
            if (selectedIdx >= trades.size()) {
                selectedIdx = 0;
                this.menu.setSelectedTradeIndex(0);
            }

            HarborTradeOffer offer = trades.get(selectedIdx);
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack res = offer.getResult();

            int baseX = this.leftPos + 40;
            int baseY = this.topPos + 67;

            // Render Cost A
            if (!costA.isEmpty()) {
                guiGraphics.renderItem(costA, baseX, baseY);
                guiGraphics.renderItemDecorations(this.font, costA, baseX, baseY);
            }

            int arrowX;
            int resultX;

            if (!costB.isEmpty()) {
                // Cost B present
                guiGraphics.drawString(this.font, "+", baseX + 19, baseY + 4, 0x555555, false);
                guiGraphics.renderItem(costB, baseX + 28, baseY);
                guiGraphics.renderItemDecorations(this.font, costB, baseX + 28, baseY);

                arrowX = baseX + 48;
                resultX = baseX + 62;
            } else {
                arrowX = baseX + 22;
                resultX = baseX + 36;
            }

            // Arrow
            guiGraphics.drawString(this.font, "\u2794", arrowX, baseY + 4, 0x555555, false);

            // Render Result
            if (!res.isEmpty()) {
                guiGraphics.renderItem(res, resultX, baseY);
                guiGraphics.renderItemDecorations(this.font, res, resultX, baseY);
            }

            // Tooltips on hovering over trade offer items
            if (isHovering(40, 67, 16, 16, mouseX, mouseY) && !costA.isEmpty()) {
                guiGraphics.renderTooltip(this.font, costA, mouseX, mouseY);
            } else if (!costB.isEmpty() && isHovering(68, 67, 16, 16, mouseX, mouseY)) {
                guiGraphics.renderTooltip(this.font, costB, mouseX, mouseY);
            } else if (isHovering(resultX - this.leftPos, 67, 16, 16, mouseX, mouseY) && !res.isEmpty()) {
                guiGraphics.renderTooltip(this.font, res, mouseX, mouseY);
            }
        }

        // Staged batch order status line
        int stagedCount = this.menu.getStagedTradeCount();
        int stagedItems = this.menu.getStagedItemCount();
        if (stagedCount > 0) {
            String statusText = "§2§l\u2714 " + Component.translatable("gui.currents_of_trade.staged_count", stagedCount, stagedItems).getString();
            guiGraphics.drawString(this.font, statusText, this.leftPos + 22, this.topPos + 107, 0x2E7D32, false);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
