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
import com.lexovian.currentsoftrade.item.MessageInABottleItem;
import com.lexovian.currentsoftrade.item.StormGlassItem;
import com.lexovian.currentsoftrade.item.CaptainsPipeItem;
import com.lexovian.currentsoftrade.loot.AddTableLootModifier;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import com.lexovian.currentsoftrade.item.CargoBoatItem;
import com.lexovian.currentsoftrade.item.TradeBoatItem;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe;
import com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor;
import com.mojang.serialization.Codec;
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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

@Mod(CurrentsofTrade.MODID)
public class CurrentsofTrade {

    public static final String MODID = "currents_of_trade";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Deferred Registries ---
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final RegistryObject<StructureProcessorType<CoastalWaterCheckProcessor>> COASTAL_WATER_CHECK =
            STRUCTURE_PROCESSORS.register("coastal_water_check", () -> () -> CoastalWaterCheckProcessor.CODEC);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<TreasureMapCraftingRecipe>> TREASURE_MAP_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_treasure_map", () -> new SimpleCraftingRecipeSerializer<>(TreasureMapCraftingRecipe::new));

    /** Forge 1.20.1 has no built-in "add_table" GLM (NeoForge 1.21 does), so the mod ships its own. */
    public static final RegistryObject<Codec<AddTableLootModifier>> ADD_TABLE_LOOT_MODIFIER =
            LOOT_MODIFIERS.register("add_table", () -> AddTableLootModifier.CODEC);

