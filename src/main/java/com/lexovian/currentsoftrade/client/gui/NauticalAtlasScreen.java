package com.lexovian.currentsoftrade.client.gui;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interactive Nautical Atlas & Maritime Compendium Screen (opened by Right-Clicking the Route Journal).
 *
 * Tabs:
 *  0 - Nautical Map: 2D ocean coordinate radar plotting player position, all logged harbors,
 *      active route course line, trade basin badges, and click-to-select active destination.
 *  1 - Regional Arbitrage: Visual guide to the 4 Maritime Trade Basins, local Exports, and 2x High-Demand Imports.
 *  2 - Port Upgrades: Complete Lv.1 -> Lv.5 Harbor Upgrade table (Cargo Slots, Cooldowns, Max Offers, Costs, Redstone).
 */
public class NauticalAtlasScreen extends Screen {

    private static final int GUI_WIDTH = 348;
    private static final int GUI_HEIGHT = 232;

    private final InteractionHand hand;
    private int currentTab = 0;
    private int guiLeft;
    private int guiTop;

    private Button tabMapBtn;
    private Button tabArbitrageBtn;
    private Button tabUpgradesBtn;

    public NauticalAtlasScreen(InteractionHand hand) {
        super(Component.translatable("gui.currents_of_trade.atlas.title"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new NauticalAtlasScreen(hand));
    }

    private ItemStack getJournalStack() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getItemInHand(this.hand);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        super.init();
        ItemStack journal = getJournalStack();
        if (!journal.isEmpty() && this.minecraft != null && this.minecraft.level != null) {
            RouteJournalItem.pruneBrokenHarbors(journal, this.minecraft.level);
        }
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        int tabW = 106;
        int tabY = this.guiTop + 22;
        this.tabMapBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_map"), b -> switchTab(0))
                .bounds(this.guiLeft + 10, tabY, tabW, 18).build();
        this.tabArbitrageBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_arbitrage"), b -> switchTab(1))
                .bounds(this.guiLeft + 121, tabY, tabW, 18).build();
        this.tabUpgradesBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_upgrades"), b -> switchTab(2))
                .bounds(this.guiLeft + 232, tabY, tabW, 18).build();

        this.addRenderableWidget(this.tabMapBtn);
        this.addRenderableWidget(this.tabArbitrageBtn);
        this.addRenderableWidget(this.tabUpgradesBtn);
        updateTabStates();
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        updateTabStates();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F);
        }
    }

    private void updateTabStates() {
        if (this.tabMapBtn != null) this.tabMapBtn.active = (this.currentTab != 0);
        if (this.tabArbitrageBtn != null) this.tabArbitrageBtn.active = (this.currentTab != 1);
        if (this.tabUpgradesBtn != null) this.tabUpgradesBtn.active = (this.currentTab != 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // Outer Nautical Parchment & Dark Navy Frame
        g.fill(this.guiLeft - 2, this.guiTop - 2, this.guiLeft + GUI_WIDTH + 2, this.guiTop + GUI_HEIGHT + 2, 0xFF1A2634);
        g.fill(this.guiLeft, this.guiTop, this.guiLeft + GUI_WIDTH, this.guiTop + GUI_HEIGHT, 0xFFF2E6CE);
        g.renderOutline(this.guiLeft + 3, this.guiTop + 3, GUI_WIDTH - 6, GUI_HEIGHT - 6, 0xFF8B6914);

        // Header Bar
        g.fill(this.guiLeft + 4, this.guiTop + 4, this.guiLeft + GUI_WIDTH - 4, this.guiTop + 20, 0xFF1B2D3E);
        g.drawCenteredString(this.font, this.title, this.guiLeft + GUI_WIDTH / 2, this.guiTop + 8, 0xFFFFD700);

        super.render(g, mouseX, mouseY, partialTick);

        if (this.currentTab == 0) {
            renderMapTab(g, mouseX, mouseY);
        } else if (this.currentTab == 1) {
            renderArbitrageTab(g, mouseX, mouseY);
        } else {
            renderUpgradesTab(g, mouseX, mouseY);
        }
    }

    // --- Tab 0: Interactive Nautical Map & Logged Harbors ---

    private void renderMapTab(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack journal = getJournalStack();
        List<RouteJournalItem.RouteEntry> routes = RouteJournalItem.getRoutes(journal);
        int selectedIdx = RouteJournalItem.getSelectedIndex(journal);

        // Left: 2D Ocean Radar Map Box (176 x 176)
        int mapX = this.guiLeft + 10;
        int mapY = this.guiTop + 44;
        int mapW = 176;
        int mapH = 176;

        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF10283E);
        g.renderOutline(mapX, mapY, mapW, mapH, 0xFF5C4317);

        // Subtle coordinate grid lines
        for (int gx = mapX + 28; gx < mapX + mapW; gx += 28) {
            g.fill(gx, mapY + 1, gx + 1, mapY + mapH - 1, 0x225BC0EB);
        }
        for (int gy = mapY + 28; gy < mapY + mapH; gy += 28) {
            g.fill(mapX + 1, gy, mapX + mapW - 1, gy + 1, 0x225BC0EB);
        }

        // Compass Rose N indicator
        g.drawString(this.font, "§eN▲", mapX + mapW - 18, mapY + 4, 0xFFD700, false);

        BlockPos playerPos = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.blockPosition() : BlockPos.ZERO;

        // Compute dynamic scale to fit player + all harbors inside the radar box
        double maxDist = 250.0;
        for (RouteJournalItem.RouteEntry r : routes) {
            double dx = Math.abs(r.pos().getX() - playerPos.getX());
            double dz = Math.abs(r.pos().getZ() - playerPos.getZ());
            maxDist = Math.max(maxDist, Math.max(dx, dz) * 1.25);
        }

        int centerX = mapX + mapW / 2;
        int centerY = mapY + mapH / 2;
        double scale = (mapW / 2.0 - 14.0) / maxDist;

        // Draw course line from player to active selected harbor
        if (!routes.isEmpty() && selectedIdx >= 0 && selectedIdx < routes.size()) {
            RouteJournalItem.RouteEntry active = routes.get(selectedIdx);
            int tx = Mth.clamp(centerX + (int) ((active.pos().getX() - playerPos.getX()) * scale), mapX + 8, mapX + mapW - 8);
            int ty = Mth.clamp(centerY + (int) ((active.pos().getZ() - playerPos.getZ()) * scale), mapY + 8, mapY + mapH - 8);
            drawDottedCourseLine(g, centerX, centerY, tx, ty, 0xFFFFD700);
        }

        // Plot each logged harbor pin
        int hoveredRoute = -1;
        for (int i = 0; i < routes.size(); i++) {
            RouteJournalItem.RouteEntry r = routes.get(i);
            int px = Mth.clamp(centerX + (int) ((r.pos().getX() - playerPos.getX()) * scale), mapX + 8, mapX + mapW - 8);
            int py = Mth.clamp(centerY + (int) ((r.pos().getZ() - playerPos.getZ()) * scale), mapY + 8, mapY + mapH - 8);

            MaritimeTradeRegion reg = MaritimeTradeRegion.byId(r.regionId());
            int pinColor = (0xFF << 24) | reg.getColor();
            boolean isSel = (i == selectedIdx);

            if (isSel) {
                g.fill(px - 5, py - 5, px + 5, py + 5, 0xFFFFD700);
            }
            g.fill(px - 3, py - 3, px + 3, py + 3, pinColor);

            if (mouseX >= px - 5 && mouseX <= px + 5 && mouseY >= py - 5 && mouseY <= py + 5) {
                hoveredRoute = i;
            }
        }

        // Plot Player ship marker at center
        g.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFFFFFFFF);
        g.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFF00E5FF);

        // Scale footer inside radar
        g.drawString(this.font, "§7±" + (int) maxDist + "m", mapX + 4, mapY + mapH - 11, 0xAAAAAA, false);

        // Right: Logged Harbors List (x=192..338)
        int listX = this.guiLeft + 192;
        int listY = this.guiTop + 44;
        int listW = 146;

        g.drawString(this.font, Component.translatable("gui.currents_of_trade.atlas.harbors_header").getString()
                + " (" + routes.size() + "/" + RouteJournalItem.MAX_ROUTES + ")", listX, listY, 0x1B2D3E, false);

        if (routes.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("gui.currents_of_trade.atlas.no_harbors"), listX, listY + 18, listW, 0x4E342E);
        } else {
            int rowY = listY + 13;
            for (int i = 0; i < routes.size(); i++) {
                RouteJournalItem.RouteEntry r = routes.get(i);
                boolean isSel = (i == selectedIdx);
                boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY < rowY + 14;

                int bg = isSel ? 0xFF2B4C65 : (rowHover ? 0xFF243B52 : 0xFF182838);
                g.fill(listX, rowY, listX + listW, rowY + 14, bg);
                g.renderOutline(listX, rowY, listW, 14, isSel ? 0xFFD4AF37 : 0xFF3A536B);

                MaritimeTradeRegion reg = MaritimeTradeRegion.byId(r.regionId());
                g.fill(listX + 2, rowY + 3, listX + 6, rowY + 11, (0xFF << 24) | reg.getColor());

                int dist = (int) Math.sqrt(playerPos.distSqr(r.pos()));
                String distStr = dist + "m";
                int distW = this.font.width(distStr);
                String label = this.font.plainSubstrByWidth(r.name(), listW - distW - 34) + " §7L" + r.level();
                int textCol = isSel ? 0xFFFFD700 : 0xFFE8EEF5;
                g.drawString(this.font, label, listX + 9, rowY + 3, textCol, false);

                g.drawString(this.font, distStr, listX + listW - distW - 15, rowY + 3, isSel ? 0xFF80DEEA : 0xFF90A4AE, false);

                int delX = listX + listW - 13;
                int delY = rowY + 1;
                boolean delHover = mouseX >= delX && mouseX <= delX + 11 && mouseY >= delY && mouseY <= delY + 12;
                g.fill(delX, delY, delX + 11, delY + 12, delHover ? 0xFFB71C1C : 0xFF2A1515);
                g.renderOutline(delX, delY, 11, 12, delHover ? 0xFFFF5252 : 0xFF6B2222);
                g.drawString(this.font, "✕", delX + 3, delY + 2, delHover ? 0xFFFFFFFF : 0xFFE57373, false);

                if (rowHover) {
                    hoveredRoute = i;
                }
                rowY += 16;
            }
        }

        if (hoveredRoute >= 0 && hoveredRoute < routes.size()) {
            RouteJournalItem.RouteEntry r = routes.get(hoveredRoute);
            MaritimeTradeRegion reg = MaritimeTradeRegion.byId(r.regionId());
            int dist = (int) Math.sqrt(playerPos.distSqr(r.pos()));
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("§e§l⚓ " + r.name() + " §7(Lv." + r.level() + ")"));
            tip.add(Component.literal("§7Coords: §f" + r.pos().getX() + ", " + r.pos().getZ() + " §8(" + dist + "m)"));
            tip.add(Component.translatable("tooltip.currents_of_trade.chart_region", reg.getDisplayName()));
            tip.add(reg.getExportDescription());
            tip.add(reg.getImportDescription());
            if (this.minecraft != null && this.minecraft.level != null) {
                HarborTradeOffer urgent = reg.getUrgentBountyOffer(this.minecraft.level, r.pos());
                if (urgent != null && !urgent.getCostA().isEmpty()) {
                    String summary = urgent.getCostA().getCount() + "x " + urgent.getCostA().getHoverName().getString()
                            + " -> " + urgent.getResult().getCount() + "x " + urgent.getResult().getHoverName().getString();
                    tip.add(Component.translatable("tooltip.currents_of_trade.harbor_daily_bounty", summary));
                }
            }
            if (hoveredRoute != selectedIdx) {
                tip.add(Component.translatable("gui.currents_of_trade.atlas.click_to_select"));
            }
            g.renderTooltip(this.font, tip, Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawDottedCourseLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 4;
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            g.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    // --- Tab 1: Regional Arbitrage & Maritime Silk Road Compendium ---

    private void renderArbitrageTab(GuiGraphics g, int mouseX, int mouseY) {
        int startX = this.guiLeft + 10;
        int startY = this.guiTop + 44;
        int cardW = GUI_WIDTH - 20; // 328px
        int cardH = 38;
        int iconBayW = 62;
        int textMaxW = cardW - iconBayW - 12; // 254px

        g.drawString(this.font, Component.translatable("gui.currents_of_trade.atlas.arbitrage_header"), startX, startY, 0x1B2D3E, false);

        MaritimeTradeRegion[] regions = MaritimeTradeRegion.values();
        ItemStack[][] regionIcons = {
                { new ItemStack(CurrentsofTrade.SILK_BALE.get()), new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get()), new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get()) },
                { new ItemStack(CurrentsofTrade.AMBER_VIAL.get()), new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get()), new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get()) },
                { new ItemStack(CurrentsofTrade.SANDALWOOD.get()), new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()), new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get()) },
                { new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get()), new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get()), new ItemStack(CurrentsofTrade.TEA_BRICK.get()) }
        };

        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < regions.length; i++) {
            MaritimeTradeRegion reg = regions[i];
            int boxY = startY + 12 + i * (cardH + 3);
            int accentCol = (0xFF << 24) | reg.getColor();

            // Dark Navy Card so §7 / §e / §b / §a / §6 colored text has crisp contrast
            g.fill(startX, boxY, startX + cardW, boxY + cardH, 0xFF182838);
            // Dedicated right-hand icon bay with vertical divider
            int bayX = startX + cardW - iconBayW;
            g.fill(bayX, boxY + 1, startX + cardW - 1, boxY + cardH - 1, 0xFF101C28);
            g.fill(bayX, boxY + 1, bayX + 1, boxY + cardH - 1, 0x885C7A99);

            g.renderOutline(startX, boxY, cardW, cardH, accentCol);
            g.fill(startX, boxY, startX + 4, boxY + cardH, accentCol);

            g.drawString(this.font, "⚓ " + reg.getDisplayName().getString(), startX + 8, boxY + 3, reg.getColor(), true);
            drawClampedText(g, reg.getExportDescription().getString(), startX + 8, boxY + 14, textMaxW, 0xFFE0E0E0);
            drawClampedText(g, reg.getImportDescription().getString(), startX + 8, boxY + 25, textMaxW, 0xFFE0E0E0);

            // Render 3 signature commodity icons inside the right-hand bay
            for (int k = 0; k < regionIcons[i].length; k++) {
                int ix = bayX + 4 + k * 19;
                int iy = boxY + 11;
                ItemStack icon = regionIcons[i][k];
                g.renderItem(icon, ix, iy);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    hoveredStack = icon;
                }
            }
        }

        if (!hoveredStack.isEmpty()) {
            g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private void drawClampedText(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        int w = this.font.width(text);
        if (w <= maxWidth) {
            g.drawString(this.font, text, x, y, color, true);
        } else {
            float scale = (float) maxWidth / (float) w;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(this.font, text, 0, 0, color, true);
            g.pose().popPose();
        }
    }

    // --- Tab 2: Port Upgrades (Lv.1 -> Lv.5) & Redstone Automation Guide ---

    private void renderUpgradesTab(GuiGraphics g, int mouseX, int mouseY) {
        int startX = this.guiLeft + 10;
        int startY = this.guiTop + 44;
        int rowW = GUI_WIDTH - 20; // 328px

        g.drawString(this.font, Component.translatable("gui.currents_of_trade.atlas.upgrades_header"), startX, startY, 0x1B2D3E, false);

        String[] names = {
                "Lv.1 Fishing Wharf",
                "Lv.2 Trading Post",
                "Lv.3 Harbor",
                "Lv.4 Grand Port",
                "Lv.5 Royal Dockyard"
        };
        String[] stats = {
                "§a18 Slots §7| §b60s CD §7| §d6 Offers",
                "§a27 Slots §7| §b45s CD §7| §d8 Offers",
                "§a36 Slots §7| §b30s CD §7| §d10 Offers",
                "§a45 Slots §7| §b20s CD §7| §d12 Offers",
                "§a54 Slots §7| §b10s CD §7| §d14 Offers"
        };
        String[] costs = {
                "§7Starter Port",
                "§e8 Dbl + 32 Planks + 16 Iron",
                "§e20 Dbl + 16 Copper + 4 Lanterns",
                "§e40 Dbl + 8 Gold + 2 Silk + 2 Spice",
                "§664 Dbl + 4 Emerald Blk + Astrolabe"
        };
        ItemStack[] icons = {
                new ItemStack(Items.OAK_BOAT),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.LANTERN),
                new ItemStack(CurrentsofTrade.SILK_BALE.get()),
                new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get())
        };

        int y = startY + 12;
        for (int i = 0; i < 5; i++) {
            boolean isMax = (i == 4);
            int bg = isMax ? 0xFF263B52 : 0xFF182838;
            int border = isMax ? 0xFFD4AF37 : 0xFF3A536B;
            int titleCol = isMax ? 0xFFFFD700 : 0xFFFFFFFF;

            g.fill(startX, y, startX + rowW, y + 21, bg);
            g.renderOutline(startX, y, rowW, 21, border);

            g.renderItem(icons[i], startX + 3, y + 2);
            g.drawString(this.font, "§l" + names[i], startX + 22, y + 2, titleCol, true);
            g.drawString(this.font, stats[i], startX + 22, y + 11, 0xFFCFD8DC, true);

            int costW = this.font.width(costs[i]);
            g.drawString(this.font, costs[i], startX + rowW - costW - 5, y + 6, 0xFFFFE082, true);
            y += 23;
        }

        // Redstone & Depot Automation Footer Box (48px tall so 3-4 lines fit comfortably inside)
        int tipY = y + 1;
        int tipH = 48;
        g.fill(startX, tipY, startX + rowW, tipY + tipH, 0xFF142830);
        g.renderOutline(startX, tipY, rowW, tipH, 0xFF2E7D6B);
        g.renderItem(new ItemStack(Items.REDSTONE), startX + 4, tipY + 15);
        g.drawWordWrap(this.font, Component.translatable("gui.currents_of_trade.atlas.redstone_guide"),
                startX + 24, tipY + 5, rowW - 30, 0xFFE0E0E0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.currentTab == 0 && button == 0) {
            ItemStack journal = getJournalStack();
            List<RouteJournalItem.RouteEntry> routes = RouteJournalItem.getRoutes(journal);
            int listX = this.guiLeft + 192;
            int rowY = this.guiTop + 57;
            int listW = 146;

            for (int i = 0; i < routes.size(); i++) {
                int delX = listX + listW - 13;
                int delY = rowY + 1;
                if (mouseX >= delX && mouseX <= delX + 11 && mouseY >= delY && mouseY <= delY + 12) {
                    RouteJournalItem.removeRouteByIndex(journal, i);
                    ModPayloads.sendToServer(new ModPayloads.SelectJournalRoutePayload(this.hand == InteractionHand.OFF_HAND ? 1 : 0, -i - 1));
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.8F, 1.2F);
                    }
                    return true;
                }
                if (mouseX >= listX && mouseX <= listX + listW - 14 && mouseY >= rowY && mouseY < rowY + 14) {
                    selectRoute(journal, i);
                    return true;
                }
                rowY += 16;
            }

            // Also check clicks on radar map pins
            BlockPos playerPos = (this.minecraft != null && this.minecraft.player != null)
                    ? this.minecraft.player.blockPosition() : BlockPos.ZERO;
            int mapX = this.guiLeft + 10;
            int mapY = this.guiTop + 44;
            int mapW = 176;
            int mapH = 176;
            double maxDist = 250.0;
            for (RouteJournalItem.RouteEntry r : routes) {
                maxDist = Math.max(maxDist, Math.max(Math.abs(r.pos().getX() - playerPos.getX()), Math.abs(r.pos().getZ() - playerPos.getZ())) * 1.25);
            }
            int centerX = mapX + mapW / 2;
            int centerY = mapY + mapH / 2;
            double scale = (mapW / 2.0 - 14.0) / maxDist;
            for (int i = 0; i < routes.size(); i++) {
                RouteJournalItem.RouteEntry r = routes.get(i);
                int px = Mth.clamp(centerX + (int) ((r.pos().getX() - playerPos.getX()) * scale), mapX + 8, mapX + mapW - 8);
                int py = Mth.clamp(centerY + (int) ((r.pos().getZ() - playerPos.getZ()) * scale), mapY + 8, mapY + mapH - 8);
                if (mouseX >= px - 5 && mouseX <= px + 5 && mouseY >= py - 5 && mouseY <= py + 5) {
                    selectRoute(journal, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectRoute(ItemStack journal, int index) {
        RouteJournalItem.setSelectedIndex(journal, index);
        ModPayloads.sendToServer(new ModPayloads.SelectJournalRoutePayload(this.hand == InteractionHand.OFF_HAND ? 1 : 0, index));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.2F);
        }
    }
}
