package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SendItemsScreen extends AbstractContainerScreen<SendItemsMenu> {

    // --- Constants & Widgets ---

    private static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/send_items_gui.png");

    private Button sendCargoButton;
    private Button backButton;
    private Button prevPageBtn;
    private Button nextPageBtn;

    // --- Initialization ---

    public SendItemsScreen(SendItemsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 196, 236);
        this.titleLabelX = 48;
        this.titleLabelY = 9;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 136;
    }

    @Override
    protected void init() {
        super.init();

        this.backButton = Button.builder(Component.translatable("gui.currents_of_trade.back"), b -> {
            ClientPacketDistributor.sendToServer(new ModPayloads.OpenAnchorPointPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 7, this.topPos + 6, 36, 15).build();

        int sendBtnWidth = 120;
        int sendBtnHeight = 18;
        int sendBtnX = this.leftPos + (this.imageWidth - sendBtnWidth) / 2;

        this.sendCargoButton = Button.builder(Component.translatable("gui.currents_of_trade.send_cargo_btn"), b -> {
            ClientPacketDistributor.sendToServer(new ModPayloads.ExecuteSendCargoPayload(this.menu.getCurrentPos()));
        }).bounds(sendBtnX, this.topPos + 111, sendBtnWidth, sendBtnHeight).build();

        this.prevPageBtn = Button.builder(Component.literal("◀"), b -> {
            this.menu.prevPage();
        }).bounds(this.leftPos + 144, this.topPos + 54, 16, 12).build();

        this.nextPageBtn = Button.builder(Component.literal("▶"), b -> {
            this.menu.nextPage();
        }).bounds(this.leftPos + 162, this.topPos + 54, 16, 12).build();

        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.sendCargoButton);
        this.addRenderableWidget(this.prevPageBtn);
        this.addRenderableWidget(this.nextPageBtn);
    }

    // --- State & Paging Updates ---

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.sendCargoButton != null) {
            this.sendCargoButton.active = this.menu.canSendCargo();
            AnchorPointBlockEntity.HarborStatus status = this.menu.getHarborStatus();
            if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_invalid_harbor"));
            } else if (this.menu.getCooldownSeconds() > 0) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_cooldown", this.menu.getCooldownSeconds()));
            } else if (!this.menu.hasValidChart()) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_chart"));
            } else if (!this.menu.hasCargo()) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_cargo"));
            } else if (this.menu.isTooExpensive()) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_too_expensive"));
            } else if (!this.menu.hasEnoughDoubloons()) {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.btn_no_fee"));
            } else {
                this.sendCargoButton.setMessage(Component.translatable("gui.currents_of_trade.send_cargo_btn"));
            }
        }

        if (this.prevPageBtn != null && this.nextPageBtn != null) {
            int maxPages = this.menu.getMaxPages();
            boolean multiPage = maxPages > 1;
            this.prevPageBtn.visible = multiPage;
            this.nextPageBtn.visible = multiPage;
            this.prevPageBtn.active = this.menu.getCurrentPage() > 0;
            this.nextPageBtn.active = this.menu.getCurrentPage() < maxPages - 1;
        }
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        // Base container background from enlarged send_items_gui
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // Chart slot box (x=23, y=35)
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos + 23, this.topPos + 35, 17, 146, 18, 18, 256, 256);
        // Fee slot box (x=63, y=35)
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos + 63, this.topPos + 35, 17, 146, 18, 18, 256, 256);

        // Draw cargo slot boxes for current page
        int page = this.menu.getCurrentPage();
        int startIndex = page * SendItemsMenu.SLOTS_PER_PAGE;
        int totalSlots = this.menu.getCargoSlotCount();

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = startIndex + row * 9 + col;
                int x = this.leftPos + 17 + col * 18;
                int y = this.topPos + 69 + row * 18;
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 17, 146, 18, 18, 256, 256);
                if (slotIndex >= totalSlots) {
                    // Dim locked slots
                    guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0x77000000);
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        super.extractLabels(guiGraphics, mouseX, mouseY);

        // Slot headers centered directly above Chart (x=32) and Fee (x=72)
        Component chartLabel = Component.translatable("gui.currents_of_trade.slot_chart");
        Component feeLabel = Component.translatable("gui.currents_of_trade.slot_fee");
        guiGraphics.text(this.font, chartLabel, 32 - this.font.width(chartLabel) / 2, 24, 0xFF555555, false);
        guiGraphics.text(this.font, feeLabel, 72 - this.font.width(feeLabel) / 2, 24, 0xFF555555, false);

        ItemStack chart = this.menu.getChartStack();
        boolean hasBoundChart = this.menu.hasValidChart();

        // Destination details placed cleanly to the right of the slots (x=86..186)
        if (hasBoundChart) {
            String destHarbor = NauticalChartItem.getTargetHarbor(chart);
            int dist = this.menu.getDistance();
            int fee = this.menu.getRequiredFee();

            Component destText = Component.literal("§2§l" + this.font.plainSubstrByWidth(destHarbor, 96));
            guiGraphics.text(this.font, destText, 86, 21, 0xFF1B4D3E, false);

            Component distText = Component.literal("§8Dist: §0" + dist + "m");
            guiGraphics.text(this.font, distText, 86, 31, 0xFF404040, false);

            if (this.menu.isTooExpensive()) {
                guiGraphics.text(this.font, Component.literal("§cToo Far (> 64)"), 86, 41, 0xFFAA0000, false);
            } else {
                Component feeText = Component.literal("§8Fee: §6" + fee + " Dbl");
                guiGraphics.text(this.font, feeText, 86, 41, 0xFF8B5A00, false);
            }
        } else {
            guiGraphics.text(this.font, Component.literal("§7No Chart"), 86, 28, 0xFF777777, false);
            guiGraphics.text(this.font, Component.literal("§8Insert chart"), 86, 40, 0xFF999999, false);
        }

        // Cargo section title (displays dynamic slot count and page)
        int cargoSlots = this.menu.getCargoSlotCount();
        int maxPages = this.menu.getMaxPages();
        String cargoTitle;
        if (maxPages > 1) {
            cargoTitle = "Cargo (" + (this.menu.getCurrentPage() + 1) + "/" + maxPages + " — " + cargoSlots + "):";
        } else {
            cargoTitle = "Cargo Hold (" + cargoSlots + " Slots):";
        }
        guiGraphics.text(this.font, Component.literal(cargoTitle), 18, 57, 0xFF333333, false);
    }
}
