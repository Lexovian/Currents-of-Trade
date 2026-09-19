package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class AnchorPointScreen extends AbstractContainerScreen<AnchorPointMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/anchor_point_gui.png");

    private EditBox nameEditBox;
    private Button saveNameButton;
    private Button travelButton;
    private Button sendItemsButton;
    private Button requestTradeButton;

    public AnchorPointScreen(AnchorPointMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 212;
        this.titleLabelX = 18;
        this.titleLabelY = -1000; // Hide default title so nameEditBox takes the header cleanly
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 112;
    }

    @Override
    protected void init() {
        super.init();

        // Harbor name input box and save button placed neatly in header (y=7)
        int editBoxWidth = 136;
        int editBoxHeight = 15;
        int editBoxX = this.leftPos + 18;
        int editBoxY = this.topPos + 7;

        this.nameEditBox = new EditBox(this.font, editBoxX, editBoxY, editBoxWidth, editBoxHeight,
                Component.translatable("gui.currents_of_trade.anchor_point.harbor_name_hint"));
        this.nameEditBox.setMaxLength(32);
        this.nameEditBox.setValue(this.menu.getHarborName());
        this.nameEditBox.setHint(Component.translatable("gui.currents_of_trade.anchor_point.harbor_name_hint"));
        this.addRenderableWidget(this.nameEditBox);

        int saveBtnX = editBoxX + editBoxWidth + 4;
        this.saveNameButton = Button.builder(Component.literal("\u2714"), b -> saveHarborName())
                .bounds(saveBtnX, editBoxY, 20, editBoxHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.currents_of_trade.anchor_point.rename_tooltip")))
                .build();
        this.addRenderableWidget(this.saveNameButton);

        int btnWidth = 156;
        int btnHeight = 22;
        int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;

        this.travelButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.travel"), b -> {
            onTravelClicked();
        }).bounds(btnX, this.topPos + 26, btnWidth, btnHeight).build();

        this.sendItemsButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.send_items"), b -> {
            onSendItemsClicked();
        }).bounds(btnX, this.topPos + 52, btnWidth, btnHeight).build();

        this.requestTradeButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.request_trade"), b -> {
            onRequestTradeClicked();
        }).bounds(btnX, this.topPos + 78, btnWidth, btnHeight).build();

        this.addRenderableWidget(this.travelButton);
        this.addRenderableWidget(this.sendItemsButton);
        this.addRenderableWidget(this.requestTradeButton);
        this.lastSavedName = this.nameEditBox.getValue();
    }

    private String lastSavedName = "";

    private void saveHarborName() {
        if (this.nameEditBox != null) {
            String newName = this.nameEditBox.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(this.lastSavedName)) {
                this.lastSavedName = newName;
                PacketDistributor.sendToServer(new ModPayloads.RenameHarborPayload(this.menu.getBlockPos(), newName));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { // Enter or Keypad Enter
                saveHarborName();
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (keyCode == 256) { // Escape
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (this.nameEditBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // Prevent the inventory key ('E') or other hotkeys from closing the container screen while typing
            if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
            if (this.nameEditBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused()) {
            if (this.nameEditBox.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        saveHarborName();
    }

    private void onTravelClicked() {
        PacketDistributor.sendToServer(new ModPayloads.OpenTravelPayload(this.menu.getBlockPos()));
    }

    private void onSendItemsClicked() {
        PacketDistributor.sendToServer(new ModPayloads.OpenSendItemsPayload(this.menu.getBlockPos()));
    }

    private void onRequestTradeClicked() {
        PacketDistributor.sendToServer(new ModPayloads.OpenRequestTradePayload(this.menu.getBlockPos()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
