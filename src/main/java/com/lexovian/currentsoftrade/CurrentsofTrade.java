package com.lexovian.currentsoftrade;

import com.google.common.collect.ImmutableSet;
import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
import com.lexovian.currentsoftrade.item.SloopItem;
import com.lexovian.currentsoftrade.item.TradeLedgerItem;
import com.lexovian.currentsoftrade.item.SpiceSackItem;
import com.lexovian.currentsoftrade.item.AmberVialItem;
import com.lexovian.currentsoftrade.item.SaltPouchItem;
import com.lexovian.currentsoftrade.item.SilkBaleItem;
import com.lexovian.currentsoftrade.item.FinePorcelainItem;
import com.lexovian.currentsoftrade.item.LuminousPearlItem;
import com.lexovian.currentsoftrade.item.BrassAstrolabeItem;
import com.lexovian.currentsoftrade.item.TeaBrickItem;
import com.lexovian.currentsoftrade.item.SandalwoodItem;
import com.lexovian.currentsoftrade.item.VanillaBundleItem;
import com.lexovian.currentsoftrade.item.AmmoniteFossilItem;
import com.lexovian.currentsoftrade.item.SaffronBundleItem;
import com.lexovian.currentsoftrade.item.JadeFigurineItem;
import com.lexovian.currentsoftrade.item.CacaoElixirItem;
import com.lexovian.currentsoftrade.item.NarwhalIvoryItem;
import com.lexovian.currentsoftrade.item.DamascusSteelItem;
import com.lexovian.currentsoftrade.item.FrankincenseTearsItem;
import com.lexovian.currentsoftrade.item.TyrianPurpleItem;
import com.lexovian.currentsoftrade.item.DragonsBloodResinItem;
import com.lexovian.currentsoftrade.item.MessageInABottleItem;
import com.lexovian.currentsoftrade.item.StormGlassItem;
import com.lexovian.currentsoftrade.item.CaptainsPipeItem;
import com.lexovian.currentsoftrade.item.TradeCharterItem;
import com.lexovian.currentsoftrade.item.GuildStrongboxItem;
import com.lexovian.currentsoftrade.item.CaptainsSpyglassItem;
import com.lexovian.currentsoftrade.item.BosunsWhistleItem;
import com.lexovian.currentsoftrade.item.SwivelHarpoonItem;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import com.lexovian.currentsoftrade.item.CargoBoatItem;
import com.lexovian.currentsoftrade.item.TradeBoatItem;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe;
import com.lexovian.currentsoftrade.world.village.CoastalDockPoolElement;
import com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

@Mod(CurrentsofTrade.MODID)
public class CurrentsofTrade {

