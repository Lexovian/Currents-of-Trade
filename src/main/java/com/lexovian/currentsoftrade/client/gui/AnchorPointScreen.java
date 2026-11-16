package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.item.TradeCharterItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.harbor.HarborUpgradeCost;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnchorPointScreen extends AbstractContainerScreen<AnchorPointMenu> {

    // --- Constants & Widgets ---

    private static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation(CurrentsofTrade.MODID, "textures/gui/anchor_point_gui.png");

    private static final String STAR_FILLED = "★";
    private static final String STAR_EMPTY  = "☆";

    private static final net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner CLAMPED_POSITIONER =
            (screenW, screenH, mx, my, tw, th) -> new org.joml.Vector2i(
                    (mx + 12 + tw + 4 > screenW) ? Math.max(4, mx - 12 - tw) : mx + 12,
                    Math.max(4, Math.min(my - 12, screenH - th - 4))
            );

    private EditBox nameEditBox;
    private Button saveNameButton;
    private Button travelButton;
    private Button sendItemsButton;
    private Button requestTradeButton;
    private Button upgradeButton;
    private Button specializationButton;
    private String lastSavedName = "";

    // --- Initialization ---

    public AnchorPointScreen(AnchorPointMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 246;
        this.titleLabelX = 18;
        this.titleLabelY = -1000;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 146;
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

        int row4Y = this.topPos + 83;
        this.upgradeButton = Button.builder(Component.translatable("gui.currents_of_trade.upgrade_harbor"), b ->
                onUpgradeClicked()
        ).bounds(btnX, row4Y, 102, btnHeight).build();

        this.specializationButton = Button.builder(Component.translatable("gui.currents_of_trade.spec_locked_btn"), b ->
                onSpecializationClicked()
        ).bounds(btnX + 106, row4Y, 54, btnHeight).build();

        this.addRenderableWidget(this.travelButton);
        this.addRenderableWidget(this.sendItemsButton);
        this.addRenderableWidget(this.requestTradeButton);
        this.addRenderableWidget(this.upgradeButton);
        this.addRenderableWidget(this.specializationButton);
        this.lastSavedName = this.nameEditBox.getValue();

        refreshActionButtons();
    }

    // --- State & Layout Updates ---

    private void refreshActionButtons() {
        if (this.travelButton != null) {
            boolean travelEnabled = com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL == null
                    || com.lexovian.currentsoftrade.Config.ENABLE_ANCHOR_TRAVEL.get();
            this.travelButton.active = travelEnabled;
            this.travelButton.setTooltip(travelEnabled ? null
                    : Tooltip.create(Component.translatable("tooltip.currents_of_trade.travel_disabled_config")));
        }

        int level = this.menu.getHarborLevel();
        int btnX = this.leftPos + (this.imageWidth - 160) / 2;
        int row4Y = this.topPos + 83;

        if (this.upgradeButton != null) {
            if (level >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) {
                this.upgradeButton.visible = false;
                this.upgradeButton.active = false;
            } else {
                this.upgradeButton.visible = true;
                int upgW = (level >= HarborSpecialization.UNLOCK_LEVEL) ? 70 : 102;
                this.upgradeButton.setPosition(btnX, row4Y);
                this.upgradeButton.setWidth(upgW);
                Component upgLabel = (level >= HarborSpecialization.UNLOCK_LEVEL)
                        ? Component.translatable("gui.currents_of_trade.upgrade_short")
                        : Component.translatable("gui.currents_of_trade.upgrade_harbor");
                this.upgradeButton.setMessage(fitLabel(upgLabel, upgW - 8));

                boolean canAfford = this.menu.canPlayerUpgrade();
                this.upgradeButton.active = canAfford;
                this.upgradeButton.setTooltip(null);
            }
        }

        if (this.specializationButton != null) {
            HarborSpecialization spec = this.menu.getSpecialization();
            this.specializationButton.setTooltip(null);

            if (level < HarborSpecialization.UNLOCK_LEVEL) {
                this.specializationButton.setPosition(btnX + 106, row4Y);
                this.specializationButton.setWidth(54);
                this.specializationButton.active = false;
                this.specializationButton.setMessage(Component.translatable("gui.currents_of_trade.spec_locked_btn"));
            } else {
                int specX = (level >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) ? btnX : (btnX + 74);
                int specW = (level >= AnchorPointBlockEntity.MAX_HARBOR_LEVEL) ? 160 : 86;
                this.specializationButton.setPosition(specX, row4Y);
                this.specializationButton.setWidth(specW);

                Component rawLabel = (spec == HarborSpecialization.NONE)
                        ? Component.translatable("gui.currents_of_trade.specialize_btn")
                        : spec.getShortBadge();
                this.specializationButton.setMessage(fitLabel(rawLabel, specW - 8));
                this.specializationButton.active = this.menu.canAffordSpecializationSwitch();
            }
        }
    }

    private static boolean isHoveringButton(Button btn, int mouseX, int mouseY) {
        return btn != null && btn.visible
                && mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight();
    }

    private Component fitLabel(Component label, int maxWidth) {
        String s = label.getString();
        if (this.font.width(s) <= maxWidth) {
            return label;
        }
        return Component.literal(this.font.plainSubstrByWidth(s, Math.max(12, maxWidth - 6)) + "..");
    }

    private List<net.minecraft.util.FormattedCharSequence> buildUpgradeTooltipLines() {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        HarborUpgradeCost cost = this.menu.getUpgradeCost();
        if (cost == null) return out;
        int wrapW = Math.min(236, Math.max(150, this.width - 32));
        out.addAll(this.font.split(Component.literal("§e").append(Component.translatable("gui.currents_of_trade.upgrade_cost")), wrapW));
        for (String line : cost.getCostLines()) {
            out.addAll(this.font.split(Component.literal(line), wrapW));
        }
        if (!this.menu.canPlayerUpgrade()) {
            out.addAll(this.font.split(Component.translatable("message.currents_of_trade.harbor_cannot_afford"), wrapW));
        }
        return out;
    }

    private List<net.minecraft.util.FormattedCharSequence> buildSpecializationTooltipLines(int level, HarborSpecialization current, boolean chosen) {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        int wrapW = Math.min(236, Math.max(150, this.width - 32));
        out.addAll(this.font.split(Component.translatable("tooltip.currents_of_trade.spec_header"), wrapW));

        if (level < HarborSpecialization.UNLOCK_LEVEL) {
            out.addAll(this.font.split(Component.translatable("tooltip.currents_of_trade.spec_unlock_hint", HarborSpecialization.UNLOCK_LEVEL), wrapW));
        } else if (current == HarborSpecialization.NONE) {
            out.addAll(this.font.split(Component.translatable("tooltip.currents_of_trade.spec_choose_prompt"), wrapW));
        }

        for (HarborSpecialization opt : new HarborSpecialization[]{
                HarborSpecialization.FREE_PORT,
                HarborSpecialization.ROYAL_DRYDOCK,
                HarborSpecialization.GUILD_DEPOT
        }) {
            String marker = (opt == current) ? "§a▶ " : "§7• ";
            out.addAll(this.font.split(Component.literal(marker).append(opt.getShortBadge()), wrapW));
            out.addAll(this.font.split(Component.literal("  ").append(opt.getDescription()), wrapW));
        }

        if (level >= HarborSpecialization.UNLOCK_LEVEL) {
            out.addAll(this.font.split(!chosen
                    ? Component.translatable("tooltip.currents_of_trade.spec_cost_free")
                    : Component.translatable("tooltip.currents_of_trade.spec_cost_switch", HarborSpecialization.SWITCH_COST_DOUBLOONS), wrapW));
        }
        return out;
    }

    private void saveHarborName() {
        if (this.nameEditBox != null) {
            String newName = this.nameEditBox.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(this.lastSavedName)) {
                this.lastSavedName = newName;
                ModPayloads.sendToServer(new ModPayloads.RenameHarborPayload(this.menu.getBlockPos(), newName));
            }
        }
    }

    // --- Input Handling ---

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                saveHarborName();
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (keyCode == 256) {
                this.nameEditBox.setFocused(false);
                return true;
            }
            if (this.nameEditBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
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
        refreshActionButtons();
    }

    // --- Action Handlers ---

    private void onTravelClicked() {
        ModPayloads.sendToServer(new ModPayloads.OpenTravelPayload(this.menu.getBlockPos()));
    }

    private void onSendItemsClicked() {
        ModPayloads.sendToServer(new ModPayloads.OpenSendItemsPayload(this.menu.getBlockPos()));
    }

    private void onRequestTradeClicked() {
        ModPayloads.sendToServer(new ModPayloads.OpenRequestTradePayload(this.menu.getBlockPos()));
    }

    private void onUpgradeClicked() {
        ModPayloads.sendToServer(new ModPayloads.UpgradeHarborPayload(this.menu.getBlockPos()));
    }

    private void onSpecializationClicked() {
        ModPayloads.sendToServer(new ModPayloads.CycleSpecializationPayload(this.menu.getBlockPos()));
    }

    // --- Rendering ---

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int level = this.menu.getHarborLevel();
        HarborSpecialization spec = this.menu.getSpecialization();
        String stars = buildStars(level);
        MutableComponent levelText = Component.literal("⚓ Lv." + level + " — " + getLevelName(level) + "  " + stars);
        int textX = this.leftPos + (this.imageWidth - this.font.width(levelText)) / 2;
        int textY = this.topPos + 105;
        guiGraphics.drawString(this.font, levelText, textX, textY, 0xFFD700, true);

        if (this.minecraft != null && this.minecraft.level != null) {
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(this.minecraft.level, this.menu.getBlockPos());
            MutableComponent regionText = Component.translatable("gui.currents_of_trade.harbor_region", region.getDisplayName());
            if (spec != HarborSpecialization.NONE) {
                regionText = regionText.append(Component.literal("  " + spec.getIcon()));
            }
            int regX = this.leftPos + (this.imageWidth - this.font.width(regionText)) / 2;
            int regY = textY + 12;
            guiGraphics.drawString(this.font, regionText, regX, regY, region.getColor(), true);

            if (mouseX >= regX && mouseX <= regX + this.font.width(regionText)
                    && mouseY >= regY && mouseY <= regY + 9) {
                List<Component> tipLines = new ArrayList<>();
                tipLines.add(region.getDisplayName());
                if (spec != HarborSpecialization.NONE) {
                    tipLines.add(Component.translatable("tooltip.currents_of_trade.spec_active", spec.getShortBadge()));
                }
                tipLines.add(region.getExportDescription());
                tipLines.add(region.getImportDescription());

                HarborTradeOffer bounty = region.getUrgentBountyOffer(this.minecraft.level, this.menu.getBlockPos());
                boolean anyReady = false;
                if (bounty != null && !bounty.getCostA().isEmpty()) {
                    int rewardCount = bounty.getResult().getCount();
                    if (spec == HarborSpecialization.FREE_PORT && bounty.getResult().is(CurrentsofTrade.DOUBLOON.get())) {
                        rewardCount = Math.max(rewardCount + 1, Math.round(rewardCount * 1.25F));
                    }
                    String summary = bounty.getCostA().getCount() + "x " + bounty.getCostA().getHoverName().getString()
                            + " -> " + rewardCount + "x " + bounty.getResult().getHoverName().getString();
                    tipLines.add(Component.translatable("tooltip.currents_of_trade.harbor_daily_bounty", summary));
                    if (this.minecraft.player != null
                            && AnchorPointBlockEntity.countPlayerMatchingItems(this.minecraft.player, bounty.getCostA()) >= bounty.getCostA().getCount()) {
                        anyReady = true;
                    }
                }

                if (spec == HarborSpecialization.GUILD_DEPOT) {
                    HarborTradeOffer secondBounty = region.getSecondaryUrgentBountyOffer(this.minecraft.level, this.menu.getBlockPos());
                    if (secondBounty != null && !secondBounty.getCostA().isEmpty()) {
                        String summary2 = secondBounty.getCostA().getCount() + "x " + secondBounty.getCostA().getHoverName().getString()
                                + " -> " + secondBounty.getResult().getCount() + "x " + secondBounty.getResult().getHoverName().getString();
                        tipLines.add(Component.translatable("tooltip.currents_of_trade.harbor_daily_bounty_secondary", summary2));
                        if (this.minecraft.player != null
                                && AnchorPointBlockEntity.countPlayerMatchingItems(this.minecraft.player, secondBounty.getCostA()) >= secondBounty.getCostA().getCount()) {
                            anyReady = true;
                        }
                    }
                }

                if (this.minecraft.player != null) {
                    for (int i = 0; i < this.minecraft.player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = this.minecraft.player.getInventory().getItem(i);
                        if (invStack.getItem() instanceof TradeCharterItem && TradeCharterItem.isInitialized(invStack)) {
                            TradeCharterItem.CharterSpec cSpec = TradeCharterItem.getSpec(invStack);
                            if (cSpec != null && cSpec.targetRegion() == region) {
                                ItemStack req = new ItemStack(cSpec.requiredItem());
                                tipLines.add(Component.translatable("tooltip.currents_of_trade.harbor_charter_match",
                                        cSpec.requiredCount(), req.getHoverName()));
                                if (AnchorPointBlockEntity.countPlayerMatchingItems(this.minecraft.player, req) >= cSpec.requiredCount()) {
                                    anyReady = true;
                                }
                                break;
                            }
                        }
                    }
                }

                tipLines.add(Component.translatable(anyReady
                        ? "tooltip.currents_of_trade.harbor_bounty_deliver_ready"
                        : "tooltip.currents_of_trade.harbor_bounty_deliver_hint"));
                guiGraphics.renderTooltip(this.font, tipLines, Optional.empty(), mouseX, mouseY);
            }
        }

        if (isHoveringButton(this.upgradeButton, mouseX, mouseY)) {
            List<net.minecraft.util.FormattedCharSequence> upgTip = buildUpgradeTooltipLines();
            if (!upgTip.isEmpty()) {
                guiGraphics.renderTooltip(this.font, upgTip, CLAMPED_POSITIONER, mouseX, mouseY);
            }
        } else if (isHoveringButton(this.specializationButton, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    buildSpecializationTooltipLines(level, spec, this.menu.isSpecializationChosen()),
                    CLAMPED_POSITIONER, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.nameEditBox != null && this.nameEditBox.isFocused() && !this.nameEditBox.isMouseOver(mouseX, mouseY)) {
            saveHarborName();
            this.nameEditBox.setFocused(false);
            this.setFocused(null);
        }
        if (button == 0 && this.minecraft != null && this.minecraft.level != null) {
            int textY = this.topPos + 105;
            HarborSpecialization spec = this.menu.getSpecialization();
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(this.minecraft.level, this.menu.getBlockPos());
            MutableComponent regionText = Component.translatable("gui.currents_of_trade.harbor_region", region.getDisplayName());
            if (spec != HarborSpecialization.NONE) {
                regionText = regionText.append(Component.literal("  " + spec.getIcon()));
            }
            int regX = this.leftPos + (this.imageWidth - this.font.width(regionText)) / 2;
            int regY = textY + 12;

            if (mouseX >= regX && mouseX <= regX + this.font.width(regionText)
                    && mouseY >= regY && mouseY <= regY + 10) {
                this.minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ModPayloads.sendToServer(new ModPayloads.DeliverBountyPayload(this.menu.getBlockPos()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        int clamped = Math.max(1, Math.min(5, level));
        return Component.translatable("harbor.currents_of_trade.level." + clamped).getString();
    }
}