    // --- Blocks & Block Entities ---
    public static final RegistryObject<AnchorPointBlock> ANCHOR_POINT = BLOCKS.register(
            "anchor_point",
            () -> new AnchorPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<BlockItem> ANCHOR_POINT_ITEM =
            ITEMS.register("anchor_point", () -> new BlockItem(ANCHOR_POINT.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<AnchorPointBlockEntity>> ANCHOR_POINT_BE =
            BLOCK_ENTITIES.register("anchor_point", () ->
                    BlockEntityType.Builder.of(AnchorPointBlockEntity::new, ANCHOR_POINT.get()).build(null)
            );

    public static final RegistryObject<com.lexovian.currentsoftrade.block.LighthouseBlock> LIGHTHOUSE = BLOCKS.register(
            "lighthouse",
            () -> new com.lexovian.currentsoftrade.block.LighthouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 15)
                    .noOcclusion())
    );
    public static final RegistryObject<BlockItem> LIGHTHOUSE_ITEM =
            ITEMS.register("lighthouse", () -> new BlockItem(LIGHTHOUSE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity>> LIGHTHOUSE_BE =
            BLOCK_ENTITIES.register("lighthouse", () ->
                    BlockEntityType.Builder.of(com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity::new, LIGHTHOUSE.get()).build(null)
            );

    // --- Core Navigation & Vessels ---
    public static final RegistryObject<NauticalChartItem> NAUTICAL_CHART =
            ITEMS.register("nautical_chart", () -> new NauticalChartItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<RouteJournalItem> ROUTE_JOURNAL =
            ITEMS.register("route_journal", () -> new RouteJournalItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<DoubloonItem> DOUBLOON =
            ITEMS.register("doubloon", () -> new DoubloonItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<TradeLedgerItem> TRADE_LEDGER =
            ITEMS.register("trade_ledger", () -> new TradeLedgerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<SloopItem> SLOOP_ITEM =
            ITEMS.register("sloop", () -> new SloopItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CargoBoatItem> CARGO_BOAT_ITEM =
            ITEMS.register("cargo_boat", () -> new CargoBoatItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<TradeBoatItem> TRADE_BOAT_ITEM =
            ITEMS.register("trade_boat", () -> new TradeBoatItem(new Item.Properties().stacksTo(1)));

    // --- Trade Commodities & Goods ---
    /** Exotic spice blend -- eating grants Speed II + Haste I for 30 seconds. */
    public static final RegistryObject<SpiceSackItem> SPICE_SACK =
            ITEMS.register("spice_sack", () -> new SpiceSackItem(new Item.Properties()));

    /** Rare amber extract -- drinking grants Regeneration II (15s) with a Nausea side-effect (3s). */
    public static final RegistryObject<AmberVialItem> AMBER_VIAL =
            ITEMS.register("amber_vial", () -> new AmberVialItem(new Item.Properties()));

    /** Sea salt pouch -- right-click on edible in offhand to preserve it (adds Salted NBT tag). */
    public static final RegistryObject<SaltPouchItem> SALT_POUCH =
            ITEMS.register("salt_pouch", () -> new SaltPouchItem(new Item.Properties()));

    /** Luxury silk bale -- high-value trade currency, rarer than Doubloon, used in harbor trade offers. */
    public static final RegistryObject<SilkBaleItem> SILK_BALE =
            ITEMS.register("silk_bale", () -> new SilkBaleItem(new Item.Properties().stacksTo(16)));

    /** Hand-painted glazed ceramic from eastern ports -- high-demand luxury trade commodity. */
    public static final RegistryObject<FinePorcelainItem> FINE_PORCELAIN =
            ITEMS.register("fine_porcelain", () -> new FinePorcelainItem(new Item.Properties().stacksTo(16)));

    /** Rare shimmering gemstone dredged from deep ocean trenches -- prized ocean treasure. */
    public static final RegistryObject<LuminousPearlItem> LUMINOUS_PEARL =
            ITEMS.register("luminous_pearl", () -> new LuminousPearlItem(new Item.Properties().stacksTo(16)));

    /** Ornate brass navigational instrument mapping the stars and seas. */
    public static final RegistryObject<BrassAstrolabeItem> BRASS_ASTROLABE =
            ITEMS.register("brass_astrolabe", () -> new BrassAstrolabeItem(new Item.Properties().stacksTo(1)));

    /** Densely pressed block of aged tea leaves -- ancient currency across shipping routes. */
    public static final RegistryObject<TeaBrickItem> TEA_BRICK =
            ITEMS.register("tea_brick", () -> new TeaBrickItem(new Item.Properties().stacksTo(16)));

    /** Fragrant piece of rare timber from tropical archipelagos. */
    public static final RegistryObject<SandalwoodItem> SANDALWOOD =
            ITEMS.register("sandalwood", () -> new SandalwoodItem(new Item.Properties().stacksTo(16)));

    /** Dried pods of tropical orchids -- precious aromatic culinary spice. */
    public static final RegistryObject<VanillaBundleItem> VANILLA_BUNDLE =
            ITEMS.register("vanilla_bundle", () -> new VanillaBundleItem(new Item.Properties().stacksTo(16)));

    /** Spiraled petrified shell recovered from ocean floor strata. */
    public static final RegistryObject<AmmoniteFossilItem> AMMONITE_FOSSIL =
            ITEMS.register("ammonite_fossil", () -> new AmmoniteFossilItem(new Item.Properties().stacksTo(16)));

    /** Weathered nautical bottle sealed with wax, containing lost voyage secrets. */
    public static final RegistryObject<MessageInABottleItem> MESSAGE_IN_A_BOTTLE =
            ITEMS.register("message_in_a_bottle", () -> new MessageInABottleItem(new Item.Properties().stacksTo(16)));

    /** Weather forecasting sealed barometer vial filled with sensitive crystal solutions. */
    public static final RegistryObject<StormGlassItem> STORM_GLASS =
            ITEMS.register("storm_glass", () -> new StormGlassItem(new Item.Properties().stacksTo(1)));

    /** Polished seaworn wooden pipe favored by seasoned captains and sea dogs. */
    public static final RegistryObject<CaptainsPipeItem> CAPTAINS_PIPE =
            ITEMS.register("captains_pipe", () -> new CaptainsPipeItem(new Item.Properties().stacksTo(1)));

    /** Prestigious naval tricorne hat granting +20% sailing speed and 10% harbor fee discounts. */
    public static final RegistryObject<com.lexovian.currentsoftrade.item.CaptainsTricorneItem> CAPTAINS_TRICORNE =
            ITEMS.register("captains_tricorne", () -> new com.lexovian.currentsoftrade.item.CaptainsTricorneItem(new Item.Properties()));

    /** Weighted iron dredge used to scrape ocean depths and haul up lost cargo and relics. */
    public static final RegistryObject<com.lexovian.currentsoftrade.item.SalvageDredgeItem> SALVAGE_DREDGE =
            ITEMS.register("salvage_dredge", () -> new com.lexovian.currentsoftrade.item.SalvageDredgeItem(new Item.Properties()));

    // --- Menu Types ---
    public static final RegistryObject<MenuType<AnchorPointMenu>> ANCHOR_POINT_MENU =
            MENUS.register("anchor_point", () -> IForgeMenuType.create(AnchorPointMenu::new));

    public static final RegistryObject<MenuType<TravelMenu>> TRAVEL_MENU =
            MENUS.register("travel", () -> new MenuType<>(TravelMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<SendItemsMenu>> SEND_ITEMS_MENU =
            MENUS.register("send_items", () -> new MenuType<>(SendItemsMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<RequestTradeMenu>> REQUEST_TRADE_MENU =
            MENUS.register("request_trade", () -> new MenuType<>(RequestTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // --- Entity Types ---
    public static final RegistryObject<EntityType<TradeBoatEntity>> TRADE_BOAT =
            ENTITY_TYPES.register("trade_boat", () ->
                    EntityType.Builder.<TradeBoatEntity>of(TradeBoatEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("trade_boat")
            );

    public static final RegistryObject<EntityType<CargoBoatEntity>> CARGO_BOAT =
            ENTITY_TYPES.register("cargo_boat", () ->
                    EntityType.Builder.<CargoBoatEntity>of(CargoBoatEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("cargo_boat")
            );

    public static final RegistryObject<EntityType<SloopEntity>> SLOOP =
            ENTITY_TYPES.register("sloop", () ->
                    EntityType.Builder.<SloopEntity>of(SloopEntity::new, MobCategory.MISC)
                            .sized(2.6F, 0.85F)
                            .clientTrackingRange(12)
                            .build("sloop")
            );

    public static final RegistryObject<EntityType<WanderingSailorEntity>> WANDERING_SAILOR =
            ENTITY_TYPES.register("wandering_sailor", () ->
                    EntityType.Builder.<WanderingSailorEntity>of(
                            WanderingSailorEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("wandering_sailor")
            );

    // --- Village & POI Integration ---
    public static final RegistryObject<PoiType> ANCHOR_POINT_POI =
            POI_TYPES.register("anchor_point_poi", () ->
                    new PoiType(ImmutableSet.copyOf(ANCHOR_POINT.get().getStateDefinition().getPossibleStates()), 1, 1)
            );

    public static final RegistryObject<VillagerProfession> HARBORMASTER =
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
    public static final RegistryObject<CreativeModeTab> CURRENTS_TAB =
            CREATIVE_MODE_TABS.register("currents_of_trade_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.currents_of_trade"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> ANCHOR_POINT_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ANCHOR_POINT_ITEM.get());
                                output.accept(LIGHTHOUSE_ITEM.get());
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
    public CurrentsofTrade() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
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
        LOOT_MODIFIERS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(WANDERING_SAILOR.get(), WanderingSailorEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModPayloads::register);
        LOGGER.info("Currents of Trade common setup completed.");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ANCHOR_POINT_ITEM);
            event.accept(LIGHTHOUSE_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(CAPTAINS_TRICORNE);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NAUTICAL_CHART);
            event.accept(ROUTE_JOURNAL);
            event.accept(CAPTAINS_TRICORNE);
            event.accept(SALVAGE_DREDGE);
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
