package com.lexovian.currentsoftrade;

import com.google.common.collect.ImmutableSet;
import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
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
import com.lexovian.currentsoftrade.item.MessageInABottleItem;
import com.lexovian.currentsoftrade.item.StormGlassItem;
import com.lexovian.currentsoftrade.item.CaptainsPipeItem;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import com.lexovian.currentsoftrade.item.CargoBoatItem;
import com.lexovian.currentsoftrade.item.TradeBoatItem;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe;
import com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
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

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<CoastalWaterCheckProcessor>> COASTAL_WATER_CHECK =
            STRUCTURE_PROCESSORS.register("coastal_water_check", () -> () -> CoastalWaterCheckProcessor.CODEC);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<TreasureMapCraftingRecipe>> TREASURE_MAP_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_treasure_map", () -> new SimpleCraftingRecipeSerializer<>(TreasureMapCraftingRecipe::new));

    // --- Blocks & Block Entities ---
    public static final DeferredBlock<AnchorPointBlock> ANCHOR_POINT = BLOCKS.registerBlock(
            "anchor_point",
            AnchorPointBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)
    );
    public static final DeferredItem<BlockItem> ANCHOR_POINT_ITEM = ITEMS.registerSimpleBlockItem("anchor_point", ANCHOR_POINT);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnchorPointBlockEntity>> ANCHOR_POINT_BE =
            BLOCK_ENTITIES.register("anchor_point", () ->
                    BlockEntityType.Builder.of(AnchorPointBlockEntity::new, ANCHOR_POINT.get()).build(null)
            );

    // --- Core Navigation & Vessels ---
    public static final DeferredItem<NauticalChartItem> NAUTICAL_CHART =
            ITEMS.registerItem("nautical_chart", NauticalChartItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<DoubloonItem> DOUBLOON =
            ITEMS.registerItem("doubloon", DoubloonItem::new, new Item.Properties().stacksTo(64));

    public static final DeferredItem<TradeLedgerItem> TRADE_LEDGER =
            ITEMS.registerItem("trade_ledger", TradeLedgerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<SloopItem> SLOOP_ITEM =
            ITEMS.registerItem("sloop", SloopItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<CargoBoatItem> CARGO_BOAT_ITEM =
            ITEMS.registerItem("cargo_boat", CargoBoatItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<TradeBoatItem> TRADE_BOAT_ITEM =
            ITEMS.registerItem("trade_boat", TradeBoatItem::new, new Item.Properties().stacksTo(1));

    // --- Trade Commodities & Goods ---
    /** Exotic spice blend -- eating grants Speed II + Haste I for 30 seconds. */
    public static final DeferredItem<SpiceSackItem> SPICE_SACK =
            ITEMS.registerItem("spice_sack", SpiceSackItem::new, new Item.Properties());

    /** Rare amber extract -- drinking grants Regeneration II (15s) with a Nausea side-effect (3s). */
    public static final DeferredItem<AmberVialItem> AMBER_VIAL =
            ITEMS.registerItem("amber_vial", AmberVialItem::new, new Item.Properties());

    /** Sea salt pouch -- right-click on edible in offhand to preserve it (adds Salted NBT tag). */
    public static final DeferredItem<SaltPouchItem> SALT_POUCH =
            ITEMS.registerItem("salt_pouch", SaltPouchItem::new, new Item.Properties());

    /** Luxury silk bale -- high-value trade currency, rarer than Doubloon, used in harbor trade offers. */
    public static final DeferredItem<SilkBaleItem> SILK_BALE =
            ITEMS.registerItem("silk_bale", SilkBaleItem::new, new Item.Properties().stacksTo(16));

    /** Hand-painted glazed ceramic from eastern ports -- high-demand luxury trade commodity. */
    public static final DeferredItem<FinePorcelainItem> FINE_PORCELAIN =
            ITEMS.registerItem("fine_porcelain", FinePorcelainItem::new, new Item.Properties().stacksTo(16));

    /** Rare shimmering gemstone dredged from deep ocean trenches -- prized ocean treasure. */
    public static final DeferredItem<LuminousPearlItem> LUMINOUS_PEARL =
            ITEMS.registerItem("luminous_pearl", LuminousPearlItem::new, new Item.Properties().stacksTo(16));

    /** Ornate brass navigational instrument mapping the stars and seas. */
    public static final DeferredItem<BrassAstrolabeItem> BRASS_ASTROLABE =
            ITEMS.registerItem("brass_astrolabe", BrassAstrolabeItem::new, new Item.Properties().stacksTo(1));

    /** Densely pressed block of aged tea leaves -- ancient currency across shipping routes. */
    public static final DeferredItem<TeaBrickItem> TEA_BRICK =
            ITEMS.registerItem("tea_brick", TeaBrickItem::new, new Item.Properties().stacksTo(16));

    /** Fragrant piece of rare timber from tropical archipelagos. */
    public static final DeferredItem<SandalwoodItem> SANDALWOOD =
            ITEMS.registerItem("sandalwood", SandalwoodItem::new, new Item.Properties().stacksTo(16));

    /** Dried pods of tropical orchids -- precious aromatic culinary spice. */
    public static final DeferredItem<VanillaBundleItem> VANILLA_BUNDLE =
            ITEMS.registerItem("vanilla_bundle", VanillaBundleItem::new, new Item.Properties().stacksTo(16));

    /** Spiraled petrified shell recovered from ocean floor strata. */
    public static final DeferredItem<AmmoniteFossilItem> AMMONITE_FOSSIL =
            ITEMS.registerItem("ammonite_fossil", AmmoniteFossilItem::new, new Item.Properties().stacksTo(16));

    /** Weathered nautical bottle sealed with wax, containing lost voyage secrets. */
    public static final DeferredItem<MessageInABottleItem> MESSAGE_IN_A_BOTTLE =
            ITEMS.registerItem("message_in_a_bottle", MessageInABottleItem::new, new Item.Properties().stacksTo(16));

    /** Weather forecasting sealed barometer vial filled with sensitive crystal solutions. */
    public static final DeferredItem<StormGlassItem> STORM_GLASS =
            ITEMS.registerItem("storm_glass", StormGlassItem::new, new Item.Properties().stacksTo(1));

    /** Polished seaworn wooden pipe favored by seasoned captains and sea dogs. */
    public static final DeferredItem<CaptainsPipeItem> CAPTAINS_PIPE =
            ITEMS.registerItem("captains_pipe", CaptainsPipeItem::new, new Item.Properties().stacksTo(1));

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
            ENTITY_TYPES.register("trade_boat", () ->
                    EntityType.Builder.<TradeBoatEntity>of(TradeBoatEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("trade_boat")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<CargoBoatEntity>> CARGO_BOAT =
            ENTITY_TYPES.register("cargo_boat", () ->
                    EntityType.Builder.<CargoBoatEntity>of(CargoBoatEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("cargo_boat")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<SloopEntity>> SLOOP =
            ENTITY_TYPES.register("sloop", () ->
                    EntityType.Builder.<SloopEntity>of(SloopEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("sloop")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<WanderingSailorEntity>> WANDERING_SAILOR =
            ENTITY_TYPES.register("wandering_sailor", () ->
                    EntityType.Builder.<WanderingSailorEntity>of(
                            WanderingSailorEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("wandering_sailor")
            );

    // --- Village & POI Integration ---
    public static final DeferredHolder<PoiType, PoiType> ANCHOR_POINT_POI =
            POI_TYPES.register("anchor_point_poi", () ->
                    new PoiType(ImmutableSet.copyOf(ANCHOR_POINT.get().getStateDefinition().getPossibleStates()), 1, 1)
            );

    public static final DeferredHolder<VillagerProfession, VillagerProfession> HARBORMASTER =
            VILLAGER_PROFESSIONS.register("harbormaster", () ->
                    new VillagerProfession(
                            "harbormaster",
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            ImmutableSet.of(),
                            ImmutableSet.of(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER
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
                                output.accept(NAUTICAL_CHART.get());
                                output.accept(DOUBLOON.get());
                                output.accept(SILK_BALE.get());
                                output.accept(TEA_BRICK.get());
                                output.accept(FINE_PORCELAIN.get());
                                output.accept(SANDALWOOD.get());
                                output.accept(VANILLA_BUNDLE.get());
                                output.accept(LUMINOUS_PEARL.get());
                                output.accept(AMMONITE_FOSSIL.get());
                                output.accept(MESSAGE_IN_A_BOTTLE.get());
                                output.accept(SPICE_SACK.get());
                                output.accept(AMBER_VIAL.get());
                                output.accept(SALT_POUCH.get());
                                output.accept(BRASS_ASTROLABE.get());
                                output.accept(STORM_GLASS.get());
                                output.accept(CAPTAINS_PIPE.get());
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
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ANCHOR_POINT_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NAUTICAL_CHART);
            event.accept(BRASS_ASTROLABE);
            event.accept(STORM_GLASS);
            event.accept(CAPTAINS_PIPE);
            event.accept(MESSAGE_IN_A_BOTTLE);
            event.accept(TRADE_LEDGER);
            event.accept(SLOOP_ITEM);
            event.accept(CARGO_BOAT_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DOUBLOON);
            event.accept(SILK_BALE);
            event.accept(TEA_BRICK);
            event.accept(FINE_PORCELAIN);
            event.accept(SANDALWOOD);
            event.accept(VANILLA_BUNDLE);
            event.accept(LUMINOUS_PEARL);
            event.accept(AMMONITE_FOSSIL);
            event.accept(SPICE_SACK);
            event.accept(AMBER_VIAL);
            event.accept(SALT_POUCH);
        }
    }
}
