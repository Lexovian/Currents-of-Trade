package com.lexovian.currentsoftrade.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AnchorPointScreen extends AbstractContainerScreen<AnchorPointMenu> {

    // --- Constants & Widgets ---

    private static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/gui/anchor_point_gui.png");

    private static final String STAR_FILLED = "★";
    private static final String STAR_EMPTY  = "☆";

    private EditBox nameEditBox;
    private Button saveNameButton;
    private Button travelButton;
    private Button sendItemsButton;
    private Button requestTradeButton;
    private Button upgradeButton;
    private String lastSavedName = "";

    // --- Initialization ---

    public AnchorPointScreen(AnchorPointMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 196, menu.getHarborLevel() >= 5 ? 218 : 246);
        boolean isMax = menu.getHarborLevel() >= 5;
        this.titleLabelX = 18;
        this.titleLabelY = -1000;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = isMax ? 118 : 146;
    }

    @Override
    protected void init() {
        super.init();

        int editBoxWidth = 136;
        int editBoxHeight = 14;
        int editBoxX = this.leftPos + 18;
        int editBoxY = this.topPos + 6;

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
        int btnHeight = 18;
        int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;

        this.travelButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.travel"), b ->
                onTravelClicked()
        ).bounds(btnX, this.topPos + 23, btnWidth, btnHeight).build();

        this.sendItemsButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.send_items"), b ->
                onSendItemsClicked()
        ).bounds(btnX, this.topPos + 43, btnWidth, btnHeight).build();

        this.requestTradeButton = Button.builder(Component.translatable("gui.currents_of_trade.anchor_point.request_trade"), b ->
                onRequestTradeClicked()
        ).bounds(btnX, this.topPos + 63, btnWidth, btnHeight).build();

        int upgradeY = this.topPos + 83;
        this.upgradeButton = Button.builder(Component.translatable("gui.currents_of_trade.upgrade_harbor"), b ->
                onUpgradeClicked()
        ).bounds(btnX, upgradeY, btnWidth, btnHeight).build();

        this.addRenderableWidget(this.travelButton);
        this.addRenderableWidget(this.sendItemsButton);
        this.addRenderableWidget(this.requestTradeButton);
        this.addRenderableWidget(this.upgradeButton);
        this.lastSavedName = this.nameEditBox.getValue();

        refreshUpgradeButton();
    }

    // --- State & Layout Updates ---

    private void refreshUpgradeButton() {
        if (this.travelButton != null) {
            boolean travelEnabled = com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL == null
                    || com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL.get();
            this.travelButton.active = travelEnabled;
            this.travelButton.setTooltip(travelEnabled ? null
                    : Tooltip.create(Component.translatable("tooltip.currents_of_trade.travel_disabled_config")));
        }

        if (this.upgradeButton == null) return;
        int level = this.menu.getHarborLevel();

        if (level >= 5) {
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
                ClientPacketDistributor.sendToServer(new ModPayloads.RenameHarborPayload(this.menu.getBlockPos(), newName));
            }
        }
    }

    // --- Input Handling ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                saveHarborName();
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (this.nameEditBox.keyPressed(event)) {
                return true;
            }
            if (this.minecraft != null && this.minecraft.options.keyInventory.matches(event)) {
                return true;
            }
            if (this.nameEditBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused()) {
            if (this.nameEditBox.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
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
        this.inventoryLabelY = isMax ? 118 : 146;

        int editBoxX = this.leftPos + 18;
        int editBoxY = this.topPos + 6;
        if (this.nameEditBox != null) {
            this.nameEditBox.setPosition(editBoxX, editBoxY);
        }
        if (this.saveNameButton != null) {
            this.saveNameButton.setPosition(editBoxX + 136 + 4, editBoxY);
        }

        int btnWidth = 160;
        int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;
        if (this.travelButton != null) {
            this.travelButton.setPosition(btnX, this.topPos + 23);
        }
        if (this.sendItemsButton != null) {
            this.sendItemsButton.setPosition(btnX, this.topPos + 43);
        }
        if (this.requestTradeButton != null) {
            this.requestTradeButton.setPosition(btnX, this.topPos + 63);
        }
        if (this.upgradeButton != null) {
            this.upgradeButton.setPosition(btnX, this.topPos + 83);
        }
    }

    // --- Action Handlers ---

    private void onTravelClicked() {
        ClientPacketDistributor.sendToServer(new ModPayloads.OpenTravelPayload(this.menu.getBlockPos()));
    }

    private void onSendItemsClicked() {
        ClientPacketDistributor.sendToServer(new ModPayloads.OpenSendItemsPayload(this.menu.getBlockPos()));
    }

    private void onRequestTradeClicked() {
        ClientPacketDistributor.sendToServer(new ModPayloads.OpenRequestTradePayload(this.menu.getBlockPos()));
    }

    private void onUpgradeClicked() {
        ClientPacketDistributor.sendToServer(new ModPayloads.UpgradeHarborPayload(this.menu.getBlockPos()));
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (this.menu.getHarborLevel() >= 5) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, 110, 256, 256);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos + 110, 0, 138, this.imageWidth, 108, 256, 256);
        } else {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        super.extractLabels(guiGraphics, mouseX, mouseY);

        int level = this.menu.getHarborLevel();
        String stars = buildStars(level);
        MutableComponent levelText = Component.literal("⚓ Lv." + level + " — " + getLevelName(level) + "  " + stars);
        int textX = (this.imageWidth - this.font.width(levelText)) / 2;
        int textY = level >= 5 ? 85 : 105;
        guiGraphics.text(this.font, levelText, textX, textY, 0xFFFFD700, true);

        if (this.minecraft != null && this.minecraft.level != null) {
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(this.minecraft.level, this.menu.getBlockPos());
            Component regionText = Component.translatable("gui.currents_of_trade.harbor_region", region.getDisplayName());
            int regX = (this.imageWidth - this.font.width(regionText)) / 2;
            int regY = textY + 12;
            guiGraphics.text(this.font, regionText, regX, regY, 0xFF000000 | region.getColor(), true);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        if (this.minecraft != null && this.minecraft.level != null) {
            int level = this.menu.getHarborLevel();
            int textY = level >= 5 ? 85 : 105;
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(this.minecraft.level, this.menu.getBlockPos());
            Component regionText = Component.translatable("gui.currents_of_trade.harbor_region", region.getDisplayName());
            int regX = this.leftPos + (this.imageWidth - this.font.width(regionText)) / 2;
            int regY = this.topPos + textY + 12;

            if (mouseX >= regX && mouseX <= regX + this.font.width(regionText)
                    && mouseY >= regY && mouseY <= regY + 9) {
                List<Component> tipLines = new ArrayList<>();
                tipLines.add(region.getDisplayName());
                tipLines.add(region.getExportDescription());
                tipLines.add(region.getImportDescription());
                HarborTradeOffer bounty = region.getUrgentBountyOffer(this.minecraft.level, this.menu.getBlockPos());
                if (bounty != null && !bounty.getCostA().isEmpty()) {
                    String summary = bounty.getCostA().getCount() + "x " + bounty.getCostA().getHoverName().getString()
                            + " -> " + bounty.getResult().getCount() + "x " + bounty.getResult().getHoverName().getString();
                    tipLines.add(Component.translatable("tooltip.currents_of_trade.harbor_daily_bounty", summary));
                    boolean hasItems = this.minecraft.player != null
                            && com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity.countPlayerMatchingItems(
                                    this.minecraft.player, bounty.getCostA()) >= bounty.getCostA().getCount();
                    tipLines.add(Component.translatable(hasItems
                            ? "tooltip.currents_of_trade.harbor_bounty_deliver_ready"
                            : "tooltip.currents_of_trade.harbor_bounty_deliver_hint"));
                }
                guiGraphics.setComponentTooltipForNextFrame(this.font, tipLines, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.level != null) {
            int level = this.menu.getHarborLevel();
            int textY = level >= 5 ? 85 : 105;
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(this.minecraft.level, this.menu.getBlockPos());
            Component regionText = Component.translatable("gui.currents_of_trade.harbor_region", region.getDisplayName());
            int regX = this.leftPos + (this.imageWidth - this.font.width(regionText)) / 2;
            int regY = this.topPos + textY + 12;
            double mouseX = event.x();
            double mouseY = event.y();

            if (mouseX >= regX && mouseX <= regX + this.font.width(regionText)
                    && mouseY >= regY && mouseY <= regY + 10) {
                this.minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPacketDistributor.sendToServer(new ModPayloads.DeliverBountyPayload(this.menu.getBlockPos()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // --- Helper Methods ---

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
