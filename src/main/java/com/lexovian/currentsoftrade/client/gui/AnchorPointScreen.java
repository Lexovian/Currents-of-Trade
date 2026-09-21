package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class AnchorPointScreen extends AbstractContainerScreen<AnchorPointMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/anchor_point_gui.png");

    // Star characters for level display
    private static final String STAR_FILLED = "★";
    private static final String STAR_EMPTY  = "☆";

    private EditBox nameEditBox;
    private Button saveNameButton;
    private Button travelButton;
    private Button sendItemsButton;
    private Button requestTradeButton;
    private Button upgradeButton;

    public AnchorPointScreen(AnchorPointMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        boolean isMax = menu.getHarborLevel() >= 5;
        this.imageWidth = 196;
        this.imageHeight = isMax ? 218 : 246;
        this.titleLabelX = 18;
        this.titleLabelY = -1000; // Hide default title so nameEditBox takes the header cleanly
        this.inventoryLabelX = 18;
        this.inventoryLabelY = isMax ? 118 : 146;
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
        this.saveNameButton = Button.builder(Component.literal("✔"), b -> saveHarborName())
                .bounds(saveBtnX, editBoxY, 20, editBoxHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.currents_of_trade.anchor_point.rename_tooltip")))
                .build();
        this.addRenderableWidget(this.saveNameButton);

        int btnWidth = 160;
        int btnHeight = 20;
        int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;

        this.travelButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.travel"), b ->
                onTravelClicked()
        ).bounds(btnX, this.topPos + 25, btnWidth, btnHeight).build();

        this.sendItemsButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.send_items"), b ->
                onSendItemsClicked()
        ).bounds(btnX, this.topPos + 48, btnWidth, btnHeight).build();

        this.requestTradeButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.request_trade"), b ->
                onRequestTradeClicked()
        ).bounds(btnX, this.topPos + 71, btnWidth, btnHeight).build();

        // Upgrade Harbor button — placed below the 3 action buttons
        int upgradeY = this.topPos + 96;
        this.upgradeButton = Button.builder(Component.translatable("gui.currents_of_trade.upgrade_harbor"), b ->
                onUpgradeClicked()
        ).bounds(btnX, upgradeY, btnWidth, 22).build();

        this.addRenderableWidget(this.travelButton);
        this.addRenderableWidget(this.sendItemsButton);
        this.addRenderableWidget(this.requestTradeButton);
        this.addRenderableWidget(this.upgradeButton);
        this.lastSavedName = this.nameEditBox.getValue();

        refreshUpgradeButton();
    }

    private String lastSavedName = "";

    /**
     * Updates the Upgrade button state and tooltip based on current harbor level and player inventory.
     */
    private void refreshUpgradeButton() {
        if (this.upgradeButton == null) return;
        int level = this.menu.getHarborLevel();

        if (level >= 5) {
            // Max level — hide the button entirely
            this.upgradeButton.visible = false;
            this.upgradeButton.active = false;
            return;
        }

        this.upgradeButton.visible = true;
        boolean canAfford = this.menu.canPlayerUpgrade();
        this.upgradeButton.active = canAfford;

        HarborUpgradeCost cost = this.menu.getUpgradeCost();
        if (cost != null) {
            List<String> lines = cost.getCostLines();
            StringBuilder sb = new StringBuilder();
            sb.append("§eUpgrade Cost:\n");
            for (String line : lines) sb.append(line).append("\n");
            if (!canAfford) sb.append("§cYou cannot afford this upgrade.");
            this.upgradeButton.setTooltip(Tooltip.create(Component.literal(sb.toString().trim())));
        }
    }

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

    @Override
    public void containerTick() {
        super.containerTick();
        updateLayoutForLevel();
        refreshUpgradeButton();
    }

    private void updateLayoutForLevel() {
        int level = this.menu.getHarborLevel();
        boolean isMax = level >= 5;
        int targetHeight = isMax ? 218 : 246;

        if (this.imageHeight != targetHeight) {
            this.imageHeight = targetHeight;
            this.inventoryLabelY = isMax ? 118 : 146;
            this.topPos = (this.height - this.imageHeight) / 2;

            int editBoxX = this.leftPos + 18;
            int editBoxY = this.topPos + 7;
            if (this.nameEditBox != null) {
                this.nameEditBox.setPosition(editBoxX, editBoxY);
            }
            if (this.saveNameButton != null) {
                this.saveNameButton.setPosition(editBoxX + 136 + 4, editBoxY);
            }

            int btnWidth = 160;
            int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;
            if (this.travelButton != null) {
                this.travelButton.setPosition(btnX, this.topPos + 25);
            }
            if (this.sendItemsButton != null) {
                this.sendItemsButton.setPosition(btnX, this.topPos + 48);
            }
            if (this.requestTradeButton != null) {
                this.requestTradeButton.setPosition(btnX, this.topPos + 71);
            }
            if (this.upgradeButton != null) {
                this.upgradeButton.setPosition(btnX, this.topPos + 96);
            }
        }
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

    private void onUpgradeClicked() {
        PacketDistributor.sendToServer(new ModPayloads.UpgradeHarborPayload(this.menu.getBlockPos()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (this.menu.getHarborLevel() >= 5) {
            // Compact 218px layout: bypass the middle 28px space, cleanly separating level text from line
            guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, 110);
            guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos + 110, 0, 138, this.imageWidth, 108);
        } else {
            guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw harbor level indicator below upgrade button (or under request trade button at max level)
        int level = this.menu.getHarborLevel();
        String stars = buildStars(level);
        MutableComponent levelText = Component.literal("⚓ Lv." + level + " — " + getLevelName(level) + "  " + stars);
        int textX = this.leftPos + (this.imageWidth - this.font.width(levelText)) / 2;
        int textY = this.topPos + (level >= 5 ? 97 : 124);
        guiGraphics.drawString(this.font, levelText, textX, textY, 0xFFD700, false);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ---- helpers ----

    private String buildStars(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= level ? STAR_FILLED : STAR_EMPTY);
        }
        return sb.toString();
    }

    private static String getLevelName(int level) {
        return switch (level) {
            case 2 -> "Trading Post";
            case 3 -> "Harbor";
            case 4 -> "Grand Port";
            case 5 -> "Royal Dockyard";
            default -> "Fishing Wharf";
        };
    }
}
