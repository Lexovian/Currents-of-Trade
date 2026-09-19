package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TravelScreen extends AbstractContainerScreen<TravelMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/travel_gui.png");

    private Button travelButton;
    private Button backButton;

    public TravelScreen(TravelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 212;
        this.titleLabelX = 48;
        this.titleLabelY = 9;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 112;
    }

    @Override
    protected void init() {
        super.init();

        // Compact Back button placed cleanly at the top-left, ending at y=21
        this.backButton = Button.builder(Component.translatable("gui.currents_of_trade.back"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.OpenAnchorPointPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 7, this.topPos + 6, 36, 15).build();

        int travelBtnWidth = 120;
        int travelBtnHeight = 20;
        int travelBtnX = this.leftPos + (this.imageWidth - travelBtnWidth) / 2;

        this.travelButton = Button.builder(Component.translatable("gui.currents_of_trade.travel_btn"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.ExecuteTravelPayload(this.menu.getCurrentPos()));
        }).bounds(travelBtnX, this.topPos + 84, travelBtnWidth, travelBtnHeight).build();

        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.travelButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.travelButton != null) {
            this.travelButton.active = this.menu.canTravel();
            AnchorPointBlockEntity.HarborStatus status = this.menu.getHarborStatus();
            if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                this.travelButton.setMessage(Component.translatable("gui.currents_of_trade.btn_invalid_harbor"));
            } else if (this.menu.getCooldownSeconds() > 0) {
                this.travelButton.setMessage(Component.translatable("gui.currents_of_trade.btn_cooldown", this.menu.getCooldownSeconds()));
            } else if (this.menu.isTooExpensive()) {
                this.travelButton.setMessage(Component.translatable("gui.currents_of_trade.btn_too_expensive"));
            } else {
                this.travelButton.setMessage(Component.translatable("gui.currents_of_trade.travel_btn"));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Draw Titles
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);

        // Slot headers - placed at y=24, safely clear of the Back button
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_chart"), 36, 24, 0x404040, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_fee"), 138, 24, 0x404040, false);

        // Arrow between slots at y=40
        guiGraphics.drawString(this.font, "\u2794", 94, 40, 0x666666, false);

        // Destination & fee text in middle area
        AnchorPointBlockEntity.HarborStatus status = this.menu.getHarborStatus();
        if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
            Component warnText;
            Component warnSub;
            switch (status) {
                case TOO_SMALL -> {
                    warnText = Component.translatable("gui.currents_of_trade.harbor_too_small");
                    warnSub = Component.translatable("gui.currents_of_trade.harbor_too_small_sub");
                }
                case UNDERGROUND -> {
                    warnText = Component.translatable("gui.currents_of_trade.harbor_underground");
                    warnSub = Component.translatable("gui.currents_of_trade.harbor_underground_sub");
                }
                case TOO_SHALLOW -> {
                    warnText = Component.translatable("gui.currents_of_trade.harbor_too_shallow");
                    warnSub = Component.translatable("gui.currents_of_trade.harbor_too_shallow_sub");
                }
                case WRONG_DIMENSION -> {
                    warnText = Component.translatable("gui.currents_of_trade.harbor_wrong_dim");
                    warnSub = Component.translatable("gui.currents_of_trade.harbor_wrong_dim_sub");
                }
                default -> {
                    warnText = Component.translatable("gui.currents_of_trade.near_water_required");
                    warnSub = Component.translatable("gui.currents_of_trade.near_water_sub");
                }
            }
            int warnX = (this.imageWidth - this.font.width(warnText)) / 2;
            int subX = (this.imageWidth - this.font.width(warnSub)) / 2;
            guiGraphics.drawString(this.font, warnText, warnX, 58, 0xC0392B, false);
            guiGraphics.drawString(this.font, warnSub, subX, 70, 0x777777, false);
        } else if (this.menu.getCooldownSeconds() > 0) {
            Component cdText = Component.translatable("gui.currents_of_trade.cooldown_active", this.menu.getCooldownSeconds());
            Component cdSub = Component.translatable("gui.currents_of_trade.cooldown_sub");
            int cdX = (this.imageWidth - this.font.width(cdText)) / 2;
            int subX = (this.imageWidth - this.font.width(cdSub)) / 2;
            guiGraphics.drawString(this.font, cdText, cdX, 58, 0xD97706, false);
            guiGraphics.drawString(this.font, cdSub, subX, 70, 0x777777, false);
        } else {
            ItemStack chart = this.menu.getChartStack();
            String harborName = NauticalChartItem.getHarborName(chart);

            if (harborName != null) {
                int distance = this.menu.getDistance();
                int req = this.menu.getRequiredDoubloons();

                Component destText = Component.translatable("gui.currents_of_trade.dest_label", harborName);
                int destX = (this.imageWidth - this.font.width(destText)) / 2;
                guiGraphics.drawString(this.font, destText, destX, 57, 0x1B4D3E, false);

                if (this.menu.isTooExpensive()) {
                    Component expensiveText = Component.translatable("gui.currents_of_trade.too_expensive_full", distance);
                    int expX = (this.imageWidth - this.font.width(expensiveText)) / 2;
                    guiGraphics.drawString(this.font, expensiveText, expX, 70, 0xC0392B, false);
                } else {
                    Component feeText = Component.translatable("gui.currents_of_trade.fee_label", distance, req);
                    int feeX = (this.imageWidth - this.font.width(feeText)) / 2;
                    guiGraphics.drawString(this.font, feeText, feeX, 70, 0x8B5A00, false);
                }
            } else {
                Component promptText = Component.translatable("gui.currents_of_trade.insert_chart");
                int promptX = (this.imageWidth - this.font.width(promptText)) / 2;
                guiGraphics.drawString(this.font, promptText, promptX, 64, 0x777777, false);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
