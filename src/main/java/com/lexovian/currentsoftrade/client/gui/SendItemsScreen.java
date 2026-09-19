package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SendItemsScreen extends AbstractContainerScreen<SendItemsMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/send_items_gui.png");

    private Button sendCargoButton;
    private Button backButton;

    public SendItemsScreen(SendItemsMenu menu, Inventory playerInventory, Component title) {
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

        // Back button to return to Anchor Point main menu
        this.backButton = Button.builder(Component.translatable("gui.currents_of_trade.back"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.OpenAnchorPointPayload(this.menu.getCurrentPos()));
        }).bounds(this.leftPos + 7, this.topPos + 6, 36, 15).build();

        int sendBtnWidth = 120;
        int sendBtnHeight = 18;
        int sendBtnX = this.leftPos + (this.imageWidth - sendBtnWidth) / 2;

        this.sendCargoButton = Button.builder(Component.translatable("gui.currents_of_trade.send_cargo_btn"), b -> {
            PacketDistributor.sendToServer(new ModPayloads.ExecuteSendCargoPayload(this.menu.getCurrentPos()));
        }).bounds(sendBtnX, this.topPos + 109, sendBtnWidth, sendBtnHeight).build();

        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.sendCargoButton);
    }

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
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Base container background from enlarged send_items_gui
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        // Chart slot box (x=23, y=35)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 23, this.topPos + 35, 17, 146, 18, 18);
        // Fee slot box (x=63, y=35)
        guiGraphics.blit(GUI_TEXTURE, this.leftPos + 63, this.topPos + 35, 17, 146, 18, 18);

        // 18 Cargo slot boxes (2 rows x 9 columns) at x = 17 + col * 18, y = 69 + row * 18
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                guiGraphics.blit(GUI_TEXTURE, this.leftPos + 17 + col * 18, this.topPos + 69 + row * 18, 17, 146, 18, 18);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Window title
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        // Slot headers directly above Chart and Fee slots (plenty of room between them)
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_chart"), 20, 24, 0x555555, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.slot_fee"), 64, 24, 0x555555, false);

        ItemStack chart = this.menu.getChartStack();
        boolean hasBoundChart = !chart.isEmpty() && chart.getItem() instanceof NauticalChartItem && NauticalChartItem.isBound(chart);

        // Destination details placed cleanly to the right of the slots (x=88..188)
        if (hasBoundChart) {
            String destHarbor = NauticalChartItem.getTargetHarbor(chart);
            int dist = this.menu.getDistance();
            int fee = this.menu.getRequiredFee();

            Component destText = Component.literal("§2§l" + this.font.plainSubstrByWidth(destHarbor, 95));
            guiGraphics.drawString(this.font, destText, 88, 24, 0x1B4D3E, false);

            Component distText = Component.literal("§8Dist: §0" + dist + "m");
            guiGraphics.drawString(this.font, distText, 88, 35, 0x404040, false);

            if (this.menu.isTooExpensive()) {
                guiGraphics.drawString(this.font, Component.literal("§cToo Far (> 64)"), 88, 46, 0xAA0000, false);
            } else {
                Component feeText = Component.literal("§8Fee: §6" + fee + " Dbl");
                guiGraphics.drawString(this.font, feeText, 88, 46, 0x8B5A00, false);
            }
        } else {
            guiGraphics.drawString(this.font, Component.literal("§7No Chart"), 88, 30, 0x777777, false);
            guiGraphics.drawString(this.font, Component.literal("§8Insert chart"), 88, 42, 0x999999, false);
        }

        // Cargo section title (cleanly above cargo slots at y=58)
        guiGraphics.drawString(this.font, Component.translatable("gui.currents_of_trade.cargo_hold_title"), 18, 58, 0x333333, false);

        // Player Inventory label (cleanly above inventory slots at y=136)
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
