package com.lexovian.currentsoftrade.compat.jei;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CurrentsOfTradeJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "jei_plugin");

    public static final RecipeType<HarborTradeJeiRecipe> HARBOR_TRADE_TYPE =
            RecipeType.create(CurrentsofTrade.MODID, "harbor_trading", HarborTradeJeiRecipe.class);

    public static final RecipeType<UpgradeJeiRecipe> HARBOR_UPGRADE_TYPE =
            RecipeType.create(CurrentsofTrade.MODID, "harbor_upgrading", UpgradeJeiRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new HarborTradeCategory(guiHelper),
                new HarborUpgradeCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get()), HARBOR_TRADE_TYPE, HARBOR_UPGRADE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get()), HARBOR_TRADE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(CurrentsofTrade.ROUTE_JOURNAL.get()), HARBOR_TRADE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()), HARBOR_UPGRADE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get()), HARBOR_UPGRADE_TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(HARBOR_TRADE_TYPE, buildHarborTradeRecipes());
        registration.addRecipes(HARBOR_UPGRADE_TYPE, buildUpgradeAndShipwrightRecipes());

        // Detailed JEI Information Pages (visible when pressing R or U)
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.anchor_point"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.ROUTE_JOURNAL.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.route_journal"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.nautical_chart"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.sloop"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.cargo_boat"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.LIGHTHOUSE_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.lighthouse"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.SALVAGE_DREDGE.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.salvage_dredge"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.frankincense_tears"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.tyrian_purple"));
        registration.addIngredientInfo(new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.currents_of_trade.info.dragons_blood_resin"));
    }

    private List<HarborTradeJeiRecipe> buildHarborTradeRecipes() {
        List<HarborTradeJeiRecipe> list = new ArrayList<>();

        // 1. All Regional Harbor Contracts (Lv.1 -> Lv.5) across the 4 Maritime Trade Basins
        for (MaritimeTradeRegion region : MaritimeTradeRegion.values()) {
            List<HarborTradeOffer> prevOffers = new ArrayList<>();
            for (int lv = 1; lv <= 5; lv++) {
                List<HarborTradeOffer> curOffers = region.generateDefaultHarborTrades(lv);
                for (int idx = prevOffers.size(); idx < curOffers.size(); idx++) {
                    HarborTradeOffer offer = curOffers.get(idx);
                    boolean isExport = offer.getCostA().is(CurrentsofTrade.DOUBLOON.get());
                    Component subtitle = Component.translatable(isExport
                            ? "jei.currents_of_trade.trade.export"
                            : "jei.currents_of_trade.trade.import");
                    list.add(new HarborTradeJeiRecipe(
                            offer.getCostA(),
                            offer.getCostB(),
                            offer.getResult(),
                            Component.literal("⚓ ").append(region.getDisplayName()),
                            Component.literal("Lv." + lv + "+"),
                            subtitle,
                            region.getColor()
                    ));
                }
                prevOffers = curOffers;
            }

            // Daily Urgent 2x Bounties for each region
            for (int v = 0; v < 5; v++) {
                HarborTradeOffer bounty = region.getUrgentBountyOffer(null, new BlockPos(v * 3129871, 64, 0));
                list.add(new HarborTradeJeiRecipe(
                        bounty.getCostA(),
                        bounty.getCostB(),
                        bounty.getResult(),
                        Component.literal("★ ").append(region.getDisplayName()),
                        Component.literal("2x Bounty"),
                        Component.translatable("jei.currents_of_trade.trade.urgent_bounty"),
                        0xFFD700
                ));
            }
        }

        // 2. Visiting Maritime Trade League & Wandering Sailor Specialty Contracts
        Object[][] leagueTrades = {
                { 0, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), 0xFF9800 },
                { 0, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), 0xFF9800 },
                { 0, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), 0xFF9800 },
                { 1, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), 0x4FC3F7 },
                { 1, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), 0x4FC3F7 },
                { 2, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 1), 0x66BB6A },
                { 2, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), 0x66BB6A },
                { 2, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), 0x66BB6A },
                { 3, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), 0xEF5350 },
                { 3, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), 0xEF5350 },
                { 4, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), 0xE53935 },
                { 4, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1), 0xE53935 },
                { 5, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1), 0x42A5F5 },
                { 5, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1), 0x42A5F5 },
                { 5, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), 0x42A5F5 },
                { 5, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10), new ItemStack(Items.EMERALD, 4), new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1), 0x42A5F5 },
                { 0, new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 0xFFD700 },
                { 0, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.EMERALD, 2), 0xFFD700 }
        };
        for (Object[] row : leagueTrades) {
            int leagueId = (int) row[0];
            list.add(new HarborTradeJeiRecipe(
                    (ItemStack) row[1],
                    (ItemStack) row[2],
                    (ItemStack) row[3],
                    Component.literal("⛵ ").append(Component.translatable("league.currents_of_trade." + leagueId)),
                    Component.literal("League"),
                    Component.translatable("jei.currents_of_trade.trade.league_deal"),
                    (int) row[4]
            ));
        }

        return list;
    }

    private List<UpgradeJeiRecipe> buildUpgradeAndShipwrightRecipes() {
        List<UpgradeJeiRecipe> list = new ArrayList<>();
        ItemStack anchor = new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get());

        // 1. Harbor Upgrades (Lv.2 -> Lv.5)
        list.add(new UpgradeJeiRecipe(
                Component.literal("⬆ Lv.2 ").append(Component.translatable("harbor.currents_of_trade.level.2")),
                Component.literal("§a27 Slots §7| §b45s CD §7| §d8 Offers"),
                List.of(
                        List.of(anchor),
                        List.of(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)),
                        List.of(new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.SPRUCE_PLANKS, 32), new ItemStack(Items.BIRCH_PLANKS, 32)),
                        List.of(new ItemStack(Items.IRON_INGOT, 16))
                ),
                anchor,
                0x5BC0EB
        ));

        list.add(new UpgradeJeiRecipe(
                Component.literal("⬆ Lv.3 ").append(Component.translatable("harbor.currents_of_trade.level.3")),
                Component.literal("§a36 Slots §7| §b30s CD §7| §d10 Offers"),
                List.of(
                        List.of(anchor),
                        List.of(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20)),
                        List.of(new ItemStack(Items.COPPER_INGOT, 16)),
                        List.of(new ItemStack(Items.LANTERN, 4))
                ),
                anchor,
                0x48A14D
        ));

        list.add(new UpgradeJeiRecipe(
                Component.literal("⬆ Lv.4 ").append(Component.translatable("harbor.currents_of_trade.level.4")),
                Component.literal("§a45 Slots §7| §b20s CD §7| §d12 Offers"),
                List.of(
                        List.of(anchor),
                        List.of(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 40)),
                        List.of(new ItemStack(Items.GOLD_INGOT, 8)),
                        List.of(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 2)),
                        List.of(new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2))
                ),
                anchor,
                0xE69A28
        ));

        list.add(new UpgradeJeiRecipe(
                Component.literal("⬆ Lv.5 ").append(Component.translatable("harbor.currents_of_trade.level.5")),
                Component.literal("§a54 Slots §7| §b10s CD §7| §d14 Offers"),
                List.of(
                        List.of(anchor),
                        List.of(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 64)),
                        List.of(new ItemStack(Items.EMERALD_BLOCK, 4)),
                        List.of(new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1))
                ),
                anchor,
                0xFFD700
        ));

        // 2. Shipwright Customizations (Sloop & Cargo Ship)
        List<ItemStack> ships = List.of(
                new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()),
                new ItemStack(CurrentsofTrade.CARGO_BOAT_ITEM.get())
        );
        List<ItemStack> dyes = List.of(
                new ItemStack(Items.RED_DYE), new ItemStack(Items.BLUE_DYE), new ItemStack(Items.YELLOW_DYE),
                new ItemStack(Items.LIME_DYE), new ItemStack(Items.ORANGE_DYE), new ItemStack(Items.PURPLE_DYE),
                new ItemStack(Items.CYAN_DYE), new ItemStack(Items.BLACK_DYE), new ItemStack(Items.WHITE_DYE),
                new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get()), new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get())
        );
        List<ItemStack> banners = List.of(
                new ItemStack(Items.WHITE_BANNER), new ItemStack(Items.RED_BANNER), new ItemStack(Items.BLUE_BANNER),
                new ItemStack(Items.YELLOW_BANNER), new ItemStack(Items.ORANGE_BANNER), new ItemStack(Items.BLACK_BANNER),
                new ItemStack(Items.GREEN_BANNER), new ItemStack(Items.LIGHT_BLUE_BANNER)
        );

        list.add(new UpgradeJeiRecipe(
                Component.translatable("jei.currents_of_trade.shipwright.dye_sails"),
                Component.translatable("jei.currents_of_trade.shipwright.dye_sails.desc"),
                List.of(ships, dyes, List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.SPONGE))),
                new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()),
                0x4FC3F7
        ));

        list.add(new UpgradeJeiRecipe(
                Component.translatable("jei.currents_of_trade.shipwright.stern_banner"),
                Component.translatable("jei.currents_of_trade.shipwright.stern_banner.desc"),
                List.of(ships, banners, List.of(new ItemStack(Items.SHEARS))),
                new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()),
                0xFFD54F
        ));

        list.add(new UpgradeJeiRecipe(
                Component.translatable("jei.currents_of_trade.shipwright.bow_lantern"),
                Component.translatable("jei.currents_of_trade.shipwright.bow_lantern.desc"),
                List.of(
                        List.of(new ItemStack(CurrentsofTrade.SLOOP_ITEM.get())),
                        List.of(new ItemStack(Items.LANTERN), new ItemStack(Items.SOUL_LANTERN))
                ),
                new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()),
                0x80DEEA
        ));

        list.add(new UpgradeJeiRecipe(
                Component.translatable("jei.currents_of_trade.shipwright.copper_hull"),
                Component.translatable("jei.currents_of_trade.shipwright.copper_hull.desc"),
                List.of(
                        List.of(new ItemStack(CurrentsofTrade.SLOOP_ITEM.get())),
                        List.of(new ItemStack(Items.COPPER_BLOCK), new ItemStack(Items.WAXED_COPPER_BLOCK))
                ),
                new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()),
                0xFF8A65
        ));

        return list;
    }

    public record HarborTradeJeiRecipe(
            ItemStack costA,
            ItemStack costB,
            ItemStack result,
            Component header,
            Component badge,
            Component subtitle,
            int accentColor
    ) {}

    public record UpgradeJeiRecipe(
            Component title,
            Component stats,
            List<List<ItemStack>> inputs,
            ItemStack output,
            int accentColor
    ) {}

    private static class HarborTradeCategory implements IRecipeCategory<HarborTradeJeiRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable slotDrawable;

        public HarborTradeCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createBlankDrawable(168, 44);
            this.icon = guiHelper.createDrawableItemStack(new ItemStack(CurrentsofTrade.ANCHOR_POINT_ITEM.get()));
            this.slotDrawable = guiHelper.getSlotDrawable();
        }

        @Override
        public RecipeType<HarborTradeJeiRecipe> getRecipeType() {
            return HARBOR_TRADE_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.currents_of_trade.category.harbor_trading");
        }

        @Override
        public IDrawable getBackground() {
            return this.background;
        }

        @Override
        public IDrawable getIcon() {
            return this.icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, HarborTradeJeiRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 19)
                    .addItemStack(recipe.costA())
                    .setBackground(this.slotDrawable, -1, -1);

            if (!recipe.costB().isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 30, 19)
                        .addItemStack(recipe.costB())
                        .setBackground(this.slotDrawable, -1, -1);
            }

            builder.addSlot(RecipeIngredientRole.OUTPUT, 138, 19)
                    .addItemStack(recipe.result())
                    .setBackground(this.slotDrawable, -1, -1);
        }

        @Override
        public void draw(HarborTradeJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
            int w = 168;
            int h = 44;
            int border = (0xFF << 24) | recipe.accentColor();

            g.fill(0, 0, w, h, 0xFF182838);
            g.renderOutline(0, 0, w, h, border);
            g.fill(0, 0, 3, h, border);

            Font font = Minecraft.getInstance().font;
            g.drawString(font, recipe.header(), 6, 4, recipe.accentColor(), true);

            int badgeW = font.width(recipe.badge());
            g.drawString(font, recipe.badge(), w - badgeW - 5, 4, 0xFFFFE082, true);

            g.drawString(font, "§6━━▶", 66, 20, 0xFFD700, true);
            g.drawString(font, recipe.subtitle(), 52, 31, 0xFFCFD8DC, false);
        }
    }

    private static class HarborUpgradeCategory implements IRecipeCategory<UpgradeJeiRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable slotDrawable;

        public HarborUpgradeCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createBlankDrawable(168, 48);
            this.icon = guiHelper.createDrawableItemStack(new ItemStack(CurrentsofTrade.SLOOP_ITEM.get()));
            this.slotDrawable = guiHelper.getSlotDrawable();
        }

        @Override
        public RecipeType<UpgradeJeiRecipe> getRecipeType() {
            return HARBOR_UPGRADE_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.currents_of_trade.category.harbor_upgrading");
        }

        @Override
        public IDrawable getBackground() {
            return this.background;
        }

        @Override
        public IDrawable getIcon() {
            return this.icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, UpgradeJeiRecipe recipe, IFocusGroup focuses) {
            List<List<ItemStack>> inputs = recipe.inputs();
            for (int i = 0; i < inputs.size(); i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 6 + i * 19, 25)
                        .addItemStacks(inputs.get(i))
                        .setBackground(this.slotDrawable, -1, -1);
            }

            builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 25)
                    .addItemStack(recipe.output())
                    .setBackground(this.slotDrawable, -1, -1);
        }

        @Override
        public void draw(UpgradeJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
            int w = 168;
            int h = 48;
            int border = (0xFF << 24) | recipe.accentColor();

            g.fill(0, 0, w, h, 0xFF182838);
            g.renderOutline(0, 0, w, h, border);
            g.fill(0, 0, 3, h, border);

            Font font = Minecraft.getInstance().font;
            g.drawString(font, recipe.title(), 6, 3, recipe.accentColor(), true);
            g.drawString(font, recipe.stats(), 6, 13, 0xFFCFD8DC, false);
            g.drawString(font, "§6▶", 126, 29, 0xFFD700, true);
        }
    }
}

