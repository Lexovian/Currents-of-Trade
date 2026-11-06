package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class TravelScreen extends AbstractContainerScreen<TravelMenu> {

    private static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/travel_gui.png");

    private Button travelButton;
    private Button backButton;

    public TravelScreen(TravelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 196, 212);
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
            ClientPacketDistributor.sendToServer(new ModPayloads.OpenAnchorPointPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 7, this.topPos + 6, 36, 15).build();

        int travelBtnWidth = 120;
        int travelBtnHeight = 20;
        int travelBtnX = this.leftPos + (this.imageWidth - travelBtnWidth) / 2;

        this.travelButton = Button.builder(Component.translatable("gui.currents_of_trade.travel_btn"), b -> {
            ClientPacketDistributor.sendToServer(new ModPayloads.ExecuteTravelPayload(this.menu.getCurrentPos()));
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
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        super.extractLabels(guiGraphics, mouseX, mouseY);

        // Slot headers centered over Slot 0 (x=43..59 -> center 51) and Slot 1 (x=137..153 -> center 145)
        Component chartLabel = Component.translatable("gui.currents_of_trade.slot_chart");
        Component feeLabel = Component.translatable("gui.currents_of_trade.slot_fee");
        guiGraphics.text(this.font, chartLabel, 51 - this.font.width(chartLabel) / 2, 24, 0xFF404040, false);
        guiGraphics.text(this.font, feeLabel, 145 - this.font.width(feeLabel) / 2, 24, 0xFF404040, false);

        // Arrow between slots at y=40
        guiGraphics.text(this.font, "\u2794", 94, 40, 0xFF666666, false);

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
            guiGraphics.text(this.font, warnText, warnX, 58, 0xFFC0392B, false);
            guiGraphics.text(this.font, warnSub, subX, 70, 0xFF777777, false);
        } else if (this.menu.getCooldownSeconds() > 0) {
            Component cdText = Component.translatable("gui.currents_of_trade.cooldown_active", this.menu.getCooldownSeconds());
            Component cdSub = Component.translatable("gui.currents_of_trade.cooldown_sub");
            int cdX = (this.imageWidth - this.font.width(cdText)) / 2;
            int subX = (this.imageWidth - this.font.width(cdSub)) / 2;
            guiGraphics.text(this.font, cdText, cdX, 58, 0xFFD97706, false);
            guiGraphics.text(this.font, cdSub, subX, 70, 0xFF777777, false);
        } else {
            ItemStack chart = this.menu.getChartStack();
            String harborName = NauticalChartItem.getHarborName(chart);

            if (harborName != null) {
                int distance = this.menu.getDistance();
                int req = this.menu.getRequiredDoubloons();

                Component destText = Component.translatable("gui.currents_of_trade.dest_label", harborName);
                int destX = (this.imageWidth - this.font.width(destText)) / 2;
                guiGraphics.text(this.font, destText, destX, 57, 0xFF1B4D3E, false);

                if (this.menu.isTooExpensive()) {
                    Component expensiveText = Component.translatable("gui.currents_of_trade.too_expensive_full", distance);
                    int expX = (this.imageWidth - this.font.width(expensiveText)) / 2;
                    guiGraphics.text(this.font, expensiveText, expX, 70, 0xFFC0392B, false);
                } else {
                    Component feeText = Component.translatable("gui.currents_of_trade.fee_label", distance, req);
                    int feeX = (this.imageWidth - this.font.width(feeText)) / 2;
                    guiGraphics.text(this.font, feeText, feeX, 70, 0xFF8B5A00, false);
                }
            } else {
                Component promptText = Component.translatable("gui.currents_of_trade.insert_chart");
                int promptX = (this.imageWidth - this.font.width(promptText)) / 2;
                guiGraphics.text(this.font, promptText, promptX, 64, 0xFF777777, false);
            }
        }
    }
}