    public static final String MODID = "currents_of_trade";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Deferred Registries ---
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, MODID);
    public static final DeferredRegister<StructurePoolElementType<?>> STRUCTURE_POOL_ELEMENTS = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, MODID);
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> BOSUNS_WHISTLE_SOUND =
            SOUND_EVENTS.register("bosuns_whistle", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "bosuns_whistle")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> HARPOON_FIRE_SOUND =
            SOUND_EVENTS.register("harpoon_fire", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "harpoon_fire")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SHIP_HORN_SOUND =
            SOUND_EVENTS.register("ship_horn", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "ship_horn")));

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<CoastalWaterCheckProcessor>> COASTAL_WATER_CHECK =
            STRUCTURE_PROCESSORS.register("coastal_water_check", () -> () -> CoastalWaterCheckProcessor.CODEC);

    public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<CoastalDockPoolElement>> COASTAL_DOCK_ELEMENT =
            STRUCTURE_POOL_ELEMENTS.register("coastal_dock", () -> () -> CoastalDockPoolElement.CODEC);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TreasureMapCraftingRecipe>> TREASURE_MAP_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_treasure_map", () -> TreasureMapCraftingRecipe.SERIALIZER);

    // --- Blocks & Block Entities ---
    public static final DeferredBlock<AnchorPointBlock> ANCHOR_POINT = BLOCKS.registerBlock(
            "anchor_point",
            AnchorPointBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)
    );
    public static final DeferredItem<BlockItem> ANCHOR_POINT_ITEM = ITEMS.registerItem("anchor_point",
            props -> new BlockItem(ANCHOR_POINT.get(), props) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                    net.minecraft.world.item.component.CustomData cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (cd != null) {
                        net.minecraft.nbt.CompoundTag root = cd.copyTag();
                        if (root.contains("SavedHarborData")) {
                            net.minecraft.nbt.CompoundTag h = root.getCompoundOrEmpty("SavedHarborData");
                            if (h.getIntOr("TradeLevel", 1) > 1 || h.getBooleanOr("SpecializationChosen", false)) {
                                return true;
                            }
                        }
                    }
                    return super.isFoil(stack);
                }

                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
                    net.minecraft.world.item.component.CustomData cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (cd != null) {
                        net.minecraft.nbt.CompoundTag root = cd.copyTag();
                        if (root.contains("SavedHarborData")) {
                            net.minecraft.nbt.CompoundTag h = root.getCompoundOrEmpty("SavedHarborData");
                            if (!h.isEmpty()) {
                                String name = h.getStringOr("HarborName", "");
                                int lvl = Math.max(1, h.getIntOr("TradeLevel", 1));
                                com.lexovian.currentsoftrade.world.harbor.HarborSpecialization spec =
                                        com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.byId(h.getStringOr("Specialization", "none"));
                                if (!name.isEmpty() && (h.getBooleanOr("CustomNamed", false) || lvl > 1 || spec != com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.NONE)) {
                                    tooltipComponents.accept(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.anchor_saved_harbor", name));
                                }
                                if (lvl > 1) {
                                    tooltipComponents.accept(net.minecraft.network.chat.Component.translatable(
                                            "tooltip.currents_of_trade.anchor_saved_level",
                                            lvl,
                                            net.minecraft.network.chat.Component.translatable("harbor.currents_of_trade.level." + lvl)));
                                }
                                if (spec != com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.NONE) {
                                    tooltipComponents.accept(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.spec_active", spec.getShortBadge()));
                                }
                            }
                        }
                    }
                    super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
                }
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnchorPointBlockEntity>> ANCHOR_POINT_BE =
            BLOCK_ENTITIES.register("anchor_point", () ->
                    new BlockEntityType<>(AnchorPointBlockEntity::new, ANCHOR_POINT.get())
            );

    public static final DeferredBlock<com.lexovian.currentsoftrade.block.LighthouseBlock> LIGHTHOUSE = BLOCKS.registerBlock(
            "lighthouse",
            com.lexovian.currentsoftrade.block.LighthouseBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 15)
                    .noOcclusion()
    );
    public static final DeferredItem<BlockItem> LIGHTHOUSE_ITEM = ITEMS.registerItem("lighthouse",
            props -> new BlockItem(LIGHTHOUSE.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
                    tooltipComponents.accept(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.lighthouse.desc"));
                    tooltipComponents.accept(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.lighthouse.tailwind"));
                    super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
                }
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity>> LIGHTHOUSE_BE =
            BLOCK_ENTITIES.register("lighthouse", () ->
                    new BlockEntityType<>(com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity::new, LIGHTHOUSE.get())
            );

    // --- Core Navigation & Vessels ---
    public static final DeferredItem<NauticalChartItem> NAUTICAL_CHART =
            ITEMS.registerItem("nautical_chart", NauticalChartItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<RouteJournalItem> ROUTE_JOURNAL =
            ITEMS.registerItem("route_journal", RouteJournalItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<DoubloonItem> DOUBLOON =
            ITEMS.registerItem("doubloon", DoubloonItem::new, p -> p.stacksTo(64));

    public static final DeferredItem<TradeLedgerItem> TRADE_LEDGER =
            ITEMS.registerItem("trade_ledger", TradeLedgerItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<SloopItem> SLOOP_ITEM =
            ITEMS.registerItem("sloop", SloopItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<CargoBoatItem> CARGO_BOAT_ITEM =
            ITEMS.registerItem("cargo_boat", CargoBoatItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<TradeBoatItem> TRADE_BOAT_ITEM =
            ITEMS.registerItem("trade_boat", TradeBoatItem::new, p -> p.stacksTo(1));

    // --- Trade Commodities & Goods ---
    /** Exotic spice blend -- eating grants Speed II + Haste I for 30 seconds. */
    public static final DeferredItem<SpiceSackItem> SPICE_SACK =
            ITEMS.registerItem("spice_sack", SpiceSackItem::new);

    /** Rare amber extract -- drinking grants Regeneration II (15s) with a Nausea side-effect (3s). */
    public static final DeferredItem<AmberVialItem> AMBER_VIAL =
            ITEMS.registerItem("amber_vial", AmberVialItem::new);

    /** Sea salt pouch -- right-click on edible in offhand to preserve it (adds Salted NBT tag). */
    public static final DeferredItem<SaltPouchItem> SALT_POUCH =
            ITEMS.registerItem("salt_pouch", SaltPouchItem::new);

    /** Luxury silk bale -- high-value trade currency, rarer than Doubloon, used in harbor trade offers. */
    public static final DeferredItem<SilkBaleItem> SILK_BALE =
            ITEMS.registerItem("silk_bale", SilkBaleItem::new, p -> p.stacksTo(16));

    /** Hand-painted glazed ceramic from eastern ports -- high-demand luxury trade commodity. */
    public static final DeferredItem<FinePorcelainItem> FINE_PORCELAIN =
            ITEMS.registerItem("fine_porcelain", FinePorcelainItem::new, p -> p.stacksTo(16));

    /** Rare shimmering gemstone dredged from deep ocean trenches -- prized ocean treasure. */
    public static final DeferredItem<LuminousPearlItem> LUMINOUS_PEARL =
            ITEMS.registerItem("luminous_pearl", LuminousPearlItem::new, p -> p.stacksTo(16));

    /** Ornate brass navigational instrument mapping the stars and seas. */
    public static final DeferredItem<BrassAstrolabeItem> BRASS_ASTROLABE =
            ITEMS.registerItem("brass_astrolabe", BrassAstrolabeItem::new, p -> p.stacksTo(1));

    /** Densely pressed block of aged tea leaves -- ancient currency across shipping routes. */
    public static final DeferredItem<TeaBrickItem> TEA_BRICK =
            ITEMS.registerItem("tea_brick", TeaBrickItem::new, p -> p.stacksTo(16));

    /** Fragrant piece of rare timber from tropical archipelagos. */
    public static final DeferredItem<SandalwoodItem> SANDALWOOD =
            ITEMS.registerItem("sandalwood", SandalwoodItem::new, p -> p.stacksTo(16));

    /** Dried pods of tropical orchids -- precious aromatic culinary spice. */
    public static final DeferredItem<VanillaBundleItem> VANILLA_BUNDLE =
            ITEMS.registerItem("vanilla_bundle", VanillaBundleItem::new, p -> p.stacksTo(16));

    /** Spiraled petrified shell recovered from ocean floor strata. */
    public static final DeferredItem<AmmoniteFossilItem> AMMONITE_FOSSIL =
            ITEMS.registerItem("ammonite_fossil", AmmoniteFossilItem::new, p -> p.stacksTo(16));

    /** Weathered nautical bottle sealed with wax, containing lost voyage secrets. */
    public static final DeferredItem<MessageInABottleItem> MESSAGE_IN_A_BOTTLE =
            ITEMS.registerItem("message_in_a_bottle", MessageInABottleItem::new, p -> p.stacksTo(16));

    /** Weather forecasting sealed barometer vial filled with sensitive crystal solutions. */
    public static final DeferredItem<StormGlassItem> STORM_GLASS =
            ITEMS.registerItem("storm_glass", StormGlassItem::new, p -> p.stacksTo(1));

    /** Polished seaworn wooden pipe favored by seasoned captains and sea dogs. */
    public static final DeferredItem<CaptainsPipeItem> CAPTAINS_PIPE =
            ITEMS.registerItem("captains_pipe", CaptainsPipeItem::new, p -> p.stacksTo(1));

    /** Prestigious naval tricorne hat granting +20% sailing speed and 10% harbor fee discounts. */
    public static final DeferredItem<com.lexovian.currentsoftrade.item.CaptainsTricorneItem> CAPTAINS_TRICORNE =
            ITEMS.registerItem("captains_tricorne", com.lexovian.currentsoftrade.item.CaptainsTricorneItem::new);

    /** Weighted iron dredge used to scrape ocean depths and haul up lost cargo and relics. */
    public static final DeferredItem<com.lexovian.currentsoftrade.item.SalvageDredgeItem> SALVAGE_DREDGE =
            ITEMS.registerItem("salvage_dredge", com.lexovian.currentsoftrade.item.SalvageDredgeItem::new);

    /** Precious desert crocus threads — eating grants Fire Resistance I (45s) and Absorption I (30s). */
    public static final DeferredItem<SaffronBundleItem> SAFFRON_BUNDLE =
            ITEMS.registerItem("saffron_bundle", SaffronBundleItem::new, p -> p.stacksTo(16));

    /** Carved jade sea-serpent statuette — right-click channels Luck I & Dolphin's Grace I (30s). */
    public static final DeferredItem<JadeFigurineItem> JADE_FIGURINE =
            ITEMS.registerItem("jade_figurine", JadeFigurineItem::new, p -> p.stacksTo(16));

    /** Spiced jungle cacao brew — drinking grants Dolphin's Grace I & Strength I and clears Weakness/Mining Fatigue. */
    public static final DeferredItem<CacaoElixirItem> CACAO_ELIXIR =
            ITEMS.registerItem("cacao_elixir", CacaoElixirItem::new, p -> p.stacksTo(16));

    /** Spiraled arctic sea tusk prized across tropical and desert ports. */
    public static final DeferredItem<NarwhalIvoryItem> NARWHAL_IVORY =
            ITEMS.registerItem("narwhal_ivory", NarwhalIvoryItem::new, p -> p.stacksTo(16));

    /** Watered crucible steel ingot — right-click with damaged item in offhand to restore 25% durability. */
    public static final DeferredItem<DamascusSteelItem> DAMASCUS_STEEL =
            ITEMS.registerItem("damascus_steel", DamascusSteelItem::new, p -> p.stacksTo(16));

    /** Golden resin tears from desert cliffs — clears debuffs and grants Resistance I (30s) + Regeneration I (20s). */
    public static final DeferredItem<FrankincenseTearsItem> FRANKINCENSE_TEARS =
            ITEMS.registerItem("frankincense_tears", FrankincenseTearsItem::new, p -> p.stacksTo(16));

    /** Royal marine snail dye — dyes sails royal purple or grants Hero of the Village I (60s) + Glowing (30s). */
    public static final DeferredItem<TyrianPurpleItem> TYRIAN_PURPLE =
            ITEMS.registerItem("tyrian_purple", TyrianPurpleItem::new, p -> p.stacksTo(16));

    /** Exotic dragon's blood resin — dyes sails blood red or grants Fire Resistance I (60s) + Strength I (30s). */
    public static final DeferredItem<DragonsBloodResinItem> DRAGONS_BLOOD_RESIN =
            ITEMS.registerItem("dragons_blood_resin", DragonsBloodResinItem::new, p -> p.stacksTo(16));

    /** Sealed Guild Trade Charter — merchant contract fulfilled at regional Anchor Points or via Cargo/Trade Ships. */
    public static final DeferredItem<TradeCharterItem> TRADE_CHARTER =
            ITEMS.registerItem("trade_charter", TradeCharterItem::new, p -> p.stacksTo(1));

    /** Guild Strongbox — ornate lockbox rewarded for completing Sealed Trade Charters. */
    public static final DeferredItem<GuildStrongboxItem> GUILD_STRONGBOX =
            ITEMS.registerItem("guild_strongbox", GuildStrongboxItem::new, p -> p.stacksTo(16));

    /** Captain's Brass Spyglass — naval telescope with live ship, harbor & maritime region Recon HUD. */
    public static final DeferredItem<CaptainsSpyglassItem> CAPTAINS_SPYGLASS =
            ITEMS.registerItem("captains_spyglass", CaptainsSpyglassItem::new, p -> p.stacksTo(1));

    /** Bosun's Whistle — summons nearby Sloops/Cargo Boats or recalls outbound autonomous ships. */
    public static final DeferredItem<BosunsWhistleItem> BOSUNS_WHISTLE =
            ITEMS.registerItem("bosuns_whistle", BosunsWhistleItem::new, p -> p.stacksTo(1));

    /** Brass Swivel Harpoon — bow-mounted shipwright defense turret repelling aquatic threats. */
    public static final DeferredItem<SwivelHarpoonItem> SWIVEL_HARPOON =
            ITEMS.registerItem("swivel_harpoon", SwivelHarpoonItem::new, p -> p.stacksTo(16));

    /** Navigation Buoy — floating illuminated sea marker placed directly on water; integrates with Nautical Charts. */
    public static final DeferredBlock<com.lexovian.currentsoftrade.block.NavigationBuoyBlock> NAVIGATION_BUOY = BLOCKS.registerBlock(
            "navigation_buoy",
            com.lexovian.currentsoftrade.block.NavigationBuoyBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.0F, 2.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> 14)
                    .noOcclusion()
    );
    public static final DeferredItem<com.lexovian.currentsoftrade.item.NavigationBuoyItem> NAVIGATION_BUOY_ITEM =
            ITEMS.registerItem("navigation_buoy",
                    p -> new com.lexovian.currentsoftrade.item.NavigationBuoyItem(NAVIGATION_BUOY.get(), p.stacksTo(16).useBlockDescriptionPrefix()));

    /** Harbor Brass Bell — rings with a Redstone pulse when trade ships arrive; right-click for fleet status. */
    public static final DeferredBlock<com.lexovian.currentsoftrade.block.HarborBellBlock> HARBOR_BELL = BLOCKS.registerBlock(
            "harbor_bell",
            com.lexovian.currentsoftrade.block.HarborBellBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );
    public static final DeferredItem<BlockItem> HARBOR_BELL_ITEM = ITEMS.registerSimpleBlockItem("harbor_bell", HARBOR_BELL);

    /** Shipboard Trawl Net — mount on a Sloop or Cargo Ship to passively haul fish & sunken relics while sailing. */
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TrawlNetItem> TRAWL_NET =
            ITEMS.registerItem("trawl_net", com.lexovian.currentsoftrade.item.TrawlNetItem::new, p -> p.stacksTo(16));

    // --- Exotic Regional Trade Commodities (v1.0.8) ---
    public static final DeferredItem<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> INDIGO_DYE_CAKE =
            ITEMS.registerItem("indigo_dye_cake", p -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("indigo_dye_cake", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> ERMINE_PELT =
            ITEMS.registerItem("ermine_pelt", p -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("ermine_pelt", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> CINNAMON_QUILLS =
            ITEMS.registerItem("cinnamon_quills", p -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("cinnamon_quills", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> VELVET_BOLT =
            ITEMS.registerItem("velvet_bolt", p -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("velvet_bolt", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.AnimalCageItem> ANIMAL_CAGE =
            ITEMS.registerItem("animal_cage", com.lexovian.currentsoftrade.item.AnimalCageItem::new);
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> FISH_CRATE =
            ITEMS.registerItem("fish_crate", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("fish_crate", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> SALT_CRATE =
            ITEMS.registerItem("salt_crate", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("salt_crate", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> SPICE_CRATE =
            ITEMS.registerItem("spice_crate", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("spice_crate", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> COCOA_CRATE =
            ITEMS.registerItem("cocoa_crate", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("cocoa_crate", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> SUGAR_CRATE =
            ITEMS.registerItem("sugar_crate", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("sugar_crate", p));
    public static final DeferredItem<com.lexovian.currentsoftrade.item.TradeCrateItem> WOOL_BALE =
            ITEMS.registerItem("wool_bale", p -> new com.lexovian.currentsoftrade.item.TradeCrateItem("wool_bale", p));

    // --- Menu Types ---
    public static final DeferredHolder<MenuType<?>, MenuType<AnchorPointMenu>> ANCHOR_POINT_MENU =
            MENUS.register("anchor_point", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AnchorPointMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TravelMenu>> TRAVEL_MENU =
            MENUS.register("travel", () -> new MenuType<>(TravelMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<SendItemsMenu>> SEND_ITEMS_MENU =
            MENUS.register("send_items", () -> new MenuType<>(SendItemsMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<RequestTradeMenu>> REQUEST_TRADE_MENU =
            MENUS.register("request_trade", () -> new MenuType<>(RequestTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // --- Entity Types ---
    public static final DeferredHolder<EntityType<?>, EntityType<TradeBoatEntity>> TRADE_BOAT =
            ENTITY_TYPES.register("trade_boat", key ->
                    EntityType.Builder.<TradeBoatEntity>of(TradeBoatEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, key))
            );

    public static final DeferredHolder<EntityType<?>, EntityType<CargoBoatEntity>> CARGO_BOAT =
            ENTITY_TYPES.register("cargo_boat", key ->
                    EntityType.Builder.<CargoBoatEntity>of(CargoBoatEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, key))
            );

    public static final DeferredHolder<EntityType<?>, EntityType<SloopEntity>> SLOOP =
            ENTITY_TYPES.register("sloop", key ->
                    EntityType.Builder.<SloopEntity>of(SloopEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, key))
            );

    public static final DeferredHolder<EntityType<?>, EntityType<WanderingSailorEntity>> WANDERING_SAILOR =
            ENTITY_TYPES.register("wandering_sailor", key ->
                    EntityType.Builder.<WanderingSailorEntity>of(
                            WanderingSailorEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, key))
            );

    public static final DeferredHolder<EntityType<?>, EntityType<com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity>> DRIFTING_FLOTSAM =
            ENTITY_TYPES.register("drifting_flotsam", key ->
                    EntityType.Builder.<com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity>of(
                            com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity::new, MobCategory.MISC)
                            .sized(0.95F, 0.75F)
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, key))
            );

    // --- Village & POI Integration ---
    public static final DeferredHolder<PoiType, PoiType> ANCHOR_POINT_POI =
            POI_TYPES.register("anchor_point_poi", () ->
                    new PoiType(ImmutableSet.copyOf(ANCHOR_POINT.get().getStateDefinition().getPossibleStates()), 1, 1)
            );

    public static final DeferredHolder<VillagerProfession, VillagerProfession> HARBORMASTER =
            VILLAGER_PROFESSIONS.register("harbormaster", () ->
                    new VillagerProfession(
                            Component.translatable("entity.minecraft.villager.currents_of_trade.harbormaster"),
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            ImmutableSet.of(),
                            ImmutableSet.of(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
                            it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap()
                    )
            );

    // --- Creative Tab ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CURRENTS_TAB =
            CREATIVE_MODE_TABS.register("currents_of_trade_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.currents_of_trade"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> ANCHOR_POINT_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ANCHOR_POINT_ITEM.get());
                                output.accept(LIGHTHOUSE_ITEM.get());
                                output.accept(NAVIGATION_BUOY_ITEM.get());
                                output.accept(HARBOR_BELL_ITEM.get());
                                output.accept(TRAWL_NET.get());
                                output.accept(CAPTAINS_TRICORNE.get());
                                output.accept(SALVAGE_DREDGE.get());
                                output.accept(NAUTICAL_CHART.get());
                                output.accept(ROUTE_JOURNAL.get());
                                output.accept(DOUBLOON.get());
                                output.accept(SILK_BALE.get());
                                output.accept(TEA_BRICK.get());
                                output.accept(FINE_PORCELAIN.get());
                                output.accept(SANDALWOOD.get());
                                output.accept(VANILLA_BUNDLE.get());
                                output.accept(LUMINOUS_PEARL.get());
                                output.accept(AMMONITE_FOSSIL.get());
                                output.accept(SAFFRON_BUNDLE.get());
                                output.accept(JADE_FIGURINE.get());
                                output.accept(CACAO_ELIXIR.get());
                                output.accept(NARWHAL_IVORY.get());
                                output.accept(DAMASCUS_STEEL.get());
                                output.accept(FRANKINCENSE_TEARS.get());
                                output.accept(TYRIAN_PURPLE.get());
                                output.accept(DRAGONS_BLOOD_RESIN.get());
                                output.accept(INDIGO_DYE_CAKE.get());
                                output.accept(ERMINE_PELT.get());
                                output.accept(CINNAMON_QUILLS.get());
                                output.accept(VELVET_BOLT.get());
                                output.accept(ANIMAL_CAGE.get());
                                output.accept(FISH_CRATE.get());
                                output.accept(SALT_CRATE.get());
                                output.accept(SPICE_CRATE.get());
                                output.accept(COCOA_CRATE.get());
                                output.accept(SUGAR_CRATE.get());
                                output.accept(WOOL_BALE.get());
                                output.accept(MESSAGE_IN_A_BOTTLE.get());
                                output.accept(SPICE_SACK.get());
                                output.accept(AMBER_VIAL.get());
                                output.accept(SALT_POUCH.get());
                                output.accept(BRASS_ASTROLABE.get());
                                output.accept(STORM_GLASS.get());
                                output.accept(CAPTAINS_PIPE.get());
                                output.accept(CAPTAINS_SPYGLASS.get());
                                output.accept(BOSUNS_WHISTLE.get());
                                output.accept(SWIVEL_HARPOON.get());
                                output.accept(TRADE_CHARTER.get());
                                output.accept(GUILD_STRONGBOX.get());
                                output.accept(TRADE_LEDGER.get());
                                output.accept(SLOOP_ITEM.get());
                                output.accept(CARGO_BOAT_ITEM.get());
                                output.accept(TRADE_BOAT_ITEM.get());
                            })
                            .build()
            );

    // --- Mod Lifecycle ---
    public CurrentsofTrade(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        POI_TYPES.register(modEventBus);
        VILLAGER_PROFESSIONS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        STRUCTURE_PROCESSORS.register(modEventBus);
        STRUCTURE_POOL_ELEMENTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(WANDERING_SAILOR.get(), WanderingSailorEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Currents of Trade common setup completed.");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // All mod items live exclusively in CURRENTS_TAB.
    }
}
