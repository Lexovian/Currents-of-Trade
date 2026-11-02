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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interactive Nautical Atlas & Maritime Compendium Screen opened by the Route Journal.
 *
 * <p>Provides 3 tabs:
 * <ul>
 *   <li><b>Tab 0 — Nautical Map:</b> 2D ocean coordinate radar plotting all recorded harbors,
 *       their biome trade regions, levels (Lv.1–5), active route line, and click-to-select destination.</li>
 *   <li><b>Tab 1 — Regional Arbitrage:</b> Visual guide to all 4 Maritime Trade Regions,
 *       their local exports, and 2x High-Demand imports.</li>
 *   <li><b>Tab 2 — Port Upgrades & Automation:</b> Reference table for Lv.1–5 harbor upgrades,
 *       cargo capacity, cooldowns, and Redstone / Depot automation.</li>
 * </ul>
 */
public class NauticalAtlasScreen extends Screen {

    private static final int GUI_WIDTH = 348;
    private static final int GUI_HEIGHT = 232;

    private final InteractionHand hand;
    private int leftPos;
    private int topPos;
    private int currentTab = 0;

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

    @Override
    protected void init() {
        super.init();
        ItemStack journal = getJournalStack();
        if (!journal.isEmpty() && this.minecraft != null && this.minecraft.level != null) {
            RouteJournalItem.pruneBrokenHarbors(journal, this.minecraft.level);
        }
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        int tabW = 106;
        int tabY = this.topPos + 22;
        this.tabMapBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_map"), b -> switchTab(0))
                .bounds(this.leftPos + 10, tabY, tabW, 18).build();
        this.tabArbitrageBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_arbitrage"), b -> switchTab(1))
                .bounds(this.leftPos + 121, tabY, tabW, 18).build();
        this.tabUpgradesBtn = Button.builder(Component.translatable("gui.currents_of_trade.atlas.tab_upgrades"), b -> switchTab(2))
                .bounds(this.leftPos + 232, tabY, tabW, 18).build();

        this.addRenderableWidget(this.tabMapBtn);
        this.addRenderableWidget(this.tabArbitrageBtn);
        this.addRenderableWidget(this.tabUpgradesBtn);
        updateTabButtons();
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        updateTabButtons();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F);
        }
    }

    private void updateTabButtons() {
        if (this.tabMapBtn != null) this.tabMapBtn.active = (this.currentTab != 0);
        if (this.tabArbitrageBtn != null) this.tabArbitrageBtn.active = (this.currentTab != 1);
        if (this.tabUpgradesBtn != null) this.tabUpgradesBtn.active = (this.currentTab != 2);
    }

    private ItemStack getJournalStack() {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        ItemStack stack = this.minecraft.player.getItemInHand(this.hand);
        if (stack.getItem() instanceof RouteJournalItem) return stack;
        return ItemStack.EMPTY;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Outer naval frame & warm parchment interior
        g.fill(this.leftPos - 2, this.topPos - 2, this.leftPos + GUI_WIDTH + 2, this.topPos + GUI_HEIGHT + 2, 0xFF1A2634);
        g.fill(this.leftPos, this.topPos, this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0xFFF2E6CE);
        g.renderOutline(this.leftPos + 3, this.topPos + 3, GUI_WIDTH - 6, GUI_HEIGHT - 6, 0xFF8B6914);

        // Dark Navy Header Bar
        g.fill(this.leftPos + 4, this.topPos + 4, this.leftPos + GUI_WIDTH - 4, this.topPos + 20, 0xFF1B2D3E);
        g.drawCenteredString(this.font, this.title, this.leftPos + GUI_WIDTH / 2, this.topPos + 8, 0xFFFFD700);

        super.render(g, mouseX, mouseY, partialTick);

        if (this.currentTab == 0) {
            renderNauticalMapTab(g, mouseX, mouseY);
        } else if (this.currentTab == 1) {
            renderArbitrageTab(g, mouseX, mouseY);
        } else {
            renderPortUpgradesTab(g, mouseX, mouseY);
        }
    }

    private void renderNauticalMapTab(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack journal = getJournalStack();
        List<RouteJournalItem.RouteEntry> routes = journal.isEmpty() ? List.of() : RouteJournalItem.getRoutes(journal);
        int selectedIdx = journal.isEmpty() ? 0 : RouteJournalItem.getSelectedIndex(journal);

        // Left: Ocean Radar Map Box (176 x 176)
        int mapX = this.leftPos + 10;
        int mapY = this.topPos + 44;
        int mapW = 176;
        int mapH = 176;

        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF10283E);
        g.renderOutline(mapX, mapY, mapW, mapH, 0xFF5C4328);

        // Subtle nautical grid lines
        for (int gx = 28; gx < mapW; gx += 28) {
            g.fill(mapX + gx, mapY + 1, mapX + gx + 1, mapY + mapH - 1, 0x2264B5F6);
        }
        for (int gy = 28; gy < mapH; gy += 28) {
            g.fill(mapX + 1, mapY + gy, mapX + mapW - 1, mapY + gy + 1, 0x2264B5F6);
        }

        // Compass Rose indicator at top-right of map
        g.drawString(this.font, "§eN▲", mapX + mapW - 18, mapY + 4, 0xFFD700, false);

        BlockPos playerPos = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.blockPosition() : BlockPos.ZERO;

        // Calculate dynamic bounds across player + all logged harbors
        double minX = playerPos.getX() - 300;
        double maxX = playerPos.getX() + 300;
        double minZ = playerPos.getZ() - 300;
        double maxZ = playerPos.getZ() + 300;
        for (RouteJournalItem.RouteEntry entry : routes) {
            minX = Math.min(minX, entry.pos().getX() - 150);
            maxX = Math.max(maxX, entry.pos().getX() + 150);
            minZ = Math.min(minZ, entry.pos().getZ() - 150);
            maxZ = Math.max(maxZ, entry.pos().getZ() + 150);
        }
        double span = Math.max(400.0, Math.max(maxX - minX, maxZ - minZ));
        double centerX = (minX + maxX) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;

        int px = mapX + mapW / 2 + (int) Math.round(((playerPos.getX() - centerX) / span) * (mapW - 24));
        int pz = mapY + mapH / 2 + (int) Math.round(((playerPos.getZ() - centerZ) / span) * (mapH - 24));
        px = Mth.clamp(px, mapX + 8, mapX + mapW - 8);
        pz = Mth.clamp(pz, mapY + 8, mapY + mapH - 8);

        // Draw active voyage line from player to selected harbor
        if (!routes.isEmpty() && selectedIdx >= 0 && selectedIdx < routes.size()) {
            RouteJournalItem.RouteEntry sel = routes.get(selectedIdx);
            int tx = Mth.clamp(mapX + mapW / 2 + (int) Math.round(((sel.pos().getX() - centerX) / span) * (mapW - 24)), mapX + 8, mapX + mapW - 8);
            int tz = Mth.clamp(mapY + mapH / 2 + (int) Math.round(((sel.pos().getZ() - centerZ) / span) * (mapH - 24)), mapY + 8, mapY + mapH - 8);
            for (int step = 0; step <= 24; step += 2) {
                int lx = px + (tx - px) * step / 24;
                int lz = pz + (tz - pz) * step / 24;
                g.fill(lx - 1, lz - 1, lx + 1, lz + 1, 0xFFFFD700);
            }
        }

        // Draw Player marker
        g.fill(px - 3, pz - 3, px + 3, pz + 3, 0xFFFFFFFF);
        g.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF00E5FF);

        // Right-side Harbor List (x=192..338)
        int listX = this.leftPos + 192;
        int listY = this.topPos + 44;
        int listW = 146;
        g.drawString(this.font, Component.translatable("gui.currents_of_trade.atlas.harbors_header").getString()
                + " (" + routes.size() + "/" + RouteJournalItem.MAX_ROUTES + ")", listX, listY, 0x1B2D3E, false);

        RouteJournalItem.RouteEntry hoveredEntry = null;
        int hoveredIndex = -1;

        for (int i = 0; i < routes.size(); i++) {
            RouteJournalItem.RouteEntry entry = routes.get(i);
            MaritimeTradeRegion region = MaritimeTradeRegion.byId(entry.regionId());
            int hx = Mth.clamp(mapX + mapW / 2 + (int) Math.round(((entry.pos().getX() - centerX) / span) * (mapW - 24)), mapX + 8, mapX + mapW - 8);
            int hz = Mth.clamp(mapY + mapH / 2 + (int) Math.round(((entry.pos().getZ() - centerZ) / span) * (mapH - 24)), mapY + 8, mapY + mapH - 8);

            boolean isSelected = (i == selectedIdx);
            int markerCol = (0xFF << 24) | region.getColor();
            if (isSelected) {
                g.fill(hx - 5, hz - 5, hx + 5, hz + 5, 0xFFFFD700);
            }
            g.fill(hx - 3, hz - 3, hx + 3, hz + 3, markerCol);

            if (Math.abs(mouseX - hx) <= 5 && Math.abs(mouseY - hz) <= 5) {
                hoveredEntry = entry;
                hoveredIndex = i;
            }

            // Right-side clickable dark slate row
            int rowY = listY + 13 + i * 16;
            boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + 14;
            int rowBg = isSelected ? 0xFF2B4C65 : (rowHover ? 0xFF243B52 : 0xFF182838);
            g.fill(listX, rowY, listX + listW, rowY + 14, rowBg);
            g.renderOutline(listX, rowY, listW, 14, isSelected ? 0xFFD4AF37 : 0xFF3A536B);
            g.fill(listX + 2, rowY + 3, listX + 6, rowY + 11, markerCol);

            int dist = (int) Math.sqrt(playerPos.distSqr(entry.pos()));
            String distStr = dist + "m";
            int distW = this.font.width(distStr);
            String label = this.font.plainSubstrByWidth(entry.name(), listW - distW - 34) + " §7L" + entry.level();
            g.drawString(this.font, label, listX + 9, rowY + 3, isSelected ? 0xFFFFD700 : 0xFFE8EEF5, false);
            g.drawString(this.font, distStr, listX + listW - distW - 15, rowY + 3, isSelected ? 0xFF80DEEA : 0xFF90A4AE, false);

            int delX = listX + listW - 13;
            int delY = rowY + 1;
            boolean delHover = mouseX >= delX && mouseX <= delX + 11 && mouseY >= delY && mouseY <= delY + 12;
            g.fill(delX, delY, delX + 11, delY + 12, delHover ? 0xFFB71C1C : 0xFF2A1515);
            g.renderOutline(delX, delY, 11, 12, delHover ? 0xFFFF5252 : 0xFF6B2222);
            g.drawString(this.font, "✕", delX + 3, delY + 2, delHover ? 0xFFFFFFFF : 0xFFE57373, false);

            if (rowHover) {
                hoveredEntry = entry;
                hoveredIndex = i;
            }
        }

        if (routes.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("gui.currents_of_trade.atlas.no_harbors"),
                    listX, listY + 18, listW, 0x4E342E);
        }

        if (hoveredEntry != null) {
            MaritimeTradeRegion reg = MaritimeTradeRegion.byId(hoveredEntry.regionId());
            int dist = (int) Math.sqrt(playerPos.distSqr(hoveredEntry.pos()));
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("§e§l⚓ " + hoveredEntry.name() + " §7(Lv." + hoveredEntry.level() + ")"));
            tip.add(Component.literal("§7Coords: §f" + hoveredEntry.pos().getX() + ", " + hoveredEntry.pos().getZ() + " §8(" + dist + "m)"));
            tip.add(Component.translatable("tooltip.currents_of_trade.chart_region", reg.getDisplayName()));
            tip.add(reg.getExportDescription());
            tip.add(reg.getImportDescription());
            if (this.minecraft != null && this.minecraft.level != null) {
                HarborTradeOffer bounty = reg.getUrgentBountyOffer(this.minecraft.level, hoveredEntry.pos());
                if (bounty != null && !bounty.getCostA().isEmpty()) {
                    String summary = bounty.getCostA().getCount() + "x " + bounty.getCostA().getHoverName().getString()
                            + " -> " + bounty.getResult().getCount() + "x " + bounty.getResult().getHoverName().getString();
                    tip.add(Component.translatable("tooltip.currents_of_trade.harbor_daily_bounty", summary));
                }
            }
            if (hoveredIndex != selectedIdx) {
                tip.add(Component.translatable("gui.currents_of_trade.atlas.click_to_select"));
            }
            g.renderTooltip(this.font, tip, Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderArbitrageTab(GuiGraphics g, int mouseX, int mouseY) {
        int startX = this.leftPos + 10;
        int startY = this.topPos + 44;
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
            MaritimeTradeRegion r = regions[i];
            int boxY = startY + 12 + i * (cardH + 3);
            int accentCol = (0xFF << 24) | r.getColor();

            // Dark Navy Card so §7 / §e / §b / §a / §6 colored text has crisp contrast
            g.fill(startX, boxY, startX + cardW, boxY + cardH, 0xFF182838);
            // Dedicated right-hand icon bay with vertical divider
            int bayX = startX + cardW - iconBayW;
            g.fill(bayX, boxY + 1, startX + cardW - 1, boxY + cardH - 1, 0xFF101C28);
            g.fill(bayX, boxY + 1, bayX + 1, boxY + cardH - 1, 0x885C7A99);

            g.renderOutline(startX, boxY, cardW, cardH, accentCol);
            g.fill(startX, boxY, startX + 4, boxY + cardH, accentCol);

            g.drawString(this.font, "⚓ " + r.getDisplayName().getString(), startX + 8, boxY + 3, r.getColor(), true);
            drawClampedText(g, r.getExportDescription().getString(), startX + 8, boxY + 14, textMaxW, 0xFFE0E0E0);
            drawClampedText(g, r.getImportDescription().getString(), startX + 8, boxY + 25, textMaxW, 0xFFE0E0E0);

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

    private void renderPortUpgradesTab(GuiGraphics g, int mouseX, int mouseY) {
        int startX = this.leftPos + 10;
        int startY = this.topPos + 44;
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

        // Redstone & Depot Automation footer box (48px tall so 3-4 lines fit comfortably inside)
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
            List<RouteJournalItem.RouteEntry> routes = journal.isEmpty() ? List.of() : RouteJournalItem.getRoutes(journal);
            if (!routes.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                BlockPos playerPos = this.minecraft.player.blockPosition();
                int mapX = this.leftPos + 10;
                int mapY = this.topPos + 44;
                int mapW = 176;
                int mapH = 176;
                int listX = this.leftPos + 192;
                int listY = this.topPos + 44;
                int listW = 146;

                double minX = playerPos.getX() - 300;
                double maxX = playerPos.getX() + 300;
                double minZ = playerPos.getZ() - 300;
                double maxZ = playerPos.getZ() + 300;
                for (RouteJournalItem.RouteEntry entry : routes) {
                    minX = Math.min(minX, entry.pos().getX() - 150);
                    maxX = Math.max(maxX, entry.pos().getX() + 150);
                    minZ = Math.min(minZ, entry.pos().getZ() - 150);
                    maxZ = Math.max(maxZ, entry.pos().getZ() + 150);
                }
                double span = Math.max(400.0, Math.max(maxX - minX, maxZ - minZ));
                double centerX = (minX + maxX) * 0.5;
                double centerZ = (minZ + maxZ) * 0.5;

                for (int i = 0; i < routes.size(); i++) {
                    RouteJournalItem.RouteEntry entry = routes.get(i);
                    int hx = Mth.clamp(mapX + mapW / 2 + (int) Math.round(((entry.pos().getX() - centerX) / span) * (mapW - 24)), mapX + 8, mapX + mapW - 8);
                    int hz = Mth.clamp(mapY + mapH / 2 + (int) Math.round(((entry.pos().getZ() - centerZ) / span) * (mapH - 24)), mapY + 8, mapY + mapH - 8);
                    int rowY = listY + 13 + i * 16;

                    int delX = listX + listW - 13;
                    int delY = rowY + 1;
                    boolean hitDel = mouseX >= delX && mouseX <= delX + 11 && mouseY >= delY && mouseY <= delY + 12;

                    if (hitDel) {
                        RouteJournalItem.removeRouteByIndex(journal, i);
                        PacketDistributor.sendToServer(new ModPayloads.SelectJournalRoutePayload(
                                this.hand == InteractionHand.OFF_HAND, -i - 1));
                        this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.2F);
                        return true;
                    }

                    boolean hitMap = Math.abs(mouseX - hx) <= 5 && Math.abs(mouseY - hz) <= 5;
                    boolean hitList = mouseX >= listX && mouseX <= listX + listW - 14 && mouseY >= rowY && mouseY <= rowY + 14;

                    if (hitMap || hitList) {
                        RouteJournalItem.setSelectedIndex(journal, i);
                        PacketDistributor.sendToServer(new ModPayloads.SelectJournalRoutePayload(
                                this.hand == InteractionHand.OFF_HAND, i));
                        this.minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.85F, 1.15F);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
